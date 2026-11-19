package com.redcore.booster.data

import kotlin.math.abs
import kotlin.math.sqrt

/*
 * Pure measurement types and formatting shared by the Android app and the PC version.
 * (Kept in the original package so existing imports keep working.)
 */

/** Result of a TCP-handshake latency test. A null sample is an attempt that failed or timed out. */
data class PingResult(val target: String, val samples: List<Long?>) {
    val ok get() = samples.filterNotNull()
    val avg: Double? get() = ok.takeIf { it.isNotEmpty() }?.average()
    val min: Long? get() = ok.minOrNull()
    val max: Long? get() = ok.maxOrNull()

    /** Share of attempts that failed. TCP retries lost packets, so this is "failed connections", not raw packet loss. */
    val loss: Float get() = if (samples.isEmpty()) 0f else samples.count { it == null }.toFloat() / samples.size

    /**
     * Mean absolute difference between *consecutive* successful samples (packet delay variation).
     * A failed attempt breaks the sequence, so samples on either side of a gap aren't compared.
     */
    val jitter: Double?
        get() {
            val diffs = samples.zipWithNext().mapNotNull { (a, b) -> if (a != null && b != null) abs(a - b).toDouble() else null }
            return if (diffs.isEmpty()) null else diffs.average()
        }

    val stability: String
        get() {
            val a = avg ?: return "No response"
            val j = jitter ?: 0.0
            return when {
                loss > 0.1f -> "Unstable (packet loss)"
                a < 40 && j < 8 -> "Excellent for gaming"
                a < 80 && j < 20 -> "Good"
                a < 150 -> "Fair — may notice lag"
                else -> "Poor for online games"
            }
        }
}

object NetMath {
    fun stdDev(v: List<Long>): Double {
        if (v.size < 2) return 0.0
        val m = v.average(); return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
    }

    /** Bits per second for [bytes] moved in [elapsedNs]. Returns null for an empty or zero-length transfer. */
    fun bitsPerSecond(bytes: Long, elapsedNs: Long): Double? =
        if (bytes <= 0 || elapsedNs <= 0) null else bytes * 8.0 * 1_000_000_000.0 / elapsedNs

    /**
     * Steady-state throughput: discards the first [warmupNs] (TCP slow start) when the transfer ran long
     * enough to have a steady part, otherwise uses the whole transfer.
     * [samples] are (elapsedNs since first byte, cumulative bytes) checkpoints in order.
     */
    fun steadyThroughput(samples: List<Pair<Long, Long>>, warmupNs: Long = 1_000_000_000L): Double? {
        if (samples.size < 2) return samples.singleOrNull()?.let { bitsPerSecond(it.second, it.first) }
        val last = samples.last()
        val start = samples.lastOrNull { it.first <= warmupNs && last.first - it.first >= 2 * warmupNs } ?: samples.first()
        return bitsPerSecond(last.second - start.second, last.first - start.first)
    }
}

/** Storage space by category. Categories Android doesn't share stay null. */
data class StorageSummary(
    val total: Long,
    val free: Long,
    val apps: Long?,
    val images: Long?,
    val video: Long?,
    val audio: Long?,
    val cache: Long?,
) {
    val used get() = (total - free).coerceAtLeast(0)
    val usedFraction: Float get() = if (total > 0) used.toFloat() / total else 0f

    /** Everything we cannot attribute to a category (system, documents, downloads, other app files). */
    val other: Long? get() = if (apps == null) null else (used - apps - (images ?: 0) - (video ?: 0) - (audio ?: 0)).coerceAtLeast(0)
}

data class AppSize(val pkg: String, val label: String, val appBytes: Long, val dataBytes: Long, val cacheBytes: Long) {
    val total get() = appBytes + dataBytes + cacheBytes
}

fun fmtBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = b.toDouble() / 1024
    var u = 0
    while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
    return if (v >= 100) "%.0f %s".format(v, units[u]) else "%.1f %s".format(v, units[u])
}

fun fmtGHz(mhz: Int): String = if (mhz >= 1000) "%.2f GHz".format(mhz / 1000f) else "$mhz MHz"

fun fmtRate(bytesPerSec: Long?): String {
    if (bytesPerSec == null) return "—"
    val bits = bytesPerSec * 8.0
    return when {
        bits >= 1_000_000_000 -> "%.2f Gbps".format(bits / 1e9)
        bits >= 1_000_000 -> "%.1f Mbps".format(bits / 1e6)
        bits >= 1_000 -> "%.0f Kbps".format(bits / 1e3)
        else -> "%.0f bps".format(bits)
    }
}

fun fmtDuration(ms: Long): String {
    val m = ms.coerceAtLeast(0) / 60000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

fun celsiusToFahrenheit(c: Float): Float = c * 9f / 5f + 32f

/** Temperature with unit, e.g. "37.2°C" / "99°F"; "—" when the phone gives no reading. */
fun fmtTemp(c: Float?, fahrenheit: Boolean): String = when {
    c == null -> "—"
    fahrenheit -> "%.0f°F".format(celsiusToFahrenheit(c))
    else -> "%.1f°C".format(c)
}

/** Temperature number without the unit (for gauges that print the unit separately); "—" when unavailable. */
fun fmtTempValue(c: Float?, fahrenheit: Boolean): String = when {
    c == null -> "—"
    fahrenheit -> "%.0f".format(celsiusToFahrenheit(c))
    else -> "%.1f".format(c)
}

/** A temperature *difference* or threshold in the user's unit, e.g. COOL guard targets. */
fun fmtTempTarget(c: Int, fahrenheit: Boolean): String =
    if (fahrenheit) "%.0f°F".format(celsiusToFahrenheit(c.toFloat())) else "$c°C"

object CpuMath {
    /**
     * CPU utilisation between two `/proc/stat` "cpu" lines (user nice system idle iowait irq softirq ...).
     * Returns null when the counters didn't advance or went backwards (counter reset / CPU hotplug).
     */
    fun usage(prev: LongArray, cur: LongArray): Float? {
        if (prev.size < 4 || cur.size != prev.size) return null
        val idle = (cur[3] + cur.getOrElse(4) { 0 }) - (prev[3] + prev.getOrElse(4) { 0 })
        val total = cur.sum() - prev.sum()
        if (total <= 0 || idle < 0 || idle > total) return null
        return (1f - idle.toFloat() / total).coerceIn(0f, 1f)
    }

    /** Average of each core's current clock relative to its own maximum (a clock level, not utilisation). */
    fun clockLevel(cur: List<Int?>, max: List<Int?>): Float? {
        val pairs = cur.zip(max).mapNotNull { (c, m) -> if (c != null && m != null && m > 0) c.toFloat() / m else null }
        if (pairs.isEmpty()) return null
        return pairs.average().toFloat().coerceIn(0f, 1f)
    }
}

object ThermalMath {
    /**
     * Kernel thermal zones report millidegrees on most phones, decidegrees on some and whole degrees on a few.
     * Returns °C, or null for values that can't be a real temperature (disabled sensors report -273 or 0).
     */
    fun zoneCelsius(raw: Long): Float? {
        val c = when {
            raw > 1000 -> raw / 1000f
            raw > 200 -> raw / 10f
            else -> raw.toFloat()
        }
        return if (c in 1f..150f) c else null
    }
}
