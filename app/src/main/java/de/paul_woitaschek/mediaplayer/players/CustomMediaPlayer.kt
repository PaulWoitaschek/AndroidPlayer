package de.paul_woitaschek.mediaplayer.players

import android.annotation.TargetApi
import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import de.paul_woitaschek.mediaplayer.logging.Log
import rx.subjects.PublishSubject
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

private fun MediaFormat.containsKeys(vararg keys: String): Boolean {
    for (key in keys) {
        if (!containsKey(key)) return false
    }
    return true
}

private fun Sonic.availableBytes(): Int {
    return numChannels * samplesAvailable() * 2
}

private fun findFormatFromChannels(numChannels: Int): Int {
    return when (numChannels) {
        1 -> AudioFormat.CHANNEL_OUT_MONO
        2 -> AudioFormat.CHANNEL_OUT_STEREO
        3 -> AudioFormat.CHANNEL_OUT_STEREO or AudioFormat.CHANNEL_OUT_FRONT_CENTER
        4 -> AudioFormat.CHANNEL_OUT_QUAD
        5 -> AudioFormat.CHANNEL_OUT_QUAD or AudioFormat.CHANNEL_OUT_FRONT_CENTER
        6 -> AudioFormat.CHANNEL_OUT_5POINT1
        7 -> AudioFormat.CHANNEL_OUT_5POINT1 or AudioFormat.CHANNEL_OUT_BACK_CENTER
        8 -> if (Build.VERSION.SDK_INT >= 23) {
            AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;
        } else {
            -1;
        }
        else -> -1 // Error
    }
}

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

 * @author James Falcon
 * *
 * @author Paul Woitaschek
 */
@TargetApi(16)
class CustomMediaPlayer(private val loggingEnabled: Boolean, private val context: Context) : MediaPlayer {

    private val log = Log(loggingEnabled, CustomMediaPlayer::class.java.simpleName)

    override var playbackSpeed = 1.0f

    override var duration: Int = 0
        private set

    override fun isPlaying(): Boolean {
        return state == State.STARTED
    }

    private val errorSubject = PublishSubject.create<Unit>()
    override val onError = errorSubject
    private val completionSubject = PublishSubject.create<Unit>()
    private val preparedSubject = PublishSubject.create<Unit>()
    override val onCompletion = completionSubject
    override val onPrepared = preparedSubject
    private val lock = ReentrantLock()
    private val decoderLock = Object()
    private val executor = Executors.newSingleThreadExecutor()
    private var wakeLock: PowerManager.WakeLock? = null
    private var track: AudioTrack? = null
    private var sonic: Sonic? = null
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var path: String? = null
    private var uri: Uri? = null

    @Volatile private var continuing = false
    @Volatile private var isDecoding = false
    @Volatile private var flushCodec = false
    @Volatile private var state = State.IDLE
    @Suppress("DEPRECATION")
    private val decoderRunnable = Runnable {
        isDecoding = true
        codec!!.start()
        val inputBuffers = codec!!.inputBuffers
        var outputBuffers = codec!!.outputBuffers
        var sawInputEOS = false
        var sawOutputEOS = false
        while (!sawInputEOS && !sawOutputEOS && continuing) {
            if (state == State.PAUSED) {
                try {
                    synchronized (decoderLock) {
                        decoderLock.wait()
                    }
                } catch (e: InterruptedException) {
                    // Purposely not doing anything here
                }

                continue
            }
            if (sonic != null) {
                sonic!!.speed = playbackSpeed
                sonic!!.pitch = 1f
            }

            val inputBufIndex = codec!!.dequeueInputBuffer(200)
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
                codec!!.queueInputBuffer(
                        inputBufIndex,
                        0,
                        sampleSize,
                        presentationTimeUs,
                        if (sawInputEOS) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                if (flushCodec) {
                    codec!!.flush()
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
                res = codec!!.dequeueOutputBuffer(info, 200)
                if (res >= 0) {
                    val chunk = ByteArray(info.size)
                    outputBuffers[res].get(chunk)
                    outputBuffers[res].clear()
                    if (chunk.size > 0) {
                        sonic!!.writeBytesToStream(chunk, chunk.size)
                    } else {
                        sonic!!.flushStream()
                    }
                    val available = sonic!!.availableBytes()
                    if (available > 0) {
                        if (modifiedSamples.size < available) {
                            modifiedSamples = ByteArray(available)
                        }
                        sonic!!.readBytesFromStream(modifiedSamples, available)
                        track!!.write(modifiedSamples, 0, available)
                    }
                    codec!!.releaseOutputBuffer(res, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = codec!!.outputBuffers
                } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track!!.stop()
                    lock.lock()
                    try {
                        track!!.release()
                        val oFormat = codec!!.outputFormat

                        initDevice(
                                oFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                oFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                        outputBuffers = codec!!.outputBuffers
                        track!!.play()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } finally {
                        lock.unlock()
                    }
                }
            } while (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED || res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
        }

        codec!!.stop()
        track!!.stop()
        isDecoding = false
        if (continuing && (sawInputEOS || sawOutputEOS)) {
            state = State.PLAYBACK_COMPLETED
            log.d { "State changed to $state " }
            val t = Thread(Runnable {
                completionSubject.onNext(Unit)
                stayAwake(false)
            })
            t.isDaemon = true
            t.start()
        }
        synchronized (decoderLock) {
            decoderLock.notifyAll()
        }
    }

    private fun errorInWrongState(validStates: Iterable<State>, method: String) {
        if (!validStates.contains(state)) {
            error(method)
            throw IllegalStateException("Must not call $method in $state")
        }
    }

    override fun start() {
        errorInWrongState(validStatesForStart, "start")

        log.d { "start called in state $state" }
        if (state == State.PLAYBACK_COMPLETED) {
            try {
                initStream()
                state = State.PREPARED
            } catch (e: IOException) {
                e.printStackTrace()
                error("start")
                return
            }
        }
        when (state) {
            State.PREPARED -> {
                state = State.STARTED
                log.d { "State changed to $state" }
                continuing = true
                track!!.play()
                decode()
                stayAwake(true)
            }
            State.STARTED -> {
            }
            State.PAUSED -> {
                state = State.STARTED
                log.d { "State changed to: $state with path=$path" }
                synchronized (decoderLock) {
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

        log.d { "reset called in state $state" }
        stayAwake(false)
        lock.lock()
        try {
            continuing = false
            try {
                if (state != State.PLAYBACK_COMPLETED) {
                    while (isDecoding) {
                        synchronized (decoderLock) {
                            decoderLock.notify()
                            decoderLock.wait()
                        }
                    }
                }
            } catch (e: InterruptedException) {
                log.e(e) { "Interrupted in reset while waiting for decoder thread to stop." }
            }

            if (codec != null) {
                codec!!.release()
                log.d { "releasing codec" }
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
            log.d { "State changed to $state" }
        } finally {
            lock.unlock()
        }
    }

    private fun internalPrepare() {
        try {
            initStream()
            state = State.PREPARED
            preparedSubject.onNext(Unit)
        } catch(io: IOException) {
            error("prepareAsync")
        }
    }

    override fun seekTo(to: Int) {
        errorInWrongState(validStatesForSeekTo, "seekTo")
        when (state) {
            State.PREPARED,
            State.STARTED,
            State.PAUSED,
            State.PLAYBACK_COMPLETED -> {
                val t = Thread(Runnable {
                    lock.lock()
                    try {
                        if (track != null) {
                            track!!.flush()
                            flushCodec = true
                            val internalTo = to.toLong() * 1000
                            extractor!!.seekTo(internalTo, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        }
                    } finally {
                        lock.unlock()
                    }
                })
                t.isDaemon = true
                t.start()
            }
            else -> throw AssertionError("Unexpected state $state")
        }
    }

    override val currentPosition: Int
        get() {
            errorInWrongState(validStatesForCurrentPosition, "currentPosition")

            return when (state) {
                State.IDLE -> 0
                State.PREPARED, State.STARTED, State.PAUSED, State.STOPPED, State.PLAYBACK_COMPLETED -> (extractor!!.sampleTime / 1000).toInt()
                else -> throw AssertionError("Unexpected state $state")
            }
        }

    override fun pause() {
        errorInWrongState(validStatesForPause, "pause")

        log.d { "pause called" }
        when (state) {
            State.PLAYBACK_COMPLETED -> {
                state = State.PAUSED
                log.d { "State changed to $state" }
                stayAwake(false)
            }
            State.STARTED, State.PAUSED -> {
                track!!.pause()
                state = State.PAUSED
                log.d { "State changed to $state" }
                stayAwake(false)
            }
            else -> throw AssertionError("Unexpected state $state")
        }
    }

    override fun prepare(file: File) {
        errorInWrongState(validStatesForPrepare, "prepare")
        log.d { "prepare $file" }

        this.path = file.absolutePath
        this.uri = null

        internalPrepare()
    }

    override fun prepareAsync(file: File) {
        errorInWrongState(validStatesForPrepare, "prepareAsync")
        log.d { "prepareAsync $file" }

        this.path = file.absolutePath
        this.uri = null

        state = State.PREPARING
        thread(isDaemon = true) {
            internalPrepare()
        }
    }

    override fun prepare(uri: Uri) {
        errorInWrongState(validStatesForPrepare, "prepare")
        log.d { "prepare $uri" }

        this.path = null
        this.uri = uri

        internalPrepare()
    }

    override fun prepareAsync(uri: Uri) {
        errorInWrongState(validStatesForPrepare, "prepareAsync")
        log.d { "prepareAsync $uri" }

        this.path = null
        this.uri = null

        state = State.PREPARING
        thread(isDaemon = true) {
            internalPrepare()
        }
    }

    override fun setWakeMode(mode: Int) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(mode, "CustomPlayer")
        wakeLock!!.setReferenceCounted(false)
    }

    @Throws(IOException::class)
    private fun initStream() {
        log.d { "initStream called in state $state" }
        lock.lock()
        try {
            extractor = MediaExtractor()
            if (path != null) {
                extractor!!.setDataSource(path)
            } else if (uri != null) {
                extractor!!.setDataSource(context, uri!!, null)
            } else {
                error("initStream")
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
            duration = (oFormat.getLong(MediaFormat.KEY_DURATION) / 1000).toInt();

            log.d { "Sample rate $sampleRate" }
            log.d { "Mime type $mime" }
            initDevice(sampleRate, channelCount)
            extractor!!.selectTrack(trackNum)
            codec = MediaCodec.createDecoderByType(mime)
            codec!!.configure(oFormat, null, null, 0)
        } finally {
            lock.unlock()
        }
    }

    private fun error(methodName: String) {
        log.d { "Error in $methodName at state=$state" }
        state = State.ERROR
        stayAwake(false)
        errorSubject.onNext(Unit)
    }


    /**
     * Initializes the basic audio track to be able to playback.

     * @param sampleRate  The sample rate of the track
     * *
     * @param numChannels The number of channels available in the track.
     */
    @Throws(IOException::class)
    private fun initDevice(sampleRate: Int, numChannels: Int) {
        log.d { "initDevice called in state $state" }
        lock.lock()
        try {
            val format = findFormatFromChannels(numChannels)
            val minSize = AudioTrack.getMinBufferSize(sampleRate, format,
                    AudioFormat.ENCODING_PCM_16BIT)

            if (minSize == AudioTrack.ERROR || minSize == AudioTrack.ERROR_BAD_VALUE) {
                log.d { "minSize=$minSize" }
                throw IOException("getMinBufferSize returned " + minSize)
            }
            track = AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, format,
                    AudioFormat.ENCODING_PCM_16BIT, minSize * 4,
                    AudioTrack.MODE_STREAM)
            sonic = Sonic(sampleRate, numChannels)
        } finally {
            lock.unlock()
        }
    }

    private fun stayAwake(awake: Boolean) {
        if (wakeLock != null) {
            if (awake && !wakeLock!!.isHeld) {
                wakeLock!!.acquire()
            } else if (!awake && wakeLock!!.isHeld) {
                wakeLock!!.release()
            }
        }
    }

    private fun decode() {
        log.d { "decode called ins state=$state" }
        executor.execute(decoderRunnable)
    }

    private val validStatesForStart = EnumSet.of(State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETED)
    private val validStatesForReset = EnumSet.of(State.IDLE, State.PREPARED, State.STARTED, State.PAUSED, State.STOPPED, State.PLAYBACK_COMPLETED, State.ERROR)
    private val validStatesForPrepare = EnumSet.of(State.IDLE)
    private val validStatesForCurrentPosition = EnumSet.of(State.IDLE, State.PREPARED, State.STARTED, State.PAUSED, State.STOPPED, State.PLAYBACK_COMPLETED)
    private val validStatesForPause = EnumSet.of(State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETED)
    private val validStatesForSeekTo = EnumSet.of(State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETED)

    private enum class State {
        IDLE,
        ERROR,
        STARTED,
        PAUSED,
        PREPARED,
        PREPARING,
        STOPPED,
        PLAYBACK_COMPLETED
    }
}