package dev.omnitrix.app

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/** Omnitrix haptic vocabulary. */
object Haptics {
    private fun vibrate(context: Context, timings: LongArray, amplitudes: IntArray) {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** Dial pops up. */
    fun dialUp(context: Context) = vibrate(context, longArrayOf(0, 18, 50, 28), intArrayOf(0, 120, 0, 200))

    /** Slam: a rising surge into a heavy hit. */
    fun transform(context: Context) =
        vibrate(context, longArrayOf(0, 30, 30, 40, 30, 60, 40, 140), intArrayOf(0, 60, 0, 120, 0, 180, 0, 255))

    /** The four timeout beeps. */
    fun timeout(context: Context) =
        vibrate(context, longArrayOf(0, 90, 160, 90, 160, 90, 160, 220), intArrayOf(0, 255, 0, 255, 0, 255, 0, 255))

    /** Denied: no energy. */
    fun denied(context: Context) = vibrate(context, longArrayOf(0, 70, 60, 70), intArrayOf(0, 90, 0, 90))

    /** Voluntary revert. */
    fun revert(context: Context) = vibrate(context, longArrayOf(0, 120, 60, 40), intArrayOf(0, 200, 0, 80))
}
