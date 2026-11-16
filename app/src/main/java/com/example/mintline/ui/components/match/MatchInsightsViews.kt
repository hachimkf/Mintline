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
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.Sports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
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

private enum class CommentKind { GOAL, YELLOW, RED, SUB, FOUL, CORNER, OFFSIDE, SHOT, WHISTLE, OTHER }

private fun kindOf(text: String): CommentKind {
    val t = text.lowercase()
    return when {
        t.startsWith("goal") || " goal!" in t || t.contains("scores") -> CommentKind.GOAL
        "second yellow" in t || "red card" in t -> CommentKind.RED
        "yellow card" in t -> CommentKind.YELLOW
        "substitution" in t -> CommentKind.SUB
        "corner" in t -> CommentKind.CORNER
        "offside" in t -> CommentKind.OFFSIDE
        "foul" in t || "free kick" in t -> CommentKind.FOUL
        "attempt" in t || "shot" in t || "saved" in t || "blocked" in t -> CommentKind.SHOT
        "half" in t || "begins" in t || "ends" in t || "lineups" in t || "match" in t -> CommentKind.WHISTLE
        else -> CommentKind.OTHER
    }
}

/** Play-by-play, newest first, on a connected timeline; 8 lines until expanded. */
@Composable
fun CommentaryCard(lines: List<String>) {
    if (lines.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .floatingCard(shape = RoundedCornerShape(32.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(scheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.Forum, null,
                    tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Commentary", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                Text("Minute by minute from ESPN", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.animateContentSize()) {
            val shown = if (expanded) lines else lines.take(8)
            shown.forEachIndexed { i, line ->
                val minute = Regex("""^(\d+'(?:\+\d+')?)\s+""").find(line)?.groupValues?.get(1)
                val text = if (minute != null) line.removePrefix(minute).trim() else line
                val kind = kindOf(text)
                val key = kind == CommentKind.GOAL || kind == CommentKind.RED || kind == CommentKind.YELLOW
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    // minute pill + connector line
                    Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            minute ?: "–",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (kind == CommentKind.GOAL) scheme.onPrimary else scheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (kind == CommentKind.GOAL) scheme.primary else scheme.surfaceContainerHigh)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        if (i < shown.lastIndex) {
                            Box(
                                Modifier.padding(vertical = 4.dp).width(2.dp).weight(1f)
                                    .clip(RoundedCornerShape(50)).background(scheme.outlineVariant.copy(alpha = 0.6f))
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(
                        Modifier
                            .weight(1f)
                            .padding(bottom = if (i < shown.lastIndex) 12.dp else 0.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                when (kind) {
                                    CommentKind.GOAL -> scheme.primaryContainer
                                    CommentKind.RED -> mint.redCard.copy(alpha = 0.14f)
                                    CommentKind.YELLOW -> mint.yellowCard.copy(alpha = 0.18f)
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                }
                            )
                            .padding(horizontal = if (key) 10.dp else 2.dp, vertical = if (key) 8.dp else 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        CommentIcon(kind)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (kind == CommentKind.GOAL) scheme.onPrimaryContainer else scheme.onSurface
                        )
                    }
                }
            }
            if (lines.size > 8) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (expanded) "Show less" else "Show all ${lines.size} updates",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(scheme.surfaceContainerHigh)
                        .bouncyClickable { expanded = !expanded }
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun CommentIcon(kind: CommentKind) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    val I = androidx.compose.material.icons.Icons.Rounded
    Box(Modifier.size(20.dp).padding(top = 1.dp), contentAlignment = Alignment.Center) {
        when (kind) {
            CommentKind.YELLOW, CommentKind.RED -> Box(
                Modifier.width(11.dp).height(15.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (kind == CommentKind.RED) mint.redCard else mint.yellowCard)
            )
            else -> androidx.compose.material3.Icon(
                when (kind) {
                    CommentKind.GOAL -> I.SportsSoccer
                    CommentKind.SUB -> I.SwapVert
                    CommentKind.FOUL -> I.Sports
                    CommentKind.CORNER -> I.Flag
                    CommentKind.OFFSIDE -> I.OutlinedFlag
                    CommentKind.SHOT -> I.GpsFixed
                    CommentKind.WHISTLE -> I.Timer
                    else -> I.ChatBubbleOutline
                },
                null,
                tint = when (kind) {
                    CommentKind.GOAL -> scheme.onPrimaryContainer
                    CommentKind.SUB -> mint.sub
                    else -> scheme.onSurfaceVariant
                },
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
