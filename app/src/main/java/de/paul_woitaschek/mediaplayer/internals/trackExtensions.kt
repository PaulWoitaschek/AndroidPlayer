package de.paul_woitaschek.mediaplayer.internals

import android.annotation.TargetApi
import android.media.AudioFormat
import android.media.MediaFormat
import android.os.Build


@TargetApi(16)
internal fun MediaFormat.containsKeys(vararg keys: String): Boolean = keys.any { containsKey(it) }

internal fun Sonic.availableBytes() = numChannels * samplesAvailable() * 2

internal fun findFormatFromChannels(numChannels: Int) = when (numChannels) {
  1 -> AudioFormat.CHANNEL_OUT_MONO
  2 -> AudioFormat.CHANNEL_OUT_STEREO
  3 -> AudioFormat.CHANNEL_OUT_STEREO or AudioFormat.CHANNEL_OUT_FRONT_CENTER
  4 -> AudioFormat.CHANNEL_OUT_QUAD
  5 -> AudioFormat.CHANNEL_OUT_QUAD or AudioFormat.CHANNEL_OUT_FRONT_CENTER
  6 -> AudioFormat.CHANNEL_OUT_5POINT1
  7 -> AudioFormat.CHANNEL_OUT_5POINT1 or AudioFormat.CHANNEL_OUT_BACK_CENTER
  8 -> if (Build.VERSION.SDK_INT >= 23) {
    AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;
  } else -1
  else -> -1 // Error
}