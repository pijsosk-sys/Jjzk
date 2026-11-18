package com.redcore.booster.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.data.CrossStyle
import com.redcore.booster.data.LiveStats
import com.redcore.booster.service.BarrageBus
import com.redcore.booster.service.BarrageItem
import com.redcore.booster.ui.RedcoreLogo
import com.redcore.booster.ui.theme.Orbitron
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

val crossColors = listOf(Color(0xFFFF1E3C), Color(0xFF2EE59D), Color(0xFF3CF2FF), Color.White, Color(0xFFFFD21E))

/** Crosshair drawn in the middle of the screen. The window is not touchable, so the game gets every touch. */
@Composable
fun CrosshairGlyph(style: CrossStyle, colorIdx: Int) {
    val color = crossColors.getOrElse(colorIdx) { crossColors[0] }
    Canvas(Modifier.fillMaxSize()) {
        val c = center; val r = size.minDimension / 2
        val sw = 2.6.dp.toPx(); val shadow = Color(0x99000000)
        fun line(a: Offset, b: Offset) { drawLine(shadow, a, b, sw + 2f); drawLine(color, a, b, sw) }
        when (style) {
            CrossStyle.CROSS -> {
                val g = r * 0.22f; val e = r * 0.85f
                line(Offset(c.x - e, c.y), Offset(c.x - g, c.y)); line(Offset(c.x + g, c.y), Offset(c.x + e, c.y))
                line(Offset(c.x, c.y - e), Offset(c.x, c.y - g)); line(Offset(c.x, c.y + g), Offset(c.x, c.y + e))
                drawCircle(color, sw * 0.7f, c)
            }
            CrossStyle.DOT -> { drawCircle(shadow, r * 0.16f + 1.5f, c); drawCircle(color, r * 0.16f, c) }
            CrossStyle.CIRCLE -> {
                drawCircle(shadow, r * 0.62f, c, style = Stroke(sw + 2f)); drawCircle(color, r * 0.62f, c, style = Stroke(sw))
                drawCircle(color, sw * 0.9f, c)
            }
            CrossStyle.LINES -> {
                line(Offset(0f, c.y), Offset(size.width, c.y)); line(Offset(c.x, 0f), Offset(c.x, size.height))
                drawCircle(color, r * 0.5f, c, style = Stroke(sw * 0.6f))
            }
        }
    }
}

/** Floating stopwatch / countdown, like a gaming phone's match timer. */
@Composable
fun StopwatchWidget(onDrag: (Float, Float) -> Unit, onClose: () -> Unit, onAlarm: () -> Unit) {
    val presets = listOf(0, 30, 60, 90, 120, 300) // 0 = stopwatch, others = countdown seconds
    var preset by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var base by remember { mutableLongStateOf(0L) }
    val target = presets[preset] * 1000L
    LaunchedEffect(running) {
        while (running) {
            elapsed = SystemClock.elapsedRealtime() - base
            if (target > 0 && elapsed >= target) { elapsed = target; running = false; onAlarm() }
            delay(50)
        }
    }
    val shown = if (target > 0) (target - elapsed).coerceAtLeast(0) else elapsed
    val txt = "%02d:%02d.%d".format(shown / 60000, (shown / 1000) % 60, (shown / 100) % 10)
    Row(
        Modifier.pointerInput(Unit) { detectDragGestures { ch, d -> ch.consume(); onDrag(d.x, d.y) } }
            .clip(RoundedCornerShape(22.dp)).background(Color(0xE60A0B10)).border(1.dp, RC.Red.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Timer, null, tint = if (target > 0) RC.Amber else RC.Red, modifier = Modifier.size(18.dp))
        Text(txt, style = RCType.number.copy(fontSize = 16.sp, color = if (target > 0 && shown == 0L) RC.Red else Color.White), modifier = Modifier.width(98.dp))
        MiniBtn(if (running) Icons.Default.Pause else Icons.Default.PlayArrow) {
            if (running) running = false else { if (target > 0 && shown == 0L) elapsed = 0; base = SystemClock.elapsedRealtime() - elapsed; running = true }
        }
        MiniBtn(Icons.Default.Replay) { running = false; elapsed = 0 }
        Box(
            Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF22252F)).clickable { running = false; elapsed = 0; preset = (preset + 1) % presets.size }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) { Text(if (target == 0L) "SW" else if (presets[preset] < 60) "${presets[preset]}s" else "${presets[preset] / 60}m", style = RCType.label.copy(fontSize = 9.sp, color = Color.White)) }
        MiniBtn(Icons.Default.Close, onClose)
    }
}

@Composable
private fun MiniBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF22252F)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/** Barrage notifications: messages glide across the top of the screen instead of popping up. */
@Composable
fun BarrageTicker() {
    val queue = remember { mutableStateListOf<BarrageItem>() }
    LaunchedEffect(Unit) { BarrageBus.items.collect { if (queue.size < 8) queue.add(it) } }
    val current = queue.firstOrNull()
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        if (current != null) {
            val screen = constraints.maxWidth.toFloat()
            var rowW by remember(current) { mutableIntStateOf(0) }
            val x = remember(current) { Animatable(screen) }
            LaunchedEffect(current, rowW) {
                if (rowW > 0) {
                    val dist = screen + rowW
                    x.animateTo(-rowW.toFloat(), tween(((dist / screen) * 6500).toInt(), easing = LinearEasing))
                    if (queue.isNotEmpty()) queue.removeAt(0)
                }
            }
            Row(
                Modifier.offset { IntOffset(x.value.toInt(), 0) }.onSizeChanged { rowW = it.width }
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xCC0A0B10)).border(1.dp, RC.Red.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(current.app.uppercase(), style = RCType.label.copy(fontSize = 9.sp, color = RC.Red), maxLines = 1, softWrap = false)
                Spacer(Modifier.width(8.dp))
                Text(listOf(current.title, current.text).filter { it.isNotBlank() }.joinToString(": "), style = RCType.body.copy(fontSize = 14.sp, color = Color.White), maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * Screen hang-up: a black, touch-blocking layer for idle farming or downloads. The game keeps running
 * underneath; on OLED screens black pixels are off, so it uses very little power.
 */
@Composable
fun HangupScreen(label: String, stats: LiveStats, onExit: () -> Unit) {
    var hint by remember { mutableStateOf(true) }
    var pos by remember { mutableStateOf(IntOffset(0, 0)) }
    var dragged by remember { mutableStateOf(0f) }
    var now by remember { mutableStateOf(Date()) }
    val alpha by animateFloatAsState(if (hint) 0.55f else 0.14f, tween(800), label = "hint")
    LaunchedEffect(hint) { if (hint) { delay(4000); hint = false } }
    LaunchedEffect(Unit) {
        while (true) { delay(20_000); now = Date(); pos = IntOffset(Random.nextInt(-120, 120), Random.nextInt(-60, 60)) } // avoid burn-in
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onExit() }, onTap = { hint = true }) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = { if (dragged < -size.height * 0.18f) onExit(); dragged = 0f }) { ch, d -> ch.consume(); dragged += d; hint = true }
            }
    ) {
        Column(Modifier.align(Alignment.Center).offset { pos }.alpha(alpha), horizontalAlignment = Alignment.CenterHorizontally) {
            RedcoreLogo(36.dp)
            Spacer(Modifier.height(10.dp))
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), style = androidx.compose.ui.text.TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = Color.White))
            Text("$label · battery ${stats.batteryPct ?: "—"}%", style = RCType.body.copy(color = Color.White))
            Spacer(Modifier.height(16.dp))
            Text("SCREEN HANG-UP · game still running", style = RCType.label.copy(fontSize = 9.sp, color = RC.Red))
            Text("Swipe up or double-tap to resume", style = RCType.bodyDim.copy(color = Color.White))
        }
    }
}

/**
 * Thin edge strip that opens the game menu when you swipe inward from the screen edge or down from a top corner.
 * It can show a faint red handle so you know where to swipe.
 */
@SuppressLint("ViewConstructor")
class SwipeHandleView(ctx: Context, private val dir: Int, private val hint: Boolean, private val onSwipe: () -> Unit) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FF1E3C.toInt() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FF1E3C }
    private var x0 = 0f
    private var y0 = 0f
    private var fired = false

    override fun onDraw(c: AndroidCanvas) {
        if (!hint) return
        when (dir) {
            DOWN -> { val w = width * 0.42f; val l = (width - w) / 2; c.drawRoundRect(l - 4 * d, 0f, l + w + 4 * d, 10 * d, 6 * d, 6 * d, glow); c.drawRoundRect(l, 3 * d, l + w, 6.5f * d, 3 * d, 3 * d, paint) }
            RIGHT -> { val h = height * 0.5f; val t = (height - h) / 2; c.drawRoundRect(0f, t - 4 * d, 10 * d, t + h + 4 * d, 6 * d, 6 * d, glow); c.drawRoundRect(3 * d, t, 6.5f * d, t + h, 3 * d, 3 * d, paint) }
            LEFT -> { val h = height * 0.5f; val t = (height - h) / 2; c.drawRoundRect(width - 10 * d, t - 4 * d, width.toFloat(), t + h + 4 * d, 6 * d, 6 * d, glow); c.drawRoundRect(width - 6.5f * d, t, width - 3 * d, t + h, 3 * d, 3 * d, paint) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x0 = e.rawX; y0 = e.rawY; fired = false }
            MotionEvent.ACTION_MOVE -> {
                val dist = when (dir) { DOWN -> e.rawY - y0; RIGHT -> e.rawX - x0; else -> x0 - e.rawX }
                if (!fired && dist > 26 * d) { fired = true; onSwipe() }
            }
        }
        return true
    }

    companion object {
        const val DOWN = 0
        const val RIGHT = 1
        const val LEFT = 2
    }
}
