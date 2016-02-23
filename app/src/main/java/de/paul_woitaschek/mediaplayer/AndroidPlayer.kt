package de.paul_woitaschek.mediaplayer

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import rx.subjects.PublishSubject
import java.io.File

/**
 * Delegates to android.media.MediaPlayer.
 *
 * @author Paul Woitaschek
 */
class AndroidPlayer(private val context: Context) : de.paul_woitaschek.mediaplayer.MediaPlayer {

    private val player = MediaPlayer()

    private val errorSubject = PublishSubject.create<Unit>()

    private val errorObservable = errorSubject.asObservable()

    private val preparedSubject = PublishSubject.create<Unit>()

    private val preparedObservable = preparedSubject.asObservable()

    private val completionSubject = PublishSubject.create<Unit>()

    private val completionObservable = completionSubject.asObservable()

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

    override var playbackSpeed: Float = 1F

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