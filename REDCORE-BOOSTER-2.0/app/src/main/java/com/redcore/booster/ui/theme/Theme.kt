package com.redcore.booster.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.redcore.booster.R

object RC {
    val Black = Color(0xFF07080B)
    val Bg = Color(0xFF0B0C11)
    val Panel = Color(0xFF13151C)
    val PanelHi = Color(0xFF1B1E27)
    val Stroke = Color(0xFF2A2E3A)
    val Metal = Color(0xFF3A3F4C)
    val Red = Color(0xFFFF1E3C)
    val RedDeep = Color(0xFFB0081F)
    val RedDark = Color(0xFF3A0610)
    val RedGlow = Color(0x66FF1E3C)
    val Blue = Color(0xFF2EA8FF)
    val BlueDeep = Color(0xFF1B3AD6)
    val Violet = Color(0xFF7A3CFF)
    val Amber = Color(0xFFFFB020)
    val Green = Color(0xFF2EE59D)
    val Text = Color(0xFFEDEFF5)
    val TextDim = Color(0xFF9BA1B0)
    val TextMute = Color(0xFF5F6575)

    val redGradient = Brush.horizontalGradient(listOf(RedDeep, Red))
    val panelGradient = Brush.verticalGradient(listOf(Color(0xFF181A22), Color(0xFF0E1015)))
    val railSelected = Brush.horizontalGradient(listOf(BlueDeep.copy(alpha = 0.85f), Violet.copy(alpha = 0.8f), Red))
}

val Orbitron = FontFamily(
    Font(R.font.orbitron_medium, FontWeight.Medium),
    Font(R.font.orbitron_bold, FontWeight.Bold),
    Font(R.font.orbitron_black, FontWeight.Black),
)

val Rajdhani = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

object RCType {
    val display = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Black, fontSize = 30.sp, color = RC.Text)
    val title = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = RC.Text, letterSpacing = 1.sp)
    val label = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = RC.TextDim, letterSpacing = 1.2.sp)
    val number = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = RC.Text)
    val body = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = RC.Text)
    val bodyDim = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = RC.TextDim, lineHeight = 16.sp)
    val strong = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = RC.Text)
}

@Composable
fun RedcoreTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = RC.Red,
        onPrimary = Color.White,
        secondary = RC.Blue,
        background = RC.Black,
        surface = RC.Panel,
        onSurface = RC.Text,
        onBackground = RC.Text,
        surfaceVariant = RC.PanelHi,
        outline = RC.Stroke,
        error = RC.Amber,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
