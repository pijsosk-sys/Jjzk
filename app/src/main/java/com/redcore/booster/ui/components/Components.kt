package com.redcore.booster.ui.components

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Angled glass panel with a thin metallic border and an optional red accent edge. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    padding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.98f else 1f, label = "press")
    var m = modifier
        .scale(s)
        .clip(shape)
        .background(RC.panelGradient)
        .border(
            1.dp,
            Brush.linearGradient(
                if (accent) listOf(RC.Red, RC.Stroke, RC.Stroke, RC.RedDeep)
                else listOf(RC.Metal.copy(alpha = 0.9f), RC.Stroke, RC.Stroke)
            ),
            shape
        )
    if (onClick != null) m = m.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    Column(m.padding(padding), content = content)
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
fun GlowButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, label = "btn")
    val bg: Brush = when {
        !enabled -> Brush.horizontalGradient(listOf(RC.PanelHi, RC.PanelHi))
        primary -> RC.redGradient
        else -> Brush.horizontalGradient(listOf(RC.PanelHi, RC.Panel))
    }
    Row(
        modifier
            .scale(s)
            .drawBehind {
                if (primary && enabled) {
                    for (k in 1..3) {
                        val g = k * 5f
                        drawRoundRect(
                            RC.Red.copy(alpha = 0.10f), topLeft = Offset(-g, -g * 0.6f), size = Size(size.width + 2 * g, size.height + 1.2f * g),
                            cornerRadius = CornerRadius(24f + g)
                        )
                    }
                }
            }
            .clip(shape)
            .background(bg)
            .border(1.dp, if (primary && enabled) RC.Red else RC.Stroke, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Color.White else RC.TextMute, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text.uppercase(),
            style = RCType.label.copy(color = if (enabled) Color.White else RC.TextMute, fontSize = 11.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Off/On pill switch styled like a gaming console toggle. */
@Composable
fun RedSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.clip(shape).background(RC.Black).border(1.dp, RC.Stroke, shape)
            .clickable(enabled = enabled) { onChange(!checked) }.padding(3.dp)
    ) {
        listOf(false to "OFF", true to "ON").forEach { (v, l) ->
            val sel = v == checked
            Box(
                Modifier.clip(RoundedCornerShape(4.dp))
                    .background(if (sel) (if (v) RC.redGradient else Brush.linearGradient(listOf(RC.Metal, RC.Metal))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(l, style = RCType.label.copy(fontSize = 9.sp, color = if (sel) Color.White else if (enabled) RC.TextMute else RC.Stroke))
            }
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    var m = modifier.fillMaxWidth()
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(RC.RedDark).border(1.dp, RC.RedDeep, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = RC.Red, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = RCType.strong)
            if (subtitle != null) Text(subtitle, style = RCType.bodyDim)
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) trailing()
        else if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = RC.TextMute)
    }
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(RC.Stroke.copy(alpha = 0.6f)))

/** Key/value row used throughout the device research pages. Missing values are shown as "Unavailable". */
@Composable
fun InfoRow(label: String, value: String?, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = RCType.bodyDim.copy(fontSize = 14.sp), modifier = Modifier.weight(0.45f))
        Text(
            value ?: "Unavailable",
            style = RCType.body.copy(
                fontSize = 14.sp,
                color = when {
                    value == null -> RC.TextMute
                    highlight -> RC.Red
                    else -> RC.Text
                },
                textAlign = TextAlign.End
            ),
            modifier = Modifier.weight(0.55f)
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RedSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, onDone: (() -> Unit)? = null) {
    val src = remember { MutableInteractionSource() }
    Slider(
        value = value, onValueChange = onChange, enabled = enabled, modifier = modifier,
        onValueChangeFinished = onDone, interactionSource = src,
        thumb = {
            Box(
                Modifier.size(20.dp).drawBehind { drawCircle(RC.Red.copy(alpha = if (enabled) 0.35f else 0f), size.minDimension * 0.9f) }
                    .clip(CircleShape).background(if (enabled) RC.Red else RC.Metal).border(3.dp, Color.White, CircleShape)
            )
        },
        track = { st ->
            val f = ((st.value - st.valueRange.start) / (st.valueRange.endInclusive - st.valueRange.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(RC.Black).border(1.dp, RC.Stroke, RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth(f).height(6.dp).background(if (enabled) Brush.horizontalGradient(listOf(RC.RedDeep, RC.Red)) else Brush.horizontalGradient(listOf(RC.Metal, RC.Metal))))
            }
        }
    )
}

/** Segmented control like "Today | Week" or the mode selector. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Row(modifier.clip(shape).background(RC.PanelHi).border(1.dp, RC.Stroke, shape).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(4.dp))
                    .background(if (sel) RC.redGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(i) }.padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) { Text(o, style = RCType.label.copy(color = if (sel) Color.White else RC.TextDim, fontSize = 10.sp), maxLines = 1) }
        }
    }
}

/**
 * Circular performance meter with tick ring, glowing progress arc and animated value.
 * [progress] null renders an "unavailable" ring.
 */
@Composable
fun RingGauge(
    progress: Float?,
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    color: Color = RC.Red,
    secondary: Color = RC.Blue,
) {
    val anim by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "gauge")
    val spin = rememberInfiniteTransition(label = "spin")
    val rot by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "rot")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = min(this.size.width, this.size.height) / 2f
                val c = center
                // outer glow
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent), c, r), r)
                // ticks
                for (i in 0 until 60) {
                    val a = Math.toRadians(i * 6.0 - 90)
                    val inner = r * if (i % 5 == 0) 0.80f else 0.84f
                    val active = progress != null && i / 60f <= anim
                    drawLine(
                        if (active) color.copy(alpha = 0.9f) else RC.Stroke,
                        Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                        Offset(c.x + cos(a).toFloat() * r * 0.89f, c.y + sin(a).toFloat() * r * 0.89f),
                        strokeWidth = if (i % 5 == 0) 3f else 1.5f
                    )
                }
                // rotating secondary dashed ring
                rotate(rot) {
                    drawArc(secondary.copy(alpha = 0.55f), 0f, 40f, false, Offset(c.x - r * 0.95f, c.y - r * 0.95f), Size(r * 1.9f, r * 1.9f), style = Stroke(2f))
                    drawArc(color.copy(alpha = 0.55f), 180f, 40f, false, Offset(c.x - r * 0.95f, c.y - r * 0.95f), Size(r * 1.9f, r * 1.9f), style = Stroke(2f))
                }
                // inner disc
                drawCircle(Brush.radialGradient(listOf(RC.PanelHi, RC.Black), c, r * 0.72f), r * 0.72f)
                drawCircle(RC.Stroke, r * 0.72f, style = Stroke(2f))
                // progress arc
                val ar = r * 0.76f
                if (progress != null) {
                    drawArc(
                        Brush.sweepGradient(listOf(color.copy(alpha = 0.3f), color, color), c),
                        -90f, 360f * anim, false, Offset(c.x - ar, c.y - ar), Size(ar * 2, ar * 2),
                        style = Stroke(7f, cap = StrokeCap.Round)
                    )
                }
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

/** Horizontal level meter (used for RAM, storage etc). */
@Composable
fun LevelBar(progress: Float, modifier: Modifier = Modifier, color: Color = RC.Red, height: Dp = 8.dp) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(2.dp)).background(RC.Black).border(1.dp, RC.Stroke, RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth(anim).height(height).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), color))))
    }
}

/** Segmented vertical-ish level indicator like a gaming HUD. */
@Composable
fun SegmentMeter(progress: Float, modifier: Modifier = Modifier, segments: Int = 12, color: Color = RC.Red) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until segments) {
            val on = (i + 1) / segments.toFloat() <= progress + 0.0001f
            Box(Modifier.weight(1f).height(10.dp).background(if (on) color else RC.Stroke, RoundedCornerShape(1.dp)))
        }
    }
}

/** Live line chart for history series. Null entries are gaps. */
@Composable
fun LineChart(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    color: Color = RC.Red,
    minY: Float? = null,
    maxY: Float? = null,
    gridLines: Int = 4,
) {
    Canvas(modifier) {
        val pts = values
        val real = pts.filterNotNull()
        for (g in 0..gridLines) {
            val y = size.height * g / gridLines
            drawLine(RC.Stroke.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        if (real.size < 2) return@Canvas
        val lo = minY ?: real.min()
        var hi = maxY ?: real.max()
        if (hi - lo < 0.0001f) hi = lo + 1f
        val step = size.width / (pts.size - 1).coerceAtLeast(1)
        val line = Path()
        val fill = Path()
        var started = false
        var lastX = 0f
        var firstX = 0f
        pts.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val x = i * step
            val y = size.height - ((v - lo) / (hi - lo)).coerceIn(0f, 1f) * size.height
            if (!started) {
                line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y); started = true; firstX = x
            } else {
                line.lineTo(x, y); fill.lineTo(x, y)
            }
            lastX = x
        }
        fill.lineTo(lastX, size.height); fill.lineTo(firstX, size.height); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(3f, cap = StrokeCap.Round))
    }
}

/** A spinning fan graphic. It only spins when [spinning] is true. */
@Composable
fun FanGraphic(modifier: Modifier = Modifier, spinning: Boolean, color: Color = RC.Red) {
    val t = rememberInfiniteTransition(label = "fan")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "fanRot")
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent), center, r), r)
        drawCircle(color, r * 0.92f, style = Stroke(4f))
        drawCircle(RC.BlueDeep.copy(alpha = 0.7f), r * 0.86f, style = Stroke(2f))
        drawCircle(Brush.radialGradient(listOf(RC.PanelHi, RC.Black), center, r * 0.85f), r * 0.84f)
        rotate(if (spinning) rot else 0f) { drawBlades(r, color) }
        drawCircle(RC.Black, r * 0.18f)
        drawCircle(color, r * 0.18f, style = Stroke(3f))
    }
}

private fun DrawScope.drawBlades(r: Float, color: Color) {
    val blades = 7
    for (i in 0 until blades) {
        rotate(i * 360f / blades) {
            val p = Path().apply {
                moveTo(center.x, center.y - r * 0.2f)
                cubicTo(center.x + r * 0.35f, center.y - r * 0.35f, center.x + r * 0.45f, center.y - r * 0.7f, center.x + r * 0.15f, center.y - r * 0.8f)
                cubicTo(center.x + r * 0.05f, center.y - r * 0.55f, center.x - r * 0.05f, center.y - r * 0.35f, center.x - r * 0.12f, center.y - r * 0.18f)
                close()
            }
            drawPath(p, Brush.linearGradient(listOf(color.copy(alpha = 0.15f), Color(0xFF2A2D38)), Offset(center.x, center.y), Offset(center.x + r, center.y - r)))
            drawPath(p, color.copy(alpha = 0.35f), style = Stroke(1.5f))
        }
    }
}

@Composable
fun PulsingDot(color: Color = RC.Red, size: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "dotA")
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = a)))
}

@Composable
fun LoadingSpinner(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val t = rememberInfiniteTransition(label = "load")
    val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "loadR")
    Canvas(modifier.size(size).rotate(r)) {
        drawArc(Brush.sweepGradient(listOf(Color.Transparent, RC.Red)), 0f, 300f, false, style = Stroke(6f, cap = StrokeCap.Round))
        drawCircle(RC.Blue.copy(alpha = 0.4f), this.size.minDimension / 4f, style = Stroke(2f))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(RC.RedDark).border(1.dp, RC.Red, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = RC.Red, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(14.dp))
        Text(title.uppercase(), style = RCType.title.copy(fontSize = 14.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = RCType.bodyDim, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(14.dp)); action() }
    }
}

/** Banner explaining a limitation or missing permission. Never hides that a feature is unavailable. */
@Composable
fun NoticeCard(title: String, message: String, modifier: Modifier = Modifier, color: Color = RC.Amber, action: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(color.copy(alpha = 0.08f)).border(1.dp, color.copy(alpha = 0.5f), shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = RCType.strong.copy(color = color, fontSize = 14.sp))
            Text(message, style = RCType.bodyDim)
        }
        if (action != null) { Spacer(Modifier.width(8.dp)); action() }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null, accent: Color = RC.Red, sub: String? = null) {
    GlassPanel(modifier, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
            Text(label.uppercase(), style = RCType.label.copy(fontSize = 9.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = RCType.number.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = RCType.bodyDim.copy(fontSize = 12.sp), maxLines = 2)
    }
}

@Composable
fun Chip(text: String, color: Color = RC.Red, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) { Text(text.uppercase(), style = RCType.label.copy(fontSize = 8.sp, color = color), maxLines = 1, softWrap = false) }
}

/** Big angular backdrop with red sweeping arcs, drawn behind main pages. */
@Composable
fun HudBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxSize().background(RC.Black).drawBehind {
            drawCircle(
                Brush.radialGradient(listOf(RC.RedDeep.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.5f, size.height * 1.05f), size.width * 0.9f),
                size.width * 0.9f, Offset(size.width * 0.5f, size.height * 1.05f)
            )
            drawCircle(
                Brush.radialGradient(listOf(RC.BlueDeep.copy(alpha = 0.10f), Color.Transparent), Offset(0f, 0f), size.width * 0.7f),
                size.width * 0.7f, Offset(0f, 0f)
            )
            val arcR = size.width * 1.1f
            drawArc(
                RC.Red.copy(alpha = 0.18f), 200f, 140f, false,
                Offset(size.width / 2 - arcR, size.height * 0.95f - arcR * 0.4f), Size(arcR * 2, arcR * 0.8f), style = Stroke(2f)
            )
        },
        content = content
    )
}

/** True when the window is landscape/wide. Provided by AppScaffold. */
val LocalWide = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Landscape: two independently scrolling panes side by side (left visual, right details), like a
 * gaming control center. Portrait fallback: one scrolling column.
 */
@Composable
fun Panes(
    leftWeight: Float = 1f,
    rightWeight: Float = 1f,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    if (LocalWide.current) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(leftWeight).fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 14.dp), content = left)
            Column(Modifier.weight(rightWeight).fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 14.dp)) {
                right()
                Spacer(Modifier.height(10.dp))
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp)) {
            left(); right()
        }
    }
}
