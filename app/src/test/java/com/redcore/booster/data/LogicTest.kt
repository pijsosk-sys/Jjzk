package com.redcore.booster.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LogicTest {
    @Test fun profileJsonRoundTrip() {
        val list = listOf(
            GameProfile("a.b", GameMode.BEAST, overlay = false, dnd = true, brightness = 80, refresh = RefreshTarget.HIGHEST, cleanBackground = false, addedAt = 5),
            GameProfile("c.d", GameMode.ECO),
        )
        assertEquals(list, GameProfile.listFromJson(GameProfile.listToJson(list)))
        assertEquals(emptyList<GameProfile>(), GameProfile.listFromJson("not json"))
    }

    @Test fun overridesApplyOnTopOfMode() {
        val p = GameProfile("x", GameMode.BEAST, dnd = false, brightness = -1)
        val e = p.effective()
        assertEquals(false, e.dnd)
        assertNull("-1 means leave brightness unchanged", e.brightness)
        assertEquals(RefreshTarget.HIGHEST, e.refresh)
        assertTrue(e.cleanBackground)
    }

    @Test fun pingStats() {
        val r = PingResult("h", listOf(10L, 20L, null, 30L))
        assertEquals(20.0, r.avg!!, 0.001)
        assertEquals(0.25f, r.loss, 0.001f)
        assertEquals(10.0, r.jitter!!, 0.001)
        assertEquals("Unstable (packet loss)", r.stability)
        assertEquals("Excellent for gaming", PingResult("h", listOf(12L, 14L, 13L)).stability)
    }

    @Test fun clockLevelIsRelativeToEachCore() {
        val s = LiveStats(coreCurMHz = listOf(1000, null, 3000), coreMaxMHz = listOf(2000, 2000, 3000))
        assertEquals(0.75f, s.clockLevel!!, 0.001f)
        assertNull(LiveStats(coreCurMHz = listOf(null), coreMaxMHz = listOf(null)).clockLevel)
    }

    @Test fun formatting() {
        assertEquals("1.5 GB", fmtBytes(1536L * 1024 * 1024))
        assertEquals("8.0 Mbps", fmtRate(1_000_000))
        assertEquals("1h 5m", fmtDuration(65 * 60_000L))
        assertEquals("2.50 GHz", fmtGHz(2500))
    }

    @Test fun insightsNeverEmpty() {
        assertTrue(Insights.performance(LiveStats(), false, true).isNotEmpty())
        assertTrue(Insights.performance(LiveStats(thermalStatus = 4), false, true).any { it.severity == Severity.CRITICAL })
        assertTrue(Insights.network(null, null).first().severity == Severity.CRITICAL)
    }
}
