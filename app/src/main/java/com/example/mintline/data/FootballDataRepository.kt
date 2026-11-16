package com.example.mintline.data

import android.content.Context
import com.example.mintline.data.provider.DemoDataProvider
import com.example.mintline.data.provider.EasySoccerDataEspnClient
import com.example.mintline.data.provider.LivescoreFootballClient
import com.example.mintline.data.timezone.TimeZoneManager
import com.example.mintline.model.Competition
import com.example.mintline.model.FavoriteType
import com.example.mintline.model.Match
import com.example.mintline.model.MatchDetail
import com.example.mintline.model.MatchStatus
import com.example.mintline.model.StandingRow
import com.example.mintline.model.StandingsCalculator
import com.example.mintline.notifications.MatchNotificationManager
import com.example.mintline.widget.MintlineWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DataProviderType(val displayName: String, val subtitle: String) {
    EASY_SOCCER_DATA_ESPN("EasySoccerData (ESPN Public API)", "Live scores, real-time match events & lineups"),
    DEMO_MODE("Demo Matchday Mode", "Curated high-contrast fixtures & events preview")
}

interface FootballDataRepository {
    val activeProvider: StateFlow<DataProviderType>
    val lastUpdatedTimestamp: StateFlow<Long>
    val favorites: StateFlow<FavoritesData>
    val timeZoneManager: TimeZoneManager

    fun setProvider(providerType: DataProviderType)
    fun addFavorites(leagueIds: Set<String>, teamIds: Set<String>)
    suspend fun getLeagueTables(leagueId: String): Result<List<com.example.mintline.model.StandingGroup>>
    suspend fun getLeagueLeaders(leagueId: String): Result<com.example.mintline.model.LeagueLeaders>
    suspend fun getMatches(
        dateString: String? = null,
        competitionId: String? = null
    ): Result<List<Match>>

    suspend fun getLiveMatches(): Result<List<Match>>
    suspend fun getMatchDetail(matchId: String, leagueId: String = "all"): Result<MatchDetail>
    fun getCompetitions(): List<Competition>
    fun getStandings(leagueId: String): List<StandingRow>

    fun isFavorite(id: String, type: FavoriteType): Boolean
    fun toggleFavorite(id: String, type: FavoriteType): Boolean
    fun clearFavorites()
}

class DefaultFootballDataRepository(
    private val context: Context,
    private val espnClient: EasySoccerDataEspnClient = EasySoccerDataEspnClient(),
    private val livescoreClient: LivescoreFootballClient = LivescoreFootballClient(),
    private val favoritesManager: FavoritesManager = FavoritesManager(context),
    override val timeZoneManager: TimeZoneManager = TimeZoneManager(context)
) : FootballDataRepository {

    private val _activeProvider = MutableStateFlow(DataProviderType.EASY_SOCCER_DATA_ESPN)
    override val activeProvider: StateFlow<DataProviderType> = _activeProvider.asStateFlow()

    private val _lastUpdatedTimestamp = MutableStateFlow(System.currentTimeMillis())
    override val lastUpdatedTimestamp: StateFlow<Long> = _lastUpdatedTimestamp.asStateFlow()

    override val favorites: StateFlow<FavoritesData> = favoritesManager.favoritesFlow

    // Memory cache for matches
    private val cachedMatches = mutableMapOf<String, List<Match>>()

    override fun setProvider(providerType: DataProviderType) {
        _activeProvider.value = providerType
    }

    override suspend fun getMatches(
        dateString: String?,
        competitionId: String?
    ): Result<List<Match>> {
        val targetZone = timeZoneManager.timeZone.value
        val cacheKey = "${dateString ?: "today"}_${competitionId ?: "all"}_${targetZone.id}"

        if (_activeProvider.value == DataProviderType.DEMO_MODE) {
            _lastUpdatedTimestamp.value = System.currentTimeMillis()
            var demoList = DemoDataProvider.getDemoMatches()
            if (!competitionId.isNullOrBlank() && competitionId != "all") {
                demoList = demoList.filter { it.leagueId == competitionId }
            }
            // Attach favorite states
            val favs = favoritesManager.favoritesFlow.value
            val enriched = demoList.map { m ->
                m.copy(isFavorite = favs.matchIds.contains(m.id) || favs.teamIds.contains(m.homeTeam.id) || favs.teamIds.contains(m.awayTeam.id))
            }
            MintlineWidgetProvider.updateAllWidgets(context, enriched)
            return Result.success(enriched)
        }

        // Live EasySoccerData Provider
        val leagueParam = if (!competitionId.isNullOrBlank() && competitionId != "all") competitionId else "all"
        val liveResult = espnClient.fetchScoreboard(league = leagueParam, dateString = dateString, targetZone = targetZone)

        val rawMatches = if (liveResult.isSuccess && !liveResult.getOrNull().isNullOrEmpty()) {
            liveResult.getOrNull()!!
        } else if (!competitionId.isNullOrBlank() && competitionId != "all") {
            // Secondary provider fallback via livescoreFootball (worldcup26.ir/get/soccer)
            val fallbackScoreboard = livescoreClient.fetchScoreboard(competitionId, dateString, targetZone)
            if (fallbackScoreboard.isSuccess && !fallbackScoreboard.getOrNull().isNullOrEmpty()) {
                fallbackScoreboard.getOrNull()!!
            } else {
                livescoreClient.fetchFixtures(competitionId, targetZone).getOrElse { emptyList() }
            }
        } else {
            liveResult.getOrNull() ?: emptyList()
        }

        return if (rawMatches.isNotEmpty() || liveResult.isSuccess) {
            val list = rawMatches

            _lastUpdatedTimestamp.value = System.currentTimeMillis()
            cachedMatches[cacheKey] = list

            val favs = favoritesManager.favoritesFlow.value
            val enriched = list.map { m ->
                m.copy(isFavorite = favs.matchIds.contains(m.id) || favs.teamIds.contains(m.homeTeam.id) || favs.teamIds.contains(m.awayTeam.id))
            }
            MintlineWidgetProvider.updateAllWidgets(context, enriched)
            Result.success(enriched)
        } else {
            // Check cache or fallback to demo
            val fallback = cachedMatches[cacheKey] ?: DemoDataProvider.getDemoMatches()
            val favs = favoritesManager.favoritesFlow.value
            val enriched = fallback.map { m ->
                m.copy(isFavorite = favs.matchIds.contains(m.id) || favs.teamIds.contains(m.homeTeam.id) || favs.teamIds.contains(m.awayTeam.id))
            }
            Result.failure(liveResult.exceptionOrNull() ?: Exception("Failed to fetch matches"))
        }
    }

    override suspend fun getLiveMatches(): Result<List<Match>> {
        val allResult = getMatches(dateString = null, competitionId = "all")
        return if (allResult.isSuccess) {
            val live = allResult.getOrNull()?.filter { it.isLive } ?: emptyList()
            Result.success(live)
        } else {
            Result.success(emptyList())
        }
    }

    override suspend fun getMatchDetail(matchId: String, leagueId: String): Result<MatchDetail> {
        if (_activeProvider.value == DataProviderType.DEMO_MODE || matchId.startsWith("m_")) {
            return Result.success(DemoDataProvider.getDemoMatchDetail(matchId))
        }

        val targetZone = timeZoneManager.timeZone.value
        val league = if (FootballCatalog.leagueBySlug(leagueId) != null) leagueId else "all"
        val result = espnClient.fetchMatchSummary(eventId = matchId, league = league, targetZone = targetZone)
        return result
    }

    override fun getCompetitions(): List<Competition> {
        val favLeagues = favoritesManager.favoritesFlow.value.leagueIds
        return FootballCatalog.competitions().map {
            it.copy(isFavorite = favLeagues.contains(it.id))
        }
    }

    override fun getStandings(leagueId: String): List<StandingRow> {
        val matches = cachedMatches.values.flatten().filter { it.leagueId == leagueId }
            .ifEmpty {
                DemoDataProvider.getDemoMatches().filter { it.leagueId == leagueId }
            }
        return StandingsCalculator.compute(matches)
    }

    override fun isFavorite(id: String, type: FavoriteType): Boolean {
        return favoritesManager.isFavorite(id, type)
    }

    override fun toggleFavorite(id: String, type: FavoriteType): Boolean {
        val isNowFav = favoritesManager.toggleFavorite(id, type)
        if (type == FavoriteType.MATCH) {
            val match = cachedMatches.values.flatten().find { it.id == id }
            if (match != null) {
                if (isNowFav) {
                    MatchNotificationManager.scheduleMatchReminder(context, match)
                } else {
                    MatchNotificationManager.cancelMatchReminder(context, match.id)
                }
            }
        }
        return isNowFav
    }

    private val insightsClient = com.example.mintline.data.provider.EspnInsightsClient()

    override suspend fun getLeagueTables(leagueId: String) = insightsClient.fetchStandings(leagueId)

    override suspend fun getLeagueLeaders(leagueId: String) = insightsClient.fetchLeaders(leagueId)

    override fun addFavorites(leagueIds: Set<String>, teamIds: Set<String>) {
        favoritesManager.addFavorites(leagueIds, teamIds)
    }

    override fun clearFavorites() {
        favoritesManager.clearAllFavorites()
    }
}
