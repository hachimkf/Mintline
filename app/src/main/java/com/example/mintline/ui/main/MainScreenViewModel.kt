package com.example.mintline.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mintline.data.AppUpdater
import com.example.mintline.data.DataProviderType
import com.example.mintline.data.UpdateInfo
import com.example.mintline.data.FootballCatalog
import com.example.mintline.data.DefaultFootballDataRepository
import com.example.mintline.data.FootballDataRepository
import com.example.mintline.data.timezone.TimeZoneManager
import com.example.mintline.data.timezone.TimeZoneManager.Companion.toDisplayLabel
import com.example.mintline.model.Competition
import com.example.mintline.model.DateItem
import com.example.mintline.model.FavoriteType
import com.example.mintline.model.Match
import com.example.mintline.model.MatchDetail
import com.example.mintline.theme.ThemeMode
import com.example.mintline.ui.components.MintlineTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class MintlineUiState(
    val matches: List<Match> = emptyList(),
    val liveMatches: List<Match> = emptyList(),
    val competitions: List<Competition> = emptyList(),
    val selectedCompetitionId: String? = null,
    val selectedDate: DateItem,
    val dateList: List<DateItem> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdatedText: String = "Just now",
    val activeTab: MintlineTab = MintlineTab.MATCHES,
    val activeProvider: DataProviderType = DataProviderType.EASY_SOCCER_DATA_ESPN,
    val autoRefreshIntervalSec: Int = 30,
    val reducedMotion: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val selectedMatchDetail: MatchDetail? = null,
    val isLoadingDetail: Boolean = false,
    val favoriteTeamIds: Set<String> = emptySet(),
    val showOnboarding: Boolean = false,
    val timeZoneLabel: String = "",
    val isCustomTimeZone: Boolean = false,
    val matchNotificationsEnabled: Boolean = true,
    val appVersion: String = "",
    val update: UpdateInfo? = null,
    val updateDismissed: Boolean = false,
    val updateProgress: Float? = null,
    val updateMessage: String? = null,
) {
    val displayMatches: List<Match>
        get() {
            var list = matches
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                list = list.filter {
                    it.homeTeam.name.lowercase().contains(q) ||
                    it.awayTeam.name.lowercase().contains(q) ||
                    it.leagueName.lowercase().contains(q)
                }
            }
            if (!selectedCompetitionId.isNullOrBlank() && selectedCompetitionId != "all") {
                list = list.filter { it.leagueId == selectedCompetitionId }
            }
            return list
        }

    /**
     * Home feed sections: "Your teams" first, then favourite competitions,
     * then everything else by importance (World Cup, UCL, top-5 leagues, cups…).
     */
    val leagueGroups: List<LeagueGroup>
        get() {
            val ordered = compareByDescending<Match> { it.isLive }.thenBy { it.dateIso }
            val favLeagueIds = competitions.filter { it.isFavorite }.map { it.id }.toSet()
            val all = displayMatches
            val mine = all.filter { favoriteTeamIds.contains(it.homeTeam.id) || favoriteTeamIds.contains(it.awayTeam.id) }
            val mineIds = mine.map { it.id }.toSet()
            val groups = mutableListOf<LeagueGroup>()
            if (mine.isNotEmpty()) {
                groups += LeagueGroup(
                    key = "my_teams", title = "Your teams", subtitle = "Clubs & nations you follow",
                    logoUrl = null, matches = mine.sortedWith(ordered),
                    kind = GroupKind.MY_TEAMS, defaultExpanded = true
                )
            }
            all.filterNot { it.id in mineIds }
                .groupBy { it.leagueId }
                .map { (leagueId, list) ->
                    val info = FootballCatalog.leagueBySlug(leagueId)
                    val fav = leagueId in favLeagueIds
                    val rank = FootballCatalog.rankOf(leagueId)
                    LeagueGroup(
                        key = leagueId,
                        title = info?.name ?: list.first().leagueName,
                        subtitle = info?.country,
                        logoUrl = info?.logoUrl ?: list.first().leagueLogo,
                        matches = list.sortedWith(ordered),
                        kind = if (fav) GroupKind.FAVORITE else GroupKind.LEAGUE,
                        defaultExpanded = fav || rank <= 10,
                        rank = rank
                    )
                }
                .sortedWith(compareBy<LeagueGroup> { if (it.kind == GroupKind.FAVORITE) 0 else 1 }.thenBy { it.rank }.thenBy { it.title })
                .let { groups += it }
            // Never open to a wall of collapsed cards: unfold the first section at least.
            if (groups.none { it.defaultExpanded } && groups.isNotEmpty()) {
                groups[0] = groups[0].copy(defaultExpanded = true)
            }
            return groups
        }

    /** League chips: favourites, then competitions playing today, then the big ones. */
    val filterChips: List<Competition>
        get() {
            val playing = matches.map { it.leagueId }.toSet()
            return competitions
                .filter { it.isFavorite || it.id in playing || FootballCatalog.rankOf(it.id) <= 9 || it.id == selectedCompetitionId }
                .sortedWith(compareBy<Competition> { if (it.isFavorite) 0 else 1 }.thenBy { FootballCatalog.rankOf(it.id) })
        }

    val favoriteMatches: List<Match>
        get() = matches.filter { it.isFavorite }
}

enum class GroupKind { MY_TEAMS, FAVORITE, LEAGUE }

data class LeagueGroup(
    val key: String,
    val title: String,
    val subtitle: String?,
    val logoUrl: String?,
    val matches: List<Match>,
    val kind: GroupKind,
    val defaultExpanded: Boolean,
    val rank: Int = 0,
)

class MainScreenViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: FootballDataRepository = DefaultFootballDataRepository(application)
    private val updater = AppUpdater(application)
    private val prefs = application.getSharedPreferences("mintline_prefs", android.content.Context.MODE_PRIVATE)

    private val initialZone = repository.timeZoneManager.timeZone.value
    private val dateItems = generateDateList(initialZone)
    private val initialDate = dateItems.find { it.isToday } ?: dateItems[3]

    private val _uiState = MutableStateFlow(
        MintlineUiState(
            selectedDate = initialDate,
            dateList = dateItems,
            competitions = repository.getCompetitions(),
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name)!!) }
                .getOrDefault(ThemeMode.SYSTEM),
            reducedMotion = prefs.getBoolean("reduced_motion", false),
            favoriteTeamIds = repository.favorites.value.teamIds,
            showOnboarding = !prefs.getBoolean("onboarding_done", false),
            timeZoneLabel = initialZone.toDisplayLabel(),
            isCustomTimeZone = repository.timeZoneManager.isCustomZone.value,
            matchNotificationsEnabled = prefs.getBoolean("match_notifications", true),
            appVersion = updater.currentVersion
        )
    )
    val uiState: StateFlow<MintlineUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        checkForUpdates(manual = false)
        loadData()
        startAutoRefresh()

        // Observe timezone changes
        viewModelScope.launch {
            repository.timeZoneManager.timeZone.collect { zone ->
                val newDates = generateDateList(zone)
                val newSel = newDates.find { it.dateString == _uiState.value.selectedDate.dateString }
                    ?: newDates.find { it.isToday }
                    ?: newDates[3]
                _uiState.update {
                    it.copy(
                        timeZoneLabel = zone.toDisplayLabel(),
                        isCustomTimeZone = repository.timeZoneManager.isCustomZone.value,
                        dateList = newDates,
                        selectedDate = newSel
                    )
                }
                loadData(isUserAction = true)
            }
        }

        // Observe repository state
        viewModelScope.launch {
            repository.activeProvider.collect { provider ->
                _uiState.update { it.copy(activeProvider = provider) }
            }
        }

        viewModelScope.launch {
            repository.lastUpdatedTimestamp.collect { ts ->
                updateLastUpdatedText(ts)
            }
        }

        viewModelScope.launch {
            repository.favorites.collect { favs ->
                // Re-map favorite flags
                _uiState.update { state ->
                    val updatedMatches = state.matches.map { m ->
                        m.copy(isFavorite = favs.matchIds.contains(m.id) || favs.teamIds.contains(m.homeTeam.id) || favs.teamIds.contains(m.awayTeam.id))
                    }
                    val updatedLive = state.liveMatches.map { m ->
                        m.copy(isFavorite = favs.matchIds.contains(m.id) || favs.teamIds.contains(m.homeTeam.id) || favs.teamIds.contains(m.awayTeam.id))
                    }
                    val updatedComps = state.competitions.map { c ->
                        c.copy(isFavorite = favs.leagueIds.contains(c.id))
                    }
                    val updatedDetail = state.selectedMatchDetail?.let { d ->
                        d.copy(match = d.match.copy(isFavorite = favs.matchIds.contains(d.match.id)))
                    }
                    state.copy(
                        matches = updatedMatches,
                        liveMatches = updatedLive,
                        competitions = updatedComps,
                        selectedMatchDetail = updatedDetail,
                        favoriteTeamIds = favs.teamIds
                    )
                }
            }
        }
    }

    private fun generateDateList(zoneId: ZoneId = repository.timeZoneManager.timeZone.value): List<DateItem> {
        val list = mutableListOf<DateItem>()
        val today = LocalDate.now(zoneId)
        val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
        val numFormat = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
        val isoFormat = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)

        // Generate 10 days before today, today, and 10 days after today
        for (i in -10..10) {
            val d = today.plusDays(i.toLong())
            val isToday = i == 0
            list.add(
                DateItem(
                    dateString = d.format(isoFormat),
                    dayOfWeek = d.format(dayFormat),
                    dayOfMonth = d.format(numFormat),
                    isToday = isToday,
                    isSelected = isToday
                )
            )
        }
        return list
    }

    fun cycleTimeZone() {
        val current = repository.timeZoneManager.timeZone.value
        val zones = TimeZoneManager.POPULAR_ZONES
        val idx = zones.indexOf(current)
        if (idx == -1) {
            // Switch from device default to first popular zone (UTC)
            repository.timeZoneManager.setTimeZone(zones[0])
        } else if (idx == zones.lastIndex) {
            // After cycling through all, return to device default
            repository.timeZoneManager.resetToDeviceDefault()
        } else {
            repository.timeZoneManager.setTimeZone(zones[idx + 1])
        }
    }

    fun resetTimeZoneToDefault() {
        repository.timeZoneManager.resetToDeviceDefault()
    }

    fun setMatchNotifications(enabled: Boolean) {
        prefs.edit().putBoolean("match_notifications", enabled).apply()
        _uiState.update { it.copy(matchNotificationsEnabled = enabled) }
    }

    fun selectTab(tab: MintlineTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectCompetition(compId: String?) {
        _uiState.update { it.copy(selectedCompetitionId = compId) }
        loadData(isUserAction = true)
    }

    fun selectDate(dateItem: DateItem) {
        val updatedDates = _uiState.value.dateList.map {
            it.copy(isSelected = it.dateString == dateItem.dateString)
        }
        _uiState.update {
            it.copy(
                selectedDate = dateItem.copy(isSelected = true),
                dateList = updatedDates
            )
        }
        loadData(isUserAction = true)
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearch(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (!active) "" else it.searchQuery
            )
        }
    }

    fun toggleFavorite(id: String, type: FavoriteType) {
        repository.toggleFavorite(id, type)
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadData(isUserAction = true)
    }

    fun loadData(isUserAction: Boolean = false) {
        viewModelScope.launch {
            if (!isUserAction && _uiState.value.matches.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            val curDate = _uiState.value.selectedDate.dateString
            val curComp = _uiState.value.selectedCompetitionId

            val result = repository.getMatches(dateString = curDate, competitionId = curComp)
            val liveResult = repository.getLiveMatches()

            if (result.isSuccess) {
                val matches = result.getOrNull() ?: emptyList()
                val live = liveResult.getOrNull() ?: matches.filter { it.isLive }

                _uiState.update {
                    it.copy(
                        matches = matches,
                        liveMatches = live,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null
                    )
                }
            } else {
                // If network failed, repository provided fallback fixtures
                val fallbackMatches = (result.exceptionOrNull() as? Exception)?.let { null }
                    ?: _uiState.value.matches

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Unable to reach data source"
                    )
                }
            }
        }
    }

    fun openMatchDetail(matchId: String, leagueId: String = "all") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetail = true) }
            val res = repository.getMatchDetail(matchId, leagueId)
            _uiState.update {
                val favIds = repository.favorites.value.matchIds
                it.copy(
                    selectedMatchDetail = res.getOrNull()?.let { d ->
                        d.copy(match = d.match.copy(isFavorite = favIds.contains(d.match.id)))
                    },
                    isLoadingDetail = false
                )
            }
        }
    }

    fun closeMatchDetail() {
        _uiState.update { it.copy(selectedMatchDetail = null) }
    }

    fun setProvider(provider: DataProviderType) {
        repository.setProvider(provider)
        loadData(isUserAction = true)
    }

    fun setAutoRefreshInterval(seconds: Int) {
        _uiState.update { it.copy(autoRefreshIntervalSec = seconds) }
        startAutoRefresh()
    }

    fun setReducedMotion(enabled: Boolean) {
        prefs.edit().putBoolean("reduced_motion", enabled).apply()
        _uiState.update { it.copy(reducedMotion = enabled) }
    }

    fun completeOnboarding(leagueIds: Set<String>, teamIds: Set<String>) {
        repository.addFavorites(leagueIds, teamIds)
        prefs.edit().putBoolean("onboarding_done", true).apply()
        _uiState.update { it.copy(showOnboarding = false) }
    }

    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            if (manual) _uiState.update { it.copy(updateMessage = "Checking…") }
            val res = updater.check()
            _uiState.update {
                it.copy(
                    update = res.getOrNull(),
                    updateDismissed = false,
                    updateMessage = when {
                        !manual -> null
                        res.isFailure -> "Couldn't reach GitHub"
                        res.getOrNull() == null -> "You're on the latest version"
                        else -> null
                    }
                )
            }
        }
    }

    fun startUpdate() {
        val info = _uiState.value.update ?: return
        if (_uiState.value.updateProgress != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(updateProgress = 0f, updateMessage = null) }
            val res = updater.download(info) { p -> _uiState.update { s -> s.copy(updateProgress = p) } }
            _uiState.update { it.copy(updateProgress = null) }
            res.onSuccess { runCatching { updater.install(it) } }
                .onFailure { e -> _uiState.update { it.copy(updateMessage = e.message ?: "Download failed") } }
        }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(updateDismissed = true) }
    }

    fun openOnboarding() {
        _uiState.update { it.copy(showOnboarding = true) }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun clearFavorites() {
        repository.clearFavorites()
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        val interval = _uiState.value.autoRefreshIntervalSec
        if (interval <= 0) return // Manual only

        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(interval * 1000L)
                // Responsible background refresh: only refresh matches when in matches tab
                if (_uiState.value.activeTab == MintlineTab.MATCHES && !_uiState.value.isLoading) {
                    val res = repository.getMatches(
                        dateString = _uiState.value.selectedDate.dateString,
                        competitionId = _uiState.value.selectedCompetitionId
                    )
                    if (res.isSuccess) {
                        _uiState.update {
                            it.copy(
                                matches = res.getOrNull() ?: it.matches,
                                liveMatches = res.getOrNull()?.filter { m -> m.isLive } ?: it.liveMatches
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateLastUpdatedText(timestamp: Long) {
        val diffSec = (System.currentTimeMillis() - timestamp) / 1000
        val text = when {
            diffSec < 15 -> "Just now"
            diffSec < 60 -> "${diffSec}s ago"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            else -> "${diffSec / 3600}h ago"
        }
        _uiState.update { it.copy(lastUpdatedText = text) }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
