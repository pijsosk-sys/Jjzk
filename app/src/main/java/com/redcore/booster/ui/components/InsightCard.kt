package com.redcore.booster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.redcore.booster.data.Insight
import com.redcore.booster.data.Severity
import com.redcore.booster.ui.theme.RC
import com.redcore.booster.ui.theme.RCType

@Composable
fun InsightList(items: List<Insight>) {
    Column {
        items.forEach { InsightCard(it); Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun InsightCard(i: Insight) {
    val (icon, color) = when (i.severity) {
        Severity.GOOD -> Icons.Default.CheckCircle to RC.Green
        Severity.INFO -> Icons.Default.Lightbulb to RC.Blue
        Severity.WARN -> Icons.Default.WarningAmber to RC.Amber
        Severity.CRITICAL -> Icons.Default.ErrorOutline to RC.Red
    }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(RC.Panel).border(1.dp, RC.Stroke, shape).padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(i.title, style = RCType.strong.copy(color = if (i.severity == Severity.CRITICAL) RC.Red else RC.Text))
            Text(i.detail, style = RCType.bodyDim)
        }
    }
}
