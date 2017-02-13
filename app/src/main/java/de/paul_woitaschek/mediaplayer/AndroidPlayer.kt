package de.paul_woitaschek.mediaplayer

import android.content.Context
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import io.reactivex.subjects.PublishSubject
import java.io.File
import android.media.MediaPlayer as AndroidMediaPlayer

/**
 * Delegates to android.media.MediaPlayer. Playback speed will be available from api 23 on
 *
 * @author Paul Woitaschek
 */
class AndroidPlayer(private val context: Context) : MediaPlayer {

  private val player = AndroidMediaPlayer()

  private val errorSubject = PublishSubject.create<Unit>()
  private val errorObservable = errorSubject.hide()!!

  private val preparedSubject = PublishSubject.create<Unit>()
  private val preparedObservable = preparedSubject.hide()!!

  private val completionSubject = PublishSubject.create<Unit>()
  private val completionObservable = completionSubject.hide()!!

  init {
    player.setOnErrorListener { mediaPlayer, i, j ->
      errorSubject.onNext(Unit)
      false
    }
    player.setOnCompletionListener { completionSubject.onNext(Unit) }
    player.setOnPreparedListener { preparedSubject.onNext(Unit) }
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

  override fun prepare(file: File) {
    player.setDataSource(file.absolutePath)
    player.prepare()
  }

  override fun prepareAsync(file: File) {
    player.setDataSource(file.absolutePath)
    player.prepareAsync()
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

  override val onError = errorObservable

  override val onCompletion = completionObservable

  override val onPrepared = preparedObservable
}