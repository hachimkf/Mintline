package com.example.mintline.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mintline.model.Competition
import com.example.mintline.model.DateItem
import com.example.mintline.model.Match
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.CompetitionFilterRow
import com.example.mintline.ui.components.DateSelectorStrip
import com.example.mintline.ui.components.ErrorOrOfflineBanner
import com.example.mintline.ui.components.HeroLiveMatchCard
import com.example.mintline.ui.components.LiveDot
import com.example.mintline.ui.components.LeagueSectionCard
import com.example.mintline.ui.main.LeagueGroup
import com.example.mintline.ui.components.MintlineLogo
import com.example.mintline.ui.components.LoadingStateView
import com.example.mintline.ui.components.StandardMatchCard
import com.example.mintline.ui.components.TonalIconButton
import com.example.mintline.ui.components.bouncyClickable
import com.example.mintline.ui.components.staggeredEntrance
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    matches: List<Match>,
    liveMatches: List<Match>,
    leagueGroups: List<LeagueGroup>,
    playerHits: List<com.example.mintline.model.PlayerHit> = emptyList(),
    onOpenPlayer: (Int) -> Unit = {},
    competitions: List<Competition>,
    selectedCompetitionId: String?,
    onSelectCompetition: (String?) -> Unit,
    dateList: List<DateItem>,
    onSelectDate: (DateItem) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    isLoading: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    lastUpdatedText: String,
    onRefresh: () -> Unit,
    onMatchClick: (Match) -> Unit,
    onToggleFavorite: (Match) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val heroMatches = (liveMatches.ifEmpty { matches.filter { it.isLive } }).take(5)
    val selectedKey = dateList.firstOrNull { it.isSelected }?.dateString

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                HomeHeader(
                    liveCount = heroMatches.size,
                    lastUpdatedText = lastUpdatedText,
                    isSearchActive = isSearchActive,
                    onToggleSearch = onToggleSearch,
                    onRefresh = onRefresh
                )
            }

            item(key = "search") {
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = expandVertically(spring(dampingRatio = 0.7f)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SearchField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            if (isSearchActive && playerHits.isNotEmpty()) {
                item(key = "players") {
                    Column {
                        Text(
                            "Players",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 22.dp, bottom = 8.dp)
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(playerHits, key = { it.id }) { p -> PlayerChip(p) { onOpenPlayer(p.id) } }
                        }
                    }
                }
            }

            item(key = "dates") {
                DateSelectorStrip(
                    dates = dateList,
                    onDateSelected = onSelectDate,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item(key = "comps") {
                CompetitionFilterRow(
                    competitions = competitions,
                    selectedCompetitionId = selectedCompetitionId,
                    onSelectCompetition = onSelectCompetition
                )
            }

            if (errorMessage != null) {
                item(key = "error") {
                    ErrorOrOfflineBanner(
                        errorMessage = errorMessage,
                        onRetry = onRefresh,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            if (isLoading && matches.isEmpty()) {
                item(key = "loading") { LoadingStateView() }
                return@LazyColumn
            }

            if (heroMatches.isNotEmpty() && searchQuery.isBlank()) {
                item(key = "liveHeader") {
                    LiveHeader(count = heroMatches.size, modifier = Modifier.padding(horizontal = 16.dp))
                }
                item(key = "hero") {
                    val pager = rememberPagerState { heroMatches.size }
                    Column {
                        HorizontalPager(
                            state = pager,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            pageSpacing = 12.dp,
                            key = { heroMatches[it].id }
                        ) { page ->
                            val m = heroMatches[page]
                            HeroLiveMatchCard(
                                match = m,
                                onClick = { onMatchClick(m) },
                                onToggleFavorite = { onToggleFavorite(m) }
                            )
                        }
                        if (heroMatches.size > 1) {
                            PagerDots(
                                count = heroMatches.size,
                                selected = pager.currentPage,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 12.dp)
                            )
                        }
                    }
                }
            }

            if (leagueGroups.isEmpty()) {
                item(key = "empty") { EmptyFixtures() }
            } else {
                val searching = searchQuery.isNotBlank()
                fun isExpanded(g: LeagueGroup) = searching || (expanded[g.key] ?: g.defaultExpanded)
                item(key = "allHeader") {
                    val anyOpen = leagueGroups.any { isExpanded(it) }
                    AllMatchesHeader(
                        total = leagueGroups.sumOf { it.matches.size },
                        leagues = leagueGroups.size,
                        anyExpanded = anyOpen,
                        onToggleAll = { leagueGroups.forEach { g -> expanded[g.key] = !anyOpen } },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                leagueGroups.forEachIndexed { i, group ->
                    item(key = "g_${group.key}") {
                        LeagueSectionCard(
                            group = group,
                            expanded = isExpanded(group),
                            onToggle = { expanded[group.key] = !isExpanded(group) },
                            onMatchClick = onMatchClick,
                            onToggleFavorite = onToggleFavorite,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem()
                                .staggeredEntrance(i, key = selectedKey to selectedCompetitionId)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    liveCount: Int,
    lastUpdatedText: String,
    isSearchActive: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MintlineLogo(size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Text(
                "Matchday",
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface
            )
        }
        TonalIconButton(
            icon = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
            contentDescription = "Search",
            onClick = { onToggleSearch(!isSearchActive) },
            container = if (isSearchActive) scheme.primary else scheme.surfaceContainerHigh,
            tint = if (isSearchActive) scheme.onPrimary else scheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        TonalIconButton(
            icon = Icons.Default.Refresh,
            contentDescription = "Refresh · updated $lastUpdatedText",
            onClick = onRefresh
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Clubs, competitions, fixtures…", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Close, "Clear",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .bouncyClickable { onQueryChange("") }
            )
        }
    }
}

@Composable
private fun LiveHeader(count: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LiveDot(size = 8.dp)
        Spacer(Modifier.width(6.dp))
        Text("Live now", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelLarge,
            color = Mint.colors.live,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Mint.colors.live.copy(alpha = 0.14f))
                .padding(horizontal = 9.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PagerDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val w by animateDpAsState(
                if (i == selected) 22.dp else 7.dp,
                spring(dampingRatio = 0.55f, stiffness = 400f), label = "dot"
            )
            Box(
                Modifier
                    .height(7.dp)
                    .width(w)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == selected) scheme.primary else scheme.outlineVariant)
            )
        }
    }
}

@Composable
private fun AllMatchesHeader(
    total: Int,
    leagues: Int,
    anyExpanded: Boolean,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text("All matches", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
            Text(
                "$total games · $leagues competitions",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant
            )
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(scheme.surfaceContainerHigh)
                .bouncyClickable(pressedScale = 0.92f, onClick = onToggleAll)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (anyExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                null, tint = scheme.onSurface, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (anyExpanded) "Collapse all" else "Expand all",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyFixtures() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MintlineLogo(size = 88.dp)
        Spacer(Modifier.height(16.dp))
        Text("No fixtures here", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
        Text(
            "Try another day or competition.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlayerChip(p: com.example.mintline.model.PlayerHit, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .bouncyClickable(pressedScale = 0.94f, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        coil.compose.AsyncImage(
            model = p.photoUrl,
            contentDescription = p.name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(scheme.surfaceContainerLowest)
        )
        Spacer(Modifier.height(8.dp))
        Text(p.name, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        p.clubName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
