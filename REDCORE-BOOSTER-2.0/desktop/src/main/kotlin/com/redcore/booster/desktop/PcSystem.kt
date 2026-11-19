package com.redcore.booster.desktop

import com.redcore.booster.data.fmtBytes
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
import oshi.SystemInfo
import oshi.hardware.CentralProcessor
import java.awt.GraphicsEnvironment

/** One live sample. Anything the PC doesn't expose stays null — REDCORE never fills in a guess. */
data class PcStats(
    val time: Long = 0,
    val cpuLoad: Float? = null,
    val coreLoads: List<Float> = emptyList(),
    val coreMHz: List<Int> = emptyList(),
    val maxMHz: Int? = null,
    val ramTotal: Long = 0,
    val ramAvail: Long = 0,
    val swapUsed: Long = 0,
    val swapTotal: Long = 0,
    val cpuTempC: Float? = null,
    val fanRpm: List<Int> = emptyList(),
    val batteryPct: Int? = null,
    val charging: Boolean? = null,
    val rxBps: Long? = null,
    val txBps: Long? = null,
    val processes: Int = 0,
    val uptimeS: Long = 0,
) {
    val ramUsed get() = ramTotal - ramAvail
    val ramFraction get() = if (ramTotal > 0) ramUsed.toFloat() / ramTotal else 0f
    val avgMHz: Int? get() = coreMHz.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.toInt()
}

data class InfoSection(val title: String, val rows: List<Pair<String, String?>>)

/** Samples the PC every second while something is watching (the app window or the HUD). */
class PcSystem(scope: CoroutineScope) {
    private val si by lazy { SystemInfo() }
    private val hal by lazy { si.hardware }
    private var prevTicks: LongArray? = null
    private var prevCoreTicks: Array<LongArray>? = null
    private var prevRx = -1L
    private var prevTx = -1L
    private var prevT = 0L

    private val _history = MutableStateFlow<List<PcStats>>(emptyList())
    val history: StateFlow<List<PcStats>> = _history.asStateFlow()

    val stats: StateFlow<PcStats> = flow {
        while (true) { emit(runCatching { sample() }.getOrElse { PcStats(time = System.currentTimeMillis()) }); delay(1000) }
    }.flowOn(Dispatchers.IO)
        .onEach { s -> _history.value = (_history.value + s).takeLast(120) }
        .stateIn(scope, SharingStarted.WhileSubscribed(4000), PcStats())

    private fun sample(): PcStats {
        val now = System.nanoTime()
        if (prevT != 0L && now - prevT > 5_000_000_000L) { prevTicks = null; prevCoreTicks = null; prevRx = -1; prevTx = -1 }
        val cpu: CentralProcessor = hal.processor
        val ticks = cpu.systemCpuLoadTicks
        val load = prevTicks?.let { cpu.getSystemCpuLoadBetweenTicks(it).toFloat() }?.takeIf { it in 0f..1f }
        prevTicks = ticks
        val coreTicks = cpu.processorCpuLoadTicks
        val cores = prevCoreTicks?.takeIf { it.size == coreTicks.size }?.let { p -> cpu.getProcessorCpuLoadBetweenTicks(p).map { it.toFloat().coerceIn(0f, 1f) } }.orEmpty()
        prevCoreTicks = coreTicks
        val freqs = runCatching { cpu.currentFreq.map { (it / 1_000_000).toInt() } }.getOrDefault(emptyList()).filter { it > 0 }
        val max = cpu.maxFreq.takeIf { it > 0 }?.let { (it / 1_000_000).toInt() }

        val mem = hal.memory
        val sensors = hal.sensors
        // OSHI reports 0 (or NaN) when the PC doesn't expose a CPU sensor: that's "unavailable", not 0 °C.
        val temp = runCatching { sensors.cpuTemperature }.getOrNull()?.takeIf { it.isFinite() && it in 1.0..125.0 }?.toFloat()
        val fans = runCatching { sensors.fanSpeeds.filter { it > 0 } }.getOrDefault(emptyList())
        val bat = runCatching { hal.powerSources.firstOrNull { it.designCapacity > 0 || it.remainingCapacityPercent > 0 } }.getOrNull()

        var rx = 0L; var tx = 0L
        runCatching { hal.getNetworkIFs(false).forEach { it.updateAttributes(); rx += it.bytesRecv; tx += it.bytesSent } }
        var rxBps: Long? = null; var txBps: Long? = null
        if (prevRx >= 0 && now > prevT) {
            val dt = (now - prevT) / 1e9
            rxBps = ((rx - prevRx) / dt).toLong().takeIf { it >= 0 }
            txBps = ((tx - prevTx) / dt).toLong().takeIf { it >= 0 }
        }
        prevRx = rx; prevTx = tx; prevT = now
        val os = si.operatingSystem
        return PcStats(
            time = System.currentTimeMillis(), cpuLoad = load, coreLoads = cores, coreMHz = freqs, maxMHz = max,
            ramTotal = mem.total, ramAvail = mem.available, swapUsed = mem.virtualMemory.swapUsed, swapTotal = mem.virtualMemory.swapTotal,
            cpuTempC = temp, fanRpm = fans,
            batteryPct = bat?.let { (it.remainingCapacityPercent * 100).toInt() }, charging = bat?.isCharging,
            rxBps = rxBps, txBps = txBps, processes = os.processCount, uptimeS = os.systemUptime,
        )
    }

    /** Static hardware report for the Device page. */
    fun report(): List<InfoSection> {
        val os = si.operatingSystem
        val cs = hal.computerSystem
        val cpu = hal.processor
        val id = cpu.processorIdentifier
        val sections = mutableListOf<InfoSection>()
        sections += InfoSection("System", listOf(
            "Operating system" to os.toString(),
            "Manufacturer" to cs.manufacturer.clean(),
            "Model" to cs.model.clean(),
            "Motherboard" to listOfNotNull(cs.baseboard.manufacturer.clean(), cs.baseboard.model.clean()).joinToString(" ").ifEmpty { null },
            "BIOS / firmware" to cs.firmware.version.clean(),
            "Java runtime" to "${System.getProperty("java.vendor")} ${System.getProperty("java.version")}",
            "Uptime" to "${os.systemUptime / 3600} h ${(os.systemUptime / 60) % 60} min",
        ))
        sections += InfoSection("Processor", listOf(
            "Name" to id.name.clean(),
            "Vendor" to id.vendor.clean(),
            "Microarchitecture" to id.microarchitecture.clean(),
            "Physical cores" to cpu.physicalProcessorCount.toString(),
            "Threads" to cpu.logicalProcessorCount.toString(),
            "Max frequency" to cpu.maxFreq.takeIf { it > 0 }?.let { "%.2f GHz".format(it / 1e9) },
            "64-bit" to (if (id.isCpu64bit) "Yes" else "No"),
        ))
        val gpus = hal.graphicsCards
        sections += InfoSection("Graphics", if (gpus.isEmpty()) listOf("GPU" to null) else gpus.flatMapIndexed { i, g ->
            listOf(
                "GPU ${i + 1}" to g.name.clean(),
                "Vendor" to g.vendor.clean(),
                "Video memory" to g.vRam.takeIf { it > 0 }?.let { fmtBytes(it) },
                "Driver" to g.versionInfo.clean(),
            )
        })
        val mem = hal.memory
        sections += InfoSection("Memory", listOf("Total RAM" to fmtBytes(mem.total), "Available" to fmtBytes(mem.available)) +
            mem.physicalMemory.mapIndexed { i, m -> "Module ${i + 1}" to listOfNotNull(fmtBytes(m.capacity), m.memoryType.clean(), m.clockSpeed.takeIf { it > 0 }?.let { "${it / 1_000_000} MT/s" }, m.manufacturer.clean()).joinToString(" · ") })
        sections += InfoSection("Displays", displays())
        sections += InfoSection("Storage", hal.diskStores.map { d -> (d.model.clean() ?: d.name) to fmtBytes(d.size) }.ifEmpty { listOf("Disks" to null) })
        val ps = runCatching { hal.powerSources }.getOrDefault(emptyList()).filter { it.designCapacity > 0 }
        sections += InfoSection("Battery", if (ps.isEmpty()) listOf("Battery" to "None (desktop or not reported)") else ps.flatMap { b ->
            listOf(
                "Battery" to b.name.clean(),
                "Charge" to "${(b.remainingCapacityPercent * 100).toInt()} %",
                "Charging" to (if (b.isCharging) "Yes" else "No"),
                "Health (full vs design)" to if (b.designCapacity > 0 && b.maxCapacity > 0) "${b.maxCapacity * 100 / b.designCapacity} %" else null,
                "Cycles" to b.cycleCount.takeIf { it > 0 }?.toString(),
            )
        })
        return sections
    }

    fun gpuNames(): List<String> = runCatching { hal.graphicsCards.mapNotNull { it.name.clean() } }.getOrDefault(emptyList())
    fun cpuName(): String = runCatching { hal.processor.processorIdentifier.name.trim() }.getOrDefault("CPU")
    fun headline(): String = runCatching { listOfNotNull(hal.computerSystem.manufacturer.clean(), hal.computerSystem.model.clean()).joinToString(" ").ifEmpty { "This PC" } }.getOrDefault("This PC")

    companion object {
        /** Each monitor's resolution and refresh rate from Java's display API (no driver access needed). */
        fun displays(): List<Pair<String, String?>> = runCatching {
            if (GraphicsEnvironment.isHeadless()) return@runCatching listOf("Displays" to null)
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.mapIndexed { i, d ->
                val m = d.displayMode
                val hz = m.refreshRate.takeIf { it > 0 }?.let { "$it Hz" } ?: "refresh rate not reported"
                "Display ${i + 1}" to "${m.width} × ${m.height} · $hz"
            }
        }.getOrDefault(listOf("Displays" to null))

        fun maxRefreshHz(): Int? = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.maxOfOrNull { it.displayMode.refreshRate }?.takeIf { it > 0 }
        }.getOrNull()
    }
}

/** OSHI uses "unknown"/"Default string" placeholders; REDCORE shows those as unavailable. */
fun String?.clean(): String? = this?.trim()?.takeIf {
    it.isNotEmpty() && !it.equals("unknown", true) && !it.equals("default string", true) && !it.equals("to be filled by o.e.m.", true) && it != "0"
}
