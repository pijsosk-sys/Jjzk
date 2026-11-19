package com.redcore.booster.desktop

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Same palette as the Android app, so both versions share the REDCORE identity. */
object RC {
    val Black = Color(0xFF07080B)
    val Panel = Color(0xFF13151C)
    val PanelHi = Color(0xFF1B1E27)
    val Stroke = Color(0xFF2A2E3A)
    val Metal = Color(0xFF3A3F4C)
    val Red = Color(0xFFFF1E3C)
    val RedDeep = Color(0xFFB0081F)
    val RedDark = Color(0xFF3A0610)
    val Blue = Color(0xFF2EA8FF)
    val BlueDeep = Color(0xFF1B3AD6)
    val Violet = Color(0xFF7A3CFF)
    val Amber = Color(0xFFFFB020)
    val Green = Color(0xFF2EE59D)
    val Orange = Color(0xFFFF7A1A)
    val Text = Color(0xFFEDEFF5)
    val TextDim = Color(0xFF9BA1B0)
    val TextMute = Color(0xFF5F6575)
    val redGradient = Brush.horizontalGradient(listOf(RedDeep, Red))
    val panelGradient = Brush.verticalGradient(listOf(Color(0xFF181A22), Color(0xFF0E1015)))
    val railSelected = Brush.horizontalGradient(listOf(BlueDeep.copy(alpha = 0.85f), Violet.copy(alpha = 0.8f), Red))
}

@Suppress("DEPRECATION")
val Orbitron = FontFamily(
    Font("font/orbitron_medium.ttf", FontWeight.Medium),
    Font("font/orbitron_bold.ttf", FontWeight.Bold),
    Font("font/orbitron_black.ttf", FontWeight.Black),
)

@Suppress("DEPRECATION")
val Rajdhani = FontFamily(
    Font("font/rajdhani_medium.ttf", FontWeight.Medium),
    Font("font/rajdhani_semibold.ttf", FontWeight.SemiBold),
    Font("font/rajdhani_bold.ttf", FontWeight.Bold),
)

object RCType {
    val display = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Black, fontSize = 30.sp, color = RC.Text)
    val title = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = RC.Text, letterSpacing = 1.sp)
    val label = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = RC.TextDim, letterSpacing = 1.2.sp)
    val number = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = RC.Text)
    val body = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = RC.Text)
    val bodyDim = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = RC.TextDim, lineHeight = 16.sp)
    val strong = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = RC.Text)
}

@Composable
fun RedcoreTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = RC.Red, onPrimary = Color.White, secondary = RC.Blue, background = RC.Black, surface = RC.Panel, onSurface = RC.Text, onBackground = RC.Text, outline = RC.Stroke),
    content = content,
)

private val angled = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)

@Composable
fun RedcoreLogo(size: Dp = 30.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val hex = Path().apply { moveTo(w * 0.5f, 0f); lineTo(w, h * 0.25f); lineTo(w, h * 0.75f); lineTo(w * 0.5f, h); lineTo(0f, h * 0.75f); lineTo(0f, h * 0.25f); close() }
        drawPath(hex, Brush.linearGradient(listOf(RC.RedDark, Color.Black)))
        drawPath(hex, RC.Red, style = Stroke(w * 0.06f))
        drawRect(RC.Red, Offset(w * 0.30f, h * 0.30f), Size(w * 0.12f, h * 0.42f))
        drawPath(Path().apply { moveTo(w * 0.48f, h * 0.40f); lineTo(w * 0.62f, h * 0.27f); lineTo(w * 0.62f, h * 0.62f); lineTo(w * 0.48f, h * 0.75f); close() }, RC.Red)
        drawPath(Path().apply { moveTo(w * 0.68f, h * 0.42f); lineTo(w * 0.75f, h * 0.36f); lineTo(w * 0.75f, h * 0.60f); lineTo(w * 0.68f, h * 0.66f); close() }, Color(0xFFFF6B7D))
    }
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, accent: Boolean = false, padding: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(angled).background(RC.panelGradient)
            .border(1.dp, Brush.linearGradient(if (accent) listOf(RC.Red, RC.Stroke, RC.Stroke, RC.RedDeep) else listOf(RC.Metal, RC.Stroke, RC.Stroke)), angled)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(14.dp).background(RC.Red))
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), style = RCType.label.copy(color = RC.Text, fontSize = 11.sp), modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun GlowButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp)
    val bg = when { !enabled -> Brush.horizontalGradient(listOf(RC.PanelHi, RC.PanelHi)); primary -> RC.redGradient; else -> Brush.horizontalGradient(listOf(RC.PanelHi, RC.Panel)) }
    Row(
        modifier.clip(shape).background(bg).border(1.dp, if (primary && enabled) RC.Red else RC.Stroke, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) Color.White else RC.TextMute, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text.uppercase(), style = RCType.label.copy(color = if (enabled) Color.White else RC.TextMute, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RedSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val shape = RoundedCornerShape(6.dp)
    Row(Modifier.clip(shape).background(RC.Black).border(1.dp, RC.Stroke, shape).clickable(enabled = enabled) { onChange(!checked) }.padding(3.dp)) {
        listOf(false to "OFF", true to "ON").forEach { (v, l) ->
            val sel = v == checked
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (sel) (if (v) RC.redGradient else Brush.linearGradient(listOf(RC.Metal, RC.Metal))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))).padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text(l, style = RCType.label.copy(fontSize = 9.sp, color = if (sel) Color.White else if (enabled) RC.TextMute else RC.Stroke))
            }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Row(modifier.clip(shape).background(RC.PanelHi).border(1.dp, RC.Stroke, shape).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(if (sel) RC.redGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(i) }.padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, style = RCType.label.copy(color = if (sel) Color.White else RC.TextDim, fontSize = 10.sp), maxLines = 1) }
        }
    }
}

/** Circular meter; [progress] null renders an "unavailable" ring (no fake value). */
@Composable
fun RingGauge(progress: Float?, value: String, unit: String, label: String, modifier: Modifier = Modifier, size: Dp = 120.dp, color: Color = RC.Red) {
    val anim by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "gauge")
    val spin = rememberInfiniteTransition(label = "spin")
    val rot by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "rot")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = min(this.size.width, this.size.height) / 2f
                val c = center
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent), c, r), r)
                for (i in 0 until 60) {
                    val a = Math.toRadians(i * 6.0 - 90)
                    val inner = r * if (i % 5 == 0) 0.80f else 0.84f
                    drawLine(if (progress != null && i / 60f <= anim) color.copy(alpha = 0.9f) else RC.Stroke,
                        Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                        Offset(c.x + cos(a).toFloat() * r * 0.89f, c.y + sin(a).toFloat() * r * 0.89f), strokeWidth = if (i % 5 == 0) 3f else 1.5f)
                }
                rotateArc(rot, c, r, color)
                drawCircle(Brush.radialGradient(listOf(RC.PanelHi, RC.Black), c, r * 0.72f), r * 0.72f)
                drawCircle(RC.Stroke, r * 0.72f, style = Stroke(2f))
                val ar = r * 0.76f
                if (progress != null) drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.3f), color, color), c), -90f, 360f * anim, false,
                    Offset(c.x - ar, c.y - ar), Size(ar * 2, ar * 2), style = Stroke(7f, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = RCType.number.copy(fontSize = (size.value / 6.2f).sp), maxLines = 1)
                if (unit.isNotEmpty()) Text(unit, style = RCType.label.copy(fontSize = 9.sp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label.uppercase(), style = RCType.label, textAlign = TextAlign.Center)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rotateArc(rot: Float, c: Offset, r: Float, color: Color) {
    val tl = Offset(c.x - r * 0.95f, c.y - r * 0.95f); val sz = Size(r * 1.9f, r * 1.9f)
    drawArc(RC.Blue.copy(alpha = 0.55f), rot, 40f, false, tl, sz, style = Stroke(2f))
    drawArc(color.copy(alpha = 0.55f), rot + 180f, 40f, false, tl, sz, style = Stroke(2f))
}

@Composable
fun LevelBar(progress: Float, modifier: Modifier = Modifier, color: Color = RC.Red, height: Dp = 8.dp) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(2.dp)).background(RC.Black).border(1.dp, RC.Stroke, RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth(anim).height(height).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), color))))
    }
}

/** Line chart; null entries are gaps (missing samples are never interpolated into fake values). */
@Composable
fun LineChart(values: List<Float?>, modifier: Modifier = Modifier, color: Color = RC.Red, minY: Float? = null, maxY: Float? = null) {
    Canvas(modifier) {
        for (g in 0..4) { val y = size.height * g / 4; drawLine(RC.Stroke.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1f) }
        val real = values.filterNotNull()
        if (real.size < 2) return@Canvas
        val lo = minY ?: real.min(); var hi = maxY ?: real.max(); if (hi - lo < 0.0001f) hi = lo + 1f
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val line = Path(); val fill = Path(); var started = false; var lastX = 0f; var firstX = 0f
        values.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val x = i * step; val y = size.height - ((v - lo) / (hi - lo)).coerceIn(0f, 1f) * size.height
            if (!started) { line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y); started = true; firstX = x } else { line.lineTo(x, y); fill.lineTo(x, y) }
            lastX = x
        }
        fill.lineTo(lastX, size.height); fill.lineTo(firstX, size.height); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(3f, cap = StrokeCap.Round))
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = RC.Red, sub: String? = null) {
    GlassPanel(modifier, padding = 12.dp) {
        Text(label.uppercase(), style = RCType.label.copy(fontSize = 9.sp, color = accent), maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(value, style = RCType.number.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = RCType.bodyDim.copy(fontSize = 12.sp), maxLines = 2)
    }
}

@Composable
fun Chip(text: String, color: Color = RC.Red, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(text.uppercase(), style = RCType.label.copy(fontSize = 8.sp, color = color), maxLines = 1, softWrap = false)
    }
}

@Composable
fun NoticeCard(title: String, message: String, modifier: Modifier = Modifier, color: Color = RC.Amber, action: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(10.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(color.copy(alpha = 0.08f)).border(1.dp, color.copy(alpha = 0.5f), shape).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Info, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(title, style = RCType.strong.copy(color = color, fontSize = 14.sp)); Text(message, style = RCType.bodyDim) }
        if (action != null) { Spacer(Modifier.width(8.dp)); action() }
    }
}

@Composable
fun InfoRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = RCType.bodyDim.copy(fontSize = 14.sp), modifier = Modifier.weight(0.42f))
        Text(value ?: "Unavailable", style = RCType.body.copy(fontSize = 14.sp, color = if (value == null) RC.TextMute else RC.Text, textAlign = TextAlign.End), modifier = Modifier.weight(0.58f))
    }
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(RC.Stroke.copy(alpha = 0.6f)))

@Composable
fun PulsingDot(color: Color = RC.Red, size: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = a)))
}

@Composable
fun LoadingSpinner(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val t = rememberInfiniteTransition(label = "load")
    val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "r")
    Canvas(modifier.size(size).rotate(r)) { drawArc(Brush.sweepGradient(listOf(Color.Transparent, RC.Red)), 0f, 300f, false, style = Stroke(6f, cap = StrokeCap.Round)) }
}

/** Dark backdrop with the red floor glow used behind every page. */
@Composable
fun Backdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(RC.Black).drawBehind {
        drawCircle(Brush.radialGradient(listOf(RC.RedDeep.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.5f, size.height * 1.05f), size.width * 0.9f), size.width * 0.9f, Offset(size.width * 0.5f, size.height * 1.05f))
        drawCircle(Brush.radialGradient(listOf(RC.BlueDeep.copy(alpha = 0.10f), Color.Transparent), Offset(0f, 0f), size.width * 0.7f), size.width * 0.7f, Offset(0f, 0f))
    }) { content() }
}
