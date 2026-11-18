package com.redcore.booster.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.app
import com.redcore.booster.booster.ActionState
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.GameMode
import com.redcore.booster.booster.GameSession
import com.redcore.booster.data.LiveStats
import com.redcore.booster.data.ModeSpec
import com.redcore.booster.data.fmtBytes
import com.redcore.booster.data.fmtRate
import com.redcore.booster.data.temp
import com.redcore.booster.record.ScreenRecordService
import com.redcore.booster.ui.RedcoreLogo
import com.redcore.booster.ui.components.RedSlider
import com.redcore.booster.ui.components.SegmentMeter
import com.redcore.booster.ui.screens.accent
import com.redcore.booster.ui.screens.icon
import com.redcore.booster.ui.screens.tempColor
import com.redcore.booster.ui.theme.Orbitron
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OverlayState(
    val stats: LiveStats,
    val settings: AppSettings,
    val session: GameSession?,
    val expanded: Boolean,
    val recording: Boolean,
    val dndOn: Boolean,
    val dndAllowed: Boolean,
    val brightness: Int?,
    val volume: Int,
    val mic: Boolean,
)

interface OverlayActions {
    fun drag(dx: Float, dy: Float) {}
    fun dragEnd() {}
    fun expand(e: Boolean) {}
    fun mode(m: GameMode) {}
    fun dnd(on: Boolean) {}
    fun brightness(p: Int) {}
    fun volume(p: Int) {}
    fun screenshot() {}
    fun record() {}
    fun mic(on: Boolean) {}
    fun clean() {}
    fun hud(on: Boolean) {}
    fun openApp() {}
    fun endSession() {}
}

/** Wires the real service and repositories to the stateless [OverlayUi]. */
@Composable
fun OverlayRoot(svc: OverlayService) {
    val a = svc.app
    val stats by a.monitor.stats.collectAsState()
    val settings by a.settings.settings.collectAsState(AppSettings())
    val session by a.boost.session.collectAsState()
    val expanded by svc.expanded.collectAsState()
    val recording by ScreenRecordService.recording.collectAsState()
    var dnd by remember { mutableStateOf(a.boost.isDndOn()) }
    var bri by remember { mutableStateOf(a.boost.currentBrightnessPct()) }
    var vol by remember { mutableStateOf(a.boost.volumePct()) }
    var mic by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(expanded) {
        if (expanded) { dnd = a.boost.isDndOn(); bri = a.boost.currentBrightnessPct(); vol = a.boost.volumePct() }
    }
    val actions = remember {
        object : OverlayActions {
            override fun drag(dx: Float, dy: Float) = svc.dragBy(dx, dy)
            override fun dragEnd() = svc.dragEnd()
            override fun expand(e: Boolean) = svc.setExpanded(e)
            override fun mode(m: GameMode) {
                scope.launch {
                    a.settings.setMode(m)
                    val cur = a.boost.session.value
                    val r = a.boost.startSession(cur?.pkg, m, ModeSpec.of(m))
                    svc.toast("${m.title} mode · ${r.results.count { it.state == ActionState.APPLIED }} actions applied")
                }
            }
            override fun dnd(on: Boolean) {
                val r = a.boost.setDnd(on)
                if (r.state != ActionState.APPLIED) svc.toast(r.detail)
                dnd = a.boost.isDndOn()
            }
            override fun brightness(p: Int) { a.boost.setBrightness(p); bri = p }
            override fun volume(p: Int) { a.boost.setVolumePct(p); vol = p }
            override fun screenshot() = svc.screenshot()
            override fun record() = svc.toggleRecording(mic)
            override fun mic(on: Boolean) {
                if (on && !com.redcore.booster.booster.Permissions.has(svc, android.Manifest.permission.RECORD_AUDIO)) {
                    svc.toast("Allow Microphone in REDCORE › Settings › Permissions to record voice chat.")
                    return
                }
                mic = on
            }
            override fun clean() { scope.launch { svc.toast(a.boost.cleanBackground().detail) } }
            override fun hud(on: Boolean) { scope.launch { a.settings.setHud(on) } }
            override fun openApp() = svc.openApp()
            override fun endSession() = svc.endSession()
        }
    }
    OverlayUi(
        OverlayState(stats, settings, session, expanded, recording, dnd, a.boost.canSetDnd(), if (a.boost.canSetBrightness()) bri else null, vol, mic),
        actions
    )
}

@Composable
fun OverlayUi(s: OverlayState, act: OverlayActions) {
    if (s.expanded) ExpandedPanel(s, act) else Collapsed(s, act)
}

@Composable
private fun Collapsed(s: OverlayState, act: OverlayActions) {
    Row(
        Modifier.scale(s.settings.overlayScale).pointerInput(Unit) {
            detectDragGestures(onDragEnd = { act.dragEnd() }) { change, drag -> change.consume(); act.drag(drag.x, drag.y) }
        }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Bubble(s.recording) { act.expand(true) }
        if (s.settings.hudEnabled) {
            Spacer(Modifier.width(6.dp))
            HudPill(s)
        }
    }
}

@Composable
fun Bubble(recording: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "bub")
    val pulse by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Box(
        Modifier.size(48.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(RC.Red.copy(alpha = 0.45f * pulse), Color.Transparent)), r)
            val hex = Path()
            for (k in 0..5) {
                val a = Math.toRadians((60 * k - 90).toDouble())
                val x = center.x + r * 0.86f * kotlin.math.cos(a).toFloat(); val y = center.y + r * 0.86f * kotlin.math.sin(a).toFloat()
                if (k == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
            }
            hex.close()
            drawPath(hex, Brush.linearGradient(listOf(Color(0xEE1A0509), Color(0xEE07080B))))
            drawPath(hex, if (recording) RC.Red.copy(alpha = pulse) else RC.Red, style = Stroke(if (recording) 5f else 3f))
        }
        if (recording) Text("REC", style = RCType.label.copy(fontSize = 9.sp, color = RC.Red))
        else RedcoreLogo(24.dp)
    }
}

@Composable
private fun HudPill(s: OverlayState) {
    val st = s.stats
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC07080B)).border(1.dp, RC.Red.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        HudItem("CPU", st.peakCurMHz?.let { "%.2fG".format(it / 1000f) } ?: "—", RC.Red)
        HudItem("RAM", "${(st.ramFraction * 100).toInt()}%", RC.Blue)
        HudItem("TMP", s.settings.temp(st.batteryTempC), tempColor(st.batteryTempC))
        HudItem("BAT", st.batteryPct?.let { "$it%" } ?: "—", RC.Green)
        HudItem("NET", fmtRate(st.rxBps).replace(" ", ""), RC.Violet)
    }
}

@Composable
private fun HudItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = RCType.label.copy(fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, style = RCType.label.copy(fontSize = 6.5.sp, color = color))
    }
}

@Composable
private fun ExpandedPanel(s: OverlayState, act: OverlayActions) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, null) { act.expand(false) },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(true, enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut() + scaleOut()) {
            BoxWithConstraints(Modifier.padding(12.dp)) {
                val wide = maxWidth >= 560.dp
                val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 22.dp, bottomEnd = 4.dp, bottomStart = 22.dp)
                Column(
                    Modifier.widthIn(max = 720.dp).clip(shape)
                        .background(Brush.verticalGradient(listOf(Color(0xF2141620), Color(0xF207080B))))
                        .border(1.dp, Brush.linearGradient(listOf(RC.Red, RC.Stroke, RC.BlueDeep)), shape)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .then(if (!wide) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(14.dp)
                ) {
                    PanelHeader(s, act)
                    Spacer(Modifier.height(10.dp))
                    if (wide) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CpuBlock(s, act, Modifier.width(150.dp))
                            Spacer(Modifier.width(12.dp))
                            QuickGrid(s, act, Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            SideStats(s, Modifier.width(150.dp))
                        }
                    } else {
                        Row {
                            CpuBlock(s, act, Modifier.weight(1f))
                            Spacer(Modifier.width(10.dp))
                            SideStats(s, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        QuickGrid(s, act, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(10.dp))
                    Sliders(s, act, wide)
                    Spacer(Modifier.height(8.dp))
                    Footer(s)
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(s: OverlayState, act: OverlayActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RedcoreLogo(22.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("REDCORE TOOLKIT", style = RCType.title.copy(fontSize = 12.sp, letterSpacing = 2.sp))
            Text(s.session?.label ?: "No active session", style = RCType.bodyDim.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GameMode.entries.forEach { m ->
            val sel = (s.session?.mode ?: s.settings.globalMode) == m
            Row(
                Modifier.padding(start = 5.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (sel) m.accent().copy(alpha = 0.25f) else Color.Transparent)
                    .border(1.dp, if (sel) m.accent() else RC.Stroke, RoundedCornerShape(6.dp))
                    .clickable { act.mode(m) }.padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(m.icon(), null, tint = if (sel) m.accent() else RC.TextMute, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text(m.title.uppercase(), style = RCType.label.copy(fontSize = 8.sp, color = if (sel) Color.White else RC.TextDim))
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).clickable { act.expand(false) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, "Close", tint = RC.TextDim, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BigReadout(value: String, unit: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = androidx.compose.ui.text.TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 26.sp, color = Color.White), maxLines = 1)
        Text(unit, style = RCType.label.copy(fontSize = 8.sp))
        Canvas(Modifier.width(96.dp).height(22.dp)) {
            for (i in 0..2) {
                val w = size.width * (1f - i * 0.22f); val h = size.height * (0.6f - i * 0.14f)
                val y = size.height * (0.2f + i * 0.2f)
                drawOval(Brush.radialGradient(listOf(color.copy(alpha = 0.6f), Color.Transparent), Offset(size.width / 2, y + h / 2), w / 2), Offset((size.width - w) / 2, y), Size(w, h))
                drawOval(color.copy(alpha = 0.9f - i * 0.25f), Offset((size.width - w) / 2, y), Size(w, h), style = Stroke(2f))
            }
        }
        Text("— $label —", style = RCType.label.copy(fontSize = 9.sp, color = color))
    }
}

@Composable
private fun CpuBlock(s: OverlayState, act: OverlayActions, modifier: Modifier) {
    val st = s.stats
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BigReadout(st.peakCurMHz?.let { "%.2f".format(it / 1000f) } ?: "—", "GHz", "CPU", RC.Red)
        Spacer(Modifier.height(6.dp))
        SegmentMeter(st.clockLevel ?: 0f, Modifier.width(110.dp), segments = 10)
        Spacer(Modifier.height(3.dp))
        Text(if (st.clockLevel != null) "Clock level ${((st.clockLevel ?: 0f) * 100).toInt()}%" else "Clock data restricted", style = RCType.bodyDim.copy(fontSize = 11.sp))
    }
}

@Composable
private fun SideStats(s: OverlayState, modifier: Modifier) {
    val st = s.stats
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (st.gpuMHz != null) BigReadout("${st.gpuMHz}", "MHz", "GPU", RC.Blue)
        else BigReadout("${(st.ramFraction * 100).toInt()}", "%", "RAM", RC.Blue)
        Spacer(Modifier.height(6.dp))
        SegmentMeter(st.ramFraction, Modifier.width(110.dp), segments = 10, color = RC.Blue)
        Spacer(Modifier.height(3.dp))
        Text("${fmtBytes(st.ramUsed)} / ${fmtBytes(st.ramTotal)}", style = RCType.bodyDim.copy(fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
private fun QuickGrid(s: OverlayState, act: OverlayActions, modifier: Modifier) {
    val tiles = listOf(
        QT("No alerts", Icons.Default.DoNotDisturbOn, s.dndOn, enabled = s.dndAllowed) { act.dnd(!s.dndOn) },
        QT("Screenshot", Icons.Default.Screenshot, false) { act.screenshot() },
        QT(if (s.recording) "Stop rec" else "Record", Icons.Default.FiberManualRecord, s.recording) { act.record() },
        QT(if (s.mic) "Mic on" else "Mic off", if (s.mic) Icons.Default.Mic else Icons.Default.MicOff, s.mic) { act.mic(!s.mic) },
        QT("Close apps", Icons.Default.AutoDelete, false) { act.clean() },
        QT(if (s.settings.hudEnabled) "HUD on" else "HUD off", Icons.Default.Speed, s.settings.hudEnabled) { act.hud(!s.settings.hudEnabled) },
        QT("REDCORE", Icons.Default.OpenInNew, false) { act.openApp() },
        QT("End session", Icons.Default.PowerSettingsNew, false, danger = true) { act.endSession() },
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { QuickTile(it, Modifier.weight(1f)) }
            }
        }
    }
}

private data class QT(val label: String, val icon: ImageVector, val active: Boolean, val enabled: Boolean = true, val danger: Boolean = false, val onClick: () -> Unit)

@Composable
private fun QuickTile(t: QT, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.clip(shape)
            .background(if (t.active) Brush.verticalGradient(listOf(Color(0xFFFF8A1E), RC.Red)) else Brush.verticalGradient(listOf(RC.PanelHi, RC.Panel)))
            .border(1.dp, if (t.active) RC.Red else if (t.danger) RC.RedDeep else RC.Stroke, shape)
            .clickable(onClick = t.onClick).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(t.icon, null, tint = when { t.active -> Color.White; !t.enabled -> RC.TextMute; t.danger -> RC.Red; else -> RC.Text }, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(t.label, style = RCType.bodyDim.copy(fontSize = 11.sp, color = if (t.active) Color.White else RC.TextDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Sliders(s: OverlayState, act: OverlayActions, wide: Boolean) {
    var b by remember(s.brightness) { mutableFloatStateOf((s.brightness ?: 50) / 100f) }
    var v by remember(s.volume) { mutableFloatStateOf(s.volume / 100f) }
    val brightness: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BrightnessMedium, null, tint = if (s.brightness != null) RC.Amber else RC.TextMute, modifier = Modifier.size(18.dp))
            if (s.brightness != null) RedSlider(b, { b = it; act.brightness((it * 100).toInt().coerceAtLeast(1)) }, Modifier.weight(1f).height(28.dp))
            else Text("  Grant \"Modify system settings\" for brightness", style = RCType.bodyDim.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
        }
    }
    val volume: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = RC.Blue, modifier = Modifier.size(18.dp))
            RedSlider(v, { v = it; act.volume((it * 100).toInt()) }, Modifier.weight(1f).height(28.dp))
        }
    }
    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { brightness(Modifier.weight(1f)); volume(Modifier.weight(1f)) }
    else Column { brightness(Modifier.fillMaxWidth()); volume(Modifier.fillMaxWidth()) }
}

@Composable
private fun Footer(s: OverlayState) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = Date() } }
    val st = s.stats
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF0B0C11)).border(1.dp, RC.Stroke, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Text("REDCORE", style = RCType.label.copy(fontSize = 9.sp, color = RC.Text, letterSpacing = 3.sp))
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), style = RCType.label.copy(fontSize = 9.sp))
        Text("BAT ${st.batteryPct ?: "—"}%", style = RCType.label.copy(fontSize = 9.sp, color = RC.Green))
        Text(s.settings.temp(st.batteryTempC), style = RCType.label.copy(fontSize = 9.sp, color = tempColor(st.batteryTempC)))
        Text("↓ ${fmtRate(st.rxBps)}", style = RCType.label.copy(fontSize = 9.sp, color = RC.Violet))
        Text("${st.refreshHz ?: "—"} Hz", style = RCType.label.copy(fontSize = 9.sp))
    }
}
