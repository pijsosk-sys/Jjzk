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
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.redcore.booster.data.temp
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
    history: List<LiveStats>,
    gpuName: String?,
    settings: com.redcore.booster.data.AppSettings,
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
    fx: com.redcore.booster.booster.EffectsState = com.redcore.booster.booster.EffectsState(),
    onCool: (Boolean) -> Unit = {},
    onCoolTarget: (Int) -> Unit = {},
    onSmooth: (Boolean) -> Unit = {},
    maxHz: Int? = null,
) {
    HudBackdrop {
        com.redcore.booster.ui.components.Panes(1f, 1f, left = {
            PerformanceRings(stats, mode, gpuName)
            Spacer(Modifier.height(12.dp))
            ModeSelector(mode, onMode)
            Spacer(Modifier.height(8.dp))
            CoolGuardPanel(settings, fx, stats, onCool, onCoolTarget)
            Spacer(Modifier.height(8.dp))
            Smooth120Panel(settings.smooth120, perms[Perm.SECURE_SETTINGS] == true, maxHz, stats.refreshHz, fx, onSmooth, onGrant)
            Spacer(Modifier.height(10.dp))
            if (session == null) GlowButton("Activate ${mode.title} mode", Modifier.fillMaxWidth(), Icons.Default.Bolt, onClick = onBoost)
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton("Re-apply", Modifier.weight(1f), Icons.Default.Bolt, onClick = onBoost)
                GlowButton("Restore", Modifier.weight(1f), Icons.Default.PowerSettingsNew, primary = false, onClick = onEnd)
            }
            Spacer(Modifier.height(12.dp))
            QuickStrip(stats, settings)
            Spacer(Modifier.height(12.dp))
            GlassPanel(Modifier.fillMaxWidth(), padding = 10.dp) {
                Row {
                    Text("CPU CLOCK LEVEL", style = RCType.label.copy(fontSize = 8.sp), modifier = Modifier.weight(1f))
                    Text("LAST ${history.size}s", style = RCType.label.copy(fontSize = 8.sp))
                }
                Spacer(Modifier.height(4.dp))
                com.redcore.booster.ui.components.LineChart(history.map { it.clockLevel?.times(100) }, Modifier.fillMaxWidth().height(70.dp), minY = 0f, maxY = 100f, gridLines = 2)
            }
        }, right = {
            SectionHeader("What ${mode.title} mode does")
            ModePlan(mode, perms, onGrant)
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
        PlanItem(Icons.Default.ScreenLockRotation, "Rotation lock", if (spec.rotationLock) "A tilt can't flip the game" else "Auto-rotate", spec.rotationLock, Perm.WRITE_SETTINGS, perms[Perm.WRITE_SETTINGS] == true),
        PlanItem(Icons.Default.SyncDisabled, "Pause auto-sync", if (spec.pauseSync) "No account sync during play" else "Sync keeps running", spec.pauseSync, null, true),
        PlanItem(Icons.Default.Wifi, "Low-latency Wi-Fi", if (spec.lowLatencyWifi) "Wi-Fi power saving off (supported phones)" else "Normal Wi-Fi", spec.lowLatencyWifi, null, true),
        PlanItem(Icons.Default.PhoneDisabled, "Calls", when (spec.calls) { com.redcore.booster.data.CallMode.DECLINE -> "Declined"; com.redcore.booster.data.CallMode.SILENCE -> "Silenced"; else -> "Your Game Tools choice" },
            spec.calls != null, if (spec.calls == com.redcore.booster.data.CallMode.DECLINE) Perm.CALL_SCREENING else Perm.DND,
            perms[if (spec.calls == com.redcore.booster.data.CallMode.DECLINE) Perm.CALL_SCREENING else Perm.DND] == true),
        PlanItem(Icons.Default.Animation, "Faster animations", if (spec.fastAnimations) "System animations at 0.5×" else "Normal", spec.fastAnimations, Perm.SECURE_SETTINGS, perms[Perm.SECURE_SETTINGS] == true),
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
                    it.perm == null -> Chip("Ready", RC.Green)
                    it.perm == Perm.SECURE_SETTINGS -> Chip("ADB only", RC.Amber)
                    else -> Box(Modifier.clip(RoundedCornerShape(4.dp)).background(RC.Amber.copy(alpha = 0.15f)).border(1.dp, RC.Amber, RoundedCornerShape(4.dp))) {
                        Text("GRANT", style = RCType.label.copy(fontSize = 8.sp, color = RC.Amber), modifier = Modifier.clickable { it.perm?.let(onGrant) }.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}


@Composable
private fun PerformanceRings(stats: LiveStats, mode: GameMode, gpuName: String?) {
    GlassPanel(Modifier.fillMaxWidth(), accent = true, padding = 12.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                com.redcore.booster.ui.components.RingGauge(
                    stats.clockLevel, stats.peakCurMHz?.let { "%.2f".format(it / 1000f) } ?: "—", "GHz", "CPU",
                    size = 128.dp, color = RC.Red, secondary = Color(0xFFFF8A1E)
                )
                Text(stats.clockLevel?.let { "${(it * 100).toInt()}% clock" } ?: "Clock restricted", style = RCType.bodyDim.copy(fontSize = 11.sp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                com.redcore.booster.ui.RedcoreLogo(34.dp)
                Spacer(Modifier.height(6.dp))
                Text("REDCORE", style = RCType.title.copy(fontSize = 11.sp, letterSpacing = 3.sp))
                Text(mode.title.uppercase(), style = RCType.label.copy(fontSize = 9.sp, color = mode.accent(), letterSpacing = 3.sp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val gpuLive = stats.gpuMHz != null
                com.redcore.booster.ui.components.RingGauge(
                    if (gpuLive) (stats.gpuLoad ?: 0) / 100f else null,
                    if (gpuLive) "${stats.gpuMHz}" else "—", if (gpuLive) "MHz" else "N/A", "GPU",
                    size = 128.dp, color = RC.Violet, secondary = RC.Red
                )
                Text(if (gpuLive) stats.gpuLoad?.let { "$it% load" } ?: "Live clock" else (gpuName ?: "Live data restricted"),
                    style = RCType.bodyDim.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(130.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun QuickStrip(stats: LiveStats, settings: com.redcore.booster.data.AppSettings) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(RC.Panel).border(1.dp, RC.Stroke, shape).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(
            Triple(com.redcore.booster.R.drawable.ic_rc_thermo, settings.temp(stats.batteryTempC), "Temp"),
            Triple(com.redcore.booster.R.drawable.ic_rc_cpu, "${(stats.ramFraction * 100).toInt()}%", "RAM"),
            Triple(com.redcore.booster.R.drawable.ic_rc_battery, stats.batteryPct?.let { "$it%" } ?: "—", "Battery"),
            Triple(com.redcore.booster.R.drawable.ic_rc_monitor, stats.refreshHz?.let { "${it}Hz" } ?: "—", "Display"),
        ).forEach { (icon, v, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(com.redcore.booster.ui.rcIcon(icon), null, tint = RC.Red, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(v, style = RCType.strong.copy(fontSize = 14.sp))
                    Text(l.uppercase(), style = RCType.label.copy(fontSize = 7.sp))
                }
            }
        }
    }
}


@Composable
private fun CoolGuardPanel(
    settings: com.redcore.booster.data.AppSettings,
    fx: com.redcore.booster.booster.EffectsState,
    stats: LiveStats,
    onCool: (Boolean) -> Unit,
    onTarget: (Int) -> Unit,
) {
    val on = settings.coolGuard
    GlassPanel(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AcUnit, null, tint = if (on) CoolBlue else RC.TextMute, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("COOL GUARD", style = RCType.title.copy(fontSize = 13.sp))
                Text("Works with every mode — even Limit", style = RCType.bodyDim.copy(fontSize = 12.sp))
            }
            com.redcore.booster.ui.components.RedSwitch(on, onCool, onBrush = Brush.horizontalGradient(listOf(Color(0xFF1B6FD6), CoolBlue)))
        }
        if (on) {
            Spacer(Modifier.height(8.dp))
            var t by androidx.compose.runtime.remember(settings.coolTarget) { androidx.compose.runtime.mutableFloatStateOf(settings.coolTarget.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TARGET ${t.toInt()}°C", style = RCType.label.copy(fontSize = 9.sp, color = CoolBlue), modifier = Modifier.width(96.dp))
                com.redcore.booster.ui.components.RedSlider((t - 37f) / 9f, { t = 37f + it * 9f }, Modifier.weight(1f), onDone = { onTarget(t.toInt()) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(fx.coolLevel.title.substringBefore(" ·"), when (fx.coolLevel) {
                    com.redcore.booster.booster.CoolLevel.HOT -> Color(0xFFFF7A1A); com.redcore.booster.booster.CoolLevel.WARM -> RC.Amber; else -> CoolBlue
                })
                Text("Battery ${stats.batteryTempC?.let { "%.1f°C".format(it) } ?: "—"}", style = RCType.bodyDim.copy(fontSize = 12.sp))
            }
            Text(
                fx.coolNote ?: "At ${settings.coolTarget}°C it caps brightness at 70 %, steps the refresh rate down and closes background apps; at ${settings.coolTarget + 3}°C it goes further and warns you. It can't throttle the game itself.",
                style = RCType.bodyDim.copy(fontSize = 11.sp)
            )
        }
    }
}


/**
 * Smooth 120: holds the display at its highest refresh rate during games. It does not invent frames —
 * real frame generation needs a dedicated display chip — so it also tells the user when a game itself is capped at 60.
 */
@Composable
private fun Smooth120Panel(
    on: Boolean, adb: Boolean, maxHz: Int?, nowHz: Int?, fx: com.redcore.booster.booster.EffectsState,
    onToggle: (Boolean) -> Unit, onGrant: (Perm) -> Unit,
) {
    val accent = Color(0xFF2EE59D)
    GlassPanel(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = if (on) 0.18f else 0.06f)), contentAlignment = Alignment.Center) {
                Text("${maxHz ?: 120}", style = RCType.label.copy(fontSize = 10.sp, color = if (on) accent else RC.TextMute))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("SMOOTH ${maxHz ?: 120}", style = RCType.title.copy(fontSize = 13.sp))
                Text("Holds the screen at ${maxHz ?: 120} Hz during games · now ${nowHz ?: "—"} Hz", style = RCType.bodyDim.copy(fontSize = 12.sp))
            }
            com.redcore.booster.ui.components.RedSwitch(on, onToggle, onBrush = Brush.horizontalGradient(listOf(Color(0xFF119E77), accent)))
        }
        Spacer(Modifier.height(6.dp))
        if (maxHz != null && maxHz < 90) Text("This screen tops out at $maxHz Hz, so there is no higher refresh rate to unlock.", style = RCType.bodyDim.copy(fontSize = 11.sp, color = RC.Amber))
        else if (!adb) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Needs the one-time ADB grant (Settings › Permissions).", style = RCType.bodyDim.copy(fontSize = 11.sp, color = RC.Amber), modifier = Modifier.weight(1f))
            GlowButton("How", primary = false) { onGrant(Perm.SECURE_SETTINGS) }
        }
        fx.fpsAdvice?.let { Text("⚠ $it", style = RCType.bodyDim.copy(fontSize = 11.sp, color = RC.Amber)) }
        Text("Games that offer 90/120 FPS can then run at it. It can't create extra frames for games limited to 60 FPS — that \"frame insertion\" needs a dedicated display chip only some gaming phones have.",
            style = RCType.bodyDim.copy(fontSize = 11.sp))
    }
}
