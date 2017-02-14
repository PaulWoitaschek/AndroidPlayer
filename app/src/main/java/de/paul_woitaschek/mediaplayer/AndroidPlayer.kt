package de.paul_woitaschek.mediaplayer

import android.content.Context
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.media.MediaPlayer as AndroidMediaPlayer

/**
 * Delegates to android.media.MediaPlayer. Playback speed will be available from api 23 on
 *
 * @author Paul Woitaschek
 */
class AndroidPlayer(private val context: Context) : MediaPlayer {

  override var onError: (() -> Unit)? = null
  override var onCompletion: (() -> Unit)? = null
  override var onPrepared: (() -> Unit)? = null

  private val player = AndroidMediaPlayer()

  init {
    player.setOnErrorListener { mediaPlayer, i, j ->
      onError?.invoke()
      false
    }
    player.setOnCompletionListener { onCompletion?.invoke() }
    player.setOnPreparedListener { onPrepared?.invoke() }
  }

  override fun seekTo(to: Int) = player.seekTo(to)

  override fun isPlaying() = player.isPlaying

  override fun start() = player.start()

  override fun pause() = player.pause()

  override fun setWakeMode(mode: Int) = player.setWakeMode(context, mode)

  override val duration: Int
    get() = player.duration

  override var playbackSpeed: Float
    get() = if (Build.VERSION.SDK_INT >= 23) {
      player.playbackParams?.speed ?: 1F
    } else 1F
    set(value) {
      if (Build.VERSION.SDK_INT >= 23) {
        player.playbackParams = PlaybackParams().apply {
          speed = value
        }
      }
    }

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