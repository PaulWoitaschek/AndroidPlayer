package de.paul_woitaschek.mediaplayer

import android.net.Uri
import java.io.IOException


/**
 * Abstraction of android.media.MediaPlayer.
 *
 * @author Paul Woitaschek
 */
interface MediaPlayer {

  fun isPlaying(): Boolean

  fun pause()

  @Throws(IOException::class)
  fun prepare(uri: Uri)

  fun prepareAsync(uri: Uri)

  fun reset()

  fun seekTo(to: Int)

  fun setVolume(volume: Float)

  fun setWakeMode(mode: Int)

  fun start()

  val currentPosition: Int

  val duration: Int

  var playbackSpeed: Float

  var onError: (() -> Unit)?

  var onCompletion: (() -> Unit)?

  var onPrepared: (() -> Unit)?
}