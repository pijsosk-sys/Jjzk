package com.redcore.booster.core

import kotlin.math.roundToInt

/**
 * Pure parsing for `dumpsys SurfaceFlinger --list` / `--latency` output.
 *
 * `--latency <layer>` prints the display refresh period (ns) on the first line, then up to 128 lines of
 * `desiredPresent actualPresent frameReady` timestamps in CLOCK_MONOTONIC nanoseconds (the same clock as
 * `System.nanoTime()` on Android). Unfinished frames show INT64_MAX and empty slots show 0.
 */
object FpsMath {
    const val SECOND_NS = 1_000_000_000L

    data class Latency(
        /** Frames per second over the last window, or null when the data can't support a number. */
        val fps: Int?,
        /** Frames presented inside the window. */
        val frames: Int,
        /** Age of the newest presented frame, or null if there were none. */
        val newestAgeNs: Long?,
        /** Display refresh period reported on the first line, if any. */
        val refreshPeriodNs: Long?,
        val why: String? = null,
    ) {
        val refreshHz: Int? get() = refreshPeriodNs?.takeIf { it in 2_000_000L..60_000_000L }?.let { (SECOND_NS.toDouble() / it).roundToInt() }
    }

    /**
     * @param staleNs if the newest frame is older than this, the layer is not updating (game paused, or the
     * wrong layer): no FPS is reported rather than a misleading 0.
     */
    fun parseLatency(out: String, nowNs: Long, windowNs: Long = SECOND_NS, staleNs: Long = SECOND_NS): Latency {
        val lines = out.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return Latency(null, 0, null, null, "No output")
        val period = lines.first().split(Regex("\\s+")).singleOrNull()?.toLongOrNull()
        val present = lines.drop(if (period != null) 1 else 0).mapNotNull { ln ->
            ln.split(Regex("\\s+")).takeIf { it.size >= 3 }?.get(1)?.toLongOrNull()
        }.filter { it > 0 && it != Long.MAX_VALUE }.distinct().sorted()
        if (present.isEmpty()) return Latency(null, 0, null, period, "No frames recorded for this layer")
        val newest = present.last()
        val age = nowNs - newest
        // Timestamps from another clock (or garbage) are far from "now": refuse instead of guessing.
        if (age < -SECOND_NS || age > 5 * SECOND_NS) return Latency(null, 0, null, period, "Timestamps don't match the system clock")
        if (age > staleNs) return Latency(null, 0, age, period, "No new frames (game paused or not drawing)")
        val inWindow = present.filter { nowNs - it in 0..windowNs }
        val n = inWindow.size
        if (n == 0) return Latency(null, 0, age, period, "No new frames")
        // Rate over the frames' own span, not "frames in the last second": SurfaceFlinger keeps only 128
        // frames, which is less than one second at 144 Hz, so counting would under-report fast games.
        val fps = if (n >= 3) {
            val span = inWindow.last() - inWindow.first()
            if (span <= 0) n else ((n - 1) * SECOND_NS.toDouble() / span).roundToInt()
        } else n
        return Latency(fps, n, age, period)
    }

    private val excluded = listOf(
        "Background for", "Bounds for", "ActivityRecord{", "Task=", "WindowToken", "animation-leash",
        "Splash Screen", "Letterbox", "Dim layer", "ImeContainer", "InputMethod",
    )

    /**
     * Layers from `--list` that belong to [pkg] (exact package, not a prefix of another package), best
     * candidates first: SurfaceViews (where most games render) before plain app windows.
     */
    fun layerCandidates(list: String, pkg: String): List<String> {
        val boundary = Regex("(^|[\\[\\s(:])" + Regex.escape(pkg) + "/")
        val lines = list.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .filter { boundary.containsMatchIn(it) }
            .filterNot { l -> excluded.any { l.contains(it, ignoreCase = true) } }
            .distinct().toList()
        return lines.sortedBy { if (it.startsWith("SurfaceView")) 0 else 1 }
    }
}
