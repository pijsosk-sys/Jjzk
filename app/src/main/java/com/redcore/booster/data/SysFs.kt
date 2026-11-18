package com.redcore.booster.data

import java.io.File

/** Small helpers for reading kernel files. Every read may be blocked by SELinux, so all return null on failure. */
object SysFs {
    fun read(path: String): String? = try {
        val f = File(path)
        if (f.exists() && f.canRead()) f.readText().trim().ifEmpty { null } else null
    } catch (_: Throwable) {
        null
    }

    fun readLong(path: String): Long? = read(path)?.lineSequence()?.firstOrNull()?.trim()?.toLongOrNull()

    fun cpuCount(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(
        File("/sys/devices/system/cpu/").listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }?.size ?: 0
    )

    fun coreCurFreqKHz(core: Int): Long? = readLong("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")
    fun coreMaxFreqKHz(core: Int): Long? = readLong("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq")
    fun coreMinFreqKHz(core: Int): Long? = readLong("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_min_freq")
    fun coreGovernor(core: Int): String? = read("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_governor")

    fun procStatCpuLine(): LongArray? {
        val line = read("/proc/stat")?.lineSequence()?.firstOrNull { it.startsWith("cpu ") } ?: return null
        return line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }.toLongArray().takeIf { it.size >= 4 }
    }

    /** Known GPU frequency nodes (Adreno/Mali). Usually blocked on modern Android, but readable on some devices. */
    private val gpuFreqPaths = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpuclk",
        "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
        "/sys/class/devfreq/gpufreq/cur_freq",
        "/sys/kernel/gpu/gpu_clock",
        "/sys/class/misc/mali0/device/clock",
    )
    private val gpuLoadPaths = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/kernel/gpu/gpu_busy",
        "/sys/class/misc/mali0/device/utilization",
    )

    fun gpuFreqMHz(): Int? {
        for (p in gpuFreqPaths) {
            val v = readLong(p) ?: continue
            return when {
                v > 10_000_000 -> (v / 1_000_000).toInt()
                v > 10_000 -> (v / 1000).toInt()
                else -> v.toInt()
            }
        }
        return null
    }

    fun gpuLoadPercent(): Int? {
        for (p in gpuLoadPaths) {
            val raw = read(p) ?: continue
            val n = Regex("\\d+").find(raw)?.value?.toIntOrNull() ?: continue
            if (n in 0..100) return n
        }
        return null
    }

    data class ThermalZone(val name: String, val celsius: Float)

    /** Reads /sys/class/thermal zones that the app is allowed to see. Values outside 0..150 °C are discarded. */
    fun thermalZones(): List<ThermalZone> {
        val dir = File("/sys/class/thermal")
        val zones = try { dir.listFiles { f -> f.name.startsWith("thermal_zone") } } catch (_: Throwable) { null } ?: return emptyList()
        return zones.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 0 }.mapNotNull { z ->
            val type = read("${z.path}/type") ?: return@mapNotNull null
            val t = readLong("${z.path}/temp") ?: return@mapNotNull null
            val c = when {
                t > 1000 -> t / 1000f
                t > 200 -> t / 10f
                else -> t.toFloat()
            }
            if (c in 1f..150f) ThermalZone(type, c) else null
        }
    }

    fun cpuInfoField(key: String): String? =
        read("/proc/cpuinfo")?.lineSequence()?.firstOrNull { it.startsWith(key, ignoreCase = true) }
            ?.substringAfter(":")?.trim()?.ifEmpty { null }
}
