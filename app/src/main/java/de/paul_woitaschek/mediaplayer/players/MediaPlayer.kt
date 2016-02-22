package de.paul_woitaschek.mediaplayer.players

import rx.Observable


/**
 * Abstraction of android.media.MediaPlayer.
 *
 * @author Paul Woitaschek
 */
interface MediaPlayer {

    fun setDataSource(path: String)

    fun seekTo(to: Int)

    fun isPlaying(): Boolean

    fun start()

    fun pause()

    fun prepare()

    fun prepareAsync()

    fun reset()

    fun setWakeMode(mode: Int)

    val currentPosition: Int

    val duration: Int

    var playbackSpeed: Float

    val onError: Observable<Unit>

    val onCompletion: Observable<Unit>

    val onPrepared: Observable<Unit>
}