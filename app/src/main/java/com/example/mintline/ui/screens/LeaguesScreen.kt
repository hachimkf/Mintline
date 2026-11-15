package com.example.mintline.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mintline.model.Competition
import com.example.mintline.model.FavoriteType
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.bouncyClickable
import com.example.mintline.ui.components.floatingCard
import com.example.mintline.ui.components.staggeredEntrance

@Composable
fun LeaguesScreen(
    competitions: List<Competition>,
    onSelectLeague: (String) -> Unit,
    onToggleFavorite: (String, FavoriteType) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            ScreenTitle("Competitions", "Every league and cup you can follow")
        }
        itemsIndexed(competitions, key = { _, c -> c.id }) { i, comp ->
            CompetitionTile(
                competition = comp,
                onClick = { onSelectLeague(comp.id) },
                onToggleFavorite = { onToggleFavorite(comp.id, FavoriteType.LEAGUE) },
                modifier = Modifier.staggeredEntrance(i)
            )
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 6.dp, top = 8.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompetitionTile(
    competition: Competition,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    val favScale by animateFloatAsState(
        if (competition.isFavorite) 1.2f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "fav"
    )
    Column(
        modifier
            .fillMaxWidth()
            .floatingCard(shape = RoundedCornerShape(32.dp))
            .bouncyClickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (mint.isDark) Color(0xFFF2F3E9) else scheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                if (!competition.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(competition.logoUrl).crossfade(true).build(),
                        contentDescription = competition.name,
                        modifier = Modifier.size(38.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(competition.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = Color(0xFF1A1C16))
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (competition.isFavorite) mint.gold.copy(alpha = 0.18f) else scheme.surfaceContainerHigh)
                    .bouncyClickable(pressedScale = 0.82f, onClick = onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (competition.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    "Favorite league",
                    tint = if (competition.isFavorite) mint.gold else scheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp).scale(favScale)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            competition.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(competition.country, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Text(
            if (competition.matchCount > 0) "${competition.matchCount} fixtures" else "Browse fixtures",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSecondaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(scheme.secondaryContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
