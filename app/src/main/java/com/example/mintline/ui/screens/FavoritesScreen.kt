package com.example.mintline.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mintline.data.FootballCatalog
import com.example.mintline.model.Competition
import com.example.mintline.model.Match
import com.example.mintline.ui.components.EmptyFavoritesView
import com.example.mintline.ui.components.FlagCircle
import com.example.mintline.ui.components.isFlagUrl
import com.example.mintline.ui.components.SectionHeader
import com.example.mintline.ui.components.StandardMatchCard
import com.example.mintline.ui.components.bouncyClickable
import com.example.mintline.ui.components.staggeredEntrance

@Composable
fun FavoritesScreen(
    favoriteMatches: List<Match>,
    favoriteCompetitions: List<Competition>,
    onMatchClick: (Match) -> Unit,
    onToggleMatchFavorite: (Match) -> Unit,
    onToggleLeagueFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLeague: (String) -> Unit = {},
    favoriteTeamIds: Set<String> = emptySet(),
    onRemoveTeam: (String) -> Unit = {},
    onEditFavorites: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle("Saved", "Your teams, matches and competitions", Modifier.padding(horizontal = 16.dp)) }

        val teams = favoriteTeamIds.mapNotNull { FootballCatalog.teamById(it) }
        if (favoriteMatches.isEmpty() && favoriteCompetitions.isEmpty() && teams.isEmpty()) {
            item { EmptyFavoritesView() }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Pick favourites",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .bouncyClickable(onClick = onEditFavorites)
                            .padding(horizontal = 22.dp, vertical = 14.dp)
                    )
                }
            }
            return@LazyColumn
        }

        if (teams.isNotEmpty()) {
            item {
                SectionHeader("Teams", Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Edit",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).bouncyClickable(onClick = onEditFavorites).padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(teams, key = { it.espnId }) { t ->
                        SavedChip(t.name, t.logoUrl, round = true, onOpen = {}, onRemove = { onRemoveTeam(t.espnId) })
                    }
                }
            }
        }

        if (favoriteCompetitions.isNotEmpty()) {
            item { SectionHeader("Competitions", Modifier.padding(horizontal = 16.dp)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(favoriteCompetitions, key = { it.id }) { comp ->
                        SavedChip(comp.name, comp.logoUrl, round = false, onOpen = { onOpenLeague(comp.id) }, onRemove = { onToggleLeagueFavorite(comp.id) })
                    }
                }
            }
        }

        if (favoriteMatches.isNotEmpty()) {
            item { SectionHeader("Matches", Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) }
            itemsIndexed(favoriteMatches, key = { _, m -> m.id }) { i, match ->
                StandardMatchCard(
                    match = match,
                    onClick = { onMatchClick(match) },
                    onToggleFavorite = { onToggleMatchFavorite(match) },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem()
                        .staggeredEntrance(i)
                )
            }
        }
    }
}

@Composable
private fun SavedChip(name: String, logoUrl: String?, round: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(scheme.primaryContainer)
            .bouncyClickable(onClick = onOpen)
            .padding(start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFlagUrl(logoUrl)) {
            FlagCircle(url = logoUrl!!, size = 36.dp)
        } else Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF2F3E9)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(logoUrl).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.size(if (round) 26.dp else 22.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .bouncyClickable(pressedScale = 0.8f, onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, "Remove", tint = scheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
        }
    }
}
