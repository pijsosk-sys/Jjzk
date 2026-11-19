package com.redcore.booster.ui.screens

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.ui.components.Divider
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.InfoRow
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.components.StatTile
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

private val fingerColors = listOf(RC.Red, RC.Blue, RC.Green, RC.Amber, RC.Violet, Color(0xFFFF5FD2), Color(0xFF3CF2FF), Color.White, RC.RedDeep, RC.BlueDeep)

/**
 * Touch tester. The report rate is measured from real MotionEvent timestamps (including batched
 * historical samples), so it reflects what the touchscreen actually delivers to apps.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TouchTestTab(features: List<Pair<String, String?>>) {
    val points = remember { mutableStateMapOf<Int, Offset>() }
    val trails = remember { mutableStateListOf<Pair<Int, Offset>>() }
    var maxPoints by remember { mutableIntStateOf(0) }
    var rate by remember { mutableFloatStateOf(0f) }
    var peakRate by remember { mutableFloatStateOf(0f) }
    val stamps = remember { ArrayDeque<Long>() }

    run {
        com.redcore.booster.ui.components.Panes(1.3f, 1f, left = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Touch points", "${points.size}", Modifier.weight(1f), sub = "Max seen: $maxPoints")
                StatTile("Report rate", if (rate > 0) "${rate.toInt()} Hz" else "—", Modifier.weight(1f), accent = RC.Blue, sub = if (peakRate > 0) "Peak ${peakRate.toInt()} Hz" else "Drag a finger")
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF0E1018), RC.Black)))
                    .border(1.dp, RC.Red.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .pointerInteropFilter { e ->
                        // Count every sample, including batched historical ones, to measure the true report rate.
                        for (h in 0 until e.historySize) stamps.addLast(e.getHistoricalEventTime(h))
                        stamps.addLast(e.eventTime)
                        while (stamps.size > 2 && stamps.last() - stamps.first() > 500) stamps.removeFirst()
                        if (e.actionMasked == MotionEvent.ACTION_MOVE && stamps.size > 4) {
                            val span = stamps.last() - stamps.first()
                            if (span > 50) { rate = (stamps.size - 1) * 1000f / span; if (rate > peakRate) peakRate = rate }
                        }
                        when (e.actionMasked) {
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { points.clear(); stamps.clear() }
                            MotionEvent.ACTION_POINTER_UP -> points.remove(e.getPointerId(e.actionIndex))
                            else -> for (i in 0 until e.pointerCount) {
                                val id = e.getPointerId(i)
                                val o = Offset(e.getX(i), e.getY(i))
                                points[id] = o
                                trails.add(id to o)
                            }
                        }
                        if (trails.size > 600) trails.removeRange(0, trails.size - 600)
                        if (points.size > maxPoints) maxPoints = points.size
                        true
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val step = 28.dp.toPx()
                    var x = 0f
                    while (x < size.width) { drawLine(RC.Stroke.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                    var y = 0f
                    while (y < size.height) { drawLine(RC.Stroke.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
                    trails.forEach { (id, o) -> drawCircle(fingerColors[id % fingerColors.size].copy(alpha = 0.35f), 3f, o) }
                    points.forEach { (id, o) ->
                        val c = fingerColors[id % fingerColors.size]
                        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.5f), Color.Transparent), o, 90f), 90f, o)
                        drawCircle(c, 44f, o, style = Stroke(4f))
                        drawLine(c.copy(alpha = 0.5f), Offset(0f, o.y), Offset(size.width, o.y), 1f)
                        drawLine(c.copy(alpha = 0.5f), Offset(o.x, 0f), Offset(o.x, size.height), 1f)
                    }
                }
                if (points.isEmpty() && trails.isEmpty()) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOUCH TEST AREA", style = RCType.title.copy(fontSize = 14.sp, color = RC.TextDim))
                        Text("Use several fingers. Drag quickly to measure report rate.", style = RCType.bodyDim, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlowButton("Clear", Modifier.fillMaxWidth(), Icons.Default.Refresh, primary = false) { trails.clear(); points.clear(); maxPoints = 0; rate = 0f; peakRate = 0f }

        }, right = {
            SectionHeader("Touchscreen")
            GlassPanel(Modifier.fillMaxWidth()) {
                features.forEachIndexed { i, (k, v) -> if (i > 0) Divider(); InfoRow(k, v) }
            }
            SectionHeader("About touch tuning")
            GlassPanel(Modifier.fillMaxWidth()) {
                Text("Touch sampling rate and sensitivity are set by the touchscreen firmware. Gaming phones change them through private manufacturer services that third-party apps cannot access, so REDCORE measures and reports touch performance instead of offering switches that would do nothing.", style = RCType.bodyDim)
                Spacer(Modifier.height(8.dp))
                Text("Tip: many phones have a \"Touch sensitivity\" or \"Glove mode\" option in Settings › Display.", style = RCType.bodyDim)
            }
        })
    }
}


interface TouchActions {
    fun edgeGuard(on: Boolean) {}
    fun edgeRange(r: Int) {}
    fun previewEdge() {}
    fun triggers(on: Boolean) {}
    fun points(l1x: Float, l1y: Float, r1x: Float, r1y: Float) {}
    fun grantOverlay() {}
    fun grantAccessibility() {}
}

@Composable
fun TouchScreen(
    features: List<Pair<String, String?>>,
    settings: com.redcore.booster.data.AppSettings,
    overlayGranted: Boolean,
    accessibilityOn: Boolean,
    act: TouchActions,
    initialTab: Int = 0,
) {
    var tab by remember { mutableIntStateOf(initialTab) }
    HudBackdrop {
        Column(Modifier.fillMaxSize()) {
            com.redcore.booster.ui.components.Segmented(
                listOf("Touch test", "Edge guard", "L1 / R1 triggers"), tab, { tab = it },
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth()
            )
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> TouchTestTab(features)
                    1 -> EdgeGuardTab(settings, overlayGranted, act)
                    else -> TriggersTab(settings, accessibilityOn, act)
                }
            }
        }
    }
}

/** Landscape phone mock used by the edge-guard and trigger tabs. */
@Composable
private fun PhoneMock(modifier: Modifier, content: androidx.compose.ui.graphics.drawscope.DrawScope.(androidx.compose.ui.geometry.Rect) -> Unit) {
    Canvas(modifier) {
        val body = androidx.compose.ui.geometry.Rect(Offset(6f, 6f), androidx.compose.ui.geometry.Size(size.width - 12f, size.height - 12f))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF22252F), Color(0xFF0B0C11))), body.topLeft, body.size, androidx.compose.ui.geometry.CornerRadius(42f))
        drawRoundRect(RC.Red, body.topLeft, body.size, androidx.compose.ui.geometry.CornerRadius(42f), style = Stroke(4f))
        val screen = androidx.compose.ui.geometry.Rect(Offset(body.left + 18f, body.top + 18f), androidx.compose.ui.geometry.Size(body.width - 36f, body.height - 36f))
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF151824), Color(0xFF0E0F16))), screen.topLeft, screen.size, androidx.compose.ui.geometry.CornerRadius(26f))
        // faint crosshair
        drawCircle(RC.Metal.copy(alpha = 0.5f), screen.height * 0.16f, screen.center, style = Stroke(2f))
        drawLine(RC.Metal.copy(alpha = 0.4f), Offset(screen.center.x - 40f, screen.center.y), Offset(screen.center.x + 40f, screen.center.y), 2f)
        drawLine(RC.Metal.copy(alpha = 0.4f), Offset(screen.center.x, screen.center.y - 40f), Offset(screen.center.x, screen.center.y + 40f), 2f)
        content(screen)
    }
}

@Composable
private fun EdgeGuardTab(s: com.redcore.booster.data.AppSettings, overlayGranted: Boolean, act: TouchActions) {
    com.redcore.booster.ui.components.Panes(1.1f, 1f, left = {
        GlassPanel(Modifier.fillMaxWidth(), accent = true) {
            Text("PROTECTED AREA", style = RCType.label.copy(fontSize = 9.sp))
            Spacer(Modifier.height(8.dp))
            PhoneMock(Modifier.fillMaxWidth().height(200.dp)) { screen ->
                val w = screen.width * (s.edgeWidthDp / 300f)
                listOf(screen.left to true, screen.right - w to false).forEach { (x, left) ->
                    val g = listOf(RC.Red.copy(alpha = 0.7f), RC.Red.copy(alpha = 0.15f))
                    drawRect(Brush.horizontalGradient(if (left) g else g.reversed(), startX = x, endX = x + w), Offset(x, screen.top), androidx.compose.ui.geometry.Size(w, screen.height))
                    drawLine(RC.Red, Offset(if (left) x + w else x, screen.top), Offset(if (left) x + w else x, screen.bottom), 2f)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Red strips ignore touches while a game session runs, so a palm gripping the phone sideways can't fire buttons by accident.", style = RCType.bodyDim)
        }
    }, right = {
        com.redcore.booster.ui.components.SectionHeader("Edge touch guard")
        GlassPanel(Modifier.fillMaxWidth()) {
            com.redcore.booster.ui.components.SettingRow(
                "Block edge touches in games", if (overlayGranted) "Turns on automatically with each game session" else "Needs \"Display over other apps\"",
                trailing = { com.redcore.booster.ui.components.RedSwitch(s.edgeGuard, { act.edgeGuard(it) }, enabled = overlayGranted) }
            )
            com.redcore.booster.ui.components.Divider()
            Spacer(Modifier.height(10.dp))
            Text("RANGE", style = RCType.label.copy(fontSize = 9.sp))
            Spacer(Modifier.height(6.dp))
            com.redcore.booster.ui.components.Segmented(listOf("Small", "Medium", "Broad"), s.edgeRange, { act.edgeRange(it) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text("${s.edgeWidthDp} dp on each side", style = RCType.bodyDim.copy(fontSize = 12.sp))
            Spacer(Modifier.height(12.dp))
            if (overlayGranted) GlowButton("Preview on screen", Modifier.fillMaxWidth(), primary = false) { act.previewEdge() }
            else GlowButton("Grant overlay permission", Modifier.fillMaxWidth()) { act.grantOverlay() }
        }
        Spacer(Modifier.height(10.dp))
        Text("REDCORE draws its own touch-blocking layer; it doesn't change the touchscreen firmware. System edge gestures (like Back) still work.", style = RCType.bodyDim.copy(fontSize = 12.sp))
    })
}

@Composable
private fun TriggersTab(s: com.redcore.booster.data.AppSettings, accessibilityOn: Boolean, act: TouchActions) {
    var l1 by remember(s.l1x, s.l1y) { mutableStateOf(Offset(s.l1x, s.l1y)) }
    var r1 by remember(s.r1x, s.r1y) { mutableStateOf(Offset(s.r1x, s.r1y)) }
    com.redcore.booster.ui.components.Panes(1.1f, 1f, left = {
        GlassPanel(Modifier.fillMaxWidth(), accent = true) {
            Text("DRAG L1 AND R1 TO YOUR GAME'S BUTTONS", style = RCType.label.copy(fontSize = 9.sp))
            Spacer(Modifier.height(8.dp))
            var dragging by remember { mutableIntStateOf(-1) }
            var screenRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
            PhoneMock(
                Modifier.fillMaxWidth().height(210.dp).pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            fun dist(o: Offset) = (Offset(screenRect.left + o.x * screenRect.width, screenRect.top + o.y * screenRect.height) - pos).getDistance()
                            dragging = if (dist(l1) < dist(r1)) 0 else 1
                        },
                        onDragEnd = { dragging = -1; act.points(l1.x, l1.y, r1.x, r1.y) },
                    ) { change, d ->
                        change.consume()
                        if (screenRect.width <= 0f) return@detectDragGestures
                        val delta = Offset(d.x / screenRect.width, d.y / screenRect.height)
                        fun clamp(o: Offset) = Offset(o.x.coerceIn(0.03f, 0.97f), o.y.coerceIn(0.05f, 0.95f))
                        if (dragging == 0) l1 = clamp(l1 + delta) else if (dragging == 1) r1 = clamp(r1 + delta)
                    }
                }
            ) { screen ->
                screenRect = screen
                listOf(Triple(l1, "L1", RC.Red), Triple(r1, "R1", RC.Blue)).forEach { (o, _, c) ->
                    val p = Offset(screen.left + o.x * screen.width, screen.top + o.y * screen.height)
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.6f), Color.Transparent), p, 60f), 60f, p)
                    drawCircle(c, 30f, p, style = Stroke(5f))
                    drawCircle(c.copy(alpha = 0.35f), 30f, p)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("L1 = Volume −", style = RCType.strong.copy(color = RC.Red, fontSize = 14.sp))
                Text("R1 = Volume +", style = RCType.strong.copy(color = RC.Blue, fontSize = 14.sp))
            }
        }
    }, right = {
        com.redcore.booster.ui.components.SectionHeader("Volume-key triggers")
        GlassPanel(Modifier.fillMaxWidth()) {
            com.redcore.booster.ui.components.SettingRow(
                "Use volume keys as L1 / R1",
                if (accessibilityOn) "Active only during REDCORE game sessions" else "Needs the REDCORE accessibility service",
                trailing = { com.redcore.booster.ui.components.RedSwitch(s.triggers, { act.triggers(it) }, enabled = accessibilityOn) }
            )
            com.redcore.booster.ui.components.Divider()
            Spacer(Modifier.height(10.dp))
            if (!accessibilityOn) GlowButton("Enable accessibility service", Modifier.fillMaxWidth()) { act.grantAccessibility() }
            else GlowButton("Reset positions", Modifier.fillMaxWidth(), primary = false) {
                l1 = Offset(0.18f, 0.55f); r1 = Offset(0.82f, 0.55f); act.points(l1.x, l1.y, r1.x, r1.y)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Pressing a volume key taps its point on screen, like a gaming phone's shoulder trigger. Volume still works normally outside game sessions. Phones with physical shoulder triggers use the maker's own software for them.", style = RCType.bodyDim.copy(fontSize = 12.sp))
    })
}
