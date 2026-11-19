package com.redcore.booster.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.booster.LiveEffect
import com.redcore.booster.booster.PendingRestore
import com.redcore.booster.booster.Perm
import com.redcore.booster.core.Tweak
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.PulsingDot
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

/** One grant that unlocks real effects. */
data class UnlockItem(val perm: Perm, val title: String, val unlocks: String, val granted: Boolean, val adb: Boolean = false)

/** What each grant really unlocks. Everything else in REDCORE works without them. */
fun unlockItems(perms: Map<Perm, Boolean>, maxHz: Int?): List<UnlockItem> = listOf(
    UnlockItem(Perm.DND, "Do Not Disturb access", "Silences pop-ups and calls while you play", perms[Perm.DND] == true),
    UnlockItem(Perm.WRITE_SETTINGS, "Modify system settings", "Brightness lock and rotation lock", perms[Perm.WRITE_SETTINGS] == true),
    UnlockItem(Perm.OVERLAY, "Display over other apps", "In-game menu, HUD, keep-awake and edge guard", perms[Perm.OVERLAY] == true),
    UnlockItem(Perm.USAGE, "Usage access", "Boost starts when a game opens and settings come back when you leave", perms[Perm.USAGE] == true),
    UnlockItem(
        Perm.SECURE_SETTINGS, "One-time ADB grant",
        if (maxHz != null && maxHz >= 90) "Locks the screen at $maxHz Hz and speeds up animations" else "Faster system animations",
        perms[Perm.SECURE_SETTINGS] == true, adb = true
    ),
    UnlockItem(Perm.FPS_ADB, "ADB frame data", "Real in-game FPS counter", perms[Perm.FPS_ADB] == true, adb = true),
)

/** Shows how much of REDCORE's real boost is available on this phone, and the one-tap fix for each missing part. */
@Composable
fun BoostPowerPanel(items: List<UnlockItem>, onGrant: (Perm) -> Unit, modifier: Modifier = Modifier) {
    val done = items.count { it.granted }
    val frac by animateFloatAsState(done / items.size.coerceAtLeast(1).toFloat(), tween(600), label = "power")
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomEnd = 2.dp, bottomStart = 14.dp)
    val color = when { done == items.size -> RC.Green; done >= items.size - 2 -> RC.Amber; else -> RC.Red }
    Column(
        modifier.fillMaxWidth().clip(shape).background(RC.panelGradient).border(1.dp, color.copy(alpha = 0.6f), shape).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (done == items.size) Icons.Default.LockOpen else Icons.Default.Lock, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("BOOST POWER", style = RCType.label.copy(fontSize = 10.sp, color = Color.White), modifier = Modifier.weight(1f))
            Text("$done / ${items.size} UNLOCKED", style = RCType.label.copy(fontSize = 9.sp, color = color))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            items.forEachIndexed { i, _ ->
                val on = (i + 1) / items.size.toFloat() <= frac + 0.001f
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(2.dp)).background(if (on) color else RC.Stroke))
            }
        }
        if (done == items.size) {
            Spacer(Modifier.height(6.dp))
            Text("Everything Android allows without root is unlocked.", style = RCType.bodyDim.copy(fontSize = 12.sp))
            return@Column
        }
        items.filterNot { it.granted }.forEach { u ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.title, style = RCType.strong.copy(fontSize = 14.sp))
                    Text(u.unlocks, style = RCType.bodyDim.copy(fontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                GlowButton(if (u.adb) "How" else "Unlock", primary = !u.adb) { onGrant(u.perm) }
            }
        }
    }
}

/** Effects in force right now, re-read from the system. A green check means REDCORE's value is still holding. */
@Composable
fun ActiveEffectsPanel(live: List<LiveEffect>, modifier: Modifier = Modifier) {
    if (live.isEmpty()) return
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomEnd = 2.dp, bottomStart = 14.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(RC.Green.copy(alpha = 0.10f), RC.Panel)))
            .border(1.dp, RC.Green.copy(alpha = 0.55f), shape).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(RC.Green, 7.dp)
            Spacer(Modifier.width(8.dp))
            Text("ACTIVE NOW", style = RCType.label.copy(fontSize = 10.sp, color = Color.White), modifier = Modifier.weight(1f))
            Icon(Icons.Default.Verified, null, tint = RC.Green, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("READ FROM SYSTEM", style = RCType.label.copy(fontSize = 8.sp, color = RC.Green))
        }
        Spacer(Modifier.height(6.dp))
        live.forEach { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (e.holding) Icons.Default.CheckCircle else Icons.Default.WarningAmber, null,
                    tint = if (e.holding) RC.Green else RC.Amber, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(e.name, style = RCType.body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                Text(e.value, style = RCType.strong.copy(fontSize = 14.sp, color = if (e.holding) Color.White else RC.Amber))
                if (!e.holding) Text("  changed", style = RCType.bodyDim.copy(fontSize = 11.sp, color = RC.Amber))
            }
        }
    }
}

/** Settings that couldn't be put back yet, with the permission that fixes it. */
@Composable
fun PendingRestoreBanner(pending: List<PendingRestore>, onGrant: (Perm) -> Unit, modifier: Modifier = Modifier) {
    if (pending.isEmpty()) return
    val perm = pending.firstNotNullOfOrNull { permFor(it.tweak) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(RC.Amber.copy(alpha = 0.10f)).border(1.dp, RC.Amber, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Restore, null, tint = RC.Amber, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${pending.size} setting${if (pending.size == 1) "" else "s"} still to restore", style = RCType.strong.copy(fontSize = 14.sp, color = RC.Amber))
            Text(pending.joinToString(" · ") { "${it.tweak.label} (needs ${it.requirement})" }, style = RCType.bodyDim.copy(fontSize = 12.sp))
        }
        if (perm != null) { Spacer(Modifier.width(8.dp)); GlowButton("Fix", primary = false) { onGrant(perm) } }
    }
}

fun permFor(t: Tweak): Perm? = when (t) {
    Tweak.DND_FILTER, Tweak.DND_POLICY -> Perm.DND
    Tweak.BRIGHTNESS, Tweak.BRIGHTNESS_MODE, Tweak.AUTO_ROTATE, Tweak.USER_ROTATION -> Perm.WRITE_SETTINGS
    Tweak.MASTER_SYNC, Tweak.PC_POWER_PLAN, Tweak.PC_GAME_MODE, Tweak.PC_POWER_PROFILE -> null
    else -> Perm.SECURE_SETTINGS
}
