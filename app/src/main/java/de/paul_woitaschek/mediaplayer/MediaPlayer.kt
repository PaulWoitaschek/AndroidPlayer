package de.paul_woitaschek.mediaplayer

import android.net.Uri
import java.io.File
import java.io.IOException


/**
 * Abstraction of android.media.MediaPlayer.
 *
 * @author Paul Woitaschek
 */
interface MediaPlayer {

  fun seekTo(to: Int)

  fun isPlaying(): Boolean

  fun start()

  fun pause()

  @Throws(IOException::class)
  fun prepare(file: File)

  fun prepareAsync(file: File)

  @Throws(IOException::class)
  fun prepare(uri: Uri)

  fun prepareAsync(uri: Uri)

  fun reset()

  fun setWakeMode(mode: Int)

  val currentPosition: Int

  val duration: Int

  var playbackSpeed: Float

  var onError: (() -> Unit)?

  var onCompletion: (() -> Unit)?

  var onPrepared: (() -> Unit)?
}