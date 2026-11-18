package com.redcore.booster.data

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import kotlin.math.hypot
import kotlin.math.roundToInt

data class InfoSection(val title: String, val rows: List<Pair<String, String?>>)

data class CoreInfo(val index: Int, val minMHz: Int?, val maxMHz: Int?, val governor: String?)

data class SensorInfo(val name: String, val vendor: String, val type: String, val range: String, val powerMa: Float, val wakeUp: Boolean)

data class DeviceReport(
    val headline: String,
    val subline: String,
    val sections: List<InfoSection>,
    val cores: List<CoreInfo>,
    val sensors: List<SensorInfo>,
    val keySensors: List<Pair<String, Boolean>>,
)

/** Gathers everything Android exposes about the phone. Nothing is guessed: missing values stay null. */
class DeviceInfoProvider(private val context: Context) {

    fun report(): DeviceReport {
        val pm = context.packageManager
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

        // ---------------- device
        val device = InfoSection(
            "Device", listOf(
                "Manufacturer" to Build.MANUFACTURER.cap(),
                "Brand" to Build.BRAND.cap(),
                "Model" to Build.MODEL,
                "Codename" to Build.DEVICE,
                "Product" to Build.PRODUCT,
                "Board" to Build.BOARD.nn(),
                "Hardware" to Build.HARDWARE.nn(),
                "Android version" to "Android ${Build.VERSION.RELEASE}",
                "SDK level" to Build.VERSION.SDK_INT.toString(),
                "Security patch" to Build.VERSION.SECURITY_PATCH.nn(),
                "Build ID" to Build.ID,
                "Build fingerprint" to Build.FINGERPRINT,
                "Build type" to Build.TYPE,
                "Bootloader" to Build.BOOTLOADER.nn(),
                "Radio firmware" to Build.getRadioVersion().nn(),
                "Kernel" to (System.getProperty("os.version")?.nn()),
                "Kernel build" to SysFs.read("/proc/version")?.take(160),
            )
        )

        // ---------------- processor
        val cores = (0 until SysFs.cpuCount()).map { i ->
            CoreInfo(i, SysFs.coreMinFreqKHz(i)?.let { (it / 1000).toInt() }, SysFs.coreMaxFreqKHz(i)?.let { (it / 1000).toInt() }, SysFs.coreGovernor(i))
        }
        val clusters = cores.mapNotNull { it.maxMHz }.groupingBy { it }.eachCount().toSortedMap(compareByDescending { it })
        val clusterText = if (clusters.isEmpty()) null else clusters.entries.joinToString("  +  ") { "${it.value}× ${fmtGHz(it.key)}" }
        val socModel = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.nn() else null
        val socMfr = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER.nn() else null
        val cpuHw = SysFs.cpuInfoField("Hardware")
        val processor = InfoSection(
            "Processor", listOf(
                "Chipset" to (listOfNotNull(socMfr, socModel).joinToString(" ").ifEmpty { null } ?: cpuHw),
                "Platform" to (Build.HARDWARE.nn()),
                "Architecture" to System.getProperty("os.arch"),
                "Supported ABIs" to Build.SUPPORTED_ABIS.joinToString(", "),
                "Cores" to cores.size.toString(),
                "Clusters" to clusterText,
                "Peak frequency" to cores.mapNotNull { it.maxMHz }.maxOrNull()?.let { fmtGHz(it) },
                "Lowest frequency" to cores.mapNotNull { it.minMHz }.minOrNull()?.let { "$it MHz" },
                "Governor" to cores.mapNotNull { it.governor }.distinct().joinToString(", ").ifEmpty { null },
                "CPU implementer" to SysFs.cpuInfoField("CPU implementer"),
                "CPU part" to SysFs.cpuInfoField("CPU part"),
            )
        )

        // ---------------- gpu
        val gl = GpuInfo.query()
        val glEs = am.deviceConfigurationInfo.reqGlEsVersion
        val vulkanVer = vulkanVersion(pm)
        val vulkanLevel = featureVersion(pm, PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val gpu = InfoSection(
            "Graphics", listOf(
                "GPU renderer" to gl.renderer,
                "GPU vendor" to gl.vendor,
                "OpenGL ES (driver)" to gl.version,
                "OpenGL ES (system)" to "${glEs shr 16}.${glEs and 0xffff}",
                "Vulkan" to (vulkanVer ?: "Not supported"),
                "Vulkan hardware level" to vulkanLevel?.toString(),
                "Vulkan compute" to featureVersion(pm, PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE)?.toString(),
                "GL extensions" to gl.extensionCount?.toString(),
                "Max texture size" to gl.maxTextureSize?.let { "$it px" },
                "AEP support" to pm.hasSystemFeature(PackageManager.FEATURE_OPENGLES_EXTENSION_PACK).yesNo(),
            )
        )

        // ---------------- memory
        val memInfo = SysFs.read("/proc/meminfo")
        fun memKb(key: String) = memInfo?.lineSequence()?.firstOrNull { it.startsWith("$key:") }
            ?.replace(Regex("[^0-9]"), "")?.toLongOrNull()?.times(1024)
        val memory = InfoSection(
            "Memory", listOf(
                "Total RAM" to fmtBytes(mi.totalMem),
                "Available RAM" to fmtBytes(mi.availMem),
                "Used RAM" to fmtBytes(mi.totalMem - mi.availMem),
                "Low-memory threshold" to fmtBytes(mi.threshold),
                "Under memory pressure" to mi.lowMemory.yesNo(),
                "Cached" to memKb("Cached")?.let { fmtBytes(it) },
                "Swap total" to memKb("SwapTotal")?.let { fmtBytes(it) },
                "Swap free" to memKb("SwapFree")?.let { fmtBytes(it) },
                "App heap limit" to "${am.memoryClass} MB (large: ${am.largeMemoryClass} MB)",
                "Low-RAM device" to am.isLowRamDevice.yesNo(),
            )
        )

        // ---------------- display
        val display = displayInfo()

        // ---------------- battery
        val battery = batterySection()

        // ---------------- storage
        val data = StatFs(Environment.getDataDirectory().path)
        val total = data.totalBytes
        val free = data.availableBytes
        val storage = InfoSection(
            "Storage", listOf(
                "Internal total" to fmtBytes(total),
                "Internal used" to fmtBytes(total - free),
                "Internal free" to fmtBytes(free),
                "External media" to (if (Environment.isExternalStorageRemovable()) "Removable storage present" else "Emulated (shared internal)"),
            )
        )

        // ---------------- sensors
        val sm = context.getSystemService(SensorManager::class.java)
        val sensors = sm?.getSensorList(Sensor.TYPE_ALL).orEmpty().map {
            SensorInfo(it.name, it.vendor, sensorTypeName(it.type, it.stringType), "%.1f".format(it.maximumRange), it.power, it.isWakeUpSensor)
        }
        val key = listOf(
            "Accelerometer" to Sensor.TYPE_ACCELEROMETER,
            "Gyroscope" to Sensor.TYPE_GYROSCOPE,
            "Compass" to Sensor.TYPE_MAGNETIC_FIELD,
            "Proximity" to Sensor.TYPE_PROXIMITY,
            "Light" to Sensor.TYPE_LIGHT,
            "Barometer" to Sensor.TYPE_PRESSURE,
            "Step counter" to Sensor.TYPE_STEP_COUNTER,
            "Rotation vector" to Sensor.TYPE_ROTATION_VECTOR,
        ).map { (n, t) -> n to (sm?.getDefaultSensor(t) != null) }

        val features = InfoSection(
            "Features", listOf(
                "NFC" to pm.hasSystemFeature(PackageManager.FEATURE_NFC).yesNo(),
                "Bluetooth LE" to pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE).yesNo(),
                "Wi-Fi Direct" to pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT).yesNo(),
                "Wi-Fi Aware" to pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE).yesNo(),
                "Fingerprint" to pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT).yesNo(),
                "Multi-touch (5+ points)" to pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND).yesNo(),
                "Gamepad support" to pm.hasSystemFeature("android.hardware.gamepad").yesNo(),
                "Vibrator" to (context.getSystemService(android.os.Vibrator::class.java)?.hasVibrator() == true).yesNo(),
            )
        )

        val name = listOf(Build.MANUFACTURER.cap(), Build.MODEL).distinct().joinToString(" ")
        val sub = listOfNotNull(socModel ?: cpuHw ?: Build.HARDWARE, "Android ${Build.VERSION.RELEASE}", fmtBytes(mi.totalMem) + " RAM").joinToString(" · ")
        return DeviceReport(name, sub, listOf(device, processor, gpu, memory, display, battery, storage, features), cores, sensors, key)
    }

    @SuppressLint("NewApi")
    private fun displayInfo(): InfoSection {
        val dm = context.getSystemService(DisplayManager::class.java)
        val d: Display? = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        val wm = context.getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") d?.getRealMetrics(metrics)
        val w = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.width() else metrics.widthPixels
        val h = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.height() else metrics.heightPixels
        val inches = if (metrics.xdpi > 0 && metrics.ydpi > 0) hypot(w / metrics.xdpi.toDouble(), h / metrics.ydpi.toDouble()) else null
        val modes = d?.supportedModes.orEmpty()
        val rates = modes.map { it.refreshRate.roundToInt() }.distinct().sorted()
        val hdr = d?.hdrCapabilities?.supportedHdrTypes?.map {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                4 -> "HDR10+"
                else -> "Type $it"
            }
        }.orEmpty()
        return InfoSection(
            "Display", listOf(
                "Resolution" to "$w × $h px",
                "Density" to "${metrics.densityDpi} dpi (${densityBucket(metrics.densityDpi)})",
                "Physical DPI" to if (metrics.xdpi > 0) "%.0f × %.0f".format(metrics.xdpi, metrics.ydpi) else null,
                "Diagonal (approx.)" to inches?.let { "%.2f\"".format(it) },
                "Current refresh rate" to d?.refreshRate?.let { "${it.roundToInt()} Hz" },
                "Supported refresh rates" to rates.takeIf { it.isNotEmpty() }?.joinToString(" / ") { "$it Hz" },
                "Display modes" to modes.size.toString(),
                "HDR" to (if (hdr.isEmpty()) "Not reported" else hdr.joinToString(", ")),
                "Wide colour gamut" to (if (Build.VERSION.SDK_INT >= 26) context.resources.configuration.isScreenWideColorGamut.yesNo() else null),
            )
        )
    }

    private fun batterySection(): InfoSection {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else null
        val temp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val volt = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val cycles = i?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it >= 0 }
        val chargeCounter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 && it != Int.MIN_VALUE }
        val design = BatteryStats.designCapacityMah(context)
        return InfoSection(
            "Battery", listOf(
                "Level" to pct?.let { "$it %" },
                "Status" to BatteryStats.statusName(i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1),
                "Power source" to BatteryStats.plugName(i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0),
                "Health" to BatteryStats.healthName(i?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1),
                "Technology" to i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.nn(),
                "Temperature" to temp?.let { "%.1f °C".format(it) },
                "Voltage" to volt?.let { "%.2f V".format(it / 1000f) },
                "Charge cycles" to cycles?.toString(),
                "Remaining charge" to chargeCounter?.let { "${it / 1000} mAh" },
                "Design capacity" to design?.let { "$it mAh" },
            )
        )
    }

    private fun vulkanVersion(pm: PackageManager): String? {
        val f = pm.systemAvailableFeatures.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION } ?: return null
        val v = f.version
        return "${v shr 22}.${(v shr 12) and 0x3ff}.${v and 0xfff}"
    }

    private fun featureVersion(pm: PackageManager, name: String): Int? =
        pm.systemAvailableFeatures.firstOrNull { it.name == name }?.version

    companion object {
        fun sensorTypeName(type: Int, fallback: String?): String = when (type) {
            Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
            Sensor.TYPE_GYROSCOPE -> "Gyroscope"
            Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
            Sensor.TYPE_PROXIMITY -> "Proximity"
            Sensor.TYPE_LIGHT -> "Light"
            Sensor.TYPE_PRESSURE -> "Barometer"
            Sensor.TYPE_GRAVITY -> "Gravity"
            Sensor.TYPE_LINEAR_ACCELERATION -> "Linear acceleration"
            Sensor.TYPE_ROTATION_VECTOR -> "Rotation vector"
            Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game rotation vector"
            Sensor.TYPE_STEP_COUNTER -> "Step counter"
            Sensor.TYPE_STEP_DETECTOR -> "Step detector"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient temperature"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "Humidity"
            Sensor.TYPE_HEART_RATE -> "Heart rate"
            Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant motion"
            else -> fallback?.substringAfterLast('.')?.replace('_', ' ')?.cap() ?: "Type $type"
        }

        private fun densityBucket(dpi: Int) = when {
            dpi <= 160 -> "mdpi"; dpi <= 240 -> "hdpi"; dpi <= 320 -> "xhdpi"; dpi <= 480 -> "xxhdpi"; else -> "xxxhdpi"
        }
    }
}

internal fun String?.nn(): String? = this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", true) && it != "0" }
internal fun String.cap(): String = replaceFirstChar { it.uppercase() }
internal fun Boolean.yesNo() = if (this) "Yes" else "No"

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
    val m = ms / 60000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}
