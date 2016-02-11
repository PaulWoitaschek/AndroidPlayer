package de.paul_woitaschek.mediaplayer

import android.content.Context
import rx.subjects.PublishSubject

/**
 * Delegates to android.media.MediaPlayer.
 *
 * @author Paul Woitaschek
 */
class AndroidPlayer(private val context: Context) : MediaPlayer {

    private val player = android.media.MediaPlayer()

    init {
        player.setOnErrorListener { mediaPlayer, i, j ->
            errorSubject.onNext(Unit)
            false
        }
        player.setOnCompletionListener { completionSubject.onNext(Unit) }
    }

    override fun seekTo(to: Int) {
        player.seekTo(to)
    }

    override fun isPlaying(): Boolean {
        return player.isPlaying
    }

    override fun start() {
        player.start()
    }

    override fun pause() {
        player.pause()
    }

    override fun setWakeMode(mode: Int) {
        player.setWakeMode(context, mode)
    }

    override val duration: Int
        get() = player.duration

    override var playbackSpeed: Float = 1F

    override fun setDataSource(path: String) {
        player.setDataSource(path)
    }

    override fun prepare() {
        player.prepare()
    }

    override fun reset() {
        player.reset()
    }

    override val currentPosition: Int
        get() = player.currentPosition

    private val errorSubject = PublishSubject.create<Unit>()

    override val onError = errorSubject.asObservable()

    private val completionSubject = PublishSubject.create<Unit>()

    override val onCompletion = completionSubject.asObservable()
}