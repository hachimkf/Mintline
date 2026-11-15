package com.example.mintline.ui.components.match

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mintline.model.IncidentType
import com.example.mintline.model.MatchIncident
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.staggeredEntrance

/** Centre-line timeline: home events on the left, away on the right. */
@Composable
fun EventTimeline(incidents: List<MatchIncident>, finished: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val lineColor = scheme.outlineVariant
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    lineColor,
                    Offset(size.width / 2f, 18.dp.toPx()),
                    Offset(size.width / 2f, size.height - 18.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Marker("Kick-off")
        incidents.forEachIndexed { i, inc ->
            Row(
                Modifier.fillMaxWidth().staggeredEntrance(i, stepMs = 60),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (inc.isHomeTeam) EventBubble(inc, alignEnd = true)
                }
                MinuteBubble(inc.minute, inc.type)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (!inc.isHomeTeam) EventBubble(inc, alignEnd = false)
                }
            }
        }
        if (finished) Marker("Full time")
    }
}

@Composable
private fun Marker(text: String) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSecondaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(scheme.secondaryContainer)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MinuteBubble(minute: String, type: IncidentType) {
    val scheme = MaterialTheme.colorScheme
    val isGoal = type == IncidentType.GOAL || type == IncidentType.PENALTY_SCORED
    Box(
        Modifier
            .padding(horizontal = 8.dp)
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isGoal) scheme.primary else scheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Text(
            minute,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (isGoal) scheme.onPrimary else scheme.onSurface
        )
    }
}

@Composable
private fun EventBubble(inc: MatchIncident, alignEnd: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val isGoal = inc.type == IncidentType.GOAL || inc.type == IncidentType.PENALTY_SCORED
    Row(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isGoal) scheme.primaryContainer else scheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!alignEnd) {
            IncidentIcon(inc.type)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                inc.primaryPlayer,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (isGoal) scheme.onPrimaryContainer else scheme.onSurface,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
            )
            val sub = when {
                !inc.secondaryPlayer.isNullOrBlank() && inc.type == IncidentType.SUBSTITUTION -> "↔ ${inc.secondaryPlayer}"
                !inc.secondaryPlayer.isNullOrBlank() -> "Assist · ${inc.secondaryPlayer}"
                !inc.detail.isNullOrBlank() -> inc.detail
                else -> null
            }
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = (if (isGoal) scheme.onPrimaryContainer else scheme.onSurfaceVariant).copy(alpha = 0.8f),
                    textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
                )
            }
        }
        if (alignEnd) {
            Spacer(Modifier.width(8.dp))
            IncidentIcon(inc.type)
        }
    }
}

@Composable
fun IncidentIcon(type: IncidentType) {
    val mint = Mint.colors
    val scheme = MaterialTheme.colorScheme
    when (type) {
        IncidentType.GOAL, IncidentType.PENALTY_SCORED ->
            Icon(Icons.Default.SportsSoccer, "Goal", tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        IncidentType.YELLOW_CARD ->
            Box(Modifier.rotate(8f).width(13.dp).height(18.dp).clip(RoundedCornerShape(3.dp)).background(mint.yellowCard))
        IncidentType.RED_CARD ->
            Box(Modifier.rotate(8f).width(13.dp).height(18.dp).clip(RoundedCornerShape(3.dp)).background(mint.redCard))
        IncidentType.SUBSTITUTION ->
            Icon(Icons.Default.SwapVert, "Substitution", tint = mint.sub, modifier = Modifier.size(20.dp))
        else ->
            Icon(Icons.Default.Info, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
