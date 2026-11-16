package com.example.mintline.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mintline.model.Match
import com.example.mintline.model.MatchStatus
import com.example.mintline.theme.Mint
import com.example.mintline.ui.main.GroupKind
import com.example.mintline.ui.main.LeagueGroup

/**
 * One floating card per competition. The header always shows; the compact
 * match list folds away so the feed reads like an index of leagues.
 */
@Composable
fun LeagueSectionCard(
    group: LeagueGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMatchClick: (Match) -> Unit,
    onToggleFavorite: (Match) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    val live = group.matches.count { it.isLive }
    val chevron by animateFloatAsState(
        if (expanded) 0f else -90f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "chev"
    )
    val highlighted = group.kind == GroupKind.MY_TEAMS

    Column(
        modifier
            .fillMaxWidth()
            .floatingCard(
                shape = RoundedCornerShape(30.dp),
                color = if (highlighted) scheme.primaryContainer.copy(alpha = if (mint.isDark) 0.55f else 1f) else null
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .bouncyClickable(pressedScale = 0.98f, onClick = onToggle)
                .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (highlighted) scheme.primary else Color(0xFFF2F3E9)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    highlighted -> Icon(Icons.Default.Favorite, null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                    !group.logoUrl.isNullOrBlank() -> AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(group.logoUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        contentScale = ContentScale.Fit
                    )
                    else -> MintlineLogo(size = 40.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (group.kind == GroupKind.FAVORITE) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Star, "Favourite", tint = mint.gold, modifier = Modifier.size(15.dp))
                    }
                }
                group.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = (if (highlighted) scheme.onPrimaryContainer else scheme.onSurfaceVariant).copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }
            if (live > 0) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(mint.live.copy(alpha = 0.15f))
                        .padding(start = 2.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiveDot(size = 5.dp)
                    Text("$live", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = mint.live)
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(
                "${group.matches.size}",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHighest.copy(alpha = 0.7f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevron)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = 500f)) + fadeOut()
        ) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                group.matches.forEach { m ->
                    ScoreTile(
                        m,
                        onClick = { onMatchClick(m) },
                        onToggleFavorite = { onToggleFavorite(m) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/** Two-line score row: status column, teams with crests, scores, star. ~64dp tall. */
@Composable
fun CompactMatchRow(match: Match, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    val favScale by animateFloatAsState(
        if (match.isFavorite) 1.2f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "fav"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (match.isLive) mint.live.copy(alpha = if (mint.isDark) 0.08f else 0.06f) else Color.Transparent)
            .bouncyClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(58.dp), contentAlignment = Alignment.Center) {
            when {
                match.isLive -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LiveDot(size = 5.dp)
                    Text(
                        match.statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = mint.live,
                        maxLines = 1
                    )
                }
                match.status == MatchStatus.FULL_TIME -> Text(
                    "FT", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant
                )
                match.status == MatchStatus.POSTPONED || match.status == MatchStatus.CANCELLED -> Text(
                    if (match.status == MatchStatus.POSTPONED) "PPD" else "CAN",
                    style = MaterialTheme.typography.labelMedium, color = mint.live
                )
                else -> Text(
                    match.kickoffFormatted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .width(1.dp)
                .height(36.dp)
                .background(scheme.outlineVariant.copy(alpha = 0.6f))
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val hs = match.homeScore
            val aws = match.awayScore
            TeamLine(match.homeTeam.shortName, match.homeTeam.name, match.homeTeam.crestUrl, hs, winner = hs != null && aws != null && hs > aws, live = match.isLive)
            TeamLine(match.awayTeam.shortName, match.awayTeam.name, match.awayTeam.crestUrl, aws, winner = hs != null && aws != null && aws > hs, live = match.isLive)
        }
        Box(
            Modifier
                .padding(start = 4.dp)
                .size(34.dp)
                .clip(CircleShape)
                .bouncyClickable(pressedScale = 0.8f, onClick = onToggleFavorite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (match.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                "Favorite",
                tint = if (match.isFavorite) mint.gold else scheme.outline,
                modifier = Modifier.size(18.dp).scale(favScale)
            )
        }
    }
}

@Composable
private fun TeamLine(short: String, full: String, crest: String?, score: Int?, winner: Boolean, live: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamCrest(crestUrl = crest, teamName = full, size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            short,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (winner) FontWeight.Medium else FontWeight.Normal,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (score != null) {
            Text(
                "$score",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = when {
                    live -> Mint.colors.live
                    winner -> scheme.onSurface
                    else -> scheme.onSurfaceVariant
                }
            )
        }
    }
}
