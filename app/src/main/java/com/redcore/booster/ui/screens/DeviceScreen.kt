package com.redcore.booster.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.data.DeviceReport
import com.redcore.booster.ui.components.Divider
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.InfoRow
import com.redcore.booster.ui.components.LevelBar
import com.redcore.booster.ui.components.LoadingSpinner
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DeviceScreen(report: DeviceReport?) {
    HudBackdrop {
        if (report == null) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                LoadingSpinner(); Spacer(Modifier.height(12.dp))
                Text("SCANNING HARDWARE…", style = RCType.label)
            }
            return@HudBackdrop
        }
        val tabs = report.sections.map { it.title } + "Sensors"
        var tab by remember { mutableIntStateOf(0) }
        com.redcore.booster.ui.components.Panes(0.8f, 1.2f, left = {
            DeviceHero(report)
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEachIndexed { i, t ->
                    val sel = i == tab
                    Box(
                        Modifier.clip(RoundedCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomEnd = 2.dp, bottomStart = 10.dp))
                            .background(if (sel) RC.redGradient else Brush.linearGradient(listOf(RC.Panel, RC.Panel)))
                            .border(1.dp, if (sel) RC.Red else RC.Stroke, RoundedCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomEnd = 2.dp, bottomStart = 10.dp))
                            .clickable { tab = i }.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text(t.uppercase(), style = RCType.label.copy(fontSize = 9.sp, color = if (sel) Color.White else RC.TextDim)) }
                }
            }
        }, right = {
            Spacer(Modifier.height(12.dp))
            if (tab < report.sections.size) {
                val sec = report.sections[tab]
                GlassPanel(Modifier.fillMaxWidth()) {
                    sec.rows.forEachIndexed { i, (k, v) ->
                        if (i > 0) Divider()
                        InfoRow(k, v)
                    }
                }
                if (sec.title == "Processor" && report.cores.isNotEmpty()) {
                    SectionHeader("Cores")
                    GlassPanel(Modifier.fillMaxWidth()) {
                        val top = report.cores.mapNotNull { it.maxMHz }.maxOrNull() ?: 1
                        report.cores.forEach { c ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("CORE ${c.index}", style = RCType.label.copy(fontSize = 9.sp, color = RC.Text), modifier = Modifier.width(56.dp))
                                LevelBar((c.maxMHz ?: 0).toFloat() / top, Modifier.weight(1f), color = if (c.maxMHz == top) RC.Red else RC.Blue)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (c.maxMHz != null) "${c.minMHz ?: "?"}–${c.maxMHz} MHz" else "Unavailable",
                                    style = RCType.body.copy(fontSize = 12.sp, color = if (c.maxMHz == null) RC.TextMute else RC.Text),
                                    modifier = Modifier.width(104.dp), textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Values Android doesn't expose are marked Unavailable — REDCORE never guesses hardware details.", style = RCType.bodyDim.copy(fontSize = 12.sp))
            } else {
                SectionHeader("Key sensors")
                val rows = report.keySensors.chunked(2)
                rows.forEach { pair ->
                    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (name, has) ->
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(RC.Panel).border(1.dp, if (has) RC.Green.copy(alpha = 0.4f) else RC.Stroke, RoundedCornerShape(10.dp)).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(if (has) Icons.Default.Check else Icons.Default.Close, null, tint = if (has) RC.Green else RC.TextMute, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(name, style = RCType.body.copy(fontSize = 14.sp, color = if (has) RC.Text else RC.TextMute))
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                SectionHeader("All sensors · ${report.sensors.size}")
                GlassPanel(Modifier.fillMaxWidth()) {
                    if (report.sensors.isEmpty()) Text("No sensors reported.", style = RCType.bodyDim)
                    report.sensors.forEachIndexed { i, s ->
                        if (i > 0) Divider()
                        Column(Modifier.padding(vertical = 7.dp)) {
                            Row {
                                Text(s.type, style = RCType.strong.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                                Text("%.2f mA".format(s.powerMa), style = RCType.bodyDim.copy(fontSize = 12.sp))
                            }
                            Text("${s.name} · ${s.vendor}" + if (s.wakeUp) " · wake-up" else "", style = RCType.bodyDim.copy(fontSize = 12.sp))
                        }
                    }
                }
            }
        })
    }
}

@Composable
private fun DeviceHero(r: DeviceReport) {
    GlassPanel(Modifier.fillMaxWidth(), accent = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 62.dp, height = 110.dp)) {
                val w = size.width; val h = size.height
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF22252F), Color(0xFF0B0C11))), cornerRadius = CornerRadius(18f))
                drawRoundRect(RC.Red, cornerRadius = CornerRadius(18f), style = Stroke(3f))
                drawRoundRect(Brush.verticalGradient(listOf(RC.RedDark, RC.BlueDeep.copy(alpha = 0.6f))), Offset(6f, 14f), Size(w - 12f, h - 28f), CornerRadius(8f))
                drawLine(RC.Red, Offset(w * 0.2f, h * 0.55f), Offset(w * 0.8f, h * 0.4f), 3f)
                drawLine(RC.Red.copy(alpha = 0.6f), Offset(w * 0.2f, h * 0.65f), Offset(w * 0.8f, h * 0.5f), 2f)
                drawCircle(RC.Metal, 4f, Offset(w / 2, 8f))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("DEVICE RESEARCH", style = RCType.label.copy(color = RC.Red, fontSize = 9.sp))
                Text(r.headline, style = RCType.title.copy(fontSize = 18.sp))
                Spacer(Modifier.height(4.dp))
                Text(r.subline, style = RCType.bodyDim)
            }
        }
    }
}
