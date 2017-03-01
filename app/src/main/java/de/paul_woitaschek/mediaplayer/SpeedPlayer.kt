package de.paul_woitaschek.mediaplayer

import android.annotation.TargetApi
import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import de.paul_woitaschek.mediaplayer.internals.Sonic
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author James Falcon, Paul Woitaschek
 */
@Suppress("unused")
@TargetApi(16)
class SpeedPlayer(private val context: Context) : MediaPlayer {

  private var onError: (() -> Unit)? = null
  private var onCompletion: (() -> Unit)? = null
  private var onPrepared: (() -> Unit)? = null
  override var playbackSpeed = 1.0f

  override var duration: Int = 0
    private set

  override fun isPlaying() = state == State.STARTED

  private val handler = Handler(context.mainLooper)

  private val lock = ReentrantLock()
  private val decoderLock = Object()
  private val executor = Executors.newSingleThreadExecutor()
  private var wakeLock: PowerManager.WakeLock? = null
  private var track: AudioTrack? = null
  private var sonic: Sonic? = null
  private var extractor: MediaExtractor? = null
  private var codec: MediaCodec? = null
  private var uri: Uri? = null
  private var audioStreamType: Int = AudioManager.STREAM_MUSIC

  @Volatile private var continuing = false
  @Volatile private var isDecoding = false
  @Volatile private var flushCodec = false
  @Volatile private var state = State.IDLE
  @Suppress("DEPRECATION")
  private val decoderRunnable = Runnable {
    isDecoding = true
    val codec = codec!!
    codec.start()
    val inputBuffers = codec.inputBuffers
    var outputBuffers = codec.outputBuffers
    var sawInputEOS = false
    var sawOutputEOS = false
    while (!sawInputEOS && !sawOutputEOS && continuing) {
      if (state == State.PAUSED) {
        try {
          synchronized(decoderLock) {
            decoderLock.wait()
          }
        } catch (e: InterruptedException) {
          // Purposely not doing anything here
        }

        continue
      }
      val sonic = sonic!!
      sonic.speed = playbackSpeed
      sonic.pitch = 1f

      val inputBufIndex = codec.dequeueInputBuffer(200)
      if (inputBufIndex >= 0) {
        val dstBuf = inputBuffers[inputBufIndex]
        var sampleSize = extractor!!.readSampleData(dstBuf, 0)
        var presentationTimeUs: Long = 0
        if (sampleSize < 0) {
          sawInputEOS = true
          sampleSize = 0
        } else {
          presentationTimeUs = extractor!!.sampleTime
        }
        codec.queueInputBuffer(
          inputBufIndex,
          0,
          sampleSize,
          presentationTimeUs,
          if (sawInputEOS) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
        if (flushCodec) {
          codec.flush()
          flushCodec = false
        }
        if (!sawInputEOS) {
          extractor!!.advance()
        }
      }
      val info = MediaCodec.BufferInfo()
      var modifiedSamples = ByteArray(info.size)
      var res: Int
      do {
        res = codec.dequeueOutputBuffer(info, 200)
        if (res >= 0) {
          val chunk = ByteArray(info.size)
          outputBuffers[res].get(chunk)
          outputBuffers[res].clear()
          if (chunk.isNotEmpty()) {
            sonic.writeBytesToStream(chunk, chunk.size)
          } else {
            sonic.flushStream()
          }
          val available = sonic.availableBytes()
          if (available > 0) {
            if (modifiedSamples.size < available) {
              modifiedSamples = ByteArray(available)
            }
            sonic.readBytesFromStream(modifiedSamples, available)
            track!!.write(modifiedSamples, 0, available)
          }
          codec.releaseOutputBuffer(res, false)
          if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            sawOutputEOS = true
          }
        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
          outputBuffers = codec.outputBuffers
        } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          track!!.stop()
          lock.lock()
          try {
            track!!.release()
            val oFormat = codec.outputFormat

            initDevice(
              oFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
              oFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            outputBuffers = codec.outputBuffers
            track!!.play()
          } catch (e: IOException) {
            e.printStackTrace()
          } finally {
            lock.unlock()
          }
        }
      } while (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED || res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
    }

    codec.stop()
    track!!.stop()
    isDecoding = false
    if (continuing && (sawInputEOS || sawOutputEOS)) {
      state = State.PLAYBACK_COMPLETED

      handler.post {
        onCompletion?.invoke()
        stayAwake(false)
      }
    }
    synchronized(decoderLock) {
      decoderLock.notifyAll()
    }
  }

  private fun errorInWrongState(validStates: Iterable<State>, method: String) {
    if (!validStates.contains(state)) {
      error()
      throw IllegalStateException("Must not call $method in $state")
    }
  }

  override fun start() {
    errorInWrongState(validStatesForStart, "start")

    if (state == State.PLAYBACK_COMPLETED) {
      try {
        initStream()
        state = State.PREPARED
      } catch (e: IOException) {
        error()
        return
      }
    }
    when (state) {
      State.PREPARED -> {
        state = State.STARTED
        continuing = true
        track!!.play()
        decode()
        stayAwake(true)
      }
      State.STARTED -> {
      }
      State.PAUSED -> {
        state = State.STARTED
        synchronized(decoderLock) {
          decoderLock.notify()
        }
        track!!.play()
        stayAwake(true)
      }
      else -> throw AssertionError("Unexpected state $state")
    }
  }

  override fun reset() {
    errorInWrongState(validStatesForReset, "reset")

    stayAwake(false)
    lock.withLock {
      continuing = false
      try {
        if (state != State.PLAYBACK_COMPLETED) {
          while (isDecoding) {
            synchronized(decoderLock) {
              decoderLock.notify()
              decoderLock.wait()
            }
          }
        }
      } catch (e: InterruptedException) {
      }

      if (codec != null) {
        codec!!.release()
        codec = null
      }
      if (extractor != null) {
        extractor!!.release()
        extractor = null
      }
      if (track != null) {
        track!!.release()
        track = null
      }
      state = State.IDLE
    }
  }

  @Throws(IOException::class)
  private fun internalPrepare() {
    initStream()
    state = State.PREPARED
    doOnMain { onPrepared?.invoke() }
  }

  override fun seekTo(to: Int) {
    errorInWrongState(validStatesForSeekTo, "seekTo")
    when (state) {
      State.PREPARED,
      State.STARTED,
      State.PAUSED,
      State.PLAYBACK_COMPLETED -> {
        thread(isDaemon = true) {
          lock.withLock {
            if (track != null) {
              track!!.flush()
              flushCodec = true
              val internalTo = to.toLong() * 1000
              extractor!!.seekTo(internalTo, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }
          }
        }
      }
      else -> throw AssertionError("Unexpected state $state")
    }
  }

  override val currentPosition: Int
    get() {
      errorInWrongState(validStatesForCurrentPosition, "currentPosition")

      return when (state) {
        State.IDLE -> 0
        State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETED -> (extractor!!.sampleTime / 1000).toInt()
        else -> throw AssertionError("Unexpected state $state")
      }
    }

  override fun audioSessionId() = track?.audioSessionId ?: -1

  override fun pause() {
    errorInWrongState(validStatesForPause, "pause")

    when (state) {
      State.PLAYBACK_COMPLETED -> {
        state = State.PAUSED
        stayAwake(false)
      }
      State.STARTED, State.PAUSED -> {
        track!!.pause()
        state = State.PAUSED
        stayAwake(false)
      }
      else -> throw AssertionError("Unexpected state $state")
    }
  }

  @Throws(IOException::class)
  override fun prepare(uri: Uri) {
    errorInWrongState(validStatesForPrepare, "prepare")

    this.uri = uri

    try {
      internalPrepare()
    } catch(e: IOException) {
      error()
      throw IOException(e)
    }
  }

  override fun release() {
    onError = null
    onCompletion = null
    onPrepared = null
    reset()
  }

  override fun setAudioStreamType(streamType: Int) {
    audioStreamType = streamType
  }

  override fun setVolume(volume: Float) {
    if (Build.VERSION.SDK_INT >= 21) track?.setVolume(volume)
    else {
      @Suppress("DEPRECATION")
      track?.setStereoVolume(volume, volume)
    }
  }

  override fun prepareAsync(uri: Uri) {
    errorInWrongState(validStatesForPrepare, "prepareAsync")

    this.uri = uri

    state = State.PREPARING
    thread(isDaemon = true) {
      try {
        internalPrepare()
      } catch(e: IOException) {
        error()
      }
    }
  }

  override fun setWakeMode(mode: Int) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(mode, "CustomPlayer")
    wakeLock!!.setReferenceCounted(false)
  }

  @Throws(IOException::class)
  private fun initStream() {
    lock.withLock {
      extractor = MediaExtractor()
      if (uri != null) {
        extractor!!.setDataSource(context, uri!!, null)
      } else {
        error()
        throw IOException("Error at initializing stream")
      }
      val trackNum = 0
      val oFormat = extractor!!.getTrackFormat(trackNum)

      if (!oFormat.containsKeys(MediaFormat.KEY_SAMPLE_RATE, MediaFormat.KEY_CHANNEL_COUNT, MediaFormat.KEY_MIME, MediaFormat.KEY_DURATION)) {
        throw IOException("MediaFormat misses keys.")
      }

      val sampleRate = oFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      val channelCount = oFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      val mime = oFormat.getString(MediaFormat.KEY_MIME)
      duration = (oFormat.getLong(MediaFormat.KEY_DURATION) / 1000).toInt()

      initDevice(sampleRate, channelCount)
      extractor!!.selectTrack(trackNum)
      codec = MediaCodec.createDecoderByType(mime)
        .apply {
          configure(oFormat, null, null, 0)
        }
    }
  }

  private fun error() {
    state = State.ERROR
    stayAwake(false)
    doOnMain { onError?.invoke() }
  }

  private inline fun doOnMain(crossinline func: () -> Unit) {
    if (Thread.currentThread() == Looper.getMainLooper().thread) {
      func()
    } else {
      handler.post { func() }
    }
  }

  private fun Sonic.availableBytes() = numChannels * samplesAvailable() * 2

  private fun MediaFormat.containsKeys(vararg keys: String): Boolean = keys.all { containsKey(it) }

  private fun findFormatFromChannels(numChannels: Int) = when (numChannels) {
    1 -> AudioFormat.CHANNEL_OUT_MONO
    2 -> AudioFormat.CHANNEL_OUT_STEREO
    3 -> AudioFormat.CHANNEL_OUT_STEREO or AudioFormat.CHANNEL_OUT_FRONT_CENTER
    4 -> AudioFormat.CHANNEL_OUT_QUAD
    5 -> AudioFormat.CHANNEL_OUT_QUAD or AudioFormat.CHANNEL_OUT_FRONT_CENTER
    6 -> AudioFormat.CHANNEL_OUT_5POINT1
    7 -> AudioFormat.CHANNEL_OUT_5POINT1 or AudioFormat.CHANNEL_OUT_BACK_CENTER
    8 -> if (Build.VERSION.SDK_INT >= 23) {
      AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
    } else -1
    else -> -1 // Error
  }

  @Throws(IOException::class)
  private fun initDevice(sampleRate: Int, numChannels: Int) {
    lock.withLock {
      val format = findFormatFromChannels(numChannels)
      val minSize = AudioTrack.getMinBufferSize(sampleRate, format,
        AudioFormat.ENCODING_PCM_16BIT)

      if (minSize == AudioTrack.ERROR || minSize == AudioTrack.ERROR_BAD_VALUE) {
        throw IOException("getMinBufferSize returned " + minSize)
      }
      track = AudioTrack(audioStreamType, sampleRate, format,
        AudioFormat.ENCODING_PCM_16BIT, minSize * 4,
        AudioTrack.MODE_STREAM)
      sonic = Sonic(sampleRate, numChannels)
    }
  }

  private fun stayAwake(awake: Boolean) {
    wakeLock?.let {
      if (awake && !it.isHeld) {
        it.acquire()
      } else if (!awake && it.isHeld) {
        it.release()
      }
    }
  }

  override fun onError(action: (() -> Unit)?) {
    onError = action
  }

  override fun onCompletion(action: (() -> Unit)?) {
    onCompletion = action
  }

  override fun onPrepared(action: (() -> Unit)?) {
    onPrepared = action
  }

  private fun decode() {
    executor.execute(decoderRunnable)
  }

  private val validStatesForStart = EnumSet.of(State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETED)
  private val validStatesForReset = EnumSet.of(State.IDLE, State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETED, State.ERROR)
  private val validStatesForPrepare = EnumSet.of(State.IDLE)
  private val validStatesForCurrentPosition = EnumSet.of(State.IDLE, State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETED)
  private val validStatesForPause = EnumSet.of(State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETED)
  private val validStatesForSeekTo = EnumSet.of(State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETED)

  private enum class State {
    IDLE,
    ERROR,
    STARTED,
    PAUSED,
    PREPARED,
    PREPARING,
    PLAYBACK_COMPLETED
  }
}