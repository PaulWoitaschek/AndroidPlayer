package de.paul_woitaschek.mediaplayer.logging

import android.util.Log

/**
 * Created by ph1b on 11/02/16.
 */
internal class Log(val loggingEnabled: Boolean, val tag: String) {

    inline fun d(message: () -> String) {
        if (loggingEnabled) {
            val toLog = message.invoke()
            Log.d(tag, toLog)
        }
    }

    inline fun e(message: () -> String) {
        if (loggingEnabled) {
            val toLog = message.invoke()
            Log.e(tag, toLog)
        }
    }

    inline fun e(throwable: Throwable, message: () -> String) {
        if (loggingEnabled) {
            val toLog = message.invoke()
            Log.e(tag, toLog, throwable)
        }
    }
}