package com.example.mintline.ui.components.match

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mintline.model.MatchStatistic
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.AnimatedCountText
import kotlinx.coroutines.delay

private fun MatchStatistic.isPossession() = label.contains("possession", ignoreCase = true)

/** Possession donut + every other stat as a mirrored, animated bar. */
@Composable
fun MatchStatsPanel(
    stats: List<MatchStatistic>,
    homeName: String,
    awayName: String,
    modifier: Modifier = Modifier
) {
    val possession = stats.firstOrNull { it.isPossession() }
    val rest = stats.filterNot { it.isPossession() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (possession != null) {
            PossessionRing(possession, homeName, awayName)
        }
        rest.forEachIndexed { i, stat -> StatBarRow(stat, delayMs = 120 + i * 70) }
    }
}

@Composable
private fun PossessionRing(stat: MatchStatistic, homeName: String, awayName: String) {
    val mint = Mint.colors
    val scheme = MaterialTheme.colorScheme
    val reduced = Mint.reducedMotion
    val sweep = remember(stat) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(stat) { if (!reduced) sweep.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 60f)) }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            AnimatedCountText(stat.homeValue, MaterialTheme.typography.headlineLarge, mint.home)
            Text(homeName, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp)) {
                val strokeW = 18.dp.toPx()
                val inset = strokeW / 2f
                val arcSize = Size(size.width - strokeW, size.height - strokeW)
                val gap = 6f
                val homeSweep = 360f * stat.homeRatio.coerceIn(0f, 1f)
                val awaySweep = 360f - homeSweep
                drawArc(
                    color = scheme.surfaceContainerHighest,
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(strokeW)
                )
                drawArc(
                    color = mint.home,
                    startAngle = -90f + gap / 2,
                    sweepAngle = ((homeSweep - gap).coerceAtLeast(0f)) * sweep.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(strokeW, cap = StrokeCap.Round)
                )
                drawArc(
                    color = mint.away,
                    startAngle = -90f - gap / 2,
                    sweepAngle = -((awaySweep - gap).coerceAtLeast(0f)) * sweep.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(strokeW, cap = StrokeCap.Round)
                )
            }
            Text(
                "Possession",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            AnimatedCountText(stat.awayValue, MaterialTheme.typography.headlineLarge, mint.away)
            Text(awayName, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun StatBarRow(stat: MatchStatistic, delayMs: Int) {
    val mint = Mint.colors
    val scheme = MaterialTheme.colorScheme
    val reduced = Mint.reducedMotion
    val r = stat.homeRatio.coerceIn(0f, 1f)
    val maxSide = maxOf(r, 1f - r).coerceAtLeast(0.0001f)
    val homeFrac = r / maxSide
    val awayFrac = (1f - r) / maxSide
    val homeLeads = r > 0.5f
    val awayLeads = r < 0.5f

    val grow = remember(stat) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(stat) {
        if (!reduced) {
            delay(delayMs.toLong())
            grow.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 120f))
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ValueChip(stat.homeValue, highlighted = homeLeads, color = mint.home, onColor = mint.onHome, delayMs = delayMs)
            Text(
                stat.label,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            ValueChip(stat.awayValue, highlighted = awayLeads, color = mint.away, onColor = mint.onAway, delayMs = delayMs)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // home grows from the centre to the left
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHighest)) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(homeFrac * grow.value.coerceAtLeast(0f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(if (homeLeads || !awayLeads) mint.home else mint.home.copy(alpha = 0.45f))
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHighest)) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(awayFrac * grow.value.coerceAtLeast(0f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(if (awayLeads || !homeLeads) mint.away else mint.away.copy(alpha = 0.45f))
                )
            }
        }
    }
}

@Composable
private fun ValueChip(
    value: String,
    highlighted: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onColor: androidx.compose.ui.graphics.Color,
    delayMs: Int
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) color else androidx.compose.ui.graphics.Color.Transparent)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedCountText(
            value,
            MaterialTheme.typography.titleMedium,
            if (highlighted) onColor else scheme.onSurface,
            delayMs = delayMs,
            fontWeight = FontWeight.Medium
        )
    }
}
