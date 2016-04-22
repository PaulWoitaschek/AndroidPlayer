package de.paul_woitaschek.mediaplayer

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Implementation of the player using AntennaPods AudioPlayer internally
 *
 * @author Paul Woitaschek
 */
class AntennaPlayer(private val context: Context) : MediaPlayer {

    private val player: SonicAudioPlayer
    private val handler = Handler(context.mainLooper)

    private inline fun postOnMain(crossinline task: () -> Unit) {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            task()
        } else handler.post { task() }
    }

    init {
        val owning = org.antennapod.audio.MediaPlayer(context)
        player = SonicAudioPlayer(owning, context)

        owning.setOnErrorListener { mediaPlayer, i, j ->
            postOnMain { errorSubject.onNext(Unit) }
            false
        }
        owning.setOnCompletionListener { postOnMain { completionSubject.onNext(Unit) } }
        owning.setOnPreparedListener { postOnMain { preparedSubject.onNext(Unit) } }
    }


    override fun seekTo(to: Int) = player.seekTo(to)

    override fun isPlaying() = player.isPlaying

    override fun start() = player.start()

    override fun pause() = player.pause()

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

    override fun setWakeMode(mode: Int) = player.setWakeMode(context, mode)

    override val currentPosition: Int
        get() = player.currentPosition

    override val duration: Int
        get() = player.duration

    override var playbackSpeed: Float
        get() = player.currentSpeedMultiplier
        set(value) = player.setPlaybackSpeed(value)

    private val errorSubject = PublishSubject.create<Unit>()
    private val errorObservable = errorSubject.asObservable()

    private val preparedSubject = PublishSubject.create<Unit>()
    private val preparedObservable = preparedSubject.asObservable()

    private val completionSubject = PublishSubject.create<Unit>()
    private val completionObservable = completionSubject.asObservable()

    override val onError = errorObservable

    override val onCompletion = completionObservable

    override val onPrepared = preparedObservable
}