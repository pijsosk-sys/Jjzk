package com.redcore.booster.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.GameMode
import com.redcore.booster.booster.GameSession
import com.redcore.booster.data.LibraryGame
import com.redcore.booster.data.LiveStats
import com.redcore.booster.data.fmtRate
import com.redcore.booster.data.temp
import com.redcore.booster.ui.Dest
import com.redcore.booster.ui.vector
import com.redcore.booster.ui.components.GameIcon
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.components.SegmentMeter
import com.redcore.booster.ui.components.StatTile
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType
import com.redcore.booster.ui.theme.Orbitron
import androidx.compose.ui.text.font.FontWeight

fun GameMode.accent(): Color = when (this) {
    GameMode.ECO -> RC.Green
    GameMode.BALANCED -> RC.Blue
    GameMode.BEAST -> RC.Red
    GameMode.LIMIT -> Color(0xFFFF7A1A)
}

fun GameMode.icon(): ImageVector = when (this) {
    GameMode.ECO -> Icons.Default.EnergySavingsLeaf
    GameMode.BALANCED -> Icons.Default.Balance
    GameMode.BEAST -> Icons.Default.LocalFireDepartment
    GameMode.LIMIT -> Icons.Default.RocketLaunch
}

@Composable
fun HomeScreen(
    stats: LiveStats,
    settings: AppSettings,
    session: GameSession?,
    recent: List<LibraryGame>,
    deviceName: String,
    deviceSub: String,
    toolkitOn: Boolean,
    onMode: (GameMode) -> Unit,
    onNavigate: (Dest) -> Unit,
    onLaunch: (String) -> Unit,
    onBoost: () -> Unit,
    onEndSession: () -> Unit,
    onToggleToolkit: () -> Unit,
    fx: com.redcore.booster.booster.EffectsState = com.redcore.booster.booster.EffectsState(),
    onCool: (Boolean) -> Unit = {},
) {
    val wide = com.redcore.booster.ui.components.LocalWide.current
    val toolbox: @Composable () -> Unit = {
        SectionHeader("Toolbox")
        val tools = listOf(Dest.COOLING, Dest.NETWORK, Dest.BATTERY, Dest.STORAGE, Dest.TOUCH, Dest.DEVICE, Dest.MONITOR, Dest.SETTINGS)
            .map { Triple(it.title, it.vector(), it) }
        tools.chunked(4).forEach { row ->
            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, icon, dest) -> ToolTile(label, icon, Modifier.weight(1f)) { onNavigate(dest) } }
            }
        }
    }
    HudBackdrop {
        com.redcore.booster.ui.components.Panes(1.35f, 1f, left = {
            HeroStage(stats, settings.globalMode, deviceName, deviceSub)
            if (wide) toolbox()
        }, right = {
            if (!wide) Spacer(Modifier.height(14.dp))
            ModeSelector(settings.globalMode, onMode)
            Spacer(Modifier.height(8.dp))
            CoolStrip(settings.coolGuard, fx, settings.coolTarget, onCool)
            Spacer(Modifier.height(10.dp))
            if (session != null) {
                SessionBar(session, onEndSession)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlowButton("Game Space", Modifier.weight(1.2f), icon = com.redcore.booster.ui.rcIcon(com.redcore.booster.R.drawable.ic_rc_gamepad)) { onNavigate(Dest.GAMES) }
                    GlowButton("Boost", Modifier.weight(1f), icon = Icons.Default.Bolt, primary = false, onClick = onBoost)
                }
            }
            Spacer(Modifier.height(10.dp))
            ToolkitStrip(toolkitOn, onToggleToolkit)

            SectionHeader("Live status")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Battery", stats.batteryPct?.let { "$it%" } ?: "—", Modifier.weight(1f), com.redcore.booster.ui.rcIcon(com.redcore.booster.R.drawable.ic_rc_battery), RC.Green,
                    sub = if (stats.charging) "Charging · ${stats.plug}" else "On battery")
                StatTile("Temp", settings.temp(stats.batteryTempC), Modifier.weight(1f), com.redcore.booster.ui.rcIcon(com.redcore.booster.R.drawable.ic_rc_thermo), tempColor(stats.batteryTempC),
                    sub = "Battery sensor")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Download", fmtRate(stats.rxBps), Modifier.weight(1f), Icons.Default.ArrowDownward, RC.Blue, sub = "↑ ${fmtRate(stats.txBps)}")
                StatTile("Display", stats.refreshHz?.let { "$it Hz" } ?: "—", Modifier.weight(1f), Icons.Default.Refresh, RC.Violet, sub = "Refresh rate")
            }
            if (!wide) toolbox()

            SectionHeader("Recently played", trailing = {
                Text("VIEW ALL", style = RCType.label.copy(color = RC.Red, fontSize = 9.sp), modifier = Modifier.clickable { onNavigate(Dest.GAMES) })
            })
            if (recent.isEmpty()) {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text("No games played yet", style = RCType.strong)
                    Text("Games you launch from Game Space appear here. Grant Usage access in Settings to include games opened from your home screen.", style = RCType.bodyDim)
                }
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    recent.take(10).forEach { g -> RecentCard(g, onLaunch) }
                }
            }
        })
    }
}

fun tempColor(c: Float?): Color = when {
    c == null -> RC.TextDim
    c < 35 -> RC.Blue
    c < 40 -> RC.Green
    c < 44 -> RC.Amber
    else -> RC.Red
}

/** The signature stage: CPU readout pad — mode core — memory readout pad, framed by chevron bands. */
@Composable
fun HeroStage(stats: LiveStats, mode: GameMode, deviceName: String, deviceSub: String) {
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 22.dp, bottomEnd = 4.dp, bottomStart = 22.dp)
    val accent by animateColorAsState(mode.accent(), tween(500), label = "acc")
    val spin = rememberInfiniteTransition(label = "stage")
    val rot by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "r")
    val flow by spin.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "f")

    val cpuGhz = stats.peakCurMHz?.let { "%.2f".format(it / 1000f) }
    val cpuLevel = stats.clockLevel
    Box(
        Modifier.fillMaxWidth().height(268.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF10121A), Color(0xFF07080B))))
            .border(1.dp, Brush.linearGradient(listOf(RC.Red.copy(alpha = 0.8f), RC.Stroke, RC.BlueDeep.copy(alpha = 0.6f))), shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            stageFloor(accent, rot)
            chevrons(RC.Red, flow)
        }
        Column(Modifier.fillMaxSize().padding(vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Readout(Modifier.weight(1f), cpuGhz ?: "—", "GHz", "CPU", cpuLevel ?: 0f, if (cpuLevel == null) "Clock N/A" else "Peak core")
                ModeCore(Modifier.weight(1.05f), mode, accent, rot)
                Readout(
                    Modifier.weight(1f),
                    if (stats.gpuMHz != null) "${stats.gpuMHz}" else "${(stats.ramFraction * 100).toInt()}",
                    if (stats.gpuMHz != null) "MHz" else "%",
                    if (stats.gpuMHz != null) "GPU" else "RAM",
                    if (stats.gpuMHz != null) (stats.gpuLoad ?: 0) / 100f else stats.ramFraction,
                    if (stats.gpuMHz != null) "Live clock" else "${com.redcore.booster.data.fmtBytes(stats.ramUsed)} used"
                )
            }
            Text(deviceName.uppercase(), style = RCType.label.copy(color = RC.Text, fontSize = 10.sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(deviceSub, style = RCType.bodyDim.copy(fontSize = 12.sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun DrawScope.stageFloor(accent: Color, rot: Float) {
    val cx = size.width / 2; val cy = size.height * 0.80f
    // floor glow
    drawOval(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), Offset(cx, cy), size.width * 0.5f),
        Offset(cx - size.width * 0.5f, cy - size.height * 0.16f), Size(size.width, size.height * 0.32f))
    for (i in 0..3) {
        val w = size.width * (0.42f + i * 0.17f); val h = w * 0.16f
        drawOval(if (i % 2 == 0) accent.copy(alpha = 0.55f - i * 0.1f) else RC.BlueDeep.copy(alpha = 0.5f - i * 0.08f),
            Offset(cx - w / 2, cy - h / 2), Size(w, h), style = Stroke(if (i == 0) 3f else 1.5f))
    }
    // rotating tick ring on the floor
    val w = size.width * 0.5f; val h = w * 0.16f
    for (k in 0 until 36) {
        val a = Math.toRadians((k * 10 + rot).toDouble())
        val x = cx + (w / 2) * kotlin.math.cos(a).toFloat()
        val y = cy + (h / 2) * kotlin.math.sin(a).toFloat()
        drawCircle(accent.copy(alpha = if (k % 3 == 0) 0.9f else 0.3f), 1.8f, Offset(x, y))
    }
    // horizon line
    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.5f), Color.Transparent)), Offset(0f, cy), Offset(size.width, cy), 1f)
}

private fun DrawScope.chevrons(color: Color, flow: Float) {
    val bandW = size.width * 0.032f
    val segs = 12
    for (side in listOf(-1, 1)) {
        val baseX = if (side < 0) size.width * 0.29f else size.width * 0.71f
        val peak = -side * size.width * 0.075f
        for (i in 0 until segs) {
            val t0 = i / segs.toFloat(); val t1 = (i + 0.72f) / segs
            fun xAt(t: Float) = baseX + peak * (1 - kotlin.math.abs(t * 2 - 1))
            val y0 = size.height * t0; val y1 = size.height * t1
            val p = Path().apply {
                moveTo(xAt(t0) - bandW / 2, y0); lineTo(xAt(t0) + bandW / 2, y0)
                lineTo(xAt(t1) + bandW / 2, y1); lineTo(xAt(t1) - bandW / 2, y1); close()
            }
            val phase = ((i / segs.toFloat()) - flow).let { it - kotlin.math.floor(it) }
            val a = 0.35f + 0.65f * (1f - phase)
            drawPath(p, Brush.horizontalGradient(listOf(color.copy(alpha = a * 0.9f), color.copy(alpha = a * 0.45f))))
        }
        // dark inner edge line
        drawLine(RC.Metal.copy(alpha = 0.7f), Offset(baseX - side * bandW, 0f), Offset(baseX + peak - side * bandW, size.height / 2), 2f)
        drawLine(RC.Metal.copy(alpha = 0.7f), Offset(baseX + peak - side * bandW, size.height / 2), Offset(baseX - side * bandW, size.height), 2f)
    }
}

@Composable
private fun Readout(modifier: Modifier, value: String, unit: String, label: String, level: Float, sub: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = androidx.compose.ui.text.TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Black, fontSize = 25.sp, color = RC.Text, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), maxLines = 1, softWrap = false)
        Text(unit, style = RCType.label.copy(fontSize = 9.sp))
        Canvas(Modifier.width(78.dp).height(26.dp)) {
            for (i in 0..2) {
                val w = size.width * (1f - i * 0.22f); val h = size.height * (0.55f - i * 0.12f)
                val y = size.height * (0.25f + i * 0.22f)
                drawOval(Brush.radialGradient(listOf(RC.Red.copy(alpha = 0.55f), Color.Transparent), Offset(size.width / 2, y + h / 2), w / 2),
                    Offset((size.width - w) / 2, y), Size(w, h))
                drawOval(RC.Red.copy(alpha = 0.9f - i * 0.25f), Offset((size.width - w) / 2, y), Size(w, h), style = Stroke(2f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(10.dp).height(1.dp).background(RC.Red))
            Text(" $label ", style = RCType.label.copy(color = RC.Red, fontSize = 10.sp))
            Box(Modifier.width(10.dp).height(1.dp).background(RC.Red))
        }
        Spacer(Modifier.height(6.dp))
        SegmentMeter(level, Modifier.width(72.dp), segments = 8)
        Spacer(Modifier.height(4.dp))
        Text(sub, style = RCType.bodyDim.copy(fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
private fun ModeCore(modifier: Modifier, mode: GameMode, accent: Color, rot: Float) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent)), r)
                rotate(rot) {
                    drawArc(accent, 0f, 80f, false, Offset(r * 0.08f, r * 0.08f), Size(r * 1.84f, r * 1.84f), style = Stroke(3f))
                    drawArc(accent, 180f, 80f, false, Offset(r * 0.08f, r * 0.08f), Size(r * 1.84f, r * 1.84f), style = Stroke(3f))
                }
                rotate(-rot * 1.5f) {
                    drawArc(RC.BlueDeep, 90f, 50f, false, Offset(r * 0.2f, r * 0.2f), Size(r * 1.6f, r * 1.6f), style = Stroke(2f))
                    drawArc(RC.BlueDeep, 270f, 50f, false, Offset(r * 0.2f, r * 0.2f), Size(r * 1.6f, r * 1.6f), style = Stroke(2f))
                }
                val hex = Path()
                for (k in 0..5) {
                    val a = Math.toRadians((60 * k - 90).toDouble())
                    val x = center.x + r * 0.62f * kotlin.math.cos(a).toFloat(); val y = center.y + r * 0.62f * kotlin.math.sin(a).toFloat()
                    if (k == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
                }
                hex.close()
                drawPath(hex, Brush.radialGradient(listOf(Color(0xFF2A0A12), RC.Black)))
                drawPath(hex, accent, style = Stroke(3f))
            }
            Icon(mode.icon(), null, tint = accent, modifier = Modifier.size(34.dp))
        }
        Text(mode.title.uppercase(), style = RCType.title.copy(fontSize = 15.sp, letterSpacing = 3.sp))
        Text("MODE", style = RCType.label.copy(fontSize = 8.sp, color = accent, letterSpacing = 4.sp))
    }
}

@Composable
fun ModeSelector(selected: GameMode, onMode: (GameMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GameMode.entries.forEach { m ->
            val sel = m == selected
            val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomEnd = 2.dp, bottomStart = 14.dp)
            Column(
                Modifier.weight(1f).clip(shape)
                    .background(if (sel) Brush.verticalGradient(listOf(m.accent().copy(alpha = 0.35f), RC.Panel)) else RC.panelGradient)
                    .border(if (sel) 1.5.dp else 1.dp, if (sel) m.accent() else RC.Stroke, shape)
                    .clickable { onMode(m) }.padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(m.icon(), null, tint = if (sel) m.accent() else RC.TextMute, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(4.dp))
                Text(m.title.uppercase(), style = RCType.label.copy(fontSize = 9.sp, letterSpacing = 0.6.sp, color = if (sel) Color.White else RC.TextDim), maxLines = 1, softWrap = false)
                Text(
                    when (m) { GameMode.ECO -> "Save power"; GameMode.BALANCED -> "Everyday"; GameMode.BEAST -> "High output"; GameMode.LIMIT -> "All in" },
                    style = RCType.bodyDim.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SessionBar(s: GameSession, onEnd: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomEnd = 2.dp, bottomStart = 14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(RC.RedDark, RC.Panel)))
            .border(1.dp, RC.Red, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (s.pkg != null) GameIcon(s.pkg, s.label, 40.dp) else Icon(Icons.Default.Bolt, null, tint = RC.Red, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("SESSION ACTIVE", style = RCType.label.copy(color = RC.Red, fontSize = 9.sp))
            Text(s.label, style = RCType.strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${s.mode.title} mode · ${s.results.count { it.state == com.redcore.booster.booster.ActionState.APPLIED }} optimizations applied", style = RCType.bodyDim)
        }
        GlowButton("End", primary = false, onClick = onEnd)
    }
}

@Composable
private fun ToolkitStrip(on: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(RC.Panel).border(1.dp, if (on) RC.Red.copy(alpha = 0.7f) else RC.Stroke, shape)
            .clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(com.redcore.booster.ui.rcIcon(com.redcore.booster.R.drawable.ic_rc_layers), null, tint = if (on) RC.Red else RC.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Floating gaming toolkit", style = RCType.strong.copy(fontSize = 15.sp))
            Text(if (on) "Running — tap the red bubble over any game" else "HUD, quick controls, screenshot & recording", style = RCType.bodyDim.copy(fontSize = 12.sp))
        }
        com.redcore.booster.ui.components.RedSwitch(on, { onToggle() })
    }
}

@Composable
private fun ToolTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp)
    Column(
        modifier.clip(shape).background(RC.panelGradient).border(1.dp, RC.Stroke, shape).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(RC.RedDark, Color(0xFF16090D)))).border(1.dp, RC.RedDeep, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = RC.Red, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label.uppercase(), style = RCType.label.copy(fontSize = 8.sp, color = RC.Text), maxLines = 1)
    }
}

@Composable
private fun RecentCard(g: LibraryGame, onLaunch: (String) -> Unit) {
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomEnd = 2.dp, bottomStart = 14.dp)
    Column(
        Modifier.width(112.dp).clip(shape).background(RC.panelGradient).border(1.dp, RC.Stroke, shape).clickable { onLaunch(g.profile.pkg) }.padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GameIcon(g.profile.pkg, g.label, 58.dp)
        Spacer(Modifier.height(8.dp))
        Text(g.label, style = RCType.strong.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, null, tint = RC.Red, modifier = Modifier.size(14.dp))
            Text("PLAY", style = RCType.label.copy(fontSize = 8.sp, color = RC.Red))
        }
    }
}


val CoolBlue = Color(0xFF3CC8FF)

/** COOL guard toggle: works together with any mode, including Limit. */
@Composable
fun CoolStrip(on: Boolean, fx: com.redcore.booster.booster.EffectsState, target: Int, onToggle: (Boolean) -> Unit) {
    val level = fx.coolLevel
    val color = if (!on) RC.TextMute else if (level == com.redcore.booster.booster.CoolLevel.HOT) Color(0xFFFF7A1A) else CoolBlue
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (on) Brush.horizontalGradient(listOf(CoolBlue.copy(alpha = 0.16f), RC.Panel)) else RC.panelGradient)
            .border(1.dp, if (on) color.copy(alpha = 0.8f) else RC.Stroke, shape)
            .clickable { onToggle(!on) }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AcUnit, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("COOL GUARD", style = RCType.label.copy(fontSize = 10.sp, color = if (on) Color.White else RC.TextDim))
            Text(
                when {
                    !on -> "Off · turn on to stop the phone overheating in any mode"
                    level == com.redcore.booster.booster.CoolLevel.WARM -> "Warm · ${fx.coolNote ?: "easing off"}"
                    level == com.redcore.booster.booster.CoolLevel.HOT -> "Hot · ${fx.coolNote ?: "cooling down"}"
                    level == com.redcore.booster.booster.CoolLevel.NORMAL -> "Watching · keeps it under $target°C"
                    else -> "On · keeps it under $target°C during games"
                },
                style = RCType.bodyDim.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        com.redcore.booster.ui.components.RedSwitch(on, onToggle, onBrush = Brush.horizontalGradient(listOf(Color(0xFF1B6FD6), CoolBlue)))
    }
}
