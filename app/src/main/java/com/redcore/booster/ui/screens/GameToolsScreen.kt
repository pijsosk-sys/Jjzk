package com.redcore.booster.ui.screens

import android.os.Build
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import com.redcore.booster.booster.Perm
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.CallMode
import com.redcore.booster.data.CrossStyle
import com.redcore.booster.data.EqPreset
import com.redcore.booster.data.InstalledApp
import com.redcore.booster.data.NotifMode
import com.redcore.booster.data.SettingsRepository.K
import com.redcore.booster.overlay.CrosshairGlyph
import com.redcore.booster.overlay.crossColors
import com.redcore.booster.ui.components.Chip
import com.redcore.booster.ui.components.Divider
import com.redcore.booster.ui.components.GameIcon
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.Panes
import com.redcore.booster.ui.components.RedSlider
import com.redcore.booster.ui.components.RedSwitch
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.components.Segmented
import com.redcore.booster.ui.components.SettingRow
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

interface ToolsActions {
    fun <T> put(key: Preferences.Key<T>, value: T)
    fun haptics(on: Boolean)
    fun calls(m: CallMode)
    fun notif(m: NotifMode)
    fun grant(p: Perm)
    fun openApp(pkg: String)
    fun copyAdb()
}

@Composable
fun GameToolsScreen(s: AppSettings, perms: Map<Perm, Boolean>, vendorApps: List<InstalledApp>, act: ToolsActions) {
    HudBackdrop {
        Panes(left = {
            SectionHeader("In-game menu")
            GlassPanel(Modifier.fillMaxWidth()) {
                SettingRow("Swipe down from a top corner", "Opens the game menu over any game", icon = Icons.Default.Swipe,
                    trailing = { RedSwitch(s.swipeTop, { act.put(K.swipeTop, it) }) })
                Divider()
                SettingRow("Swipe in from a side edge", "Upper part of the left/right edge", icon = Icons.Default.Swipe,
                    trailing = { RedSwitch(s.swipeSide, { act.put(K.swipeSide, it) }) })
                Divider()
                SettingRow("Show swipe hints", "Faint red marks where you can swipe", trailing = { RedSwitch(s.handleHint, { act.put(K.hint, it) }) })
                Divider()
                SettingRow("Floating bubble", "Tap a red bubble instead of swiping", trailing = { RedSwitch(s.showBubble, { act.put(K.bubble, it) }) })
                Divider()
                SettingRow("Live stats bar", "CPU · RAM · temp · battery · ping · FPS", icon = Icons.Default.Speed, trailing = { RedSwitch(s.hudEnabled, { act.put(K.hud, it) }) })
            }

            SectionHeader("Crosshair")
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF3B6FB6), Color(0xFF9CC7EE), Color(0xFF6B5A3E)))),
                        contentAlignment = Alignment.Center
                    ) { Box(Modifier.size((36 + 64 * s.crossSize).dp * 0.8f)) { CrosshairGlyph(s.crossStyle, s.crossColor) } }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GpsFixed, null, tint = RC.Red, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text("Show crosshair", style = RCType.strong, modifier = Modifier.weight(1f))
                            RedSwitch(s.crosshair, { act.put(K.cross, it) })
                        }
                        Text("Drawn in the centre of the screen; touches pass straight through to the game.", style = RCType.bodyDim.copy(fontSize = 12.sp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Segmented(CrossStyle.entries.map { it.title }, s.crossStyle.ordinal, { act.put(K.crossStyle, CrossStyle.entries[it].name) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("COLOUR", style = RCType.label.copy(fontSize = 9.sp), modifier = Modifier.width(64.dp))
                    crossColors.forEachIndexed { i, c ->
                        Box(
                            Modifier.padding(end = 10.dp).size(26.dp).clip(CircleShape).background(c)
                                .border(if (i == s.crossColor) 3.dp else 1.dp, if (i == s.crossColor) Color.White else RC.Stroke, CircleShape)
                                .clickable { act.put(K.crossColor, i) }
                        )
                    }
                }
                var size by remember(s.crossSize) { mutableFloatStateOf(s.crossSize) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SIZE", style = RCType.label.copy(fontSize = 9.sp), modifier = Modifier.width(64.dp))
                    RedSlider(size, { size = it }, Modifier.weight(1f), onDone = { act.put(K.crossSize, size) })
                }
                Text("Some competitive games don't allow screen overlays — check your game's rules.", style = RCType.bodyDim.copy(fontSize = 11.sp))
            }

            SectionHeader("Match tools")
            GlassPanel(Modifier.fillMaxWidth()) {
                SettingRow("Stopwatch & countdown", "Floating timer: tap SW to cycle 30 s · 1 m · 90 s · 2 m · 5 m. Open it from the menu's tool column.", icon = Icons.Default.Timer)
                Divider()
                SettingRow("Screen hang-up", "Blacks out the screen while the game keeps running — for idle farming or downloads. Swipe up to resume.", icon = Icons.Default.NightsStay)
                Divider()
                SettingRow("Break reminder", if (s.breakMinutes == 0) "Off" else "Reminds you every ${s.breakMinutes} minutes of play", icon = Icons.Default.SelfImprovement)
                Segmented(listOf("Off", "30 min", "60 min", "90 min"), listOf(0, 30, 60, 90).indexOf(s.breakMinutes).coerceAtLeast(0),
                    { act.put(K.breakMin, listOf(0, 30, 60, 90)[it]) }, Modifier.fillMaxWidth())
            }
        }, right = {
            SectionHeader("Notifications during games")
            GlassPanel(Modifier.fillMaxWidth()) {
                SettingRow("When Do Not Disturb is on", when (s.notifMode) {
                    NotifMode.ALL -> "Notifications behave normally"
                    NotifMode.BARRAGE -> "Messages glide across the top as a barrage"
                    NotifMode.BLOCK -> "Everything is held until you finish"
                }, icon = Icons.Default.Notifications)
                Segmented(listOf("Barrage", "Block"), if (s.notifMode == NotifMode.BLOCK) 1 else 0, { act.notif(if (it == 0) NotifMode.BARRAGE else NotifMode.BLOCK) }, Modifier.fillMaxWidth())
                PermLine(Perm.NOTIF_ACCESS, perms, act, "Barrage needs Notification access")
            }

            SectionHeader("Calls during games")
            GlassPanel(Modifier.fillMaxWidth()) {
                SettingRow("No calls", when (s.callMode) {
                    CallMode.ALLOW -> "Calls ring normally (Limit mode declines them)"
                    CallMode.SILENCE -> "Calls don't ring; they show as missed calls"
                    CallMode.DECLINE -> "Calls are declined automatically"
                }, icon = Icons.Default.PhoneDisabled)
                Segmented(CallMode.entries.map { it.title }, s.callMode.ordinal, { act.calls(CallMode.entries[it]) }, Modifier.fillMaxWidth())
                if (Build.VERSION.SDK_INT >= 29) PermLine(Perm.CALL_SCREENING, perms, act, "Declining needs the Call blocking permission")
                else Text("Declining calls needs Android 10 or newer; calls will be silenced instead.", style = RCType.bodyDim.copy(fontSize = 12.sp))
            }

            SectionHeader("Feel & sound")
            GlassPanel(Modifier.fillMaxWidth()) {
                SettingRow("4D vibration", "Pulses the vibration motor on bass hits — explosions, recoil, engines", icon = Icons.Default.Vibration,
                    trailing = { RedSwitch(s.haptics, { act.haptics(it) }) })
                var strength by remember(s.hapticStrength) { mutableFloatStateOf(s.hapticStrength) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 46.dp)) {
                    Text("STRENGTH", style = RCType.label.copy(fontSize = 8.sp), modifier = Modifier.width(70.dp))
                    RedSlider(strength, { strength = it }, Modifier.weight(1f), onDone = { act.put(K.hapticStrength, strength.coerceAtLeast(0.15f)) })
                }
                PermLine(Perm.MICROPHONE, perms, act, "Needs Microphone (audio is analysed on the phone, never recorded)")
                Divider()
                SettingRow("Sound equalizer", "Applied to the phone's audio output; some phones ignore it", icon = Icons.Default.Equalizer)
                Segmented(EqPreset.entries.map { it.title }, s.eq.ordinal, { act.put(K.eq, EqPreset.entries[it].name) }, Modifier.fillMaxWidth())
            }

            SectionHeader("L1 / R1 triggers")
            GlassPanel(Modifier.fillMaxWidth()) {
                Segmented(listOf("Tap", "Hold"), if (s.triggerHold) 1 else 0, { act.put(K.trigHold, it == 1) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(if (s.triggerHold) "The touch stays down while you hold the volume key (one trigger at a time)." else "Each press taps the point once.", style = RCType.bodyDim.copy(fontSize = 12.sp))
                SettingRow("Vibrate on press", trailing = { RedSwitch(s.triggerVibrate, { act.put(K.trigVib, it) }) })
            }

            SectionHeader("Capture & stats")
            GlassPanel(Modifier.fillMaxWidth()) {
                SettingRow("Recording quality", listOf("720p · 30 fps · small files", "1080p · 60 fps", "Native resolution · 60 fps · high bitrate")[s.recQuality.coerceIn(0, 2)], icon = Icons.Default.Videocam)
                Segmented(listOf("720p", "1080p", "Native"), s.recQuality.coerceIn(0, 2), { act.put(K.recQ, it) }, Modifier.fillMaxWidth())
                Divider()
                SettingRow("Show ping", "Latency to the internet, every few seconds", trailing = { RedSwitch(s.showPing, { act.put(K.ping, it) }) })
                SettingRow("Show FPS", if (perms[Perm.FPS_ADB] == true) "Real game frame rate (experimental)" else "Shows the screen's frame rate where the phone reports it; the game's real FPS needs the ADB grant",
                    trailing = { RedSwitch(s.showFps, { act.put(K.fps, it) }) })
                if (perms[Perm.FPS_ADB] != true) GlowButton("Copy ADB command", Modifier.fillMaxWidth(), primary = false) { act.copyAdb() }
            }

            SectionHeader("Your phone's game software")
            GlassPanel(Modifier.fillMaxWidth()) {
                if (vendorApps.isEmpty()) Text("No manufacturer game app found. Hardware extras like cooling fans, physical shoulder triggers and touch sampling modes only exist in a phone maker's own software.", style = RCType.bodyDim)
                else {
                    Text("Use these for hardware REDCORE can't reach: fans, physical triggers, touch sampling rate, RGB.", style = RCType.bodyDim)
                    vendorApps.forEach { a ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            GameIcon(a.pkg, a.label, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(a.label, style = RCType.body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            GlowButton("Open", primary = false) { act.openApp(a.pkg) }
                        }
                    }
                }
            }
        })
    }
}

@Composable
private fun PermLine(p: Perm, perms: Map<Perm, Boolean>, act: ToolsActions, text: String) {
    if (perms[p] == true) return
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip("Permission", RC.Amber)
        Spacer(Modifier.width(8.dp))
        Text(text, style = RCType.bodyDim.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
        GlowButton("Allow", onClick = { act.grant(p) })
    }
}

