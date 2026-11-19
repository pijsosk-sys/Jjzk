package com.redcore.booster.ui.screens

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.redcore.booster.data.LibraryGame
import com.redcore.booster.data.PlaySummary
import com.redcore.booster.data.fmtDuration
import com.redcore.booster.ui.components.Chip
import com.redcore.booster.ui.components.EmptyState
import com.redcore.booster.ui.components.GameIcon
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.LoadingSpinner
import com.redcore.booster.ui.components.NoticeCard
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.components.Segmented
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType
import kotlin.math.absoluteValue

@Composable
fun GameSpaceScreen(
    library: List<LibraryGame>?,
    today: PlaySummary?,
    week: PlaySummary?,
    usageGranted: Boolean,
    onPlay: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onGrantUsage: () -> Unit,
    session: com.redcore.booster.booster.GameSession? = null,
) {
    HudBackdrop {
        when {
            library == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingSpinner() }
            library.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    Icons.Default.SportsEsports, "Your game space is empty",
                    "REDCORE adds apps that identify themselves as games. Some games don't, so you can also add any app manually.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlowButton("Scan for games", icon = Icons.Default.Search, onClick = onScan)
                        GlowButton("Add manually", icon = Icons.Default.Add, primary = false, onClick = onAdd)
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (session != null && session.pkg == null) BoostedBanner(session)
                GameStage(library, onPlay, onOpen, onAdd)
                val wide = com.redcore.booster.ui.components.LocalWide.current
                val summary: @Composable () -> Unit = {
                    SectionHeader("Play summary")
                    if (!usageGranted) {
                        NoticeCard("Usage access needed", "Play-time statistics come from Android's Usage Access. REDCORE never sends them anywhere.") {
                            GlowButton("Grant", onClick = onGrantUsage)
                        }
                    } else PlaySummaryPanel(library, today, week)
                }
                val lib: @Composable () -> Unit = {
                    SectionHeader("Library · ${library.size}", trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("SCAN", style = RCType.label.copy(color = RC.TextDim, fontSize = 9.sp), modifier = Modifier.clickable(onClick = onScan).padding(6.dp))
                            Text("+ ADD", style = RCType.label.copy(color = RC.Red, fontSize = 9.sp), modifier = Modifier.clickable(onClick = onAdd).padding(6.dp))
                        }
                    })
                    library.forEach { g ->
                        LibraryRow(g, today?.perGame?.firstOrNull { it.pkg == g.profile.pkg }?.foregroundMs, onOpen, onPlay)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (wide) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) { summary() }
                    Column(Modifier.weight(1f)) { lib() }
                } else Column(Modifier.padding(horizontal = 16.dp)) { summary(); lib() }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun GameStage(library: List<LibraryGame>, onPlay: (String) -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val pager = rememberPagerState(initialPage = if (library.size >= 2) 1 else 0) { library.size + 1 }
    val t = rememberInfiniteTransition(label = "stage")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "r")
    val wide = com.redcore.booster.ui.components.LocalWide.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(if (wide) 300.dp else 330.dp)) {
        val cardW = if (wide) 270.dp else maxWidth - 144.dp
        val sidePad = (maxWidth - cardW) / 2
        // neon platform
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height * 0.86f
            drawOval(Brush.radialGradient(listOf(RC.Blue.copy(alpha = 0.35f), Color.Transparent), Offset(cx, cy), size.width * 0.55f),
                Offset(cx - size.width * 0.55f, cy - 60f), Size(size.width * 1.1f, 120f))
            listOf(0.95f to RC.Blue, 0.80f to RC.Violet, 0.62f to RC.Red, 0.45f to RC.Blue).forEachIndexed { i, (k, c) ->
                val w = size.width * k; val h = w * 0.17f
                drawOval(c.copy(alpha = 0.75f - i * 0.12f), Offset(cx - w / 2, cy - h / 2), Size(w, h), style = Stroke(if (i == 0) 5f else 2.5f))
            }
            // radial "bars" around the platform edge
            val w = size.width * 0.88f; val h = w * 0.17f
            for (k in 0 until 90) {
                val a = Math.toRadians((k * 4 + rot).toDouble())
                val x = cx + (w / 2) * kotlin.math.cos(a).toFloat(); val y = cy + (h / 2) * kotlin.math.sin(a).toFloat()
                if (kotlin.math.sin(a) > -0.2) drawLine(RC.Blue.copy(alpha = 0.5f), Offset(x, y), Offset(x, y + 10f), 2f)
            }
        }
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = sidePad),
            pageSpacing = if (wide) 18.dp else 4.dp,
            modifier = Modifier.fillMaxWidth().height(if (wide) 250.dp else 290.dp).padding(top = if (wide) 12.dp else 16.dp)
        ) { page ->
            val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
            val abs = off.absoluteValue.coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = lerp(1f, 0.82f, abs); scaleY = lerp(1f, 0.82f, abs)
                    alpha = lerp(1f, 0.55f, abs)
                    rotationY = off.coerceIn(-1f, 1f) * (if (wide) 22f else 28f)
                    cameraDistance = 14 * density
                }
            ) {
                if (page == library.size) AddCard(onAdd) else GameCard(library[page], { onPlay(library[page].profile.pkg) }, { onOpen(library[page].profile.pkg) })
            }
        }
        // page indicator
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(library.size + 1) { i ->
                val sel = i == pager.currentPage
                Box(Modifier.height(4.dp).width(if (sel) 18.dp else 6.dp).clip(CircleShape).background(if (sel) RC.Red else RC.Metal))
            }
        }
    }
}

@Composable
private fun GameCard(g: LibraryGame, onPlay: () -> Unit, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomEnd = 4.dp, bottomStart = 24.dp)
    val accent = g.profile.mode.accent()
    Column(
        Modifier.fillMaxSize().clip(shape)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), Color(0xFF12131A), Color(0xFF0A0B10))))
            .border(1.5.dp, Brush.verticalGradient(listOf(accent, RC.Stroke, accent.copy(alpha = 0.6f))), shape)
            .clickable(onClick = onOpen).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Chip(g.profile.mode.title, accent)
            if (!g.installed) Chip("Not installed", RC.Amber)
        }
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            val wide = com.redcore.booster.ui.components.LocalWide.current
            Box(Modifier.size(if (wide) 96.dp else 118.dp).clip(CircleShape).background(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent))))
            GameIcon(g.profile.pkg, g.label, if (wide) 66.dp else 88.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(g.label, style = RCType.title.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        GlowButton("Play", Modifier.fillMaxWidth(), icon = Icons.Default.PlayArrow, enabled = g.installed, onClick = onPlay)
    }
}

@Composable
private fun AddCard(onAdd: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomEnd = 4.dp, bottomStart = 24.dp)
    Column(
        Modifier.fillMaxSize().clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF1A0B14), Color(0xFF0A0B10))))
            .border(1.dp, RC.Violet.copy(alpha = 0.6f), shape).clickable(onClick = onAdd),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(70.dp).clip(CircleShape).border(2.dp, RC.Violet, CircleShape).background(RC.Violet.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, null, tint = RC.Violet, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("ADD GAMES", style = RCType.title.copy(fontSize = 14.sp))
        Text("Pick any installed app", style = RCType.bodyDim)
    }
}

@Composable
private fun PlaySummaryPanel(library: List<LibraryGame>, today: PlaySummary?, week: PlaySummary?) {
    var tab by remember { mutableIntStateOf(0) }
    val s = if (tab == 0) today else week
    GlassPanel(Modifier.fillMaxWidth(), accent = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (tab == 0) "TODAY" else "LAST 7 DAYS", style = RCType.label.copy(color = RC.Text), modifier = Modifier.weight(1f))
            Segmented(listOf("Today", "Week"), tab, { tab = it }, Modifier.width(150.dp))
        }
        Spacer(Modifier.height(14.dp))
        if (s == null) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { LoadingSpinner() }
        } else {
            val total = s.totalMs
            val top = s.perGame.firstOrNull()
            val topGame = top?.let { t -> library.firstOrNull { it.profile.pkg == t.pkg } }
            val dailyAvg = if (tab == 0) total else total / 7
            val (word, color) = when {
                dailyAvg < 90 * 60_000L -> "Healthy" to RC.Green
                dailyAvg < 180 * 60_000L -> "Moderate" to RC.Amber
                else -> "Take a break" to RC.Red
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                SummaryRing("Total", Modifier.weight(1f), RC.Red) {
                    Text(fmtDuration(total), style = RCType.number.copy(fontSize = 15.sp), maxLines = 1)
                    Text("played", style = RCType.label.copy(fontSize = 7.sp))
                }
                SummaryRing("Status", Modifier.weight(1f), color, big = true) {
                    Text(word, style = RCType.strong.copy(fontSize = if (word.length > 8) 13.sp else 16.sp, color = Color.White), textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 10.dp))
                }
                SummaryRing("Longest", Modifier.weight(1f), RC.Blue) {
                    if (topGame != null) {
                        GameIcon(topGame.profile.pkg, topGame.label, 28.dp)
                        Text(fmtDuration(top.foregroundMs), style = RCType.label.copy(fontSize = 8.sp, color = RC.Text))
                    } else Text("—", style = RCType.number)
                }
            }
            if (tab == 1 && s.dailyMs.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                WeekBars(s.dailyMs)
            }
            if (s.perGame.isNotEmpty() && total > 0) {
                Spacer(Modifier.height(12.dp))
                Text("SHARE OF PLAY TIME", style = RCType.label.copy(fontSize = 8.sp))
                val palette = listOf(RC.Red, RC.Violet, RC.Blue, RC.Green, RC.Amber)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(RC.Black)) {
                    s.perGame.take(5).forEachIndexed { i, g ->
                        val w = g.foregroundMs.toFloat() / total
                        if (w > 0.01f) Box(Modifier.weight(w).fillMaxHeight().background(palette[i % palette.size]))
                    }
                }
                s.perGame.take(5).forEachIndexed { i, g ->
                    val name = library.firstOrNull { it.profile.pkg == g.pkg }?.label ?: g.pkg
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(palette[i % palette.size]))
                        Spacer(Modifier.width(8.dp))
                        Text("${(g.foregroundMs * 100 / total)}%", style = RCType.strong.copy(fontSize = 13.sp), modifier = Modifier.width(44.dp))
                        Text(name, style = RCType.body.copy(fontSize = 13.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fmtDuration(g.foregroundMs), style = RCType.bodyDim.copy(fontSize = 12.sp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("• Moderate play is good for body and mind •", style = RCType.bodyDim.copy(fontSize = 12.sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SummaryRing(label: String, modifier: Modifier, color: Color, big: Boolean = false, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = RCType.label.copy(fontSize = 8.sp))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.size(if (big) 96.dp else 84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)), r)
                drawCircle(RC.Black, r * 0.82f)
                drawCircle(color.copy(alpha = 0.9f), r * 0.82f, style = Stroke(3f))
                drawCircle(RC.Stroke, r * 0.95f, style = Stroke(1.5f))
                for (i in 0 until 40) {
                    val a = Math.toRadians(i * 9.0)
                    val c = center
                    drawLine(color.copy(alpha = if (i % 5 == 0) 0.8f else 0.25f),
                        Offset(c.x + kotlin.math.cos(a).toFloat() * r * 0.86f, c.y + kotlin.math.sin(a).toFloat() * r * 0.86f),
                        Offset(c.x + kotlin.math.cos(a).toFloat() * r * 0.92f, c.y + kotlin.math.sin(a).toFloat() * r * 0.92f), 2f)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
        }
    }
}

@Composable
private fun WeekBars(daily: List<Long>) {
    val max = (daily.maxOrNull() ?: 0L).coerceAtLeast(1)
    val days = remember {
        val cal = java.util.Calendar.getInstance()
        (6 downTo 0).map { d ->
            val c = cal.clone() as java.util.Calendar; c.add(java.util.Calendar.DAY_OF_YEAR, -d)
            java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(c.time).take(2).uppercase()
        }
    }
    Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        daily.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / max * 0.8f).coerceAtLeast(0.03f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(Brush.verticalGradient(listOf(if (i == 6) RC.Red else RC.RedDeep, RC.RedDark)))
                )
                Spacer(Modifier.height(4.dp))
                Text(days[i], style = RCType.label.copy(fontSize = 7.sp, color = if (i == 6) RC.Text else RC.TextMute))
            }
        }
    }
}

@Composable
private fun LibraryRow(g: LibraryGame, todayMs: Long?, onOpen: (String) -> Unit, onPlay: (String) -> Unit) {
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomEnd = 2.dp, bottomStart = 14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(RC.panelGradient).border(1.dp, RC.Stroke, shape)
            .clickable { onOpen(g.profile.pkg) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameIcon(g.profile.pkg, g.label, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(g.label, style = RCType.strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(g.profile.mode.title, g.profile.mode.accent())
                Spacer(Modifier.width(8.dp))
                Text(if (!g.installed) "Not installed" else todayMs?.let { "Today ${fmtDuration(it)}" } ?: "Not played today", style = RCType.bodyDim.copy(fontSize = 12.sp))
            }
        }
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(if (g.installed) RC.redGradient else Brush.linearGradient(listOf(RC.PanelHi, RC.PanelHi)))
                .clickable(enabled = g.installed) { onPlay(g.profile.pkg) },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.PlayArrow, "Play", tint = Color.White) }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, null, tint = RC.TextMute)
    }
}


@Composable
private fun BoostedBanner(s: com.redcore.booster.booster.GameSession) {
    val accent = s.mode.accent()
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomEnd = 2.dp, bottomStart = 14.dp)
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.25f), RC.Panel)))
            .border(1.dp, accent, shape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(s.mode.icon(), null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("BOOSTED · ${s.mode.title.uppercase()} MODE", style = RCType.label.copy(fontSize = 10.sp, color = Color.White))
            Text("${s.results.count { it.state == com.redcore.booster.booster.ActionState.APPLIED }} optimizations active — pick a game and press Play", style = RCType.bodyDim.copy(fontSize = 12.sp))
        }
        com.redcore.booster.ui.components.PulsingDot(accent)
    }
}
