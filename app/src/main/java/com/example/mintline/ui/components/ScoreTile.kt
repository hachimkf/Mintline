package com.example.mintline.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mintline.model.Match
import com.example.mintline.model.MatchStatus
import com.example.mintline.theme.Mint

private fun teamColor(hex: String?): Color? = hex?.removePrefix("#")?.takeIf { it.length == 6 }?.let {
    runCatching { Color(("FF$it").toLong(16)) }.getOrNull()
}

/**
 * Scoreboard tile: each match is its own rounded card with a soft glow in
 * both clubs' colours, big scores either side and the clock in the middle.
 * Tiles are spaced apart so live highlights never touch.
 */
@Composable
fun ScoreTile(
    match: Match,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    val glow = if (mint.isDark) 0.30f else 0.16f
    val home = teamColor(match.homeTeam.colorHex) ?: mint.home
    val away = teamColor(match.awayTeam.colorHex) ?: mint.away
    val base = if (mint.isDark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
    val shape = RoundedCornerShape(26.dp)
    val hs = match.homeScore
    val aws = match.awayScore
    val played = hs != null && aws != null
    val favScale by animateFloatAsState(
        if (match.isFavorite) 1.2f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "fav"
    )

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(base)
            .background(Brush.horizontalGradient(listOf(home.copy(alpha = glow), Color.Transparent, away.copy(alpha = glow))))
            .border(
                1.dp,
                if (match.isLive) mint.live.copy(alpha = 0.55f) else Color.White.copy(alpha = if (mint.isDark) 0.06f else 0.5f),
                shape
            )
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TileTeam(match.homeTeam.shortName, match.homeTeam.name, match.homeTeam.crestUrl, Modifier.width(76.dp))
            Text(
                if (played) "$hs" else "",
                style = MaterialTheme.typography.displaySmall,
                color = if (played && aws!! > hs!!) scheme.onSurfaceVariant else scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
                when {
                    match.isLive -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(size = 5.dp)
                            Text(match.statusText, style = MaterialTheme.typography.titleSmall, color = mint.live, maxLines = 1, softWrap = false)
                        }
                    }
                    match.status == MatchStatus.FULL_TIME ->
                        Text("Final", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                    match.status == MatchStatus.POSTPONED || match.status == MatchStatus.CANCELLED ->
                        Text(if (match.status == MatchStatus.POSTPONED) "Postponed" else "Cancelled", style = MaterialTheme.typography.labelLarge, color = mint.live)
                    else -> Text(
                        match.kickoffFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Text(
                if (played) "$aws" else "",
                style = MaterialTheme.typography.displaySmall,
                color = if (played && hs!! > aws!!) scheme.onSurfaceVariant else scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            TileTeam(match.awayTeam.shortName, match.awayTeam.name, match.awayTeam.crestUrl, Modifier.width(76.dp))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(30.dp)
                .clip(CircleShape)
                .bouncyClickable(pressedScale = 0.8f, onClick = onToggleFavorite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (match.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                "Favorite",
                tint = if (match.isFavorite) mint.gold else scheme.outline,
                modifier = Modifier.size(16.dp).scale(favScale)
            )
        }
    }
}

@Composable
private fun TileTeam(short: String, full: String, crest: String?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamCrest(crestUrl = crest, teamName = full, size = 38.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            short,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
