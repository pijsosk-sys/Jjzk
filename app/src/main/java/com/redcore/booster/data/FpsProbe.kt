package com.redcore.booster.data

import android.content.Context
import com.redcore.booster.booster.Permissions
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Frame-rate sources that work without root. Returns null when nothing trustworthy is available —
 * REDCORE never shows an estimated FPS.
 *  1. SurfaceFlinger frame timestamps for the game's layer (needs DUMP, granted once over ADB).
 *  2. The display driver's measured commit rate (readable on some Qualcomm phones).
 */
object FpsProbe {
    private val sysfs = listOf("/sys/class/drm/sde-crtc-0/measured_fps", "/sys/class/graphics/fb0/measured_fps")
    @Volatile private var dumpDenied = false
    private var layer: Pair<String, String>? = null

    data class Reading(val fps: Int, val source: String)

    fun read(context: Context, pkg: String?): Reading? {
        if (pkg != null && !dumpDenied && Permissions.has(context, "android.permission.DUMP")) {
            surfaceFlinger(pkg)?.let { return Reading(it, "game") }
        }
        for (p in sysfs) {
            val raw = SysFs.read(p) ?: continue
            val v = Regex("[0-9]+(\\.[0-9]+)?").find(raw)?.value?.toFloatOrNull() ?: continue
            if (v in 1f..1000f) return Reading(v.roundToInt(), "display")
        }
        return null
    }

    private fun exec(vararg cmd: String): String? = try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor(); out
    } catch (_: Throwable) { null }

    private fun surfaceFlinger(pkg: String): Int? {
        val name = layer?.takeIf { it.first == pkg }?.second ?: run {
            val list = exec("dumpsys", "SurfaceFlinger", "--list") ?: return null
            if (list.contains("Permission Denial", true)) { dumpDenied = true; return null }
            val cands = list.lines().map { it.trim() }.filter { it.contains(pkg) }
            val pick = cands.firstOrNull { it.startsWith("SurfaceView") && !it.contains("Background") } ?: cands.firstOrNull() ?: return null
            layer = pkg to pick; pick
        }
        val out = exec("dumpsys", "SurfaceFlinger", "--latency", name) ?: return null
        val now = System.nanoTime()
        return parseLatency(out, now).also { if (it == null) layer = null }
    }

    /** Parses `dumpsys SurfaceFlinger --latency` output: frames presented in the last second, or null if the data isn't usable. */
    fun parseLatency(out: String, nowNs: Long): Int? {
        val present = out.lines().drop(1).mapNotNull { ln ->
            ln.trim().split(Regex("\\s+")).takeIf { it.size >= 3 }?.get(1)?.toLongOrNull()
        }.filter { it > 0 && it < Long.MAX_VALUE }
        if (present.size < 2) return null
        val newest = present.max()
        if (abs(nowNs - newest) > 5_000_000_000L) return null // different clock or stale data: don't guess
        return present.count { nowNs - it in 0..1_000_000_000L }
    }
}
