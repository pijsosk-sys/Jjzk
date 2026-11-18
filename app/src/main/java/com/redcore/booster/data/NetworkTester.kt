package com.redcore.booster.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.sqrt

data class NetInfo(
    val connected: Boolean,
    val type: String,
    val validated: Boolean,
    val metered: Boolean,
    val vpn: Boolean,
    val estDownKbps: Int?,
    val estUpKbps: Int?,
    val ssid: String?,
    val rssi: Int?,
    val signalLevel: Int?, // 0..4
    val linkMbps: Int?,
    val frequencyMhz: Int?,
    val wifiStandard: String?,
    val ipv4: String?,
    val ipv6: Boolean,
    val dns: List<String>,
    val ssidNeedsLocation: Boolean,
)

data class PingResult(val target: String, val samples: List<Long?>) {
    val ok get() = samples.filterNotNull()
    val avg: Double? get() = ok.takeIf { it.isNotEmpty() }?.average()
    val min: Long? get() = ok.minOrNull()
    val max: Long? get() = ok.maxOrNull()
    val loss: Float get() = if (samples.isEmpty()) 0f else samples.count { it == null }.toFloat() / samples.size
    /** Mean absolute difference between consecutive samples (RFC 3550-style jitter). */
    val jitter: Double? get() = if (ok.size < 2) null else ok.zipWithNext { a, b -> abs(a - b).toDouble() }.average()
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

class NetworkTester(private val context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    fun info(): NetInfo {
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val lp = net?.let { cm.getLinkProperties(it) }
        val type = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth tether"
            else -> "Other"
        }
        var ssid: String? = null; var rssi: Int? = null; var link: Int? = null; var freq: Int? = null; var std: String? = null; var level: Int? = null
        val hasLoc = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (type == "Wi-Fi") {
            // NetworkCapabilities.transportInfo has the SSID redacted on Android 12+, so the (deprecated but
            // still supported) WifiManager.connectionInfo is used; it returns the SSID when location is granted.
            @Suppress("DEPRECATION")
            val legacy: WifiInfo? = runCatching { context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo }.getOrNull()
            val wi: WifiInfo? = legacy ?: if (Build.VERSION.SDK_INT >= 31) caps?.transportInfo as? WifiInfo else null
            if (wi != null) {
                rssi = wi.rssi.takeIf { it in -127..0 && it != -127 }
                link = wi.linkSpeed.takeIf { it > 0 }
                freq = wi.frequency.takeIf { it > 0 }
                val raw = wi.ssid?.trim('"')
                ssid = raw?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                if (Build.VERSION.SDK_INT >= 30) std = when (wi.wifiStandard) {
                    4 -> "Wi-Fi 4 (802.11n)"; 5 -> "Wi-Fi 5 (802.11ac)"; 6 -> "Wi-Fi 6 (802.11ax)"; 8 -> "Wi-Fi 7 (802.11be)"; 1 -> "Legacy"; else -> null
                }
                rssi?.let { level = WifiManager.calculateSignalLevel(it, 5) }
            }
        } else if (type == "Mobile data" && Build.VERSION.SDK_INT >= 28) {
            level = runCatching { context.getSystemService(TelephonyManager::class.java)?.signalStrength?.level }.getOrNull()
        }
        val v4 = lp?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress
        val v6 = lp?.linkAddresses?.any { it.address is java.net.Inet6Address && !it.address.isLinkLocalAddress } ?: false
        return NetInfo(
            connected = caps != null,
            type = type,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            metered = cm.isActiveNetworkMetered,
            vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            estDownKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
            estUpKbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 },
            ssid = ssid, rssi = rssi, signalLevel = level, linkMbps = link, frequencyMhz = freq, wifiStandard = std,
            ipv4 = v4, ipv6 = v6,
            dns = lp?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
            ssidNeedsLocation = type == "Wi-Fi" && ssid == null && !hasLoc,
        )
    }

    /** TCP handshake time — works without root (ICMP ping needs raw sockets). */
    suspend fun ping(host: String, port: Int = 443, count: Int = 10, onSample: (List<Long?>) -> Unit = {}): PingResult = withContext(Dispatchers.IO) {
        val out = mutableListOf<Long?>()
        repeat(count) {
            coroutineContext.ensureActive()
            val t0 = SystemClock.elapsedRealtimeNanos()
            val ms = try {
                Socket().use { s -> s.connect(InetSocketAddress(host, port), 2000) }
                (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
            } catch (_: Exception) { null }
            out += ms
            onSample(out.toList())
            Thread.sleep(150)
        }
        PingResult(host, out)
    }

    /** Downloads from Cloudflare's public speed-test endpoint for up to [seconds]. Returns bits per second. */
    suspend fun downloadTest(seconds: Int = 8, onProgress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val conn = URL("https://speed.cloudflare.com/__down?bytes=200000000").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        var bytes = 0L
        val start = SystemClock.elapsedRealtime()
        try {
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var lastReport = start
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf); if (n < 0) break
                    bytes += n
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastReport > 200) { onProgress(bytes * 8.0 * 1000 / (now - start)); lastReport = now }
                    if (now - start > seconds * 1000) break
                }
            }
        } finally { conn.disconnect() }
        val el = (SystemClock.elapsedRealtime() - start).coerceAtLeast(1)
        bytes * 8.0 * 1000 / el
    }

    suspend fun uploadTest(seconds: Int = 6, onProgress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val chunk = ByteArray(64 * 1024) { (it * 31).toByte() }
        val total = 40L * 1024 * 1024
        val conn = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 8000
        conn.doOutput = true; conn.requestMethod = "POST"
        conn.setFixedLengthStreamingMode(total)
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        var sent = 0L
        val start = SystemClock.elapsedRealtime()
        try {
            conn.outputStream.use { os ->
                var lastReport = start
                while (sent < total) {
                    coroutineContext.ensureActive()
                    os.write(chunk); sent += chunk.size
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastReport > 200) { onProgress(sent * 8.0 * 1000 / (now - start)); lastReport = now }
                    if (now - start > seconds * 1000) break
                }
            }
        } catch (_: Exception) {
            // Stopping early closes the stream before the declared length; the measured rate is still valid.
        } finally {
            runCatching { conn.disconnect() }
        }
        val el = (SystemClock.elapsedRealtime() - start).coerceAtLeast(1)
        sent * 8.0 * 1000 / el
    }

    companion object {
        fun stdDev(v: List<Long>): Double {
            if (v.size < 2) return 0.0
            val m = v.average(); return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
        }
    }
}
