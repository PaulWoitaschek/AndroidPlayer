package de.paul_woitaschek.mediaplayer.players

import android.content.Context
import android.media.MediaPlayer
import rx.subjects.PublishSubject

/**
 * Delegates to android.media.MediaPlayer.
 *
 * @author Paul Woitaschek
 */
 class AndroidPlayer(private val context: Context) : de.paul_woitaschek.mediaplayer.players.MediaPlayer {

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

    override fun setDataSource(path: String) = player.setDataSource(path)

    override fun prepare() = player.prepare()

    override fun prepareAsync() = player.prepareAsync()

    override fun reset() = player.reset()

    override val currentPosition: Int
        get() = player.currentPosition

    override val onError = errorObservable

    override val onCompletion = completionObservable

    override val onPrepared = preparedObservable
}