package com.redcore.booster.booster

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.redcore.booster.data.EqPreset

/**
 * Sound equalizer for games, applied to Android's global output mix (audio session 0).
 * Many phones honour this; some ignore it — the result tells the user which.
 */
class GameAudio {
    private var eq: Equalizer? = null
    private var loud: LoudnessEnhancer? = null
    var active: EqPreset = EqPreset.OFF
        private set

    fun apply(p: EqPreset): ActionResult {
        release()
        if (p == EqPreset.OFF) return ActionResult("Sound EQ", ActionState.SKIPPED, "Off.")
        return try {
            val e = Equalizer(0, 0)
            val range = e.bandLevelRange
            for (b in 0 until e.numberOfBands) {
                val hz = e.getCenterFreq(b.toShort()) / 1000
                e.setBandLevel(b.toShort(), levelFor(p, hz).coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            e.enabled = true
            eq = e
            if (p == EqPreset.LOUD) loud = LoudnessEnhancer(0).apply { setTargetGain(650); enabled = true }
            active = p
            ActionResult("Sound EQ", ActionState.APPLIED, "${p.title} preset on.")
        } catch (t: Throwable) {
            release()
            ActionResult("Sound EQ", ActionState.BLOCKED, "This phone doesn't let apps equalize other apps' audio.")
        }
    }

    fun release() {
        runCatching { eq?.release() }; eq = null
        runCatching { loud?.release() }; loud = null
        active = EqPreset.OFF
    }

    companion object {
        /** Band gain in millibels for a preset at a band centre frequency. */
        fun levelFor(p: EqPreset, hz: Int): Int = when (p) {
            EqPreset.OFF -> 0
            EqPreset.FOOTSTEPS -> when { hz < 120 -> -500; hz < 400 -> -200; hz in 1000..5000 -> 700; else -> 200 }
            EqPreset.BASS -> when { hz < 120 -> 800; hz < 400 -> 500; hz < 2000 -> 0; else -> -100 }
            EqPreset.VOICE -> when { hz < 150 -> -400; hz in 300..3500 -> 500; else -> 0 }
            EqPreset.LOUD -> 300
        }
    }
}
