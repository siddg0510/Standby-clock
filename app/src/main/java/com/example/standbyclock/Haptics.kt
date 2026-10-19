package com.example.standbyclock

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/** Short tactile feedback, used for taps and counter presses. Respects the haptics setting. */
object Haptics {

    fun tap(context: Context) {
        if (!Settings(context).haptics) return
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
