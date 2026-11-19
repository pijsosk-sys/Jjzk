package com.redcore.booster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.data.AppSize
import com.redcore.booster.data.BigFile
import com.redcore.booster.data.StorageSummary
import com.redcore.booster.data.fmtBytes
import com.redcore.booster.ui.components.Divider
import com.redcore.booster.ui.components.GameIcon
import com.redcore.booster.ui.components.GlassPanel
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.HudBackdrop
import com.redcore.booster.ui.components.LoadingSpinner
import com.redcore.booster.ui.components.NoticeCard
import com.redcore.booster.ui.components.RingGauge
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

@Composable
fun StorageScreen(
    summary: StorageSummary?,
    apps: List<AppSize>?,
    files: com.redcore.booster.data.FileScan?,
    usageGranted: Boolean,
    mediaGranted: Boolean,
    onGrantUsage: () -> Unit,
    onGrantMedia: () -> Unit,
    onOpenFile: (BigFile) -> Unit,
    onAppDetails: (String) -> Unit,
    onFreeUp: () -> Unit,
) {
    HudBackdrop {
        if (summary == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingSpinner() }; return@HudBackdrop }
        com.redcore.booster.ui.components.Panes(1f, 1f, left = {
            val frac = summary.used.toFloat() / summary.total.coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingGauge(frac, "${(frac * 100).toInt()}", "% used", "Internal storage", size = 150.dp, color = if (frac > 0.9f) RC.Amber else RC.Red)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(fmtBytes(summary.free), style = RCType.number.copy(fontSize = 24.sp))
                    Text("free of ${fmtBytes(summary.total)}", style = RCType.bodyDim)
                    Spacer(Modifier.height(8.dp))
                    Text(fmtBytes(summary.used), style = RCType.strong)
                    Text("used", style = RCType.bodyDim)
                }
            }

            SectionHeader("Breakdown")
            GlassPanel(Modifier.fillMaxWidth()) {
                val appsBytes = summary.apps
                if (appsBytes == null) {
                    NoticeCard("Category breakdown needs Usage access", "Android only shares per-category storage with apps that have Usage access.") { GlowButton("Grant", onClick = onGrantUsage) }
                } else {
                    val cats = listOf(
                        Triple("Apps & data", appsBytes, RC.Red),
                        Triple("Images", summary.images ?: 0, RC.Blue),
                        Triple("Videos", summary.video ?: 0, RC.Violet),
                        Triple("Audio", summary.audio ?: 0, RC.Green),
                        Triple("System & other", summary.other ?: 0, RC.Metal),
                    )
                    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(RC.Black)) {
                        cats.forEach { (_, v, c) ->
                            val w = v.toFloat() / summary.total
                            if (w > 0.002f) Box(Modifier.weight(w).fillMaxHeight().background(c))
                        }
                        val freeW = summary.free.toFloat() / summary.total
                        if (freeW > 0.002f) Box(Modifier.weight(freeW).fillMaxHeight().background(RC.PanelHi))
                    }
                    Spacer(Modifier.height(10.dp))
                    cats.forEach { (n, v, c) ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                            Spacer(Modifier.width(10.dp))
                            Text(n, style = RCType.body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                            Text(fmtBytes(v), style = RCType.strong.copy(fontSize = 14.sp))
                        }
                    }
                    summary.cache?.let {
                        Divider()
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Text("App caches (included above)", style = RCType.bodyDim, modifier = Modifier.weight(1f))
                            Text(fmtBytes(it), style = RCType.strong.copy(fontSize = 14.sp, color = RC.Amber))
                        }
                    }
                }
            }

        }, right = {
            SectionHeader("Largest apps")
            GlassPanel(Modifier.fillMaxWidth()) {
                when {
                    !usageGranted -> Text("Grant Usage access to see how much space each app uses.", style = RCType.bodyDim)
                    apps == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { LoadingSpinner(size = 32.dp) }
                    else -> apps.take(10).forEachIndexed { i, a ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().clickable { onAppDetails(a.pkg) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            GameIcon(a.pkg, a.label, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.label, style = RCType.body.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("App ${fmtBytes(a.appBytes)} · Data ${fmtBytes(a.dataBytes)} · Cache ${fmtBytes(a.cacheBytes)}", style = RCType.bodyDim.copy(fontSize = 11.sp), maxLines = 1)
                            }
                            Text(fmtBytes(a.total), style = RCType.strong.copy(fontSize = 14.sp))
                        }
                    }
                }
            }

            SectionHeader("Large files")
            GlassPanel(Modifier.fillMaxWidth()) {
                when {
                    !mediaGranted -> {
                        Text("Allow access to photos, videos and audio so REDCORE can list your biggest files. Nothing is ever deleted automatically.", style = RCType.bodyDim)
                        Spacer(Modifier.height(10.dp))
                        GlowButton("Allow media access", Modifier.fillMaxWidth(), primary = false, onClick = onGrantMedia)
                    }
                    files == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { LoadingSpinner(size = 32.dp) }
                    files.error != null -> NoticeCard("Couldn't list large files", files.error)
                    files.files.isEmpty() -> Text(if (files.partialAccess) "No large files among the photos and videos you selected. Allow all media to scan everything." else "No media files larger than 20 MB found.", style = RCType.bodyDim)
                    else -> files.files.take(15).forEachIndexed { i, f ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().clickable { onOpenFile(f) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            val (icon, c) = when {
                                f.mime?.startsWith("video") == true -> Icons.Default.Movie to RC.Violet
                                f.mime?.startsWith("image") == true -> Icons.Default.Image to RC.Blue
                                f.mime?.startsWith("audio") == true -> Icons.Default.AudioFile to RC.Green
                                else -> Icons.AutoMirrored.Filled.InsertDriveFile to RC.TextDim
                            }
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = RCType.body.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(f.path ?: f.mime ?: "", style = RCType.bodyDim.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(fmtBytes(f.size), style = RCType.strong.copy(fontSize = 14.sp))
                        }
                    }
                }
            }

            SectionHeader("Safe cleanup")
            GlassPanel(Modifier.fillMaxWidth()) {
                listOf(
                    "Clear game caches" to "Open a large app above and tap \"Clear cache\". Caches rebuild automatically; your progress and logins are kept.",
                    "Don't clear game data" to "\"Clear storage/data\" deletes downloaded assets and may delete offline progress.",
                    "Review big videos" to "Screen recordings add up fast. Tap a file above to open it, then delete it from your gallery if you no longer need it.",
                ).forEachIndexed { i, (t, d) ->
                    if (i > 0) Divider()
                    Column(Modifier.padding(vertical = 7.dp)) { Text(t, style = RCType.strong.copy(fontSize = 14.sp)); Text(d, style = RCType.bodyDim) }
                }
                Spacer(Modifier.height(10.dp))
                GlowButton("Open Android's Free up space", Modifier.fillMaxWidth(), Icons.Default.CleaningServices, primary = false, onClick = onFreeUp)
            }
        })
    }
}

