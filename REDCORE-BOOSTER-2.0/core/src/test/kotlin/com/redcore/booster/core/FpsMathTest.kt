package com.redcore.booster.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FpsMathTest {
    private fun latency(now: Long, frames: List<Long>, period: Long = 8_333_333) =
        "$period\n" + frames.sorted().joinToString("\n") { "${it - 1000} $it ${it - 500}" } + "\n0 9223372036854775807 0\n0 0 0\n"

    @Test fun steady120() {
        val now = 50_000_000_000L
        val frames = (0 until 120).map { now - it * 8_333_333L }
        val r = FpsMath.parseLatency(latency(now, frames), now)
        assertEquals(120, r.fps)
        assertEquals(120, r.refreshHz)
    }

    @Test fun highRefreshIsNotCappedByTheHistorySize() {
        // SurfaceFlinger keeps 128 frames: at 144 FPS that's < 1 s. Counting frames would say 128.
        val now = 80_000_000_000L
        val frames = (0 until 128).map { now - it * 6_944_444L }
        assertEquals(144, FpsMath.parseLatency(latency(now, frames, 6_944_444), now).fps)
    }

    @Test fun lowFps() {
        val now = 10_000_000_000L
        val frames = (0 until 30).map { now - it * 33_333_333L }
        assertEquals(30, FpsMath.parseLatency(latency(now, frames), now).fps)
    }

    @Test fun staleFramesGiveNoNumber() {
        val now = 20_000_000_000L
        val frames = (0 until 60).map { now - 2_000_000_000L - it * 16_666_666L } // last frame 2 s ago
        val r = FpsMath.parseLatency(latency(now, frames), now)
        assertNull("a paused game or wrong layer must not read as 0 or 60 FPS", r.fps)
        assertNotNull(r.newestAgeNs)
    }

    @Test fun foreignClockIsRejected() {
        val now = 50_000_000_000L
        val frames = (0 until 60).map { now - it * 16_666_666L }
        assertNull(FpsMath.parseLatency(latency(now, frames), now + 60_000_000_000L).fps)
        assertNull(FpsMath.parseLatency(latency(now, frames), now - 30_000_000_000L).fps)
    }

    @Test fun emptyOrPendingOnly() {
        assertNull(FpsMath.parseLatency("16666666\n0 0 0\n", 1).fps)
        assertNull(FpsMath.parseLatency("16666666\n0 9223372036854775807 0\n", 1).fps)
        assertNull(FpsMath.parseLatency("", 1).fps)
    }

    @Test fun oldFramesOutsideTheWindowAreIgnored() {
        val now = 30_000_000_000L
        val old = (0 until 60).map { now - 1_500_000_000L - it * 8_333_333L } // 120 FPS burst 1.5 s ago
        val recent = (0 until 30).map { now - it * 33_333_333L }             // 30 FPS now
        assertEquals(30, FpsMath.parseLatency(latency(now, old + recent), now).fps)
    }

    @Test fun layerMatchingUsesTheExactPackage() {
        val list = """
            Display 0 HWC layers:
            com.game.helper/com.game.helper.Main#12
            SurfaceView[com.game/com.unity3d.player.UnityPlayerActivity](BLAST)#145
            Background for SurfaceView[com.game/com.unity3d.player.UnityPlayerActivity]#146
            com.game/com.unity3d.player.UnityPlayerActivity#140
            ActivityRecord{1a2b u0 com.game/com.unity3d.player.UnityPlayerActivity t42}#139
            com.gamer/com.gamer.Main#3
        """.trimIndent()
        assertEquals(
            listOf("SurfaceView[com.game/com.unity3d.player.UnityPlayerActivity](BLAST)#145", "com.game/com.unity3d.player.UnityPlayerActivity#140"),
            FpsMath.layerCandidates(list, "com.game")
        )
        assertEquals(emptyList<String>(), FpsMath.layerCandidates(list, "com.ga"))
    }
}
