package com.redcore.booster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ZoomIn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.booster.Perm
import com.redcore.booster.booster.Permissions
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.GameMode
import com.redcore.booster.ui.RedcoreLogo
import com.redcore.booster.ui.components.Divider
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.RedSlider
import com.redcore.booster.ui.components.RedSwitch
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.components.Segmented
import com.redcore.booster.ui.components.SettingRow
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

interface SettingsActions {
    fun mode(m: GameMode)
    fun autoDetect(v: Boolean)
    fun restore(v: Boolean)
    fun hud(v: Boolean)
    fun scale(v: Float)
    fun fahrenheit(v: Boolean)
    fun toolkit()
    fun grant(p: Perm)
    fun copyAdb()
}

@Composable
fun SettingsScreen(s: AppSettings, perms: Map<Perm, Boolean>, toolkitOn: Boolean, version: String, act: SettingsActions) {
    HudBackdrop {
        com.redcore.booster.ui.components.Panes(1f, 1f, left = {
            SectionHeader("Gaming")
            GlassPanel(Modifier.fillMaxWidth()) {
                Text("DEFAULT MODE FOR NEW GAMES", style = RCType.label.copy(fontSize = 9.sp))
                Spacer(Modifier.height(8.dp))
                Segmented(GameMode.entries.map { it.title }, s.globalMode.ordinal, { act.mode(GameMode.entries[it]) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Divider()
                SettingRow("Auto game boost", "Apply a game's profile automatically when you open it from anywhere (uses Usage access, checks every 2.5 s while on)", icon = Icons.Default.AutoMode,
                    trailing = { RedSwitch(s.autoDetect, act::autoDetect) })
                Divider()
                SettingRow("Restore settings after play", "Return Do Not Disturb, brightness and refresh rate to how they were", icon = Icons.Default.Restore,
                    trailing = { RedSwitch(s.restoreOnExit, act::restore) })
            }

            SectionHeader("Floating toolkit")
            GlassPanel(Modifier.fillMaxWidth()) {
                SettingRow("Toolkit", if (toolkitOn) "Running" else "Stopped", icon = Icons.Default.Layers, trailing = { RedSwitch(toolkitOn, { act.toolkit() }) })
                Divider()
                SettingRow("Performance HUD", "CPU, RAM, temperature, battery and network next to the bubble", icon = Icons.Default.Speed, trailing = { RedSwitch(s.hudEnabled, act::hud) })
                Divider()
                var scale by remember(s.overlayScale) { mutableFloatStateOf(s.overlayScale) }
                SettingRow("Toolkit size", "${(scale * 100).toInt()} %", icon = Icons.Default.ZoomIn)
                RedSlider((scale - 0.7f) / 0.6f, { scale = 0.7f + it * 0.6f }, Modifier.fillMaxWidth(), onDone = { act.scale(scale) })
            }

            SectionHeader("Display")
            GlassPanel(Modifier.fillMaxWidth()) {
                SettingRow("Temperature unit", if (s.fahrenheit) "Fahrenheit" else "Celsius", icon = Icons.Default.Thermostat,
                    trailing = { Segmented(listOf("°C", "°F"), if (s.fahrenheit) 1 else 0, { act.fahrenheit(it == 1) }, Modifier.width(110.dp)) })
            }

        }, right = {
            SectionHeader("Permissions")
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Perm.entries.filter { it != Perm.CALL_SCREENING || android.os.Build.VERSION.SDK_INT >= 29 }.forEach { p ->
                        if (p in Permissions.adbOnly) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.title, style = RCType.strong.copy(fontSize = 15.sp))
                                    Text(p.why, style = RCType.bodyDim)
                                }
                                Spacer(Modifier.width(10.dp))
                                if (perms[p] == true) Icon(Icons.Default.CheckCircle, "Granted", tint = RC.Green, modifier = Modifier.size(24.dp))
                                else GlowButton("ADB", primary = false, icon = Icons.Default.ContentCopy, onClick = act::copyAdb)
                            }
                        } else PermCard(p, perms[p] == true) { act.grant(p) }
                    }
                }
                if (perms[Perm.SECURE_SETTINGS] != true || perms[Perm.FPS_ADB] != true) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(RC.Black).border(1.dp, RC.Stroke, RoundedCornerShape(8.dp)).padding(10.dp)) {
                        Text(Permissions.ADB_GRANT_ALL, style = RCType.body.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = RC.Amber))
                    }
                    Text("Run once from a computer with USB debugging enabled to unlock refresh-rate control, faster animations and the experimental FPS counter. Optional.", style = RCType.bodyDim.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 6.dp))
                }
            }

            SectionHeader("Gaming phone features")
            GlassPanel(Modifier.fillMaxWidth()) {
                val rows = listOf(
                    Triple("Game library, auto-detect & play stats", 0, ""),
                    Triple("Eco · Balance · Beast · Limit modes", 0, ""),
                    Triple("COOL guard (heat control)", 0, ""),
                    Triple("Swipe-open game menu & live stats HUD", 0, ""),
                    Triple("Do Not Disturb, barrage notifications, no calls", 0, ""),
                    Triple("Brightness lock, rotation lock, keep awake", 0, ""),
                    Triple("Close background apps, pause auto-sync", 0, ""),
                    Triple("Low-latency Wi-Fi", 1, "phone dependent"),
                    Triple("Edge touch protection", 0, ""),
                    Triple("L1/R1 triggers (volume keys, tap or hold)", 0, ""),
                    Triple("Crosshair, stopwatch & timer, screen hang-up", 0, ""),
                    Triple("Screenshot & screen recording", 0, ""),
                    Triple("4D vibration, sound equalizer", 1, "phone dependent"),
                    Triple("Break reminder, session timer, ping", 0, ""),
                    Triple("Refresh-rate changer, faster animations", 2, "one ADB command"),
                    Triple("FPS counter", 2, "ADB · experimental"),
                    Triple("CPU/GPU overclock, Diablo-level clocks", 3, "root"),
                    Triple("Touch sampling rate, sensitivity, smoothness", 4, "manufacturer"),
                    Triple("GPU settings (MSAA, AF, LOD, VRS)", 4, "manufacturer"),
                    Triple("Frame insertion & super resolution", 4, "display chip"),
                    Triple("Cooling fan, bypass charging, RGB", 4, "hardware"),
                    Triple("Blocking system gestures & status bar", 4, "system only"),
                    Triple("Macros & rapid fire", 5, "not included"),
                )
                rows.forEachIndexed { i, (name, status, note) ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = RCType.body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        com.redcore.booster.ui.components.Chip(
                            when (status) { 0 -> "Works"; 1 -> "Best effort"; 2 -> "ADB"; 3 -> "Root"; 4 -> "Not possible"; else -> "Not included" },
                            when (status) { 0 -> RC.Green; 1 -> RC.Blue; 2 -> RC.Amber; else -> RC.TextMute }
                        )
                    }
                    if (note.isNotEmpty() && status >= 1) Text(note, style = RCType.bodyDim.copy(fontSize = 11.sp), modifier = Modifier.padding(bottom = 2.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text("\"Not possible\" features live in the phone maker's firmware or hardware — no app can reach them on a regular phone. Macros and rapid fire are left out because automated input breaks many games' rules.", style = RCType.bodyDim.copy(fontSize = 12.sp))
            }

            SectionHeader("About")
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RedcoreLogo(40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("REDCORE BOOSTER", style = RCType.title.copy(fontSize = 14.sp))
                        Text("Version $version", style = RCType.bodyDim)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("REDCORE works entirely on your phone. It has no accounts, ads or analytics, and never uploads your data. The network speed test contacts Cloudflare only when you start it.", style = RCType.bodyDim)
            }
        })
    }
}
