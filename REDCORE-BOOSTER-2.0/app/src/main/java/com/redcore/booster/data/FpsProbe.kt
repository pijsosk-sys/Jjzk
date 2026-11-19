package com.redcore.booster.data

import android.content.Context
import com.redcore.booster.booster.Permissions
import com.redcore.booster.core.FpsMath
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Frame-rate sources that work without root. REDCORE never estimates FPS:
 *  1. [Source.GAME_LAYER]: SurfaceFlinger present timestamps of the game's own layer (needs the DUMP
 *     permission, granted once over ADB). This is the game's real frame rate.
 *  2. [Source.DISPLAY_COMPOSITION]: the display driver's measured commit rate (some Qualcomm phones). This is
 *     how often the *screen* updated — it includes every layer and is NOT the game's FPS, so the UI labels it
 *     "screen updates" and session reports never use it.
 */
object FpsProbe {
    enum class Source { GAME_LAYER, DISPLAY_COMPOSITION }

    data class Reading(val value: Int, val source: Source)

    /** A sample: a reading, or null with the reason no trustworthy number exists right now. */
    data class Sample(val reading: Reading?, val note: String?)

    private const val DUMP = "android.permission.DUMP"
    private val sysfs = listOf("/sys/class/drm/sde-crtc-0/measured_fps", "/sys/class/graphics/fb0/measured_fps")

    @Volatile private var dumpDeniedAtMs = 0L
    @Volatile private var layer: Pair<String, String>? = null
    @Volatile private var layerPickedAtMs = 0L

    fun sample(context: Context, pkg: String?): Sample {
        var note: String? = null
        if (pkg != null) {
            when {
                !Permissions.has(context, DUMP) -> note = "Real game FPS needs the one-time ADB grant."
                System.currentTimeMillis() - dumpDeniedAtMs < 60_000 -> note = "Android refused to share frame data."
                else -> {
                    val s = surfaceFlinger(pkg)
                    if (s.reading != null) return s
                    note = s.note
                }
            }
        }
        displayComposition()?.let { return Sample(Reading(it, Source.DISPLAY_COMPOSITION), note) }
        return Sample(null, note ?: "This phone doesn't share frame data with apps.")
    }

    /** Old entry point kept for callers that only need a value. */
    fun read(context: Context, pkg: String?): Reading? = sample(context, pkg).reading

    /** Parses `--latency` output: the game's FPS over the last second, or null when the data can't support a number. */
    fun parseLatency(out: String, nowNs: Long): Int? = FpsMath.parseLatency(out, nowNs).fps

    private fun displayComposition(): Int? {
        for (p in sysfs) {
            val raw = SysFs.read(p) ?: continue
            val v = Regex("[0-9]+(\\.[0-9]+)?").find(raw)?.value?.toFloatOrNull() ?: continue
            if (v in 1f..1000f) return v.roundToInt()
        }
        return null
    }

    private fun surfaceFlinger(pkg: String): Sample {
        val now = System.currentTimeMillis()
        layer?.takeIf { it.first == pkg }?.second?.let { name ->
            val r = latency(name) ?: return Sample(null, "SurfaceFlinger didn't answer in time.")
            r.fps?.let { return Sample(Reading(it, Source.GAME_LAYER), null) }
            // Stale or empty: the game may have switched surfaces. Re-pick at most every 5 s.
            if (now - layerPickedAtMs < 5_000) return Sample(null, r.why)
        }
        val list = run("dumpsys", "SurfaceFlinger", "--list") ?: return Sample(null, "SurfaceFlinger didn't answer in time.")
        if (list.contains("Permission Denial", ignoreCase = true)) {
            dumpDeniedAtMs = now
            return Sample(null, "Android refused to share frame data.")
        }
        val candidates = FpsMath.layerCandidates(list, pkg).take(4)
        layerPickedAtMs = now
        if (candidates.isEmpty()) { layer = null; return Sample(null, "The game's surface isn't visible yet.") }
        // Several surfaces (game view, ads, video): pick the one that actually presents the most frames.
        val measured = candidates.mapNotNull { c -> latency(c)?.let { c to it } }
        val best = measured.filter { it.second.fps != null }.maxByOrNull { it.second.frames }
        layer = pkg to (best?.first ?: candidates.first())
        return if (best != null) Sample(Reading(best.second.fps!!, Source.GAME_LAYER), null)
        else Sample(null, measured.firstOrNull()?.second?.why ?: "No frame data for the game yet.")
    }

    private fun latency(layerName: String): FpsMath.Latency? {
        val out = run("dumpsys", "SurfaceFlinger", "--latency", layerName) ?: return null
        if (out.contains("Permission Denial", ignoreCase = true)) { dumpDeniedAtMs = System.currentTimeMillis(); return null }
        // Timestamps are CLOCK_MONOTONIC, which is what System.nanoTime() reads on Android.
        return FpsMath.parseLatency(out, System.nanoTime())
    }

    /**
     * Runs a command with a hard timeout. Output is drained on a separate thread so a large `--list`
     * can't fill the pipe and block the process; a hung dumpsys is killed instead of stalling the caller.
     */
    private fun run(vararg cmd: String, timeoutMs: Long = 1_500): String? {
        val p = try { ProcessBuilder(*cmd).redirectErrorStream(true).start() } catch (_: Throwable) { return null }
        val out = StringBuilder()
        val reader = thread(isDaemon = true, name = "fps-probe") {
            runCatching {
                p.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(8192)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        synchronized(out) { if (out.length < 2_000_000) out.appendRange(buf, 0, n) }
                    }
                }
            }
        }
        val finished = try { p.waitFor(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { false }
        if (!finished) {
            p.destroyForcibly()
            reader.join(200)
            return null
        }
        reader.join(500)
        return synchronized(out) { out.toString() }
    }
}
