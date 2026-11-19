package com.redcore.booster.booster

import android.content.Context
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 4D vibration: listens to the phone's audio output (via Android's Visualizer on the output mix) and
 * pulses the vibration motor on bass hits — explosions, gunfire, engine rumble. Best effort: some phones
 * don't allow apps to analyse the global audio mix, in which case [start] reports it.
 * Requires the microphone permission because Android groups audio analysis under it; nothing is recorded.
 */
class Haptics4D(private val context: Context) {
    private var viz: Visualizer? = null
    private var avg = 0f
    private var last = 0L
    @Volatile var strength = 0.7f

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    val running get() = viz != null

    fun start(strength: Float): ActionResult {
        this.strength = strength
        if (viz != null) return ActionResult("4D vibration", ActionState.APPLIED, "Already running.")
        if (vibrator?.hasVibrator() != true) return ActionResult("4D vibration", ActionState.SKIPPED, "This phone has no vibration motor.")
        if (!Permissions.has(context, android.Manifest.permission.RECORD_AUDIO))
            return ActionResult("4D vibration", ActionState.BLOCKED, "Allow Microphone so REDCORE can feel the game's sound (nothing is recorded).")
        return try {
            val v = Visualizer(0)
            v.captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(256).coerceAtMost(512)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, sr: Int) = Unit
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, sr: Int) { if (fft != null) onFft(fft) }
            }, Visualizer.getMaxCaptureRate(), false, true)
            v.enabled = true
            viz = v
            ActionResult("4D vibration", ActionState.APPLIED, "Vibrates on bass hits in the game's audio.")
        } catch (t: Throwable) {
            stop()
            ActionResult("4D vibration", ActionState.BLOCKED, "This phone doesn't let apps analyse other apps' audio.")
        }
    }

    private fun onFft(fft: ByteArray) {
        // Bins 1..4 cover roughly 40–350 Hz at common sample rates: the "impact" band.
        var e = 0f
        for (k in 1..4) {
            if (2 * k + 1 >= fft.size) break
            val re = fft[2 * k].toFloat(); val im = fft[2 * k + 1].toFloat()
            e += re * re + im * im
        }
        avg = if (avg == 0f) e else avg * 0.93f + e * 0.07f
        val now = SystemClock.uptimeMillis()
        if (e > 300f && e > avg * 2.3f && now - last > 85) {
            last = now
            val punch = ((e / (avg * 2.3f)) - 1f).coerceIn(0f, 1f)
            val amp = ((70 + 185 * punch) * strength).toInt().coerceIn(1, 255)
            pulse(22 + (punch * 18).toLong(), amp)
        }
    }

    private fun pulse(ms: Long, amp: Int) {
        val vib = vibrator ?: return
        runCatching {
            vib.vibrate(VibrationEffect.createOneShot(ms, if (vib.hasAmplitudeControl()) amp else VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /** Short tick used as trigger feedback. */
    fun tick() = pulse(12, (140 * strength).toInt().coerceIn(1, 255))

    fun stop() {
        runCatching { viz?.enabled = false }
        runCatching { viz?.release() }
        viz = null; avg = 0f
    }
}
