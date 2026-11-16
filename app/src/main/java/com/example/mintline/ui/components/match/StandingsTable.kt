package com.example.mintline.ui.components.match

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mintline.model.PlayerLeader
import com.example.mintline.model.StandingGroup
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.TeamCrest

private fun parseHex(hex: String?): Color? = hex?.removePrefix("#")?.takeIf { it.length == 6 }?.let {
    runCatching { Color(("FF$it").toLong(16)) }.getOrNull()
}

/**
 * League table. ESPN's qualification zones show as a coloured stripe and a
 * legend; [highlightTeamIds] tints rows (e.g. the two sides of a match).
 */
@Composable
fun StandingsTable(
    group: StandingGroup,
    modifier: Modifier = Modifier,
    highlightTeamIds: Set<String> = emptySet(),
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.width(26.dp))
            Text("Team", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(1f))
            HeaderCell("P"); if (!compact) { HeaderCell("W"); HeaderCell("D"); HeaderCell("L") }
            HeaderCell("GD", 36.dp); HeaderCell("Pts", 36.dp)
        }
        group.rows.forEach { tr ->
            val r = tr.row
            val zone = parseHex(tr.zoneColor)
            val highlighted = r.team.id in highlightTeamIds
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (highlighted) scheme.primaryContainer.copy(alpha = if (Mint.colors.isDark) 0.5f else 1f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(26.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(50)).background(zone ?: Color.Transparent))
                        Spacer(Modifier.width(5.dp))
                        Text("${r.rank}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                    }
                }
                TeamCrest(crestUrl = r.team.crestUrl, teamName = r.team.name, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    r.team.shortName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (highlighted) FontWeight.Medium else null,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Cell("${r.played}"); if (!compact) { Cell("${r.won}"); Cell("${r.drawn}"); Cell("${r.lost}") }
                Cell((if (r.goalDifference > 0) "+" else "") + r.goalDifference, 36.dp)
                Cell("${r.points}", 36.dp, strong = true)
            }
        }
        val zones = group.rows.mapNotNull { tr -> parseHex(tr.zoneColor)?.let { it to (tr.zoneLabel ?: "") } }
            .distinctBy { it.second }.filter { it.second.isNotBlank() }
        if (zones.isNotEmpty() && !compact) {
            Spacer(Modifier.height(10.dp))
            zones.forEach { (c, label) ->
                Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, w: Dp = 26.dp) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.width(w))
}

@Composable
private fun Cell(text: String, w: Dp = 26.dp, strong: Boolean = false) {
    Text(
        text,
        style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        fontWeight = if (strong) FontWeight.Medium else null,
        color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(w)
    )
}

/** Ranked list of top scorers or assist providers. */
@Composable
fun LeadersList(leaders: List<PlayerLeader>, unit: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        leaders.forEachIndexed { i, p ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.width(26.dp))
                TeamCrest(crestUrl = p.teamLogo, teamName = p.teamName, size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(p.teamName, p.matches?.let { "$it matches" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1
                    )
                }
                Text(
                    "${p.value}",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (i == 0) scheme.onPrimary else scheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (i == 0) scheme.primary else scheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}
