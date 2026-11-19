package com.redcore.booster.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

/** Loads app icons (slow: PackageManager + bitmap). Provided by MainActivity; previews/tests can supply fakes. */
val LocalIconLoader = compositionLocalOf<(String) -> ImageBitmap?> { { null } }

/** Already-decoded icons (fast, main-thread safe), so cached icons appear without a placeholder flash. */
val LocalIconCache = compositionLocalOf<(String) -> ImageBitmap?> { { null } }

private val fallbackPalette = listOf(
    listOf(Color(0xFFB0081F), Color(0xFF3A0610)),
    listOf(Color(0xFF1B3AD6), Color(0xFF0B1240)),
    listOf(Color(0xFF7A3CFF), Color(0xFF220B4D)),
    listOf(Color(0xFFE0661A), Color(0xFF3D1704)),
    listOf(Color(0xFF119E77), Color(0xFF032A20)),
)

@Composable
fun GameIcon(pkg: String, label: String, size: Dp, modifier: Modifier = Modifier, corner: Dp = size / 4.5f) {
    val loader = LocalIconLoader.current
    val cache = LocalIconCache.current
    // Decode off the main thread so scrolling the library never stalls on PackageManager.
    val bmp by produceState(initialValue = cache(pkg), pkg) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { loader(pkg) }.getOrNull() }
    }
    val shape = RoundedCornerShape(corner)
    val loaded = bmp
    if (loaded != null) {
        Image(loaded, label, modifier.size(size).clip(shape))
    } else {
        val colors = fallbackPalette[(pkg.hashCode() and 0x7fffffff) % fallbackPalette.size]
        Box(
            modifier.size(size).clip(shape).background(Brush.linearGradient(colors)).border(1.dp, colors[0].copy(alpha = 0.8f), shape),
            contentAlignment = Alignment.Center
        ) {
            Text(label.take(1).uppercase(), style = RCType.title.copy(fontSize = (size.value / 2.4f).sp, color = Color.White))
        }
    }
}
