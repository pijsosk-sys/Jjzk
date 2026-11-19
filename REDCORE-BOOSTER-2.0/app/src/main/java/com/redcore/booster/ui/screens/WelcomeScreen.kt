package com.redcore.booster.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.ui.RedcoreLogo
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val t = rememberInfiniteTransition(label = "w")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "r")
    HudBackdrop {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth > maxHeight
            val brand: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(if (wide) 170.dp else 180.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val r = size.minDimension / 2
                            drawCircle(Brush.radialGradient(listOf(RC.Red.copy(alpha = 0.35f), Color.Transparent)), r)
                            rotate(rot) {
                                drawArc(RC.Red, 0f, 100f, false, Offset(8f, 8f), Size(size.width - 16, size.height - 16), style = Stroke(4f))
                                drawArc(RC.Red, 180f, 100f, false, Offset(8f, 8f), Size(size.width - 16, size.height - 16), style = Stroke(4f))
                            }
                            rotate(-rot) {
                                drawArc(RC.BlueDeep, 60f, 60f, false, Offset(r * 0.25f, r * 0.25f), Size(r * 1.5f, r * 1.5f), style = Stroke(3f))
                                drawArc(RC.BlueDeep, 240f, 60f, false, Offset(r * 0.25f, r * 0.25f), Size(r * 1.5f, r * 1.5f), style = Stroke(3f))
                            }
                        }
                        RedcoreLogo(84.dp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("REDCORE", style = RCType.display.copy(fontSize = 36.sp, letterSpacing = 6.sp))
                    Text("GAMING BOOSTER 2.0", style = RCType.label.copy(color = RC.Red, fontSize = 11.sp, letterSpacing = 5.sp))
                }
            }
            val features: @Composable () -> Unit = {
                Column {
                    listOf(
                        Triple(Icons.Default.SportsEsports, "Game Space", "All your games in one place, each with its own performance profile."),
                        Triple(Icons.Default.Layers, "Floating toolkit", "HUD, Do Not Disturb, brightness, screenshots and recording over any game."),
                        Triple(Icons.Default.MonitorHeart, "Live monitoring", "CPU clocks, memory, temperature, battery and network in real time."),
                        Triple(Icons.Default.PhoneAndroid, "Real, verified boosts", "Every change is read back from the system and shown before → after, then put back when you stop playing. If Android doesn't allow something, REDCORE says so."),
                    ).forEach { (icon, title, text) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(RC.RedDark), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = RC.Red, modifier = Modifier.size(21.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, style = RCType.strong)
                                Text(text, style = RCType.bodyDim)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    GlowButton("Get started", Modifier.fillMaxWidth(), Icons.AutoMirrored.Filled.ArrowForward, onClick = onStart)
                    Spacer(Modifier.height(8.dp))
                    Text("You can grant optional permissions later in Settings.", style = RCType.bodyDim.copy(fontSize = 12.sp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { brand() }
                    Spacer(Modifier.width(32.dp))
                    Box(Modifier.weight(1.1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) { features() }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp)); brand(); Spacer(Modifier.height(28.dp)); features()
                }
            }
        }
    }
}
