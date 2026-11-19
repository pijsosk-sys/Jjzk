package com.redcore.booster.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import com.redcore.booster.data.GameMode
import com.redcore.booster.data.GameProfile
import com.redcore.booster.data.LibraryGame
import com.redcore.booster.data.RefreshTarget
import com.redcore.booster.data.fmtDuration
import com.redcore.booster.ui.components.Chip
import com.redcore.booster.ui.components.Divider
import com.redcore.booster.ui.components.GameIcon
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.RedSlider
import com.redcore.booster.ui.components.RedSwitch
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.components.Segmented
import com.redcore.booster.ui.components.SettingRow
import com.redcore.booster.ui.components.StatTile
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType
import java.text.DateFormat
import java.util.Date

data class Capabilities(
    val dnd: Boolean,
    val brightness: Boolean,
    val refreshAdb: Boolean,
    val overlay: Boolean,
    val usage: Boolean,
    val refreshRates: List<Int>,
    /** Android 13 and older only: Android 14+ ignores requests to close other apps. */
    val canCloseApps: Boolean = true,
)

@Composable
fun GameDetailScreen(
    game: LibraryGame,
    todayMs: Long?,
    weekMs: Long?,
    lastPlayed: Long?,
    caps: Capabilities,
    onPlay: () -> Unit,
    onUpdate: (GameProfile) -> Unit,
    onRemove: () -> Unit,
) {
    val p = game.profile
    val spec = p.effective()
    HudBackdrop {
        com.redcore.booster.ui.components.Panes(1f, 1f, left = {
            // banner
            GlassPanel(Modifier.fillMaxWidth(), accent = true, padding = 0.dp) {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(p.mode.accent().copy(alpha = 0.3f), Color.Transparent))).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(Modifier.size(104.dp).clip(CircleShape).background(Brush.radialGradient(listOf(p.mode.accent().copy(alpha = 0.5f), Color.Transparent))))
                            GameIcon(p.pkg, game.label, 80.dp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(game.label, style = RCType.title.copy(fontSize = 17.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(p.pkg, style = RCType.bodyDim.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Chip("${p.mode.title} profile", p.mode.accent())
                        }
                    }
                }
                Box(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
                    GlowButton(if (game.installed) "Launch with ${p.mode.title} profile" else "Not installed", Modifier.fillMaxWidth(), Icons.Default.PlayArrow, enabled = game.installed, onClick = onPlay)
                }
            }

            SectionHeader("Statistics")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Today", if (caps.usage) fmtDuration(todayMs ?: 0) else "—", Modifier.weight(1f))
                StatTile("7 days", if (caps.usage) fmtDuration(weekMs ?: 0) else "—", Modifier.weight(1f))
                StatTile("Last played", lastPlayed?.takeIf { it > 0 }?.let { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)) } ?: "—", Modifier.weight(1.2f))
            }
            if (!caps.usage) Text("Grant Usage access in Settings to see play time.", style = RCType.bodyDim, modifier = Modifier.padding(top = 6.dp))

        }, right = {
            SectionHeader("Gaming profile")
            GlassPanel(Modifier.fillMaxWidth()) {
                Segmented(GameMode.entries.map { it.title }, p.mode.ordinal, { onUpdate(p.copy(mode = GameMode.entries[it])) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(p.mode.tagline, style = RCType.bodyDim)
                Spacer(Modifier.height(6.dp))
                Divider()
                SettingRow("Floating toolkit", "Show the HUD & quick panel when this game starts", icon = Icons.Default.Layers,
                    trailing = { RedSwitch(p.overlay, { onUpdate(p.copy(overlay = it)) }) })
                Divider()
                SettingRow(
                    "Do Not Disturb", if (caps.dnd) "Silence notifications while playing" else "Needs Do Not Disturb access",
                    icon = Icons.Default.DoNotDisturbOn,
                    trailing = { RedSwitch(spec.dnd, { onUpdate(p.copy(dnd = it)) }, enabled = caps.dnd) })
                Divider()
                SettingRow(
                    "Close background apps",
                    when {
                        !caps.canCloseApps -> "Not possible on Android 14 and newer — Android manages background apps itself"
                        !caps.usage -> "Needs Usage access"
                        else -> "Ask Android to close recently used apps at launch"
                    },
                    icon = Icons.Default.AutoDelete,
                    trailing = { RedSwitch(spec.cleanBackground && caps.canCloseApps, { onUpdate(p.copy(cleanBackground = it)) }, enabled = caps.usage && caps.canCloseApps) })
                Divider()
                BrightnessRow(p, spec.brightness, caps.brightness, onUpdate)
                Divider()
                SettingRow(
                    "Refresh rate",
                    when {
                        caps.refreshRates.size < 2 -> "This display has a single refresh rate"
                        !caps.refreshAdb -> "Supported: ${caps.refreshRates.joinToString("/")} Hz · usually needs ADB permission"
                        else -> "Supported: ${caps.refreshRates.joinToString("/")} Hz"
                    },
                    icon = Icons.Default.Refresh
                )
                Segmented(RefreshTarget.entries.map { it.label }, spec.refresh.ordinal, { onUpdate(p.copy(refresh = RefreshTarget.entries[it])) }, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(18.dp))
            GlowButton("Remove from Game Space", Modifier.fillMaxWidth(), Icons.Default.DeleteOutline, primary = false, onClick = onRemove)
        })
    }
}

@Composable
private fun BrightnessRow(p: GameProfile, value: Int?, allowed: Boolean, onUpdate: (GameProfile) -> Unit) {
    var v by remember(value) { mutableFloatStateOf((value ?: 70) / 100f) }
    SettingRow(
        "Brightness", when {
            !allowed -> "Needs \"Modify system settings\""
            value == null -> "Unchanged — uses your current brightness"
            else -> "Set to ${(v * 100).toInt()} % when the game starts"
        },
        icon = Icons.Default.BrightnessMedium,
        trailing = { RedSwitch(value != null, { on -> onUpdate(p.copy(brightness = if (on) 70 else -1)) }, enabled = allowed) }
    )
    if (value != null && allowed) {
        RedSlider(v, { v = it }, Modifier.fillMaxWidth().padding(start = 46.dp), onDone = { onUpdate(p.copy(brightness = (v * 100).toInt().coerceAtLeast(5))) })
    }
}
