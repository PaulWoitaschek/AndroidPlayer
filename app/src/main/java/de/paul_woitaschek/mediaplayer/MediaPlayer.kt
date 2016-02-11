package de.paul_woitaschek.mediaplayer

import android.content.Context
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

    fun prepare()

    fun start()

    fun pause()

    fun reset()

    fun setWakeMode(context: Context, mode: Int)

    val currentPosition: Int

    val duration: Int

    var playbackSpeed: Float

    val onError: Observable<Unit>

    val onCompletion: Observable<Unit>
}