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
fun TouchScreen(features: List<Pair<String, String?>>) {
    val points = remember { mutableStateMapOf<Int, Offset>() }
    val trails = remember { mutableStateListOf<Pair<Int, Offset>>() }
    var maxPoints by remember { mutableIntStateOf(0) }
    var rate by remember { mutableFloatStateOf(0f) }
    var peakRate by remember { mutableFloatStateOf(0f) }
    val stamps = remember { ArrayDeque<Long>() }

    HudBackdrop {
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
