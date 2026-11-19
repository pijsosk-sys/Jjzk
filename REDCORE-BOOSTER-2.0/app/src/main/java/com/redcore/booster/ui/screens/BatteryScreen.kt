package com.redcore.booster.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.InfoSection
import com.redcore.booster.data.Insight
import com.redcore.booster.data.LiveStats
import com.redcore.booster.data.temp
import com.redcore.booster.ui.components.Divider
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.InfoRow
import com.redcore.booster.ui.components.InsightList
import com.redcore.booster.ui.components.LineChart
import com.redcore.booster.ui.components.RingGauge
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.components.StatTile
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType
import kotlin.math.abs

@Composable
fun BatteryScreen(
    stats: LiveStats,
    history: List<LiveStats>,
    settings: AppSettings,
    info: InfoSection?,
    insights: List<Insight>,
    batterySaver: Boolean,
    onUsage: () -> Unit,
    onSaver: () -> Unit,
) {
    HudBackdrop {
        com.redcore.booster.ui.components.Panes(1f, 1f, left = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RingGauge(
                    stats.batteryPct?.div(100f), stats.batteryPct?.toString() ?: "—", "%",
                    if (stats.charging) "Charging · ${stats.plug}" else "On battery",
                    size = 180.dp, color = when { (stats.batteryPct ?: 100) <= 15 -> RC.Red; stats.charging -> RC.Green; else -> RC.Red }, secondary = RC.Green
                )
            }
            Spacer(Modifier.height(14.dp))
            val watts = if (stats.voltageMv != null && stats.currentMa != null) abs(stats.voltageMv * stats.currentMa) / 1_000_000f else null
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Temp", settings.temp(stats.batteryTempC), Modifier.weight(1f), Icons.Default.Thermostat, tempColor(stats.batteryTempC))
                StatTile("Voltage", stats.voltageMv?.let { "%.2f V".format(it / 1000f) } ?: "—", Modifier.weight(1f), Icons.Default.ElectricBolt, RC.Amber)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Current", stats.currentMa?.let { "${abs(it)} mA" } ?: "—", Modifier.weight(1f), Icons.Default.Bolt, RC.Blue,
                    sub = stats.currentMa?.let { if (stats.charging) "Flowing in" else "Drawing" } ?: "Not reported")
                StatTile("Power", watts?.let { "%.1f W".format(it) } ?: "—", Modifier.weight(1f), Icons.Default.Bolt, RC.Violet, sub = "Voltage × current")
            }

        }, right = {
            SectionHeader("Live level & temperature")
            GlassPanel(Modifier.fillMaxWidth()) {
                val temps = history.map { it.batteryTempC }
                if (temps.count { it != null } < 2) Text("Collecting samples while this screen is open…", style = RCType.bodyDim, modifier = Modifier.height(90.dp))
                else {
                    Text("TEMPERATURE", style = RCType.label.copy(fontSize = 8.sp))
                    LineChart(temps, Modifier.fillMaxWidth().height(80.dp), color = tempColor(stats.batteryTempC))
                    Spacer(Modifier.height(8.dp))
                    Text("CURRENT DRAW (mA)", style = RCType.label.copy(fontSize = 8.sp))
                    LineChart(history.map { it.currentMa?.let { c -> abs(c).toFloat() } }, Modifier.fillMaxWidth().height(70.dp), color = RC.Blue, minY = 0f)
                }
            }

            SectionHeader("Battery details")
            GlassPanel(Modifier.fillMaxWidth()) {
                info?.rows?.forEachIndexed { i, (k, v) -> if (i > 0) Divider(); InfoRow(k, v) } ?: Text("Loading…", style = RCType.bodyDim)
                Divider()
                InfoRow("Battery Saver", if (batterySaver) "On" else "Off", highlight = batterySaver)
            }

            SectionHeader("Recommendations")
            InsightList(insights)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton("Battery usage", Modifier.weight(1f), Icons.Default.BarChart, primary = false, onClick = onUsage)
                GlowButton("Battery saver", Modifier.weight(1f), Icons.Default.BatterySaver, primary = false, onClick = onSaver)
            }
        })
    }
}
