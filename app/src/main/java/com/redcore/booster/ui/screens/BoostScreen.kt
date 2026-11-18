package com.redcore.booster.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.booster.ActionState
import com.redcore.booster.booster.GameSession
import com.redcore.booster.booster.Perm
import com.redcore.booster.data.GameMode
import com.redcore.booster.data.Insight
import com.redcore.booster.data.LiveStats
import com.redcore.booster.data.ModeSpec
import com.redcore.booster.data.RefreshTarget
import com.redcore.booster.data.fmtBytes
import com.redcore.booster.ui.components.Chip
import com.redcore.booster.ui.components.Divider
import com.redcore.booster.ui.components.GameIcon
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.InsightList
import com.redcore.booster.ui.components.LevelBar
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

data class BgApp(val pkg: String, val label: String, val lastUsedAgo: String)

@Composable
fun BoostScreen(
    stats: LiveStats,
    mode: GameMode,
    session: GameSession?,
    perms: Map<Perm, Boolean>,
    background: List<BgApp>?,
    insights: List<Insight>,
    onMode: (GameMode) -> Unit,
    onBoost: () -> Unit,
    onEnd: () -> Unit,
    onClean: () -> Unit,
    onGrant: (Perm) -> Unit,
) {
    HudBackdrop {
        com.redcore.booster.ui.components.Panes(1f, 1f, left = {
            ModeSelector(mode, onMode)
            Spacer(Modifier.height(12.dp))
            ModePlan(mode, perms, onGrant)
            Spacer(Modifier.height(12.dp))
            if (session == null) GlowButton("Activate ${mode.title} mode", Modifier.fillMaxWidth(), Icons.Default.Bolt, onClick = onBoost)
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton("Re-apply", Modifier.weight(1f), Icons.Default.Bolt, onClick = onBoost)
                GlowButton("Restore", Modifier.weight(1f), Icons.Default.PowerSettingsNew, primary = false, onClick = onEnd)
            }

            if (session != null) {
                SectionHeader("Active session")
                GlassPanel(Modifier.fillMaxWidth(), accent = true) {
                    Text(session.label, style = RCType.strong)
                    Text("${session.mode.title} mode", style = RCType.bodyDim)
                    Spacer(Modifier.height(6.dp))
                    session.results.forEach { r ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Chip(
                                when (r.state) { ActionState.APPLIED -> "Done"; ActionState.SKIPPED -> "Skip"; ActionState.BLOCKED -> "Blocked" },
                                when (r.state) { ActionState.APPLIED -> RC.Green; ActionState.SKIPPED -> RC.TextMute; ActionState.BLOCKED -> RC.Amber },
                                Modifier.width(78.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(r.name, style = RCType.body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

        }, right = {
            SectionHeader("Memory")
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(stats.ramFraction * 100).toInt()}%", style = RCType.number.copy(fontSize = 26.sp))
                    Spacer(Modifier.width(8.dp))
                    Text("${fmtBytes(stats.ramUsed)} of ${fmtBytes(stats.ramTotal)} in use", style = RCType.bodyDim, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                LevelBar(stats.ramFraction, color = if (stats.ramFraction > 0.85f) RC.Amber else RC.Red)
                Spacer(Modifier.height(12.dp))
                Text("RECENTLY USED APPS", style = RCType.label.copy(fontSize = 9.sp))
                Spacer(Modifier.height(4.dp))
                when {
                    background == null -> Text("Grant Usage access to see which apps ran recently.", style = RCType.bodyDim)
                    background.isEmpty() -> Text("No other apps used in the last 3 hours.", style = RCType.bodyDim)
                    else -> background.take(6).forEach { a ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            GameIcon(a.pkg, a.label, 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(a.label, style = RCType.body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(a.lastUsedAgo, style = RCType.bodyDim.copy(fontSize = 12.sp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (background == null) GlowButton("Grant usage access", Modifier.fillMaxWidth(), primary = false) { onGrant(Perm.USAGE) }
                else GlowButton("Close background apps", Modifier.fillMaxWidth(), Icons.Default.AutoDelete, primary = false, enabled = background.isNotEmpty(), onClick = onClean)
                Spacer(Modifier.height(6.dp))
                Text("Uses Android's killBackgroundProcesses. Android decides what actually closes and may restart important services.", style = RCType.bodyDim.copy(fontSize = 11.sp))
            }

            SectionHeader("Recommendations")
            InsightList(insights)

            SectionHeader("What Android doesn't allow")
            GlassPanel(Modifier.fillMaxWidth()) {
                listOf(
                    "CPU / GPU overclocking" to "Clock speeds and governors are kernel-controlled. Changing them needs root.",
                    "Thermal limits" to "Throttling thresholds are set by the manufacturer. No app can safely raise them.",
                    "Touch sampling rate" to "Set by the touchscreen firmware; only the maker's own software can change it.",
                    "Other apps' FPS" to "Android doesn't expose another app's frame rate without root or ADB tools.",
                ).forEachIndexed { i, (t, d) ->
                    if (i > 0) Divider()
                    Row(Modifier.padding(vertical = 8.dp)) {
                        Icon(Icons.Default.Block, null, tint = RC.TextMute, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column { Text(t, style = RCType.strong.copy(fontSize = 14.sp)); Text(d, style = RCType.bodyDim) }
                    }
                }
            }
        })
    }
}

private data class PlanItem(val icon: ImageVector, val title: String, val detail: String, val active: Boolean, val perm: Perm?, val granted: Boolean)

@Composable
private fun ModePlan(mode: GameMode, perms: Map<Perm, Boolean>, onGrant: (Perm) -> Unit) {
    val spec = ModeSpec.of(mode)
    val items = listOf(
        PlanItem(Icons.Default.DoNotDisturbOn, "Do Not Disturb", if (spec.dnd) "Silence notifications" else "Notifications stay on", spec.dnd, Perm.DND, perms[Perm.DND] == true),
        PlanItem(Icons.Default.BrightnessMedium, "Brightness", spec.brightness?.let { "Set to $it %" } ?: "Unchanged", spec.brightness != null, Perm.WRITE_SETTINGS, perms[Perm.WRITE_SETTINGS] == true),
        PlanItem(Icons.Default.Refresh, "Refresh rate", when (spec.refresh) { RefreshTarget.HIGHEST -> "Highest supported"; RefreshTarget.LOWEST -> "Lowest supported"; else -> "System default" }, spec.refresh != RefreshTarget.DEFAULT, Perm.SECURE_SETTINGS, perms[Perm.SECURE_SETTINGS] == true),
        PlanItem(Icons.Default.AutoDelete, "Background apps", if (spec.cleanBackground) "Close recently used apps" else "Left running", spec.cleanBackground, Perm.USAGE, perms[Perm.USAGE] == true),
        PlanItem(Icons.Default.LightMode, "Keep screen awake", if (spec.keepScreenOn) "While toolkit is shown" else "Normal timeout", spec.keepScreenOn, Perm.OVERLAY, perms[Perm.OVERLAY] == true),
    )
    val accent = mode.accent()
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.14f), RC.Panel, Color(0xFF0E1015))))
            .border(1.dp, Brush.verticalGradient(listOf(accent, RC.Stroke)), shape).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(mode.icon(), null, tint = accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${mode.title.uppercase()} MODE", style = RCType.title.copy(fontSize = 15.sp))
                Text(mode.tagline, style = RCType.bodyDim)
            }
        }
        Spacer(Modifier.height(10.dp))
        items.forEach { it ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(if (it.active) accent.copy(alpha = 0.15f) else RC.PanelHi), contentAlignment = Alignment.Center) {
                    Icon(it.icon, null, tint = if (it.active) accent else RC.TextMute, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.title, style = RCType.strong.copy(fontSize = 14.sp, color = if (it.active) RC.Text else RC.TextDim))
                    Text(it.detail, style = RCType.bodyDim.copy(fontSize = 12.sp))
                }
                when {
                    !it.active -> Chip("Off", RC.TextMute)
                    it.granted -> Chip("Ready", RC.Green)
                    it.perm == Perm.SECURE_SETTINGS -> Chip("ADB only", RC.Amber)
                    else -> Box(Modifier.clip(RoundedCornerShape(4.dp)).background(RC.Amber.copy(alpha = 0.15f)).border(1.dp, RC.Amber, RoundedCornerShape(4.dp))) {
                        Text("GRANT", style = RCType.label.copy(fontSize = 8.sp, color = RC.Amber), modifier = Modifier.clickable { it.perm?.let(onGrant) }.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}
