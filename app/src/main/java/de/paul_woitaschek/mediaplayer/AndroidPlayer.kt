package de.paul_woitaschek.mediaplayer

import android.content.Context
import android.net.Uri
import android.media.MediaPlayer as AndroidMediaPlayer

/**
 * Delegates to [android.media.MediaPlayer]. Playback speed is not available as
 * [android.media.PlaybackParams] is not reliable. (Still crashes in N_MR1).
 *
 * @author Paul Woitaschek
 */
@Suppress("unused")
class AndroidPlayer(private val context: Context) : MediaPlayer {

  private var onError: (() -> Unit)? = null
  private var onCompletion: (() -> Unit)? = null
  private var onPrepared: (() -> Unit)? = null
  private val player = AndroidMediaPlayer()

  init {
    player.setOnErrorListener { _, _, _ ->
      onError?.invoke()
      false
    }
    player.setOnCompletionListener { onCompletion?.invoke() }
    player.setOnPreparedListener { onPrepared?.invoke() }
  }

  override fun seekTo(to: Int) = player.seekTo(to)

  override fun isPlaying() = player.isPlaying

  override fun start() {
    player.start()
  }

  override fun pause() = player.pause()

  override fun setWakeMode(mode: Int) = player.setWakeMode(context, mode)

  override val duration: Int
    get() = player.duration

  override var playbackSpeed: Float = 1F
    get() = 1F

  override fun release() = player.release()

  override fun audioSessionId() = player.audioSessionId

  override fun onError(action: (() -> Unit)?) {
    onError = action
  }

  override fun onCompletion(action: (() -> Unit)?) {
    onCompletion = action
  }

  override fun onPrepared(action: (() -> Unit)?) {
    onPrepared = action
  }

  override fun setAudioStreamType(streamType: Int) = player.setAudioStreamType(streamType)

  override fun setVolume(volume: Float) {
    player.setVolume(volume, volume)
  }

  override fun prepare(uri: Uri) {
    player.setDataSource(context, uri)
    player.prepare()
  }

  override fun prepareAsync(uri: Uri) {
    player.setDataSource(context, uri)
    player.prepareAsync()
  }

  override fun reset() = player.reset()

  override val currentPosition: Int
    get() = player.currentPosition
}