package com.example.mintline.ui.components.match

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mintline.model.Bracket
import com.example.mintline.model.BracketTie
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.TeamCrest
import com.example.mintline.ui.components.staggeredEntrance

private val TieWidth = 168.dp
private val TieHeight = 76.dp
private val ColumnGap = 34.dp
private val BaseGap = 12.dp

/**
 * Knockout draw as a horizontally scrolling chart: one column per round,
 * ties centred between the two ties that feed them, curved connectors,
 * winners highlighted and the final crowned with a trophy.
 */
@Composable
fun BracketView(bracket: Bracket, modifier: Modifier = Modifier, highlightNames: Set<String> = emptySet()) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    val rounds = bracket.rounds
    if (rounds.isEmpty()) return
    val firstCount = rounds.first().ties.size.coerceAtLeast(1)
    val slot = TieHeight + BaseGap
    val totalHeight = slot * firstCount
    val lineColor = scheme.outlineVariant

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp)) {
            rounds.forEachIndexed { r, round ->
                val isFinal = r == rounds.lastIndex
                Column(Modifier.width(TieWidth + if (r < rounds.lastIndex) ColumnGap else 0.dp)) {
                    // round header
                    Row(
                        Modifier
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isFinal) mint.gold.copy(alpha = 0.2f) else scheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isFinal) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = mint.gold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(round.name, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                    }
                    // ties, spaced so each sits between its two feeders
                    val factor = 1 shl r // 1, 2, 4, 8...
                    val step = slot * factor.coerceAtMost(firstCount)
                    Box(Modifier.height(totalHeight)) {
                        round.ties.forEachIndexed { i, tie ->
                            val top: Dp = step * i + (step - TieHeight) / 2
                            Box(Modifier.padding(top = top.coerceAtLeast(0.dp)).staggeredEntrance(i + r * 4, stepMs = 35)) {
                                TieCard(tie, isFinal, highlightNames)
                            }
                        }
                        // connectors to the next round
                        if (r < rounds.lastIndex) {
                            Canvas(Modifier.width(TieWidth + ColumnGap).height(totalHeight)) {
                                val stepPx = step.toPx()
                                val tieH = TieHeight.toPx()
                                val x0 = TieWidth.toPx()
                                val x1 = x0 + ColumnGap.toPx()
                                round.ties.indices.chunked(2).forEach { pair ->
                                    val ys = pair.map { stepPx * it + (stepPx - tieH) / 2 + tieH / 2 }
                                    val target = if (ys.size == 2) (ys[0] + ys[1]) / 2 else ys[0]
                                    ys.forEach { y ->
                                        val path = Path().apply {
                                            moveTo(x0, y)
                                            cubicTo((x0 + x1) / 2, y, (x0 + x1) / 2, target, x1, target)
                                        }
                                        drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                                    }
                                    drawCircle(lineColor, 3.dp.toPx(), Offset(x1, target))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TieCard(tie: BracketTie, isFinal: Boolean, highlight: Set<String>) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    Column(
        Modifier
            .width(TieWidth)
            .height(TieHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(if (mint.isDark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest)
            .then(if (isFinal) Modifier.background(mint.gold.copy(alpha = 0.08f)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        TieRow(tie.homeName, tie.homeLogo, tie.homeScore, tie.homeAdvanced == true, tie.homeAdvanced == false, tie.homeName in highlight)
        TieRow(tie.awayName, tie.awayLogo, tie.awayScore, tie.homeAdvanced == false, tie.homeAdvanced == true, tie.awayName in highlight)
    }
}

@Composable
private fun TieRow(name: String, logo: String?, score: String?, won: Boolean, lost: Boolean, highlighted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(50)).background(if (won) scheme.primary else Color.Transparent))
        Spacer(Modifier.width(6.dp))
        TeamCrest(crestUrl = logo, teamName = name, size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (won || highlighted) FontWeight.Medium else null,
            color = if (lost) scheme.onSurfaceVariant else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (highlighted) Box(Modifier.padding(end = 4.dp).size(6.dp).clip(CircleShape).background(Mint.colors.live))
        Text(score ?: "", style = MaterialTheme.typography.titleSmall, color = if (lost) scheme.onSurfaceVariant else scheme.onSurface)
    }
}
