package com.redcore.booster.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.hardware.display.DisplayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs
import kotlin.math.roundToInt

data class LiveStats(
    val time: Long = 0L,
    val coreCurMHz: List<Int?> = emptyList(),
    val coreMaxMHz: List<Int?> = emptyList(),
    /** Real CPU utilisation from /proc/stat. Android 8+ blocks this for apps, so it is usually null. */
    val cpuUsage: Float? = null,
    val ramTotal: Long = 0,
    val ramAvail: Long = 0,
    val lowMemory: Boolean = false,
    val batteryPct: Int? = null,
    val batteryTempC: Float? = null,
    val charging: Boolean = false,
    val plug: String = "Battery",
    val voltageMv: Int? = null,
    val currentMa: Int? = null,
    val thermalStatus: Int? = null,
    val thermalHeadroom: Float? = null,
    val rxBps: Long? = null,
    val txBps: Long? = null,
    val gpuMHz: Int? = null,
    val gpuLoad: Int? = null,
    val refreshHz: Int? = null,
    val zones: List<SysFs.ThermalZone> = emptyList(),
) {
    val ramUsed get() = ramTotal - ramAvail
    val ramFraction get() = if (ramTotal > 0) ramUsed.toFloat() / ramTotal else 0f
    val avgCurMHz: Int? get() = coreCurMHz.filterNotNull().takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val peakCurMHz: Int? get() = coreCurMHz.filterNotNull().maxOrNull()
    val maxMHz: Int? get() = coreMaxMHz.filterNotNull().maxOrNull()

    /** Current clocks relative to each core's maximum. This is a clock level, NOT utilisation, and is labelled that way. */
    val clockLevel: Float?
        get() {
            val pairs = coreCurMHz.zip(coreMaxMHz).filter { it.first != null && it.second != null && it.second!! > 0 }
            if (pairs.isEmpty()) return null
            return pairs.map { it.first!!.toFloat() / it.second!! }.average().toFloat().coerceIn(0f, 1f)
        }

    /** Hottest reading available (battery or readable thermal zones). */
    val hottestC: Float? get() = (zones.map { it.celsius } + listOfNotNull(batteryTempC)).maxOrNull()
}

/**
 * Polls system counters only while someone is collecting [stats] (screens, overlay). When nothing
 * observes it the sampling loop stops, so the app costs no battery in the background.
 */
class SystemMonitor(private val context: Context, scope: CoroutineScope) {
    private val am = context.getSystemService(ActivityManager::class.java)
    private val pm = context.getSystemService(PowerManager::class.java)
    private val bm = context.getSystemService(BatteryManager::class.java)
    private val dm = context.getSystemService(DisplayManager::class.java)

    private val _history = MutableStateFlow<List<LiveStats>>(emptyList())
    val history: StateFlow<List<LiveStats>> = _history.asStateFlow()

    private var prevCpu: LongArray? = null
    private var prevRx = -1L
    private var prevTx = -1L
    private var prevT = 0L

    val stats: StateFlow<LiveStats> = flow {
        while (true) {
            emit(sample())
            delay(1000)
        }
    }.flowOn(Dispatchers.IO)
        .onEach { s -> _history.value = (_history.value + s).takeLast(HISTORY) }
        .stateIn(scope, SharingStarted.WhileSubscribed(4000), LiveStats())

    private fun sample(): LiveStats {
        val n = SysFs.cpuCount()
        val cur = (0 until n).map { SysFs.coreCurFreqKHz(it)?.let { k -> (k / 1000).toInt() } }
        val max = (0 until n).map { SysFs.coreMaxFreqKHz(it)?.let { k -> (k / 1000).toInt() } }

        val cpu = SysFs.procStatCpuLine()
        var usage: Float? = null
        if (cpu != null) {
            val p = prevCpu
            if (p != null && p.size == cpu.size) {
                val idle = (cpu[3] + cpu.getOrElse(4) { 0 }) - (p[3] + p.getOrElse(4) { 0 })
                val total = cpu.sum() - p.sum()
                if (total > 0) usage = (1f - idle.toFloat() / total).coerceIn(0f, 1f)
            }
            prevCpu = cpu
        }

        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

        val bi = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val temp = bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE && it != 0 }?.let { it / 10f }
        val volt = bi?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val rawCurrent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.takeIf { it != Int.MIN_VALUE && it != 0 }
        // Most devices report µA, a few report mA. Normalise to mA.
        val currentMa = rawCurrent?.let { if (abs(it) > 20_000) it / 1000 else it }

        val thermalStatus = if (Build.VERSION.SDK_INT >= 29) pm?.currentThermalStatus else null
        val headroom = if (Build.VERSION.SDK_INT >= 30) pm?.getThermalHeadroom(0)?.takeIf { !it.isNaN() } else null

        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        var rxBps: Long? = null
        var txBps: Long? = null
        if (rx != TrafficStats.UNSUPPORTED.toLong() && prevRx >= 0 && now > prevT) {
            val dt = (now - prevT) / 1000.0
            rxBps = ((rx - prevRx) / dt).toLong().coerceAtLeast(0)
            txBps = ((tx - prevTx) / dt).toLong().coerceAtLeast(0)
        }
        prevRx = rx; prevTx = tx; prevT = now

        val hz = dm?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.roundToInt()

        return LiveStats(
            time = System.currentTimeMillis(),
            coreCurMHz = cur, coreMaxMHz = max, cpuUsage = usage,
            ramTotal = mi.totalMem, ramAvail = mi.availMem, lowMemory = mi.lowMemory,
            batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else null,
            batteryTempC = temp,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            plug = BatteryStats.plugName(plugged), voltageMv = volt, currentMa = currentMa,
            thermalStatus = thermalStatus, thermalHeadroom = headroom,
            rxBps = rxBps, txBps = txBps,
            gpuMHz = SysFs.gpuFreqMHz(), gpuLoad = SysFs.gpuLoadPercent(),
            refreshHz = hz,
            zones = SysFs.thermalZones(),
        )
    }

    companion object {
        const val HISTORY = 120

        fun thermalName(s: Int?): String = when (s) {
            null -> "Unavailable"
            0 -> "Normal"
            1 -> "Light"
            2 -> "Moderate"
            3 -> "Severe"
            4 -> "Critical"
            5 -> "Emergency"
            6 -> "Shutdown"
            else -> "Unknown"
        }
    }
}
