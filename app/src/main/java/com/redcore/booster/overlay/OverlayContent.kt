package com.redcore.booster.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.NightsStay
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.R
import com.redcore.booster.app
import com.redcore.booster.booster.ActionState
import com.redcore.booster.booster.CoolLevel
import com.redcore.booster.booster.EffectsState
import com.redcore.booster.booster.GameSession
import com.redcore.booster.booster.Perm
import com.redcore.booster.booster.Permissions
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.CallMode
import com.redcore.booster.data.EqPreset
import com.redcore.booster.data.GameMode
import com.redcore.booster.data.LiveStats
import com.redcore.booster.data.ModeSpec
import com.redcore.booster.data.NotifMode
import com.redcore.booster.data.SettingsRepository.K
import com.redcore.booster.data.fmtBytes
import com.redcore.booster.data.fmtRate
import com.redcore.booster.data.temp
import com.redcore.booster.record.ScreenRecordService
import com.redcore.booster.service.BarrageBus
import com.redcore.booster.ui.RedcoreLogo
import com.redcore.booster.ui.components.RedSlider
import com.redcore.booster.ui.components.SegmentMeter
import com.redcore.booster.ui.rcIcon
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

private val HudOrange = Color(0xFFFF7A1A)
private val CoolBlue = Color(0xFF3CC8FF)

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
    val edgeGuard: Boolean = false,
    val triggers: Boolean = false,
    val autoBrightness: Boolean = false,
    val secureGranted: Boolean = false,
    val fx: EffectsState = EffectsState(),
    val notifMode: NotifMode = NotifMode.ALL,
    val rotationLocked: Boolean = false,
    val stopwatch: Boolean = false,
)

interface OverlayActions {
    fun drag(dx: Float, dy: Float) {}
    fun dragEnd() {}
    fun expand(e: Boolean) {}
    fun mode(m: GameMode) {}
    fun cool(on: Boolean) {}
    fun notif(m: NotifMode) {}
    fun calls(m: CallMode) {}
    fun brightness(p: Int) {}
    fun autoBrightness(on: Boolean) {}
    fun volume(p: Int) {}
    fun screenshot() {}
    fun record() {}
    fun mic(on: Boolean) {}
    fun clean() {}
    fun hud(on: Boolean) {}
    fun edgeGuard(on: Boolean) {}
    fun triggers(on: Boolean) {}
    fun crosshair() {}
    fun stopwatch() {}
    fun hangup() {}
    fun haptics(on: Boolean) {}
    fun eq(p: EqPreset) {}
    fun rotation(on: Boolean) {}
    fun smooth120(on: Boolean) {}
    fun refresh() {}
    fun open(dest: String?) {}
    fun grant(p: Perm) {}
    fun endSession() {}
}

/** Wires the real service and repositories to the stateless [OverlayUi]. */
@Composable
fun OverlayRoot(svc: OverlayService) {
    val a = svc.app
    val stats by a.monitor.stats.collectAsState()
    val settings by a.settings.settings.collectAsState(AppSettings())
    val session by a.boost.session.collectAsState()
    val fx by a.boost.effects.state.collectAsState()
    val expanded by svc.expanded.collectAsState()
    val stopwatch by svc.stopwatchOpen.collectAsState()
    val recording by ScreenRecordService.recording.collectAsState()
    val edge by EdgeGuard.active.collectAsState()
    var dnd by remember { mutableStateOf(a.boost.isDndOn()) }
    var notif by remember { mutableStateOf(if (a.boost.isDndOn()) BarrageBus.mode else NotifMode.ALL) }
    var bri by remember { mutableStateOf(a.boost.currentBrightnessPct()) }
    var vol by remember { mutableStateOf(a.boost.volumePct()) }
    var mic by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(a.boost.isAutoBrightness()) }
    var rot by remember { mutableStateOf(a.boost.isRotationLocked()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(expanded) {
        if (expanded) {
            dnd = a.boost.isDndOn(); notif = if (dnd) BarrageBus.mode else NotifMode.ALL
            bri = a.boost.currentBrightnessPct(); vol = a.boost.volumePct(); auto = a.boost.isAutoBrightness(); rot = a.boost.isRotationLocked()
        }
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
                    svc.toast("${m.title} mode · ${r.results.count { it.state == ActionState.APPLIED }} optimizations active")
                }
            }
            override fun cool(on: Boolean) { scope.launch { a.settings.put(K.cool, on) }; svc.toast(if (on) "❄ COOL guard on — eases off when the phone gets hot" else "COOL guard off") }
            override fun notif(m: NotifMode) {
                if (m != NotifMode.ALL && !a.boost.canSetDnd()) { grant(Perm.DND); return }
                if (m == NotifMode.BARRAGE && !Permissions.granted(svc, Perm.NOTIF_ACCESS)) svc.toast("Grant Notification access in REDCORE › Game Tools to see the barrage")
                a.boost.setNotifMode(m)
                if (m != NotifMode.ALL) scope.launch { a.settings.put(K.notif, m.name) }
                notif = m; dnd = a.boost.isDndOn()
            }
            override fun calls(m: CallMode) {
                if (m == CallMode.DECLINE && !Permissions.granted(svc, Perm.CALL_SCREENING)) svc.toast("Declining calls needs Call blocking (REDCORE › Game Tools). Calls will be silenced.")
                scope.launch { a.settings.put(K.calls, m.name) }
                if (a.boost.isDndOn()) a.boost.setDnd(true, if (m == CallMode.ALLOW) CallMode.ALLOW else CallMode.SILENCE)
            }
            override fun brightness(p: Int) { a.boost.setBrightness(p); bri = p; auto = false }
            override fun autoBrightness(on: Boolean) {
                if (!a.boost.canSetBrightness()) { grant(Perm.WRITE_SETTINGS); return }
                a.boost.setAutoBrightness(on); auto = a.boost.isAutoBrightness()
            }
            override fun volume(p: Int) { a.boost.setVolumePct(p); vol = p }
            override fun screenshot() = svc.screenshot()
            override fun record() = svc.toggleRecording(mic)
            override fun mic(on: Boolean) {
                if (on && !Permissions.has(svc, android.Manifest.permission.RECORD_AUDIO)) {
                    svc.toast("Allow Microphone in REDCORE › Settings › Permissions to record voice chat.")
                    svc.openApp("SETTINGS")
                    return
                }
                mic = on
            }
            override fun clean() { scope.launch { svc.toast(a.boost.cleanBackground().detail) } }
            override fun hud(on: Boolean) { scope.launch { a.settings.setHud(on) } }
            override fun edgeGuard(on: Boolean) {
                if (on) EdgeGuard.show(svc, settings.edgeWidthDp) else EdgeGuard.hide(svc)
                if (on) EdgeGuard.setPassThrough(svc, true) // the menu is open right now
                svc.toast(if (on) "Edge touch guard on" else "Edge touch guard off")
            }
            override fun triggers(on: Boolean) {
                if (on && !Permissions.accessibilityEnabled(svc)) { grant(Perm.ACCESSIBILITY); return }
                scope.launch { a.settings.setTriggers(on) }
                svc.toast(if (on) "Vol− = L1, Vol+ = R1" else "Volume keys back to normal")
            }
            override fun crosshair() = svc.toggleCrosshair()
            override fun stopwatch() = svc.toggleStopwatch()
            override fun hangup() = svc.startHangup()
            override fun haptics(on: Boolean) {
                if (on && !Permissions.has(svc, android.Manifest.permission.RECORD_AUDIO)) { svc.toast("4D vibration needs Microphone access (audio is analysed on the phone, never recorded)."); svc.openApp("TOOLS"); return }
                scope.launch { a.settings.put(K.haptics, on) }
            }
            override fun eq(p: EqPreset) { scope.launch { a.settings.put(K.eq, p.name) } }
            override fun rotation(on: Boolean) {
                if (!a.boost.canSetBrightness()) { grant(Perm.WRITE_SETTINGS); return }
                a.boost.setRotationLock(on); rot = a.boost.isRotationLocked()
            }
            override fun refresh() { svc.toast(a.boost.cycleRefresh(stats.refreshHz).detail) }
            override fun smooth120(on: Boolean) {
                scope.launch { a.settings.put(K.smooth, on) }
                svc.toast(a.boost.applySmooth120(on).detail)
            }
            override fun open(dest: String?) = svc.openApp(dest)
            override fun grant(p: Perm) { svc.requestPermission(p, "REDCORE needs \"${p.title}\" for this — turn it on, then come back to your game.") }
            override fun endSession() = svc.endSession()
        }
    }
    OverlayUi(
        OverlayState(
            stats, settings, session, expanded, recording, dnd, a.boost.canSetDnd(), if (a.boost.canSetBrightness()) bri else null, vol, mic,
            edge, settings.triggers, auto, Permissions.granted(svc, Perm.SECURE_SETTINGS), fx, notif, rot, stopwatch
        ),
        actions
    )
}

@Composable
fun OverlayUi(s: OverlayState, act: OverlayActions) {
    if (!s.expanded) Collapsed(s, act)
    else BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight && maxWidth >= 560.dp) GameHud(s, act) else CompactPanel(s, act)
    }
}

// ============================================================================================
// Collapsed: optional bubble + live stats pill (drag to move, tap to open the menu)
// ============================================================================================

@Composable
private fun Collapsed(s: OverlayState, act: OverlayActions) {
    if (!s.settings.showBubble && !s.settings.hudEnabled) return
    Row(
        Modifier.scale(s.settings.overlayScale).pointerInput(Unit) {
            detectDragGestures(onDragEnd = { act.dragEnd() }) { change, drag -> change.consume(); act.drag(drag.x, drag.y) }
        }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (s.settings.showBubble) Bubble(s.recording) { act.expand(true) }
        if (s.settings.showBubble && s.settings.hudEnabled) Spacer(Modifier.width(6.dp))
        if (s.settings.hudEnabled) HudPill(s) { act.expand(true) }
    }
}

@Composable
fun Bubble(recording: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "bub")
    val pulse by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Box(Modifier.size(46.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onClick), contentAlignment = Alignment.Center) {
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
        if (recording) Text("REC", style = RCType.label.copy(fontSize = 9.sp, color = RC.Red)) else RedcoreLogo(22.dp)
    }
}

@Composable
private fun HudPill(s: OverlayState, onOpen: () -> Unit) {
    val st = s.stats
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomEnd = 2.dp, bottomStart = 10.dp)
    Row(
        Modifier.clip(shape).background(Color(0xD907080B)).border(1.dp, Brush.horizontalGradient(listOf(RC.Red, RC.Stroke, RC.Red.copy(alpha = 0.4f))), shape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onOpen)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (s.recording) HudItem("REC", "●", RC.Red)
        s.fx.fps?.let { HudItem(if (s.fx.fpsSource == "game") "FPS" else "SCR FPS", "$it", RC.Green) }
        HudItem("CPU", st.peakCurMHz?.let { "%.2fG".format(it / 1000f) } ?: "—", RC.Red)
        HudItem("RAM", "${(st.ramFraction * 100).toInt()}%", RC.Blue)
        HudItem("TMP", s.settings.temp(st.batteryTempC), tempColor(st.batteryTempC))
        HudItem("BAT", st.batteryPct?.let { "$it%" } ?: "—", RC.Green)
        s.fx.pingMs?.let { HudItem("PING", "${it}ms", if (it < 50) RC.Green else if (it < 100) RC.Amber else RC.Red) }
        HudItem("NET", fmtRate(st.rxBps).replace(" ", ""), RC.Violet)
        if (s.fx.coolLevel == CoolLevel.WARM || s.fx.coolLevel == CoolLevel.HOT)
            Icon(Icons.Default.AcUnit, "COOL guard active", tint = if (s.fx.coolLevel == CoolLevel.HOT) HudOrange else CoolBlue, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun HudItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = RCType.label.copy(fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, style = RCType.label.copy(fontSize = 6.5.sp, color = color))
    }
}

// ============================================================================================
// Landscape game menu (opened by swiping in from a side edge): panels spring in from both edges behind segmented chevron bands; the game
// stays visible in a glowing frame in the middle. Tapping the game (or ✕) animates it closed.
// ============================================================================================

@Composable
private fun GameHud(s: OverlayState, act: OverlayActions) {
    // Start hidden, then animate in; closing animates out and only then collapses the window.
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(closing) { if (closing) { visible = false; delay(260); act.expand(false) } }
    val close = { closing = true }
    val t = rememberInfiniteTransition(label = "hud")
    val flow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "flow")
    val reveal by animateFloatAsState(if (visible) 1f else 0f, tween(560, easing = FastOutSlowInEasing), label = "reveal")
    BoxWithConstraints(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { close() }) {
        val sideW = maxWidth * 0.37f
        AnimatedVisibility(visible, enter = fadeIn(tween(260)), exit = fadeOut(tween(220))) { CenterFrame(reveal, flow) }
        AnimatedVisibility(
            visible, modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 420f)) { -it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(230, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(200))
        ) { SidePanel(left = true, width = sideW, flow = flow, reveal = reveal) { LeftContent(s, act) } }
        AnimatedVisibility(
            visible, modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 420f)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(230, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
        ) { SidePanel(left = false, width = sideW, flow = flow, reveal = reveal) { RightContent(s, act, close) } }
        AnimatedVisibility(
            visible, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            enter = slideInVertically(tween(380)) { -it * 2 } + fadeIn(), exit = slideOutVertically(tween(220)) { -it * 2 } + fadeOut()
        ) { TopTitle(s) }
        AnimatedVisibility(
            visible, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            enter = slideInVertically(tween(380)) { it * 2 } + fadeIn(), exit = slideOutVertically(tween(220)) { it * 2 } + fadeOut()
        ) { BottomInfo(s) }
    }
}

/** Vignette and glowing HUD brackets framing the game between the panels. */
@Composable
private fun CenterFrame(reveal: Float, flow: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.horizontalGradient(0f to Color(0x8C000000), 0.33f to Color(0x22000000), 0.67f to Color(0x22000000), 1f to Color(0x8C000000)))
        drawRect(Brush.verticalGradient(0f to Color(0x55000000), 0.18f to Color.Transparent, 0.82f to Color.Transparent, 1f to Color(0x66000000)))
        val l = size.width * 0.385f; val r = size.width * 0.615f; val top = size.height * 0.16f; val bot = size.height * 0.84f
        val len = 26.dp.toPx() * reveal; val sw = 2.5.dp.toPx()
        val c = RC.Red.copy(alpha = 0.85f)
        fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c.copy(alpha = 0.25f), Offset(x, y), Offset(x + dx * len, y), sw * 4, StrokeCap.Round)
            drawLine(c.copy(alpha = 0.25f), Offset(x, y), Offset(x, y + dy * len), sw * 4, StrokeCap.Round)
            drawLine(c, Offset(x, y), Offset(x + dx * len, y), sw, StrokeCap.Round)
            drawLine(c, Offset(x, y), Offset(x, y + dy * len), sw, StrokeCap.Round)
        }
        bracket(l, top, 1f, 1f); bracket(r, top, -1f, 1f); bracket(l, bot, 1f, -1f); bracket(r, bot, -1f, -1f)
        // centre aim ticks + scanning line
        val cx = size.width / 2; val cy = size.height / 2
        drawLine(RC.Red.copy(alpha = 0.35f * reveal), Offset(cx - 10.dp.toPx(), cy), Offset(cx + 10.dp.toPx(), cy), 1.5f)
        drawLine(RC.Red.copy(alpha = 0.35f * reveal), Offset(cx, cy - 10.dp.toPx()), Offset(cx, cy + 10.dp.toPx()), 1.5f)
        val scanY = top + (bot - top) * flow
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, RC.Red.copy(alpha = 0.18f), Color.Transparent), startX = l, endX = r), Offset(l, scanY), Offset(r, scanY), 2f)
    }
}

/** Dark side panel with a diagonal texture whose inner edge is a segmented chevron band pointing at the game. */
@Composable
private fun SidePanel(left: Boolean, width: Dp, flow: Float, reveal: Float, content: @Composable () -> Unit) {
    Box(
        Modifier.width(width).fillMaxHeight()
            .clickable(remember { MutableInteractionSource() }, null) {}
            .drawWithCache {
                val w = size.width; val h = size.height
                fun edgeX(tt: Float): Float {
                    val k = 1f - kotlin.math.abs(tt * 2f - 1f)
                    val x = w * (0.80f + 0.13f * k)
                    return if (left) x else w - x
                }
                val fill = Path().apply {
                    if (left) { moveTo(0f, 0f); lineTo(edgeX(0f), 0f); lineTo(edgeX(0.5f), h / 2); lineTo(edgeX(1f), h); lineTo(0f, h) }
                    else { moveTo(w, 0f); lineTo(edgeX(0f), 0f); lineTo(edgeX(0.5f), h / 2); lineTo(edgeX(1f), h); lineTo(w, h) }
                    close()
                }
                val texture = Path().apply {
                    var x = -h
                    while (x < w) { moveTo(x, h); lineTo(x + h, 0f); x += 18.dp.toPx() }
                }
                val edge = Path().apply { moveTo(edgeX(0f), 0f); lineTo(edgeX(0.5f), h / 2); lineTo(edgeX(1f), h) }
                val bg = Brush.horizontalGradient(if (left) listOf(Color(0xF5090A0F), Color(0xEB12141C)) else listOf(Color(0xEB12141C), Color(0xF5090A0F)))
                val bandW = w * 0.065f
                val dir = if (left) -1f else 1f
                val segs = 16
                onDrawBehind {
                    drawPath(fill, bg)
                    clipPath(fill) {
                        drawPath(texture, Color(0x0DFFFFFF), style = Stroke(1f))
                        drawRect(Brush.verticalGradient(listOf(RC.Red.copy(alpha = 0.10f), Color.Transparent, Color.Transparent, RC.Red.copy(alpha = 0.06f))))
                    }
                    // glow along the edge
                    drawPath(edge, RC.Red.copy(alpha = 0.12f * reveal), style = Stroke(bandW * 2.4f))
                    for (i in 0 until segs) {
                        val t0 = i / segs.toFloat(); val t1 = (i + 0.68f) / segs
                        if (t0 > reveal) continue // sequential light-up when opening
                        val p = Path().apply {
                            moveTo(edgeX(t0), h * t0); lineTo(edgeX(t0) + dir * bandW, h * t0)
                            lineTo(edgeX(t1) + dir * bandW, h * t1); lineTo(edgeX(t1), h * t1); close()
                        }
                        val phase = ((i / segs.toFloat()) - flow).let { it - kotlin.math.floor(it) }
                        val a = 0.5f + 0.5f * (1f - phase)
                        drawPath(p, Brush.verticalGradient(listOf(RC.Red.copy(alpha = a), HudOrange.copy(alpha = a)), startY = h * t0, endY = h * t1))
                    }
                    val rim = Path().apply {
                        val o = -dir * bandW * 1.3f
                        moveTo(edgeX(0f) + o, 0f); lineTo(edgeX(0.5f) + o, h / 2); lineTo(edgeX(1f) + o, h)
                    }
                    drawPath(rim, Color(0xFF4A4F5C), style = Stroke(2f))
                    // top accent line
                    drawLine(Brush.horizontalGradient(if (left) listOf(RC.Red, Color.Transparent) else listOf(Color.Transparent, RC.Red)), Offset(0f, 1f), Offset(w, 1f), 2f)
                }
            }
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(0.79f).align(if (left) Alignment.CenterStart else Alignment.CenterEnd)) { content() }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, tint: Color = RC.Text, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF262934), Color(0xFF16181F))))
            .border(1.dp, RC.Stroke, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(17.dp)) }
}

@Composable
private fun LeftContent(s: OverlayState, act: OverlayActions) {
    Column(Modifier.fillMaxSize().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIcon(rcIcon(R.drawable.ic_rc_home), "Open REDCORE") { act.open(null) }
            RoundIcon(rcIcon(R.drawable.ic_rc_gamepad), "Game Space") { act.open("GAMES") }
            RoundIcon(rcIcon(R.drawable.ic_rc_gauge), "Performance") { act.open("BOOST") }
            RoundIcon(rcIcon(R.drawable.ic_rc_tools), "Game Tools") { act.open("TOOLS") }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.weight(1f)) {
            Column(Modifier.width(80.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ToolCard("Edge guard", rcIcon(R.drawable.ic_rc_touch), s.edgeGuard) { act.edgeGuard(!s.edgeGuard) }
                ToolCard("L1 / R1", rcIcon(R.drawable.ic_rc_gamepad), s.triggers) { act.triggers(!s.triggers) }
                ToolCard("Crosshair", Icons.Default.GpsFixed, s.settings.crosshair) { act.crosshair() }
                ToolCard("Stopwatch", Icons.Default.Timer, s.stopwatch) { act.stopwatch() }
                ToolCard("Hang-up", Icons.Default.NightsStay, false) { act.hangup() }
                ToolCard("HUD", Icons.Default.Speed, s.settings.hudEnabled) { act.hud(!s.settings.hudEnabled) }
                ToolCard("Close apps", Icons.Default.AutoDelete, false) { act.clean() }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val st = s.stats
                HudReadout(st.peakCurMHz?.div(1000f), 2, "GHz", "CPU", RC.Red)
                Spacer(Modifier.height(6.dp))
                val mode = s.session?.mode ?: s.settings.globalMode
                ChevronChip(mode.title, mode.accent(), mode.icon()) { act.mode(GameMode.entries[(mode.ordinal + 1) % GameMode.entries.size]) }
                Spacer(Modifier.height(6.dp))
                ChevronChip(
                    if (!s.settings.coolGuard) "Cool off" else when (s.fx.coolLevel) { CoolLevel.WARM -> "Cooling"; CoolLevel.HOT -> "Hot!"; else -> "Cool on" },
                    if (!s.settings.coolGuard) RC.TextMute else if (s.fx.coolLevel == CoolLevel.HOT) HudOrange else CoolBlue, Icons.Default.AcUnit
                ) { act.cool(!s.settings.coolGuard) }
                Spacer(Modifier.weight(1f))
                VerticalLevel(
                    if (s.autoBrightness) 0f else (s.brightness ?: 0) / 100f, enabled = s.brightness != null,
                    icon = Icons.Default.BrightnessMedium, color = Color.White,
                    onChange = { act.brightness((it * 100).toInt().coerceAtLeast(1)) },
                    onLocked = { act.grant(Perm.WRITE_SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun RightContent(s: OverlayState, act: OverlayActions, close: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(end = 14.dp, top = 12.dp, bottom = 12.dp, start = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            s.fx.pingMs?.let { Text("${it} ms", style = RCType.label.copy(fontSize = 9.sp, color = if (it < 50) RC.Green else if (it < 100) RC.Amber else RC.Red)); Spacer(Modifier.width(10.dp)) }
            Text("${s.stats.refreshHz ?: "—"} Hz", style = RCType.label.copy(fontSize = 9.sp, color = RC.TextDim))
            Spacer(Modifier.width(10.dp))
            RoundIcon(Icons.Default.Close, "Close", onClick = close)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.weight(1f)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val st = s.stats
                when {
                    s.fx.fps != null -> HudReadout(s.fx.fps.toFloat(), 0, if (s.fx.fpsSource == "game") "FPS" else "screen FPS", "FPS", RC.Green)
                    st.gpuMHz != null -> HudReadout(st.gpuMHz.toFloat(), 0, "MHz", "GPU", RC.Violet)
                    else -> HudReadout(st.ramFraction * 100, 0, "%", "RAM", RC.Blue)
                }
                Spacer(Modifier.height(6.dp))
                ChevronChip(s.notifMode.title, if (s.notifMode == NotifMode.ALL) RC.TextDim else HudOrange) {
                    act.notif(NotifMode.entries[(s.notifMode.ordinal + 1) % NotifMode.entries.size])
                }
                Spacer(Modifier.weight(1f))
                VerticalLevel(s.volume / 100f, enabled = true, icon = Icons.AutoMirrored.Filled.VolumeUp, color = Color.White, onChange = { act.volume((it * 100).toInt()) }, onLocked = {})
            }
            Spacer(Modifier.width(8.dp))
            TilePager(s, act, Modifier.width(126.dp).fillMaxHeight())
        }
    }
}

private data class QT(val label: String, val icon: ImageVector, val active: Boolean, val enabled: Boolean = true, val danger: Boolean = false, val onClick: () -> Unit)

@Composable
private fun TilePager(s: OverlayState, act: OverlayActions, modifier: Modifier) {
    val calls = s.session?.spec?.calls ?: s.settings.callMode
    val pages = listOf(
        listOf(
            QT("Screenshot", Icons.Default.Screenshot, false) { act.screenshot() },
            QT(if (s.recording) "Stop rec" else "Record", Icons.Default.FiberManualRecord, s.recording) { act.record() },
            QT(if (s.mic) "Mic on" else "Mic off", if (s.mic) Icons.Default.Mic else Icons.Default.MicOff, s.mic) { act.mic(!s.mic) },
            QT("${s.stats.refreshHz ?: "—"}Hz", rcIcon(R.drawable.ic_rc_monitor), false, enabled = s.secureGranted) { act.refresh() },
            QT("Auto light", Icons.Default.BrightnessAuto, s.autoBrightness, enabled = s.brightness != null) { act.autoBrightness(!s.autoBrightness) },
            QT("End", Icons.Default.PowerSettingsNew, false, danger = true) { act.endSession() },
        ),
        listOf(
            QT("Rotation", Icons.Default.ScreenLockRotation, s.rotationLocked) { act.rotation(!s.rotationLocked) },
            QT(when (calls) { CallMode.ALLOW -> "Calls on"; CallMode.SILENCE -> "Silence"; CallMode.DECLINE -> "No calls" }, Icons.Default.PhoneDisabled, calls != CallMode.ALLOW) {
                act.calls(CallMode.entries[(calls.ordinal + 1) % CallMode.entries.size])
            },
            QT("4D vibe", Icons.Default.Vibration, s.settings.haptics) { act.haptics(!s.settings.haptics) },
            QT("EQ ${s.settings.eq.title}", Icons.Default.Equalizer, s.settings.eq != EqPreset.OFF) { act.eq(EqPreset.entries[(s.settings.eq.ordinal + 1) % EqPreset.entries.size]) },
            QT("Smooth 120", rcIcon(R.drawable.ic_rc_monitor), s.settings.smooth120, enabled = s.secureGranted) { act.smooth120(!s.settings.smooth120) },
            QT("Tools", rcIcon(R.drawable.ic_rc_tools), false) { act.open("TOOLS") },
        ),
    )
    val pager = rememberPagerState { pages.size }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pages[page].chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { HudTile(it, Modifier.weight(1f)) } }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(pages.size) { i ->
                Box(Modifier.height(4.dp).width(if (i == pager.currentPage) 16.dp else 5.dp).clip(CircleShape).background(if (i == pager.currentPage) HudOrange else RC.Metal))
            }
        }
    }
}

/** Big italic readout with glowing pads; the number counts up when the menu opens. */
@Composable
private fun HudReadout(value: Float?, decimals: Int, unit: String, label: String, color: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) { if (value != null) anim.animateTo(value, tween(if (anim.value == 0f) 650 else 300, easing = FastOutSlowInEasing)) }
    val text = if (value == null) "—" else if (decimals == 0) "${anim.value.toInt()}" else "%.${decimals}f".format(anim.value)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 30.sp, color = Color.White), maxLines = 1, softWrap = false)
        Text(unit, style = RCType.label.copy(fontSize = 8.sp))
        Canvas(Modifier.width(112.dp).height(30.dp)) {
            for (i in 0..3) {
                val w = size.width * (1f - i * 0.18f); val h = size.height * (0.62f - i * 0.12f)
                val y = size.height * (0.15f + i * 0.17f)
                drawOval(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), Offset(size.width / 2, y + h / 2), w / 2), Offset((size.width - w) / 2, y), Size(w, h))
                drawOval(if (i % 2 == 0) color.copy(alpha = 0.95f - i * 0.2f) else HudOrange.copy(alpha = 0.7f), Offset((size.width - w) / 2, y), Size(w, h), style = Stroke(2f))
            }
        }
        Text("— $label —", style = RCType.label.copy(fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun ChevronChip(text: String, color: Color, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.height(30.dp).widthIn(min = 112.dp).clickable(onClick = onClick).drawBehind {
            val w = size.width; val h = size.height; val n = h * 0.45f
            val p = Path().apply { moveTo(n, 0f); lineTo(w - n, 0f); lineTo(w, h / 2); lineTo(w - n, h); lineTo(n, h); lineTo(0f, h / 2); close() }
            drawPath(p, Brush.verticalGradient(listOf(Color(0xE61C1F28), Color(0xE60E1015))))
            drawPath(p, color.copy(alpha = 0.25f), style = Stroke(7f))
            drawPath(p, color, style = Stroke(2.5f))
        }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)) }
        Text(text, style = RCType.strong.copy(fontSize = 13.sp, color = Color.White), maxLines = 1)
    }
}

/** Stacked-bar vertical slider (brightness / volume): drag or tap to set. */
@Composable
private fun VerticalLevel(value: Float, enabled: Boolean, icon: ImageVector, color: Color, onChange: (Float) -> Unit, onLocked: () -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.coerceIn(0f, 1f)) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.width(42.dp).height(96.dp)
                .pointerInput(enabled) {
                    if (!enabled) detectTapGestures { onLocked() }
                    else detectTapGestures { o -> v = (1f - o.y / size.height).coerceIn(0f, 1f); onChange(v) }
                }
                .pointerInput(enabled) {
                    if (enabled) detectVerticalDragGestures { change, _ ->
                        change.consume(); v = (1f - change.position.y / size.height).coerceIn(0f, 1f); onChange(v)
                    }
                }
        ) {
            val n = 12; val gap = size.height / n
            for (i in 0 until n) {
                val y = size.height - (i + 1) * gap + gap * 0.25f
                val on = enabled && (i + 1) / n.toFloat() <= v + 0.001f
                val wFrac = 0.55f + 0.45f * (i / (n - 1f))
                val c = if (on) (if (i >= n - 3) HudOrange else color) else Color(0x40FFFFFF)
                drawRoundRect(c, Offset(size.width * (1 - wFrac) / 2, y), Size(size.width * wFrac, gap * 0.5f), androidx.compose.ui.geometry.CornerRadius(3f))
            }
        }
        Spacer(Modifier.height(3.dp))
        Icon(icon, null, tint = if (enabled) color else RC.TextMute, modifier = Modifier.size(15.dp))
        if (!enabled) Text("Tap to allow", style = RCType.label.copy(fontSize = 6.sp, color = HudOrange))
    }
}

@Composable
private fun ToolCard(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 9.dp, bottomEnd = 2.dp, bottomStart = 9.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (active) Brush.verticalGradient(listOf(HudOrange.copy(alpha = 0.95f), RC.Red)) else Brush.verticalGradient(listOf(Color(0xFF2A2D37), Color(0xFF191B22))))
            .drawBehind { drawRect(Color(0x14FFFFFF), size = Size(size.width, size.height * 0.45f)) }
            .border(1.dp, if (active) HudOrange else RC.Stroke, shape)
            .clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        Text(label, style = RCType.bodyDim.copy(fontSize = 10.sp, color = if (active) Color.White else RC.TextDim), maxLines = 1)
    }
}

@Composable
private fun HudTile(t: QT, modifier: Modifier) {
    Column(modifier.clickable(onClick = t.onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(44.dp)
                .drawBehind { if (t.active) drawCircle(HudOrange.copy(alpha = 0.28f), size.minDimension * 0.75f) }
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        t.active -> Brush.verticalGradient(listOf(HudOrange, RC.Red))
                        t.danger -> Brush.verticalGradient(listOf(Color(0xFF3A0610), Color(0xFF1C0408)))
                        else -> Brush.verticalGradient(listOf(Color(0xFF30333E), Color(0xFF1C1E26)))
                    }
                )
                .drawBehind { drawRect(Color(0x18FFFFFF), size = Size(size.width, size.height * 0.45f)) }
                .border(1.dp, if (t.active) HudOrange else if (t.danger) RC.Red else RC.Stroke, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(t.icon, t.label, tint = if (!t.enabled) RC.TextMute else if (t.danger) RC.Red else Color.White, modifier = Modifier.size(20.dp)) }
        Text(t.label, style = RCType.bodyDim.copy(fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TopTitle(s: OverlayState) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val start = s.session?.startedAt ?: s.fx.sessionStart.takeIf { it > 0 }
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomEnd = 2.dp, bottomStart = 14.dp)
    Row(
        Modifier.clip(shape).background(Color(0xD90A0B10)).border(1.dp, Brush.horizontalGradient(listOf(RC.Red, RC.Stroke, RC.Red)), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RedcoreLogo(16.dp)
        Text((s.session?.label ?: "REDCORE").uppercase(), style = RCType.label.copy(fontSize = 9.sp, color = Color.White), maxLines = 1)
        if (start != null) {
            val e = (now - start).coerceAtLeast(0) / 1000
            Text("%d:%02d:%02d".format(e / 3600, (e / 60) % 60, e % 60), style = RCType.label.copy(fontSize = 9.sp, color = RC.Red))
        }
        if (s.fx.coolLevel != CoolLevel.OFF) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AcUnit, null, tint = if (s.fx.coolLevel == CoolLevel.HOT) HudOrange else CoolBlue, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
            Text(s.fx.coolLevel.title.substringBefore(" ·").uppercase(), style = RCType.label.copy(fontSize = 8.sp, color = if (s.fx.coolLevel == CoolLevel.HOT) HudOrange else CoolBlue))
        }
    }
}

@Composable
private fun BottomInfo(s: OverlayState) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = Date() } }
    val st = s.stats
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xCC0A0B10)).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Text("REDCORE", style = RCType.label.copy(fontSize = 8.sp, color = RC.Text, letterSpacing = 3.sp))
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), style = RCType.label.copy(fontSize = 8.sp, color = RC.Text))
            Text("▮ ${st.batteryPct ?: "—"}%", style = RCType.label.copy(fontSize = 8.sp, color = RC.Green))
            Text(s.settings.temp(st.batteryTempC), style = RCType.label.copy(fontSize = 8.sp, color = tempColor(st.batteryTempC)))
            Text("⇣ ${fmtRate(st.rxBps)}", style = RCType.label.copy(fontSize = 8.sp, color = RC.Text))
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(horizontal = 14.dp, vertical = 4.dp)) {
            Text(s.fx.coolNote?.let { "❄ $it" } ?: s.fx.fpsAdvice?.let { "⚠ $it" } ?: "Tap the game to return · swipe the tile grid for more", style = RCType.bodyDim.copy(fontSize = 11.sp, color = RC.Text), maxLines = 1)
        }
    }
}

// ============================================================================================
// Portrait fallback: compact centred panel
// ============================================================================================

@Composable
private fun CompactPanel(s: OverlayState, act: OverlayActions) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, null) { act.expand(false) },
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 22.dp, bottomEnd = 4.dp, bottomStart = 22.dp)
        Column(
            Modifier.padding(12.dp).widthIn(max = 520.dp).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF2141620), Color(0xF207080B))))
                .border(1.dp, Brush.linearGradient(listOf(RC.Red, RC.Stroke, RC.BlueDeep)), shape)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .verticalScroll(rememberScrollState()).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RedcoreLogo(22.dp); Spacer(Modifier.width(8.dp))
                Text("REDCORE TOOLKIT", style = RCType.title.copy(fontSize = 12.sp, letterSpacing = 2.sp), modifier = Modifier.weight(1f))
                RoundIcon(Icons.Default.Close, "Close") { act.expand(false) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GameMode.entries.forEach { m ->
                    val sel = (s.session?.mode ?: s.settings.globalMode) == m
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (sel) m.accent().copy(alpha = 0.25f) else Color.Transparent)
                            .border(1.dp, if (sel) m.accent() else RC.Stroke, RoundedCornerShape(6.dp)).clickable { act.mode(m) }.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(m.title.uppercase(), style = RCType.label.copy(fontSize = 8.sp, color = if (sel) Color.White else RC.TextDim)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { HudReadout(s.stats.peakCurMHz?.div(1000f), 2, "GHz", "CPU", RC.Red) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { HudReadout(s.stats.ramFraction * 100, 0, "%", "RAM", RC.Blue) }
            }
            Spacer(Modifier.height(8.dp))
            SegmentMeter(s.stats.clockLevel ?: 0f, Modifier.fillMaxWidth(), segments = 16)
            Spacer(Modifier.height(10.dp))
            val tiles = listOf(
                QT("Screenshot", Icons.Default.Screenshot, false) { act.screenshot() },
                QT(if (s.recording) "Stop rec" else "Record", Icons.Default.FiberManualRecord, s.recording) { act.record() },
                QT("Crosshair", Icons.Default.GpsFixed, s.settings.crosshair) { act.crosshair() },
                QT("Stopwatch", Icons.Default.Timer, s.stopwatch) { act.stopwatch() },
                QT("Hang-up", Icons.Default.NightsStay, false) { act.hangup() },
                QT("Rotation", Icons.Default.ScreenLockRotation, s.rotationLocked) { act.rotation(!s.rotationLocked) },
                QT("Cool", Icons.Default.AcUnit, s.settings.coolGuard) { act.cool(!s.settings.coolGuard) },
                QT("End", Icons.Default.PowerSettingsNew, false, danger = true) { act.endSession() },
            )
            tiles.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) { row.forEach { HudTile(it, Modifier.weight(1f)) } }
            }
            var b by remember(s.brightness) { mutableFloatStateOf((s.brightness ?: 50) / 100f) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BrightnessMedium, null, tint = RC.Amber, modifier = Modifier.size(18.dp))
                RedSlider(b, { b = it; act.brightness((it * 100).toInt().coerceAtLeast(1)) }, Modifier.weight(1f).height(28.dp), enabled = s.brightness != null)
            }
            Text("${fmtBytes(s.stats.ramUsed)} / ${fmtBytes(s.stats.ramTotal)} RAM · ${s.settings.temp(s.stats.batteryTempC)}", style = RCType.bodyDim.copy(fontSize = 11.sp))
        }
    }
}
