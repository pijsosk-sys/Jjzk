package com.redcore.booster.core

import com.redcore.booster.data.AppSize
import com.redcore.booster.data.CpuMath
import com.redcore.booster.data.NetMath
import com.redcore.booster.data.PingResult
import com.redcore.booster.data.StorageSummary
import com.redcore.booster.data.ThermalMath
import com.redcore.booster.data.fmtBytes
import com.redcore.booster.data.fmtDuration
import com.redcore.booster.data.fmtGHz
import com.redcore.booster.data.fmtRate
import com.redcore.booster.data.fmtTemp
import com.redcore.booster.data.fmtTempTarget
import com.redcore.booster.data.fmtTempValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class MeasurementsTest {
    @Before fun locale() { Locale.setDefault(Locale.US) }

    @Test fun pingStatistics() {
        val r = PingResult("h", listOf(10L, 20L, null, 30L))
        assertEquals(20.0, r.avg!!, 0.001)
        assertEquals(0.25f, r.loss, 0.001f)
        assertEquals(10L, r.min); assertEquals(30L, r.max)
        assertEquals("Unstable (packet loss)", r.stability)
        assertEquals("Excellent for gaming", PingResult("h", listOf(12L, 14L, 13L)).stability)
        assertEquals("No response", PingResult("h", listOf(null, null)).stability)
        assertEquals(1f, PingResult("h", listOf(null, null)).loss, 0f)
        assertEquals(0f, PingResult("h", emptyList()).loss, 0f)
    }

    @Test fun jitterOnlyComparesConsecutiveSamples() {
        // 10 and 90 are separated by a failed attempt: they must not create an 80 ms "jitter".
        assertEquals(2.0, PingResult("h", listOf(10L, 12L, null, 90L, 92L)).jitter!!, 0.001)
        assertNull(PingResult("h", listOf(10L, null, 12L)).jitter)
    }

    @Test fun throughput() {
        assertEquals(8_000_000.0, NetMath.bitsPerSecond(1_000_000, 1_000_000_000)!!, 0.1)
        assertNull(NetMath.bitsPerSecond(0, 1_000_000_000))
        assertNull(NetMath.bitsPerSecond(10, 0))
        // 1 MB in the first second (slow start), then 2 MB/s for 3 s: steady rate is 16 Mbps.
        val samples = listOf(0L to 0L, 1_000_000_000L to 1_000_000L, 2_000_000_000L to 3_000_000L, 3_000_000_000L to 5_000_000L, 4_000_000_000L to 7_000_000L)
        assertEquals(16_000_000.0, NetMath.steadyThroughput(samples)!!, 1.0)
        assertEquals(1.4, NetMath.stdDev(listOf(10, 12, 13, 11, 9)) , 0.2)
    }

    @Test fun formatting() {
        assertEquals("1.5 GB", fmtBytes(1536L * 1024 * 1024))
        assertEquals("512 B", fmtBytes(512))
        assertEquals("8.0 Mbps", fmtRate(1_000_000))
        assertEquals("—", fmtRate(null))
        assertEquals("1h 5m", fmtDuration(65 * 60_000L))
        assertEquals("0m", fmtDuration(-5))
        assertEquals("2.50 GHz", fmtGHz(2500))
        assertEquals("850 MHz", fmtGHz(850))
    }

    @Test fun temperatures() {
        assertEquals("37.2°C", fmtTemp(37.2f, false))
        assertEquals("99°F", fmtTemp(37.2f, true))
        assertEquals("—", fmtTemp(null, true))
        assertEquals("—", fmtTempValue(null, false))
        assertEquals("37.2", fmtTempValue(37.2f, false))
        assertEquals("41°C", fmtTempTarget(41, false))
        assertEquals("106°F", fmtTempTarget(41, true))
    }

    @Test fun cpuUsageFromProcStat() {
        val a = longArrayOf(100, 0, 100, 800, 0, 0, 0)
        val b = longArrayOf(200, 0, 200, 1400, 0, 0, 0) // +200 busy, +600 idle
        assertEquals(0.25f, CpuMath.usage(a, b)!!, 0.001f)
        assertNull("counter reset", CpuMath.usage(b, a))
        assertNull(CpuMath.usage(longArrayOf(1, 2), longArrayOf(1, 2, 3)))
        assertEquals(0.75f, CpuMath.clockLevel(listOf(1000, null, 3000), listOf(2000, 2000, 3000))!!, 0.001f)
        assertNull(CpuMath.clockLevel(listOf(null), listOf(null)))
    }

    @Test fun thermalZoneUnits() {
        assertEquals(45.5f, ThermalMath.zoneCelsius(45_500)!!, 0.01f)
        assertEquals(45.5f, ThermalMath.zoneCelsius(455)!!, 0.01f)
        assertEquals(45f, ThermalMath.zoneCelsius(45)!!, 0.01f)
        assertNull("disabled sensor", ThermalMath.zoneCelsius(-273_000))
        assertNull(ThermalMath.zoneCelsius(0))
    }

    @Test fun storageCategories() {
        val s = StorageSummary(total = 100, free = 40, apps = 30, images = 10, video = 5, audio = 1, cache = 2)
        assertEquals(60L, s.used)
        assertEquals(14L, s.other)
        assertEquals(0.6f, s.usedFraction, 0.001f)
        assertNull(StorageSummary(100, 40, null, null, null, null, null).other)
        assertEquals(0L, StorageSummary(100, 90, 30, 10, 5, 1, null).other) // never negative
        assertEquals(6L, AppSize("p", "l", 1, 2, 3).total)
    }
}
