package com.example.mintline.ui.components.match

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mintline.model.FormGame
import com.example.mintline.model.H2HGame
import com.example.mintline.model.MatchInsights
import com.example.mintline.model.TopPerformer
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.TeamCrest
import com.example.mintline.ui.components.bouncyClickable
import com.example.mintline.ui.components.floatingCard
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
private fun InsightCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .floatingCard(shape = RoundedCornerShape(32.dp))
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

private fun shortDate(iso: String): String = runCatching {
    val inFmt = SimpleDateFormat(if (iso.length > 17) "yyyy-MM-dd'T'HH:mm:ss" else "yyyy-MM-dd'T'HH:mm", Locale.ENGLISH)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    SimpleDateFormat("d MMM yy", Locale.ENGLISH).format(inFmt.parse(iso.removeSuffix("Z"))!!)
}.getOrDefault("")

/** All pre-/post-match context ESPN offers, in the Info tab. */
@Composable
fun MatchInsightsSection(
    insights: MatchInsights,
    homeName: String,
    awayName: String,
    homeTeamId: String,
    awayTeamId: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (insights.homeForm.isNotEmpty() || insights.awayForm.isNotEmpty()) {
            InsightCard("Form", "Last five games") {
                FormRow(homeName, insights.homeForm)
                Spacer(Modifier.height(14.dp))
                FormRow(awayName, insights.awayForm)
            }
        }
        if (insights.headToHead.isNotEmpty()) {
            InsightCard("Head to head", "Recent meetings") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    insights.headToHead.forEach { H2HRow(it) }
                }
            }
        }
        insights.table?.let { t ->
            InsightCard("Table", t.name.ifBlank { null }) {
                StandingsTable(t, highlightTeamIds = setOf(homeTeamId, awayTeamId), compact = true)
            }
        }
        if (insights.performers.isNotEmpty()) {
            InsightCard("Top performers") {
                PerformersGrid(insights.performers, homeName, awayName)
            }
        }
    }
}

@Composable
private fun FormRow(team: String, games: List<FormGame>) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    Text(team, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    if (games.isEmpty()) {
        Text("No recent games", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        games.takeLast(5).forEach { g ->
            val (bg, fg) = when (g.result.uppercase()) {
                "W" -> scheme.primary to scheme.onPrimary
                "L" -> mint.live to mint.onLive
                else -> scheme.surfaceContainerHighest to scheme.onSurface
            }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerHigh).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                    Text(g.result.uppercase().take(1), style = MaterialTheme.typography.labelMedium, color = fg)
                }
                Spacer(Modifier.height(6.dp))
                TeamCrest(crestUrl = g.opponentLogo, teamName = g.opponent, size = 22.dp)
                Spacer(Modifier.height(4.dp))
                Text(g.score, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, maxLines = 1)
            }
        }
    }
}

@Composable
private fun H2HRow(g: H2HGame) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(shortDate(g.dateIso), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Text(g.homeName, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        TeamCrest(crestUrl = g.homeLogo, teamName = g.homeName, size = 20.dp)
        Text(
            "${g.homeScore} – ${g.awayScore}",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        TeamCrest(crestUrl = g.awayLogo, teamName = g.awayName, size = 20.dp)
        Spacer(Modifier.width(6.dp))
        Text(g.awayName, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PerformersGrid(performers: List<TopPerformer>, homeName: String, awayName: String) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    val byCategory = performers.groupBy { it.category }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        byCategory.forEach { (cat, list) ->
            Text(cat, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true, false).forEach { home ->
                    val p = list.firstOrNull { it.isHome == home }
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerHigh).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (home) mint.home else mint.away))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p?.player ?: "—", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (home) homeName else awayName, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text(p?.value ?: "", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    }
                }
            }
        }
    }
}

/** Play-by-play, newest first; shows 8 lines until expanded. */
@Composable
fun CommentaryCard(lines: List<String>) {
    if (lines.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    InsightCard("Commentary", "Minute by minute from ESPN") {
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (if (expanded) lines else lines.take(8)).forEach { line ->
                val minute = Regex("""^(\d+'(?:\+\d+')?)\s+""").find(line)?.groupValues?.get(1)
                Row {
                    Text(
                        minute ?: "•",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                        modifier = Modifier.width(52.dp)
                    )
                    Text(
                        if (minute != null) line.removePrefix(minute).trim() else line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface
                    )
                }
            }
            if (lines.size > 8) {
                Text(
                    if (expanded) "Show less" else "Show all ${lines.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .bouncyClickable { expanded = !expanded }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
        }
    }
}
