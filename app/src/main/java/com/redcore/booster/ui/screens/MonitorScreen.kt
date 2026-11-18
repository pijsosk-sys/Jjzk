package com.redcore.booster.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.LiveStats
import com.redcore.booster.data.SystemMonitor
import com.redcore.booster.data.fmtBytes
import com.redcore.booster.data.fmtRate
import com.redcore.booster.data.temp
import com.redcore.booster.ui.Dest
import com.redcore.booster.ui.components.Chip
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.LevelBar
import com.redcore.booster.ui.components.LineChart
import com.redcore.booster.ui.components.PulsingDot
import com.redcore.booster.ui.components.RingGauge
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.components.SettingRow
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

@Composable
fun MonitorScreen(stats: LiveStats, history: List<LiveStats>, settings: AppSettings, gpuName: String?, onNavigate: (Dest) -> Unit) {
    HudBackdrop {
        com.redcore.booster.ui.components.Panes(1f, 1f, left = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(RC.Green); Spacer(Modifier.width(8.dp))
                Text("LIVE · UPDATES EVERY SECOND", style = RCType.label.copy(fontSize = 9.sp, color = RC.Green))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RingGauge(stats.clockLevel, stats.avgCurMHz?.let { "%.2f".format(it / 1000f) } ?: "N/A", "GHz avg", "CPU clock", size = 104.dp)
                RingGauge(stats.ramFraction, "${(stats.ramFraction * 100).toInt()}", "%", "Memory", size = 104.dp, color = RC.Blue, secondary = RC.Red)
                RingGauge(stats.batteryTempC?.let { ((it - 20f) / 30f).coerceIn(0f, 1f) }, settings.temp(stats.batteryTempC).dropLast(2), if (settings.fahrenheit) "°F" else "°C", "Temp", size = 104.dp, color = tempColor(stats.batteryTempC))
            }

            SectionHeader("CPU cores")
            GlassPanel(Modifier.fillMaxWidth()) {
                if (stats.coreCurMHz.all { it == null }) {
                    Text("This phone hides live CPU clocks from apps (SELinux policy). Core count: ${stats.coreCurMHz.size}.", style = RCType.bodyDim)
                } else {
                    stats.coreCurMHz.forEachIndexed { i, cur ->
                        val max = stats.coreMaxMHz.getOrNull(i)
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("C$i", style = RCType.label.copy(fontSize = 9.sp, color = RC.Text), modifier = Modifier.width(28.dp))
                            LevelBar(if (cur != null && max != null && max > 0) cur.toFloat() / max else 0f, Modifier.weight(1f), color = if (max == stats.maxMHz) RC.Red else RC.Blue)
                            Spacer(Modifier.width(10.dp))
                            Text(cur?.let { "$it MHz" } ?: "offline", style = RCType.body.copy(fontSize = 13.sp), modifier = Modifier.width(78.dp), maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (stats.cpuUsage != null) "CPU utilisation: ${(stats.cpuUsage * 100).toInt()}%"
                        else "Bars show each core's current clock vs. its maximum. Android 8+ blocks apps from reading true CPU utilisation, so it isn't shown.",
                        style = RCType.bodyDim.copy(fontSize = 12.sp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                LineChart(history.map { it.avgCurMHz?.toFloat() }, Modifier.fillMaxWidth().height(80.dp), minY = 0f, maxY = stats.maxMHz?.toFloat())
            }

        }, right = {
            SectionHeader("Memory")
            ChartPanel("${fmtBytes(stats.ramUsed)} / ${fmtBytes(stats.ramTotal)}", if (stats.lowMemory) "Memory pressure" else "Normal", history.map { it.ramFraction * 100 }, RC.Blue, 0f, 100f)

            SectionHeader("Temperature")
            ChartPanel(settings.temp(stats.batteryTempC) + " battery", "Thermal: ${SystemMonitor.thermalName(stats.thermalStatus)}", history.map { it.batteryTempC }, tempColor(stats.batteryTempC), null, null)

            SectionHeader("Network")
            ChartPanel("↓ ${fmtRate(stats.rxBps)}   ↑ ${fmtRate(stats.txBps)}", "All interfaces", history.map { it.rxBps?.toFloat() }, RC.Violet, 0f, null)

            SectionHeader("Graphics & frame rate")
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(gpuName ?: "GPU", style = RCType.strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (stats.gpuMHz != null) "Live clock ${stats.gpuMHz} MHz" + (stats.gpuLoad?.let { " · load $it%" } ?: "")
                            else "Live GPU clock and load are blocked for apps on this phone.",
                            style = RCType.bodyDim
                        )
                    }
                    Chip(if (stats.gpuMHz != null) "Live" else "Restricted", if (stats.gpuMHz != null) RC.Green else RC.TextMute)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Display ${stats.refreshHz ?: "—"} Hz", style = RCType.strong)
                        Text("A game's actual FPS can't be read by other apps without root or ADB. REDCORE shows the display refresh rate instead of inventing an FPS number.", style = RCType.bodyDim)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassPanel(Modifier.fillMaxWidth(), padding = 4.dp) {
                SettingRow("Cooling dashboard", "Thermal zones, history and advice", icon = Icons.Default.Thermostat, onClick = { onNavigate(Dest.COOLING) }, modifier = Modifier.padding(horizontal = 10.dp))
            }
        })
    }
}

@Composable
private fun ChartPanel(value: String, sub: String, series: List<Float?>, color: Color, minY: Float?, maxY: Float?) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = RCType.strong.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
            Text(sub, style = RCType.bodyDim.copy(fontSize = 12.sp))
        }
        Spacer(Modifier.height(8.dp))
        if (series.count { it != null } < 2) Text("Collecting samples…", style = RCType.bodyDim, modifier = Modifier.height(80.dp))
        else LineChart(series, Modifier.fillMaxWidth().height(80.dp), color = color, minY = minY, maxY = maxY)
    }
}
