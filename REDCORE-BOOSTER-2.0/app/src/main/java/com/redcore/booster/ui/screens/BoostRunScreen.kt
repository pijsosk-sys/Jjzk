package com.redcore.booster.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.booster.ActionState
import com.redcore.booster.ui.BoostRun
import com.redcore.booster.ui.components.Chip
import com.redcore.booster.ui.components.GameIcon
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.theme.Orbitron
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType
import kotlinx.coroutines.delay

/**
 * Full-screen boost sequence. The log only shows what really happened (from the session results);
 * the timing just paces the reveal. Ends by opening the game library, or launching the game.
 */
@Composable
fun BoostRunScreen(run: BoostRun, onDone: () -> Unit, onBatterySaver: () -> Unit) {
    val results = run.results
    val accent = run.mode.accent()
    var shown by remember(results) { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(results) {
        if (results == null) {
            progress.animateTo(0.35f, tween(900, easing = FastOutSlowInEasing))
        } else {
            val step = if (run.launch) 70L else 110L
            for (i in 1..results.size) { delay(step); shown = i; progress.animateTo(0.35f + 0.65f * i / results.size, tween(step.toInt())) }
            delay(if (run.batterySaver) 2400 else if (run.launch) 500 else 900)
            onDone()
        }
    }
    val t = rememberInfiniteTransition(label = "run")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val ready = results != null && shown >= results.size
    val applied = results?.count { it.state == ActionState.APPLIED } ?: 0

    HudBackdrop {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .clickable(remember { MutableInteractionSource() }, null) { if (results != null) onDone() }
        ) {
            val wide = maxWidth > maxHeight
            val ring: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(if (wide) 210.dp else 190.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val r = size.minDimension / 2
                            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f * pulse), Color.Transparent)), r)
                            rotate(spin) {
                                drawArc(accent.copy(alpha = 0.6f), 0f, 70f, false, Offset(6f, 6f), Size(size.width - 12, size.height - 12), style = Stroke(3f))
                                drawArc(RC.BlueDeep, 180f, 70f, false, Offset(6f, 6f), Size(size.width - 12, size.height - 12), style = Stroke(3f))
                            }
                            for (i in 0 until 72) {
                                val a = Math.toRadians(i * 5.0 - 90)
                                val on = i / 72f <= progress.value
                                drawLine(if (on) accent else RC.Stroke,
                                    Offset(center.x + kotlin.math.cos(a).toFloat() * r * 0.74f, center.y + kotlin.math.sin(a).toFloat() * r * 0.74f),
                                    Offset(center.x + kotlin.math.cos(a).toFloat() * r * 0.84f, center.y + kotlin.math.sin(a).toFloat() * r * 0.84f),
                                    strokeWidth = if (i % 6 == 0) 5f else 3f, cap = StrokeCap.Round)
                            }
                            drawCircle(Brush.radialGradient(listOf(RC.PanelHi, RC.Black)), r * 0.66f)
                            drawCircle(accent, r * 0.66f, style = Stroke(3f))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (run.pkg != null) GameIcon(run.pkg, run.label, 56.dp) else Icon(run.mode.icon(), null, tint = accent, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("${(progress.value * 100).toInt()}%", style = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = Color.White))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(if (ready) "READY" else "OPTIMIZING", style = RCType.title.copy(fontSize = 20.sp, letterSpacing = 4.sp, color = if (ready) accent else Color.White))
                    Text(run.label, style = RCType.body.copy(color = RC.TextDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Chip("${run.mode.title} mode", accent)
                }
            }
            val log: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth()) {
                    Text("BOOST LOG", style = RCType.label.copy(fontSize = 9.sp, color = RC.Red))
                    Spacer(Modifier.height(6.dp))
                    if (results == null) Text("Applying ${run.mode.title} mode…", style = RCType.bodyDim)
                    results?.forEachIndexed { i, r ->
                        AnimatedVisibility(i < shown, enter = fadeIn(tween(160)) + slideInHorizontally(tween(200)) { it / 6 }) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                val (icon, c) = when (r.state) {
                                    ActionState.APPLIED -> Icons.Default.CheckCircle to RC.Green
                                    ActionState.SKIPPED -> Icons.Default.RemoveCircleOutline to RC.TextMute
                                    ActionState.BLOCKED -> Icons.Default.WarningAmber to RC.Amber
                                }
                                Icon(icon, null, tint = c, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(r.name, style = RCType.strong.copy(fontSize = 13.sp, color = if (r.state == ActionState.SKIPPED) RC.TextMute else RC.Text), modifier = Modifier.width(150.dp), maxLines = 1)
                                Text(r.detail, style = RCType.bodyDim.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (ready) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("$applied optimizations active", RC.Green)
                            run.freedMb?.let { Chip("+$it MB free RAM", RC.Blue) }
                        }
                    }
                    if (run.batterySaver) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RC.Amber.copy(alpha = 0.1f)).border(1.dp, RC.Amber, RoundedCornerShape(10.dp)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.BatterySaver, null, tint = RC.Amber, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Battery Saver is on and slows games down. Turn it off for full speed.", style = RCType.bodyDim.copy(color = RC.Amber), modifier = Modifier.weight(1f))
                            GlowButton("Fix", primary = false, onClick = onBatterySaver)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    GlowButton(
                        if (results == null) "Optimizing…" else if (run.launch) "Launch ${run.label}" else "Choose a game",
                        Modifier.fillMaxWidth(), enabled = results != null, onClick = onDone
                    )
                    Text(if (run.launch) "Launching automatically — tap anywhere to go now" else "Opening your game library…", style = RCType.bodyDim.copy(fontSize = 11.sp), modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(0.8f), contentAlignment = Alignment.Center) { ring() }
                Spacer(Modifier.width(24.dp))
                Box(Modifier.weight(1.2f).verticalScroll(rememberScrollState())) { log() }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ring(); Spacer(Modifier.height(18.dp)); log()
            }
        }
    }
}
