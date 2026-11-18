package com.redcore.booster.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.R
import com.redcore.booster.booster.Perm
import com.redcore.booster.ui.components.Chip
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.RingGauge
import com.redcore.booster.ui.rcIcon
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

@Composable
fun Perm.icon(): ImageVector = when (this) {
    Perm.OVERLAY -> rcIcon(R.drawable.ic_rc_layers)
    Perm.USAGE -> rcIcon(R.drawable.ic_rc_monitor)
    Perm.DND -> Icons.Default.DoNotDisturbOn
    Perm.WRITE_SETTINGS -> Icons.Default.BrightnessMedium
    Perm.NOTIFICATIONS -> Icons.Default.Notifications
    Perm.ACCESSIBILITY -> rcIcon(R.drawable.ic_rc_gamepad)
    Perm.MEDIA -> rcIcon(R.drawable.ic_rc_storage)
    Perm.MICROPHONE -> Icons.Default.Mic
    Perm.SECURE_SETTINGS -> rcIcon(R.drawable.ic_rc_gauge)
    Perm.NOTIF_ACCESS -> Icons.Default.Notifications
    Perm.CALL_SCREENING -> Icons.Default.PhoneDisabled
    Perm.FPS_ADB -> rcIcon(R.drawable.ic_rc_monitor)
}

val Perm.recommended: Boolean get() = this in setOf(Perm.OVERLAY, Perm.USAGE, Perm.DND, Perm.WRITE_SETTINGS, Perm.NOTIFICATIONS)

/** What the user will see in system settings, so the hand-off isn't confusing. */
val Perm.howTo: String get() = when (this) {
    Perm.OVERLAY -> "Android opens \"Display over other apps\". Switch REDCORE on, then press Back."
    Perm.USAGE -> "Android opens \"Usage access\". Tap REDCORE, switch it on, then press Back."
    Perm.DND -> "Android opens \"Do Not Disturb access\". Allow REDCORE, then press Back."
    Perm.WRITE_SETTINGS -> "Android opens \"Modify system settings\". Switch it on, then press Back."
    Perm.ACCESSIBILITY -> "Android opens Accessibility. Tap \"Installed apps\" › REDCORE screenshot & triggers › On."
    Perm.NOTIFICATIONS, Perm.MEDIA, Perm.MICROPHONE -> "Android will show a permission pop-up. Tap Allow."
    Perm.SECURE_SETTINGS, Perm.FPS_ADB -> "Run the ADB command shown in Settings › Permissions from a computer."
    Perm.NOTIF_ACCESS -> "Android opens \"Notification access\". Switch on \"REDCORE game barrage\", then press Back."
    Perm.CALL_SCREENING -> "Android asks whether REDCORE may be your call-screening app. Tap Set as default / Allow."
}

/** First-run permission setup. Every permission is optional; the screen explains what each unlocks. */
@Composable
fun PermissionSetupScreen(perms: Map<Perm, Boolean>, onGrant: (Perm) -> Unit, onDone: () -> Unit) {
    val list = Perm.entries.filter { it !in com.redcore.booster.booster.Permissions.adbOnly && (it != Perm.CALL_SCREENING || android.os.Build.VERSION.SDK_INT >= 29) }
    val granted = list.count { perms[it] == true }
    val recommendedDone = list.filter { it.recommended }.all { perms[it] == true }
    HudBackdrop {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth > maxHeight
            val summary: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RingGauge(granted / list.size.toFloat(), "$granted/${list.size}", "granted", "Permissions", size = 150.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("UNLOCK REDCORE", style = RCType.title.copy(fontSize = 18.sp, letterSpacing = 2.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Android keeps gaming features behind permissions you control. Grant the ones you want — you can change them any time in Settings.",
                        style = RCType.bodyDim, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    GlowButton(if (recommendedDone) "Start gaming" else "Continue", Modifier.widthIn(min = 220.dp), primary = recommendedDone || granted > 0, onClick = onDone)
                    if (!recommendedDone) Text("You can skip this and grant later", style = RCType.bodyDim.copy(fontSize = 11.sp), modifier = Modifier.padding(top = 6.dp))
                }
            }
            val cards: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { p -> PermCard(p, perms[p] == true) { onGrant(p) } }
                }
            }
            if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(0.85f), contentAlignment = Alignment.Center) { summary() }
                Spacer(Modifier.width(20.dp))
                Box(Modifier.weight(1.15f).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) { cards() }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                summary(); Spacer(Modifier.height(20.dp)); cards()
            }
        }
    }
}

@Composable
fun PermCard(p: Perm, granted: Boolean, onGrant: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomEnd = 2.dp, bottomStart = 14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(RC.panelGradient)
            .border(1.dp, if (granted) RC.Green.copy(alpha = 0.5f) else if (p.recommended) RC.Red.copy(alpha = 0.6f) else RC.Stroke, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (granted) RC.Green.copy(alpha = 0.12f) else RC.RedDark),
            contentAlignment = Alignment.Center
        ) { Icon(p.icon(), null, tint = if (granted) RC.Green else RC.Red, modifier = Modifier.size(21.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.title, style = RCType.strong.copy(fontSize = 15.sp))
                Spacer(Modifier.width(8.dp))
                Chip(if (p.recommended) "Recommended" else "Optional", if (p.recommended) RC.Red else RC.TextMute)
            }
            Text(p.why, style = RCType.bodyDim)
        }
        Spacer(Modifier.width(10.dp))
        if (granted) Icon(Icons.Default.CheckCircle, "Granted", tint = RC.Green, modifier = Modifier.size(26.dp))
        else GlowButton("Allow", primary = p.recommended, onClick = onGrant)
    }
}

data class PermAsk(val perm: Perm, val message: String, val declineLabel: String = "Not now")

/** In-app explanation shown before sending the user to a system permission screen. */
@Composable
fun PermissionDialog(ask: PermAsk?, onAllow: () -> Unit, onDecline: () -> Unit) {
    AnimatedVisibility(ask != null, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(androidx.compose.runtime.remember { MutableInteractionSource() }, null, onClick = onDecline))
    }
    AnimatedVisibility(ask != null, enter = scaleIn(initialScale = 0.9f) + fadeIn(), exit = scaleOut(targetScale = 0.95f) + fadeOut(), modifier = Modifier.fillMaxSize()) {
        val a = ask ?: return@AnimatedVisibility
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 22.dp, bottomEnd = 4.dp, bottomStart = 22.dp)
            Column(
                Modifier.padding(24.dp).widthIn(max = 480.dp).clip(shape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A1C24), RC.Black)))
                    .border(1.dp, Brush.verticalGradient(listOf(RC.Red, RC.Stroke)), shape)
                    .clickable(androidx.compose.runtime.remember { MutableInteractionSource() }, null) {}
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(RC.RedDark).border(1.dp, RC.Red, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(a.perm.icon(), null, tint = RC.Red, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("PERMISSION NEEDED", style = RCType.label.copy(fontSize = 9.sp, color = RC.Red))
                        Text(a.perm.title, style = RCType.title.copy(fontSize = 16.sp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(a.message, style = RCType.body)
                Spacer(Modifier.height(8.dp))
                Text(a.perm.howTo, style = RCType.bodyDim)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlowButton(a.declineLabel, Modifier.weight(1f), primary = false, onClick = onDecline)
                    GlowButton("Allow", Modifier.weight(1f), onClick = onAllow)
                }
            }
        }
    }
}
