package com.example.mintline.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mintline.model.FavoriteType
import com.example.mintline.model.MatchDetail
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.LiquidGlassBottomBar
import com.example.mintline.ui.components.UpdateBanner
import com.example.mintline.ui.components.MintlineTab
import com.example.mintline.ui.components.glass.LocalGlassBackdrop
import com.example.mintline.ui.components.glass.edgeBlur
import com.example.mintline.ui.components.glass.glassSource
import com.example.mintline.ui.components.glass.rememberGlassBackdrop
import com.example.mintline.ui.screens.FavoritesScreen
import com.example.mintline.ui.screens.HomeScreen
import com.example.mintline.ui.screens.LeaguePageScreen
import com.example.mintline.ui.screens.LeaguesScreen
import com.example.mintline.ui.screens.MatchDetailScreen
import com.example.mintline.ui.screens.OnboardingScreen
import com.example.mintline.ui.screens.SettingsScreen

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reduced = Mint.reducedMotion
    val backdrop = rememberGlassBackdrop()
    // keep the last detail around so the exit animation has content to show
    var lastDetail by remember { mutableStateOf<MatchDetail?>(null) }
    state.selectedMatchDetail?.let { lastDetail = it }

    BackHandler(enabled = !state.showOnboarding && (state.selectedMatchDetail != null || state.isSearchActive || state.openLeagueId != null)) {
        if (state.selectedMatchDetail != null) viewModel.closeMatchDetail()
        else if (state.openLeagueId != null) viewModel.closeLeague()
        else if (state.isSearchActive) viewModel.toggleSearch(false)
    }

    // Home: back goes to Matches first, then needs a second press within 2s to leave the app
    val context = LocalContext.current
    var lastBackAt by remember { mutableStateOf(0L) }
    BackHandler(enabled = !state.showOnboarding && state.selectedMatchDetail == null && !state.isSearchActive && state.openLeagueId == null) {
        if (state.activeTab != MintlineTab.MATCHES) {
            viewModel.selectTab(MintlineTab.MATCHES)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackAt < 2000) {
                (context as? android.app.Activity)?.finish()
            } else {
                lastBackAt = now
                android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Screens scroll underneath the floating glass bar
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
    val contentPadding = PaddingValues(top = top, bottom = bottom)

    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .glassSource(backdrop)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AnimatedContent(
                    targetState = state.activeTab,
                    transitionSpec = {
                        if (reduced) fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                        else (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 400f), initialScale = 0.96f)) togetherWith
                            (fadeOut(tween(120)) + scaleOut(targetScale = 1.02f))
                    },
                    label = "tabTransition"
                ) { tab ->
                    when (tab) {
                        MintlineTab.MATCHES -> HomeScreen(
                            matches = state.displayMatches,
                            liveMatches = state.liveMatches,
                            leagueGroups = state.leagueGroups,
                            competitions = state.filterChips,
                            selectedCompetitionId = state.selectedCompetitionId,
                            onSelectCompetition = viewModel::selectCompetition,
                            dateList = state.dateList,
                            onSelectDate = viewModel::selectDate,
                            searchQuery = state.searchQuery,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            isSearchActive = state.isSearchActive,
                            onToggleSearch = viewModel::toggleSearch,
                            isLoading = state.isLoading,
                            isRefreshing = state.isRefreshing,
                            errorMessage = state.errorMessage,
                            lastUpdatedText = state.lastUpdatedText,
                            onRefresh = viewModel::refresh,
                            onMatchClick = { m -> viewModel.openMatchDetail(m.id, m.leagueId) },
                            onToggleFavorite = { m -> viewModel.toggleFavorite(m.id, FavoriteType.MATCH) },
                            contentPadding = contentPadding
                        )
                        MintlineTab.LEAGUES -> LeaguesScreen(
                            competitions = state.competitions,
                            onSelectLeague = viewModel::openLeague,
                            onToggleFavorite = viewModel::toggleFavorite,
                            contentPadding = contentPadding
                        )
                        MintlineTab.FAVORITES -> FavoritesScreen(
                            favoriteMatches = state.favoriteMatches,
                            favoriteCompetitions = state.competitions.filter { it.isFavorite },
                            onMatchClick = { m -> viewModel.openMatchDetail(m.id, m.leagueId) },
                            onToggleMatchFavorite = { m -> viewModel.toggleFavorite(m.id, FavoriteType.MATCH) },
                            onToggleLeagueFavorite = { id -> viewModel.toggleFavorite(id, FavoriteType.LEAGUE) },
                            favoriteTeamIds = state.favoriteTeamIds,
                            onRemoveTeam = { id -> viewModel.toggleFavorite(id, FavoriteType.TEAM) },
                            onEditFavorites = viewModel::openOnboarding,
                            onOpenLeague = { id ->
                                viewModel.selectCompetition(id)
                                viewModel.selectTab(MintlineTab.MATCHES)
                            },
                            contentPadding = contentPadding
                        )
                        MintlineTab.SETTINGS -> SettingsScreen(
                            themeMode = state.themeMode,
                            onSelectThemeMode = viewModel::setThemeMode,
                            refreshIntervalSec = state.autoRefreshIntervalSec,
                            onSelectRefreshInterval = viewModel::setAutoRefreshInterval,
                            reducedMotion = state.reducedMotion,
                            onToggleReducedMotion = viewModel::setReducedMotion,
                            lastUpdatedText = state.lastUpdatedText,
                            onClearFavorites = viewModel::clearFavorites,
                            onEditFavorites = viewModel::openOnboarding,
                            matchNotificationsEnabled = state.matchNotificationsEnabled,
                            onToggleMatchNotifications = viewModel::setMatchNotifications,
                            timeZoneLabel = state.timeZoneLabel,
                            isCustomTimeZone = state.isCustomTimeZone,
                            onCycleTimeZone = viewModel::cycleTimeZone,
                            onResetTimeZone = viewModel::resetTimeZoneToDefault,
                            appVersion = state.appVersion,
                            updateLabel = state.updateMessage
                                ?: state.update?.let { "Version ${it.version} available — tap to install" },
                            onCheckUpdates = {
                                if (state.update != null) viewModel.startUpdate() else viewModel.checkForUpdates(manual = true)
                            },
                            contentPadding = contentPadding
                        )
                    }
                }
            }

            // Gradient blur edges: content softly dissolves under the status bar and the nav bar
            val bg = MaterialTheme.colorScheme.background
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(top + 20.dp)
                    .edgeBlur(fromTop = true, scrimColor = bg, scrimAlpha = 0.7f)
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottom + 12.dp)
                    .edgeBlur(fromTop = false, scrimColor = bg, scrimAlpha = 0.85f)
            )

            AnimatedVisibility(
                visible = state.selectedMatchDetail == null && state.openLeagueId == null,
                enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                LiquidGlassBottomBar(
                    currentTab = state.activeTab,
                    onTabSelected = viewModel::selectTab,
                    backdrop = backdrop,
                    modifier = Modifier.navigationBarsPadding()
                )
            }

            // League page (table, top scorers, assists) sits below the match screen
            var lastLeague by remember { mutableStateOf<String?>(null) }
            state.openLeagueId?.let { lastLeague = it }
            AnimatedVisibility(
                visible = state.openLeagueId != null,
                enter = if (reduced) fadeIn() else slideInVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) { it / 4 } + fadeIn(),
                exit = if (reduced) fadeOut() else slideOutVertically { it / 4 } + fadeOut()
            ) {
                lastLeague?.let { id ->
                    LeaguePageScreen(
                        leagueId = id,
                        tables = state.leagueTables,
                        leaders = state.leagueLeaders,
                        loading = state.leagueLoading,
                        favoriteTeamIds = state.favoriteTeamIds,
                        onBack = viewModel::closeLeague,
                        onShowMatches = {
                            viewModel.closeLeague()
                            viewModel.selectCompetition(id)
                            viewModel.selectTab(MintlineTab.MATCHES)
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = state.selectedMatchDetail != null,
                enter = if (reduced) fadeIn() else slideInVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) { it / 4 } + fadeIn(),
                exit = if (reduced) fadeOut() else slideOutVertically { it / 4 } + fadeOut()
            ) {
                lastDetail?.let { detail ->
                    MatchDetailScreen(
                        detail = detail,
                        onBack = viewModel::closeMatchDetail,
                        onToggleFavorite = viewModel::toggleFavorite
                    )
                }
            }

            val update = state.update
            AnimatedVisibility(
                visible = update != null && !state.updateDismissed && state.selectedMatchDetail == null && !state.showOnboarding,
                enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (update != null) {
                    UpdateBanner(
                        info = update,
                        progress = state.updateProgress,
                        onUpdate = viewModel::startUpdate,
                        onDismiss = viewModel::dismissUpdate
                    )
                }
            }

            AnimatedVisibility(
                visible = state.showOnboarding,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(250)) + scaleOut(targetScale = 1.04f)
            ) {
                OnboardingScreen(
                    initialLeagues = state.competitions.filter { it.isFavorite }.map { it.id }.toSet(),
                    initialTeams = state.favoriteTeamIds,
                    onDone = viewModel::completeOnboarding
                )
            }
        }
    }
}
