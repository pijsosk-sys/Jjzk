package com.redcore.booster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redcore.booster.data.InstalledApp
import com.redcore.booster.ui.components.Chip
import com.redcore.booster.ui.components.GameIcon
import com.redcore.booster.ui.components.GlowButton
import com.redcore.booster.ui.components.LoadingSpinner
import com.redcore.booster.ui.components.SectionHeader
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

@Composable
fun AddGamesScreen(apps: List<InstalledApp>?, inLibrary: Set<String>, onAdd: (Set<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize().background(RC.Black)) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RC.Panel).border(1.dp, RC.Stroke, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = RC.TextDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search installed apps…", style = RCType.body.copy(color = RC.TextMute))
                BasicTextField(query, { query = it }, textStyle = RCType.body, singleLine = true, cursorBrush = SolidColor(RC.Red), modifier = Modifier.fillMaxWidth())
            }
        }
        if (apps == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingSpinner() }
        } else {
            val filtered = apps.filter { it.pkg !in inLibrary && (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)) }
            val games = filtered.filter { it.isGame }
            val others = filtered.filterNot { it.isGame }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                if (games.isNotEmpty()) {
                    item { SectionHeader("Detected games · ${games.size}") }
                    items(games, key = { it.pkg }) { a -> AppRow(a, a.pkg in picked) { if (a.pkg in picked) picked.remove(a.pkg) else picked.add(a.pkg) } }
                }
                item { SectionHeader("Other apps · ${others.size}") }
                if (others.isEmpty() && games.isEmpty()) item {
                    Text(if (query.isBlank()) "Every app is already in your Game Space." else "No apps match \"$query\".", style = RCType.bodyDim, modifier = Modifier.padding(vertical = 20.dp))
                }
                items(others, key = { it.pkg }) { a -> AppRow(a, a.pkg in picked) { if (a.pkg in picked) picked.remove(a.pkg) else picked.add(a.pkg) } }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
        Box(Modifier.fillMaxWidth().background(Color(0xFF0B0C11)).padding(16.dp)) {
            GlowButton(
                if (picked.isEmpty()) "Select apps to add" else "Add ${picked.size} to Game Space",
                Modifier.fillMaxWidth(), Icons.Default.Add, enabled = picked.isNotEmpty()
            ) { onAdd(picked.toSet()); picked.clear() }
        }
    }
}

@Composable
private fun AppRow(a: InstalledApp, selected: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) RC.RedDark else Color.Transparent)
            .border(1.dp, if (selected) RC.Red else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameIcon(a.pkg, a.label, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.label, style = RCType.strong.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.pkg, style = RCType.bodyDim.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (a.isGame) { Chip("Game", RC.Red); Spacer(Modifier.width(8.dp)) }
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(if (selected) RC.Red else Color.Transparent).border(1.5.dp, if (selected) RC.Red else RC.Metal, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) { if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}
