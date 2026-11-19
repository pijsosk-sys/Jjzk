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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.Insights
import com.redcore.booster.data.LiveStats
import com.redcore.booster.data.SystemMonitor
import com.redcore.booster.data.temp
import com.redcore.booster.data.tempUnit
import com.redcore.booster.data.tempValue
import com.redcore.booster.ui.components.Chip
import com.redcore.booster.ui.components.Divider
import com.redcore.booster.ui.components.FanGraphic
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.InsightList
import com.redcore.booster.ui.components.LevelBar
import com.redcore.booster.ui.components.LineChart
import com.redcore.booster.ui.components.RingGauge
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

/** [fanNote] describes what REDCORE detected about a built-in fan (never a fake control). */
@Composable
fun CoolingScreen(stats: LiveStats, history: List<LiveStats>, settings: AppSettings, fanNote: Pair<String, String>) {
    val t = stats.batteryTempC
    HudBackdrop {
        com.redcore.booster.ui.components.Panes(0.9f, 1.1f, left = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RingGauge(
                    t?.let { ((it - 20f) / 30f).coerceIn(0f, 1f) },
                    settings.tempValue(t), settings.tempUnit, "Battery temperature",
                    size = 190.dp, color = tempColor(t), secondary = RC.Blue
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassPanel(Modifier.weight(1f)) {
                    Text("THERMAL STATUS", style = RCType.label.copy(fontSize = 9.sp))
                    Spacer(Modifier.height(4.dp))
                    Text(SystemMonitor.thermalName(stats.thermalStatus), style = RCType.number.copy(fontSize = 17.sp, color = when (stats.thermalStatus) { null -> RC.TextMute; 0, 1 -> RC.Green; 2 -> RC.Amber; else -> RC.Red }))
                    Text("Reported by Android", style = RCType.bodyDim.copy(fontSize = 12.sp))
                }
                GlassPanel(Modifier.weight(1f)) {
                    Text("THERMAL HEADROOM", style = RCType.label.copy(fontSize = 9.sp))
                    Spacer(Modifier.height(4.dp))
                    val h = stats.thermalHeadroom
                    Text(h?.let { "${((1f - it.coerceIn(0f, 1f)) * 100).toInt()}% left" } ?: "Unavailable", style = RCType.number.copy(fontSize = 17.sp, color = if (h == null) RC.TextMute else RC.Text))
                    Spacer(Modifier.height(6.dp))
                    LevelBar(h ?: 0f, color = if ((h ?: 0f) > 0.85f) RC.Red else RC.Blue, height = 6.dp)
                }
            }

        }, right = {
            SectionHeader("Temperature history")
            GlassPanel(Modifier.fillMaxWidth()) {
                val series = history.map { it.batteryTempC }
                val real = series.filterNotNull()
                if (real.size < 2) Text("Collecting samples while this screen is open…", style = RCType.bodyDim, modifier = Modifier.height(100.dp))
                else {
                    Row {
                        Text("Min ${settings.temp(real.min())}", style = RCType.bodyDim, modifier = Modifier.weight(1f))
                        Text("Max ${settings.temp(real.max())}", style = RCType.bodyDim)
                    }
                    Spacer(Modifier.height(6.dp))
                    LineChart(series, Modifier.fillMaxWidth().height(110.dp), color = tempColor(t), minY = real.min() - 1, maxY = real.max() + 1)
                    Text("Last ${history.size} seconds", style = RCType.bodyDim.copy(fontSize = 11.sp))
                }
            }

            SectionHeader("Thermal zones")
            GlassPanel(Modifier.fillMaxWidth()) {
                if (stats.zones.isEmpty()) {
                    Text("This phone doesn't let apps read its internal thermal sensors (blocked by Android's security policy). Battery temperature and thermal status above are the official readings.", style = RCType.bodyDim)
                } else stats.zones.take(16).forEachIndexed { i, z ->
                    if (i > 0) Divider()
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(z.name, style = RCType.body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                        LevelBar(((z.celsius - 20f) / 60f).coerceIn(0f, 1f), Modifier.width(90.dp), color = tempColor(z.celsius), height = 6.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(settings.temp(z.celsius), style = RCType.strong.copy(fontSize = 14.sp, color = tempColor(z.celsius)), modifier = Modifier.width(62.dp))
                    }
                }
            }

            SectionHeader("Cooling fan")
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FanGraphic(Modifier.size(84.dp), spinning = false, color = RC.Metal)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Chip("Not controllable", RC.TextMute)
                        Spacer(Modifier.height(6.dp))
                        Text(fanNote.first, style = RCType.strong)
                        Text(fanNote.second, style = RCType.bodyDim)
                    }
                }
            }

            SectionHeader("Recommendations")
            InsightList(Insights.cooling(stats))
        })
    }
}
