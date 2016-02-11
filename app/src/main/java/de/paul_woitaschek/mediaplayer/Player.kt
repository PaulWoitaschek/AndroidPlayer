/*
 * This file is part of Material Audiobook Player.
 *
 * Material Audiobook Player is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * Material Audiobook Player is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Material Audiobook Player. If not, see <http://www.gnu.org/licenses/>.
 * /licenses/>.
 */

package de.paul_woitaschek.mediaplayer

import android.content.Context
import android.os.PowerManager
import de.paul_woitaschek.mediaplayer.logging.Log
import de.paul_woitaschek.mediaplayer.players.AndroidPlayer
import de.paul_woitaschek.mediaplayer.players.CustomMediaPlayer
import rx.subjects.PublishSubject
import java.io.File
import java.io.IOException

/**
 * The media player.
 *
 * @author Paul Woitaschek
 */
class Player(private val type: Type, context: Context, val loggingEnabled: Boolean) {

    private val log = Log(loggingEnabled, Player::class.java.simpleName)

    private val mediaPlayer = if (type == Type.CUSTOM) {
        CustomMediaPlayer(loggingEnabled, context)
    } else {
        AndroidPlayer(context)
    }

    private var state = State.NONE
    private var currentFile: File? = null

    init {
        mediaPlayer.onError
                .subscribe {
                    log.d { "onError at $currentFile" }
                    mediaPlayer.reset()
                    state = State.NONE
                    errorSubject.onNext(Unit)
                }

        mediaPlayer.onCompletion
                .subscribe {
                    if (currentFile != null) {
                        try {
                            prepare(currentFile!!)
                        } catch(e: IOException) {
                            log.e(e) { "Error at re-preparing $currentFile in onCompletion." }
                        }
                    }
                    completionSubject.onNext(Unit)
                }

        mediaPlayer.setWakeMode(PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE)
    }


    /**
     * Prepares an audio file.
     */
    fun prepare(file: File) {
        currentFile = file
        mediaPlayer.reset()
        mediaPlayer.setDataSource(file.absolutePath)
        mediaPlayer.prepare()
        state = State.PREPARED
    }

    /**
     * The current position in the track.
     */
    var currentPosition: Int
        get() = when (state) {
            State.PREPARED, State.PLAYING -> mediaPlayer.currentPosition
            else -> 0
        }
        set(value) {
            when (state) {
                State.PREPARED, State.PLAYING -> {
                    mediaPlayer.seekTo(value)
                }
                else -> log.e { "Get current position called in state $state" }
            }
        }

    /**
     * If true the player will start as soon as he is prepared
     */
    var playing: Boolean
        get() = mediaPlayer.isPlaying()
        set(value) {
            if (value) {
                when (state) {
                    State.PREPARED, State.PLAYING -> {
                        mediaPlayer.start()
                        state = State.PLAYING
                    }
                    else -> log.e { "Play called in state $state" }
                }
            } else {
                when (state) {
                    State.PLAYING -> {
                        mediaPlayer.pause()
                        state = State.PREPARED
                    }
                    else -> log.e { "Pause called in state $state" }
                }
            }
        }

    /**
     * The playback rate. 1.0 is normal
     */
    var playbackSpeed: Float
        get() = mediaPlayer.playbackSpeed
        set(value) {
            mediaPlayer.playbackSpeed = value
        }

    private val errorSubject = PublishSubject.create<Unit>()

    private val completionSubject = PublishSubject.create<Unit>()

    /**
     * An observable that emits when an error is detected
     */
    val errorObservable = errorSubject.asObservable()

    /**
     * An observable that emits once a track is finished.
     */
    val completionObservable = completionSubject.asObservable()


    /**
     * The duration of the current track
     */
    val duration: Int
        get() = when (state) {
            State.PREPARED, State.PLAYING -> mediaPlayer.duration
            else -> 0
        }

    private enum class State {
        NONE,
        PREPARED,
        PLAYING
    }

    enum class Type {
        CUSTOM,
        ANDROID
    }
}