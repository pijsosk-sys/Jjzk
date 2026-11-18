package com.redcore.booster.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import com.redcore.booster.ui.components.LocalWide
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.booster.ActionState
import com.redcore.booster.booster.GameSession
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.PulsingDot
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

/** The REDCORE hexagon mark, drawn in code so it stays crisp at any size. */
@Composable
fun RedcoreLogo(size: androidx.compose.ui.unit.Dp = 30.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val hex = Path().apply {
            moveTo(w * 0.5f, 0f); lineTo(w, h * 0.25f); lineTo(w, h * 0.75f); lineTo(w * 0.5f, h); lineTo(0f, h * 0.75f); lineTo(0f, h * 0.25f); close()
        }
        drawPath(hex, Brush.linearGradient(listOf(RC.RedDark, Color.Black)))
        drawPath(hex, RC.Red, style = Stroke(w * 0.06f))
        drawRect(RC.Red, Offset(w * 0.30f, h * 0.30f), androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.42f))
        val blade = Path().apply { moveTo(w * 0.48f, h * 0.40f); lineTo(w * 0.62f, h * 0.27f); lineTo(w * 0.62f, h * 0.62f); lineTo(w * 0.48f, h * 0.75f); close() }
        drawPath(blade, RC.Red)
        val blade2 = Path().apply { moveTo(w * 0.68f, h * 0.42f); lineTo(w * 0.75f, h * 0.36f); lineTo(w * 0.75f, h * 0.60f); lineTo(w * 0.68f, h * 0.66f); close() }
        drawPath(blade2, Color(0xFFFF6B7D))
    }
}

@Composable
fun TopHeader(title: String, canBack: Boolean, onBack: () -> Unit, onSettings: () -> Unit, session: GameSession?, onSession: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).drawBehind {
            drawLine(Brush.horizontalGradient(listOf(RC.Red, RC.Stroke, Color.Transparent)), Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
        }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canBack) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = RC.Text)
            }
            Spacer(Modifier.width(6.dp))
            Text(title.uppercase(), style = RCType.title.copy(fontSize = 15.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            RedcoreLogo(28.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("REDCORE", style = RCType.title.copy(fontSize = 18.sp, letterSpacing = 3.sp))
                Text(if (title == "Home") "GAMING BOOSTER" else title.uppercase(), style = RCType.label.copy(fontSize = 8.sp, color = RC.Red, letterSpacing = 2.sp))
            }
        }
        if (session != null) {
            Row(
                Modifier.clip(RoundedCornerShape(6.dp)).background(RC.RedDark).border(1.dp, RC.Red, RoundedCornerShape(6.dp))
                    .clickable(onClick = onSession).padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PulsingDot(size = 6.dp)
                Spacer(Modifier.width(6.dp))
                Text(session.mode.title.uppercase(), style = RCType.label.copy(fontSize = 9.sp, color = Color.White))
            }
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Settings, "Settings", tint = RC.TextDim)
        }
    }
}

@Composable
fun BottomBar(current: Dest, onSelect: (Dest) -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF0E0F14), RC.Black)))
            .drawBehind {
                drawLine(Brush.horizontalGradient(listOf(Color.Transparent, RC.Red.copy(alpha = 0.7f), Color.Transparent)), Offset(0f, 0f), Offset(size.width, 0f), 2f)
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Dest.bottom.forEach { d ->
                val sel = d == current
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { onSelect(d) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (sel) Box(
                            Modifier.size(width = 44.dp, height = 28.dp).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomEnd = 2.dp, bottomStart = 10.dp))
                                .background(RC.redGradient)
                        )
                        Icon(d.vector(), d.title, tint = if (sel) Color.White else RC.TextMute, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(d.title.uppercase(), style = RCType.label.copy(fontSize = 7.5.sp, color = if (sel) RC.Text else RC.TextMute), maxLines = 1)
                }
            }
        }
    }
}

/** Left rail used in landscape — mirrors a gaming phone's "control center" layout. */
@Composable
fun SideRail(current: Dest, onSelect: (Dest) -> Unit) {
    Column(
        Modifier.width(158.dp).fillMaxHeight().background(Color(0xFF0D0E13))
            .drawBehind { drawLine(RC.Stroke, Offset(size.width - 1, 0f), Offset(size.width - 1, size.height), 1f) }
            .verticalScroll(rememberScrollState()).padding(vertical = 6.dp)
    ) {
        Dest.rail.forEach { d ->
            val sel = d == current
            Row(
                Modifier.fillMaxWidth().height(38.dp).clickable { onSelect(d) }
                    .background(if (sel) RC.railSelected else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .drawBehind {
                        if (sel) {
                            // notch pointing into the content, like a tab indicator
                            val p = Path().apply {
                                moveTo(size.width, size.height / 2 - 7f); lineTo(size.width - 8f, size.height / 2); lineTo(size.width, size.height / 2 + 7f); close()
                            }
                            drawPath(p, RC.Black)
                        }
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(d.vector(), null, tint = if (sel) Color.White else RC.TextMute, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(10.dp))
                Text(d.title, style = RCType.body.copy(fontSize = 14.sp, color = if (sel) Color.White else RC.TextDim), maxLines = 1)
            }
        }
    }
}

/** Landscape title bar: logo block above the rail, angled divider, page title, session chip, settings. */
@Composable
fun WideHeader(title: String, canBack: Boolean, onBack: () -> Unit, onSettings: () -> Unit, session: GameSession?, onSession: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).background(Color(0xFF101118)).drawBehind {
            drawLine(Brush.horizontalGradient(listOf(RC.Red, RC.Stroke, Color.Transparent)), Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
            // angled divider after the logo block
            val x = 158.dp.toPx()
            val p = Path().apply { moveTo(x - 10f, 0f); lineTo(x + 14f, 0f); lineTo(x - 4f, size.height); lineTo(x - 28f, size.height); close() }
            drawPath(p, RC.Stroke)
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.width(158.dp).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            RedcoreLogo(24.dp)
            Spacer(Modifier.width(8.dp))
            Text("REDCORE", style = RCType.title.copy(fontSize = 13.sp, letterSpacing = 2.sp))
        }
        Spacer(Modifier.width(14.dp))
        if (canBack) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = RC.Text, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(title, style = RCType.strong.copy(fontSize = 16.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (session != null) {
            Row(
                Modifier.clip(RoundedCornerShape(6.dp)).background(RC.RedDark).border(1.dp, RC.Red, RoundedCornerShape(6.dp))
                    .clickable(onClick = onSession).padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PulsingDot(size = 6.dp)
                Spacer(Modifier.width(6.dp))
                Text("${session.mode.title.uppercase()} · ${session.label}", style = RCType.label.copy(fontSize = 9.sp, color = Color.White), maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Settings, "Settings", tint = RC.TextDim)
        }
        Spacer(Modifier.width(8.dp))
    }
}

/** Adaptive scaffold: bottom bar on phones in portrait, side rail when wide. */
@Composable
fun AppScaffold(
    route: Route,
    stackDepth: Int,
    title: String,
    session: GameSession?,
    onSelect: (Dest) -> Unit,
    onBack: () -> Unit,
    onSession: () -> Unit,
    overlay: @Composable () -> Unit = {},
    content: @Composable (Route) -> Unit,
) {
    val root = route.dest
    val tabDest = if (root in Dest.bottom) root else Dest.HOME
    BoxWithConstraints(Modifier.fillMaxSize().background(RC.Black)) {
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp
        val page: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 8 }) togetherWith
                            (fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { -it / 12 })
                    },
                    label = "page"
                ) { r -> CompositionLocalProvider(LocalWide provides wide) { content(r) } }
            }
        }
        if (wide) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                WideHeader(title, stackDepth > 1, onBack, { onSelect(Dest.SETTINGS) }, session, onSession)
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    SideRail(if (root == Dest.GAME_DETAIL || root == Dest.ADD_GAMES) Dest.GAMES else root, onSelect)
                    page(Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    TopHeader(title, stackDepth > 1, onBack, { onSelect(Dest.SETTINGS) }, session, onSession)
                }
                page(Modifier.weight(1f).fillMaxWidth())
                BottomBar(tabDest, onSelect)
            }
        }
        overlay()
    }
}

/** Bottom sheet listing what each booster action actually did. */
@Composable
fun ResultsPanel(sheet: ResultSheet?, onDismiss: () -> Unit) {
    AnimatedVisibility(sheet != null, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
    }
    AnimatedVisibility(
        sheet != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val s = sheet ?: return@Box
            Column(
                Modifier.widthIn(max = 620.dp).fillMaxWidth().heightIn(max = 400.dp).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A1C24), RC.Black)))
                    .border(1.dp, Brush.verticalGradient(listOf(RC.Red, Color.Transparent)), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.title.uppercase(), style = RCType.title.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Close, "Close", tint = RC.TextDim, modifier = Modifier.clickable(onClick = onDismiss))
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                s.results.forEach { r ->
                    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                        val (icon, color) = when (r.state) {
                            ActionState.APPLIED -> Icons.Default.CheckCircle to RC.Green
                            ActionState.SKIPPED -> Icons.Default.RemoveCircleOutline to RC.TextMute
                            ActionState.BLOCKED -> Icons.Default.WarningAmber to RC.Amber
                        }
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(r.name, style = RCType.strong.copy(fontSize = 15.sp))
                            Text(r.detail, style = RCType.bodyDim)
                        }
                    }
                }
                }
                Spacer(Modifier.height(14.dp))
                GlowButton("Got it", Modifier.fillMaxWidth(), onClick = onDismiss)
            }
        }
    }
}
