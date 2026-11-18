package com.redcore.booster.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.data.Insights
import com.redcore.booster.data.LiveStats
import com.redcore.booster.data.NetInfo
import com.redcore.booster.data.PingResult
import com.redcore.booster.data.fmtRate
import com.redcore.booster.ui.SpeedPhase
import com.redcore.booster.ui.SpeedState
import com.redcore.booster.ui.components.Chip
import com.redcore.booster.ui.components.Divider
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.InfoRow
import com.redcore.booster.ui.components.InsightList
import com.redcore.booster.ui.components.LineChart
import com.redcore.booster.ui.components.LoadingSpinner
import com.redcore.booster.ui.components.RingGauge
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.components.StatTile
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType
import kotlin.math.ln

@Composable
fun NetworkScreen(
    info: NetInfo?,
    ping: PingResult?,
    pinging: Boolean,
    speed: SpeedState,
    stats: LiveStats,
    history: List<LiveStats>,
    onPing: () -> Unit,
    onSpeed: () -> Unit,
    onShowName: () -> Unit,
    onWifiSettings: () -> Unit,
) {
    HudBackdrop {
        if (info == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingSpinner() }; return@HudBackdrop }
        com.redcore.booster.ui.components.Panes(1f, 1f, left = {
            ConnectionHeader(info, onShowName)

            SectionHeader("Speed test")
            GlassPanel(Modifier.fillMaxWidth(), accent = true) {
                val running = speed.phase in listOf(SpeedPhase.PING, SpeedPhase.DOWNLOAD, SpeedPhase.UPLOAD)
                val shown = when (speed.phase) {
                    SpeedPhase.DOWNLOAD, SpeedPhase.UPLOAD -> speed.liveBps
                    SpeedPhase.DONE -> speed.downBps ?: 0.0
                    else -> 0.0
                }
                val mbps = shown / 1e6
                // log scale so both 5 Mbps and 900 Mbps read well
                val prog = if (mbps <= 0) 0f else (ln(1 + mbps) / ln(1 + 1000.0)).toFloat()
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RingGauge(
                        if (speed.phase == SpeedPhase.IDLE) 0f else prog,
                        if (speed.phase == SpeedPhase.IDLE || speed.phase == SpeedPhase.PING) "—" else if (mbps >= 100) "%.0f".format(mbps) else "%.1f".format(mbps),
                        "Mbps",
                        when (speed.phase) {
                            SpeedPhase.IDLE -> "Ready"; SpeedPhase.PING -> "Measuring latency"; SpeedPhase.DOWNLOAD -> "Download"
                            SpeedPhase.UPLOAD -> "Upload"; SpeedPhase.DONE -> "Download result"; SpeedPhase.ERROR -> "Test failed"
                        },
                        size = 170.dp, color = if (speed.phase == SpeedPhase.UPLOAD) RC.Violet else RC.Red
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Ping", speed.pingMs?.let { "%.0f ms".format(it) } ?: "—", Modifier.weight(1f), accent = RC.Green)
                    StatTile("Down", speed.downBps?.let { fmtRate((it / 8).toLong()) } ?: "—", Modifier.weight(1f))
                    StatTile("Up", speed.upBps?.let { fmtRate((it / 8).toLong()) } ?: "—", Modifier.weight(1f), accent = RC.Violet)
                }
                if (speed.phase == SpeedPhase.ERROR) Text(speed.error ?: "", style = RCType.bodyDim.copy(color = RC.Amber), modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(12.dp))
                GlowButton(if (running) "Stop" else if (speed.phase == SpeedPhase.DONE) "Test again" else "Start speed test", Modifier.fillMaxWidth(),
                    if (running) Icons.Default.Stop else Icons.Default.PlayArrow, enabled = info.connected, onClick = onSpeed)
                Spacer(Modifier.height(6.dp))
                Text("Uses Cloudflare's public speed-test servers. A full test transfers roughly 50–300 MB${if (info.metered) " — you're on a metered connection" else ""}.", style = RCType.bodyDim.copy(fontSize = 11.sp, color = if (info.metered) RC.Amber else RC.TextDim))
            }

        }, right = {
            SectionHeader("Gaming latency")
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ping?.avg?.let { "%.0f ms".format(it) } ?: "—", style = RCType.number.copy(fontSize = 26.sp))
                        Text(if (ping != null && ping.samples.isNotEmpty() && !pinging) ping.stability else if (pinging) "Measuring…" else "TCP handshake to 1.1.1.1", style = RCType.bodyDim)
                    }
                    GlowButton(if (pinging) "Testing" else "Test", icon = Icons.Default.NetworkPing, primary = !pinging, enabled = !pinging && info.connected, onClick = onPing)
                }
                if (ping != null && ping.samples.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    PingBars(ping)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat("Min", ping.min?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                        MiniStat("Max", ping.max?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                        MiniStat("Jitter", ping.jitter?.let { "%.1f ms".format(it) } ?: "—", Modifier.weight(1f))
                        MiniStat("Loss", "${(ping.loss * 100).toInt()}%", Modifier.weight(1f))
                    }
                }
            }

            SectionHeader("Live traffic")
            GlassPanel(Modifier.fillMaxWidth()) {
                Row {
                    Text("↓ ${fmtRate(stats.rxBps)}", style = RCType.strong.copy(color = RC.Red), modifier = Modifier.weight(1f))
                    Text("↑ ${fmtRate(stats.txBps)}", style = RCType.strong.copy(color = RC.Violet))
                }
                Spacer(Modifier.height(6.dp))
                LineChart(history.map { it.rxBps?.toFloat() }, Modifier.fillMaxWidth().height(70.dp), color = RC.Red, minY = 0f)
            }

            SectionHeader("Connection details")
            GlassPanel(Modifier.fillMaxWidth()) {
                val rows = listOf(
                    "Type" to info.type,
                    "Internet access" to if (info.validated) "Verified" else "Not verified",
                    "Metered" to if (info.metered) "Yes" else "No",
                    "VPN" to if (info.vpn) "Active" else "Off",
                    "Signal" to (info.rssi?.let { "$it dBm" } ?: info.signalLevel?.let { "Level $it / 4" }),
                    "Link speed" to info.linkMbps?.let { "$it Mbps" },
                    "Frequency" to info.frequencyMhz?.let { "$it MHz (${if (it < 3000) "2.4 GHz" else if (it < 5900) "5 GHz" else "6 GHz"})" },
                    "Wi-Fi standard" to info.wifiStandard,
                    "Estimated bandwidth" to info.estDownKbps?.let { "↓ ${it / 1000} Mbps / ↑ ${(info.estUpKbps ?: 0) / 1000} Mbps" },
                    "IPv4 address" to info.ipv4,
                    "IPv6" to if (info.ipv6) "Available" else "Not available",
                    "DNS servers" to info.dns.takeIf { it.isNotEmpty() }?.joinToString(", "),
                )
                rows.forEachIndexed { i, (k, v) -> if (i > 0) Divider(); InfoRow(k, v) }
                if (info.type == "Wi-Fi") {
                    Spacer(Modifier.height(10.dp))
                    GlowButton("Wi-Fi settings", Modifier.fillMaxWidth(), Icons.Default.SettingsEthernet, primary = false, onClick = onWifiSettings)
                }
            }

            SectionHeader("Recommendations")
            InsightList(Insights.network(info, ping))
            Spacer(Modifier.height(6.dp))
            Text("REDCORE can't make your connection faster than your network allows — it measures it and tells you what to fix.", style = RCType.bodyDim.copy(fontSize = 12.sp))
        })
    }
}

@Composable
private fun ConnectionHeader(info: NetInfo, onShowName: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), accent = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignalBars(info.signalLevel, Modifier.size(54.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, null, tint = RC.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(info.type.uppercase(), style = RCType.label.copy(color = RC.Red))
                }
                Text(info.ssid ?: if (info.type == "Wi-Fi") "Network name hidden" else info.type, style = RCType.title.copy(fontSize = 16.sp))
                if (info.ssidNeedsLocation) Text("Tap to show name (Android requires location permission)", style = RCType.bodyDim.copy(fontSize = 12.sp, color = RC.Blue),
                    modifier = Modifier.padding(top = 2.dp).clickable(onClick = onShowName))
            }
            Chip(if (!info.connected) "Offline" else if (info.validated) "Online" else "No internet", if (info.connected && info.validated) RC.Green else RC.Amber)
        }
    }
}

@Composable
private fun SignalBars(level: Int?, modifier: Modifier) {
    Canvas(modifier) {
        val bars = 4
        val bw = size.width / (bars * 1.6f)
        for (i in 0 until bars) {
            val h = size.height * (0.3f + 0.7f * (i + 1) / bars)
            val x = i * bw * 1.6f
            val on = level != null && i < level.coerceAtLeast(1)
            drawRect(if (on) RC.Red else RC.Stroke, Offset(x, size.height - h), Size(bw, h))
        }
    }
}

@Composable
private fun PingBars(p: PingResult) {
    val max = (p.ok.maxOrNull() ?: 1L).coerceAtLeast(30L).toFloat()
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val n = 12
        val w = size.width / n
        p.samples.forEachIndexed { i, v ->
            if (v == null) {
                drawRect(RC.Amber.copy(alpha = 0.5f), Offset(i * w + 3, size.height - 6), Size(w - 6, 6f))
            } else {
                val h = (v / max).coerceIn(0.05f, 1f) * size.height
                val c = when { v < 40 -> RC.Green; v < 90 -> RC.Amber; else -> RC.Red }
                drawRect(c, Offset(i * w + 3, size.height - h), Size(w - 6, h))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = RCType.label.copy(fontSize = 8.sp))
        Text(value, style = RCType.strong.copy(fontSize = 14.sp, color = Color.White))
    }
}
