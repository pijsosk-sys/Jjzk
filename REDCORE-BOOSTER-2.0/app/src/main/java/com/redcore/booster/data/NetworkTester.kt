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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.coroutines.coroutineContext

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

/** A speed-test transfer that failed. The message is shown to the user as-is. */
class SpeedTestException(message: String) : IOException(message)

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
                rssi = wi.rssi.takeIf { it in -126..-1 }
                link = wi.linkSpeed.takeIf { it > 0 }
                freq = wi.frequency.takeIf { it > 0 }
                val raw = wi.ssid?.trim('"')
                ssid = raw?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                if (Build.VERSION.SDK_INT >= 30) std = when (wi.wifiStandard) {
                    4 -> "Wi-Fi 4 (802.11n)"; 5 -> "Wi-Fi 5 (802.11ac)"; 6 -> "Wi-Fi 6 (802.11ax)"; 8 -> "Wi-Fi 7 (802.11be)"; 1 -> "Legacy"; else -> null
                }
                rssi?.let {
                    @Suppress("DEPRECATION")
                    level = WifiManager.calculateSignalLevel(it, 5)
                }
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

    /**
     * TCP handshake time — works without root (ICMP ping needs raw sockets). The host is resolved once
     * before measuring, so DNS lookup time is never counted as latency. Samples arrive through [onSample].
     */
    suspend fun ping(host: String, port: Int = 443, count: Int = 10, onSample: (List<Long?>) -> Unit = {}): PingResult = withContext(Dispatchers.IO) {
        val address = try { InetAddress.getByName(host) } catch (e: Exception) {
            return@withContext PingResult(host, List(count) { null }).also { onSample(it.samples) }
        }
        val out = mutableListOf<Long?>()
        repeat(count) {
            coroutineContext.ensureActive()
            val t0 = SystemClock.elapsedRealtimeNanos()
            val ms = try {
                Socket().use { s -> s.connect(InetSocketAddress(address, port), 2000) }
                (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
            } catch (_: Exception) { null }
            out += ms
            onSample(out.toList())
            delay(150)
        }
        PingResult(host, out)
    }

    /**
     * Downloads from Cloudflare's public speed-test endpoint for up to [seconds]. The clock starts at the
     * first byte (connection setup isn't bandwidth) and the result skips TCP slow start. Returns bits/s.
     */
    suspend fun downloadTest(seconds: Int = 8, onProgress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val conn = URL("https://speed.cloudflare.com/__down?bytes=200000000").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        conn.setRequestProperty("Cache-Control", "no-cache")
        val checkpoints = mutableListOf<Pair<Long, Long>>()
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw SpeedTestException("The test server answered HTTP $code. Try again later.")
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var bytes = 0L
                var t0 = 0L
                var lastMark = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf); if (n < 0) break
                    val now = SystemClock.elapsedRealtimeNanos()
                    if (t0 == 0L) { t0 = now; checkpoints += 0L to 0L; lastMark = now }
                    bytes += n
                    if (now - lastMark >= 200_000_000L) {
                        lastMark = now
                        checkpoints += (now - t0) to bytes
                        NetMath.steadyThroughput(checkpoints)?.let(onProgress)
                    }
                    if (now - t0 > seconds * 1_000_000_000L) break
                }
                if (t0 != 0L) checkpoints += (SystemClock.elapsedRealtimeNanos() - t0) to bytes
            }
        } finally { conn.disconnect() }
        NetMath.steadyThroughput(checkpoints) ?: throw SpeedTestException("No data was received.")
    }

    /**
     * Upload test with complete requests: each POST is timed until the server's response, so only data the
     * server actually received is counted (bytes still sitting in the phone's send buffer would inflate the
     * result). Request sizes grow so slow and fast connections both get a steady measurement.
     */
    suspend fun uploadTest(seconds: Int = 6, onProgress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtimeNanos()
        var size = 256 * 1024
        var sentBytes = 0L
        var sentNs = 0L
        var lastError: String? = null
        while (SystemClock.elapsedRealtimeNanos() - start < seconds * 1_000_000_000L) {
            coroutineContext.ensureActive()
            val body = ByteArray(size) { (it * 31).toByte() }
            val conn = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 10_000
            conn.doOutput = true; conn.requestMethod = "POST"
            conn.setFixedLengthStreamingMode(body.size)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            val t0 = SystemClock.elapsedRealtimeNanos()
            try {
                conn.outputStream.use { it.write(body) }
                val code = conn.responseCode // waits until the server has the whole body
                if (code !in 200..299) { lastError = "The test server answered HTTP $code."; break }
                runCatching { conn.inputStream.use { it.readBytes() } }
                val dt = SystemClock.elapsedRealtimeNanos() - t0
                sentBytes += body.size; sentNs += dt
                NetMath.bitsPerSecond(sentBytes, sentNs)?.let(onProgress)
                // Aim for requests of roughly 1 s so per-request overhead stays small.
                if (dt < 700_000_000L && size < 16 * 1024 * 1024) size *= 2
            } catch (e: IOException) {
                lastError = e.message ?: "Connection lost."
                break
            } finally { runCatching { conn.disconnect() } }
        }
        NetMath.bitsPerSecond(sentBytes, sentNs) ?: throw SpeedTestException(lastError?.let { "Upload failed: $it" } ?: "Upload failed.")
    }
}
