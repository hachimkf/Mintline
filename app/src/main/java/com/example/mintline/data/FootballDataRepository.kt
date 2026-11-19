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
    suspend fun getBracket(leagueId: String, leagueName: String): Result<com.example.mintline.model.Bracket?>
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

        // Extra sources (merged into "all", or alone when one of their leagues is selected):
        //  - API-Football: AFCON, CAF cups, CHAN, Botola, Egypt, SA PSL, youth & Olympic tournaments
        //  - 365Scores: remaining Arab & African leagues
        val all = competitionId.isNullOrBlank() || competitionId == "all"
        val apiSportsMatches: List<Match> = if (all || competitionId.startsWith("af.")) {
            apiSports.fetchFixtures(dateString, targetZone).getOrDefault(emptyList())
                .filter { all || it.leagueId == competitionId }
        } else emptyList()
        val s365Matches: List<Match> = if (all || competitionId.startsWith("s365.")) {
            scores365.fetchFixtures(dateString, targetZone, skipApiSportsLeagues = apiSports.isEnabled).getOrDefault(emptyList())
                .filter { all || it.leagueId == competitionId }
        } else emptyList()
        val proxyMatches = apiSportsMatches + s365Matches
        if (competitionId?.startsWith("s365.") == true || competitionId?.startsWith("af.") == true) {
            val favs = favoritesManager.favoritesFlow.value
            return Result.success(proxyMatches.map { m ->
                m.copy(isFavorite = favs.matchIds.contains(m.id) || favs.teamIds.contains(m.homeTeam.id) || favs.teamIds.contains(m.awayTeam.id))
            })
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

        return if (rawMatches.isNotEmpty() || liveResult.isSuccess || proxyMatches.isNotEmpty()) {
            // drop any 365Scores game ESPN already has (same two teams)
            fun key(m: Match) = (m.homeTeam.name + "|" + m.awayTeam.name).lowercase().replace(Regex("[^a-z|]"), "")
            val espnKeys = rawMatches.map(::key).toSet()
            // ESPN first, then API-Football, then 365Scores — skip a game already listed by an earlier source
            val seen = rawMatches.map(::key).toMutableSet()
            val extra = (apiSportsMatches + s365Matches).filter { seen.add(key(it)) }
            val list = rawMatches + extra

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
        if (matchId.startsWith("s365_")) return scores365.fetchDetail(matchId, timeZoneManager.timeZone.value)
        if (matchId.startsWith("af_") && apiSports.isEnabled) return apiSports.fetchDetail(matchId, timeZoneManager.timeZone.value)
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
                if (isNowFav && com.example.mintline.notifications.LiveMatchService.alertsEnabled(context)) {
                    if (match.isLive) com.example.mintline.notifications.LiveMatchService.follow(context, match.id, match.leagueId)
                    MatchNotificationManager.scheduleMatchReminder(context, match)
                } else {
                    MatchNotificationManager.cancelMatchReminder(context, match.id)
                }
            }
        }
        return isNowFav
    }

    private val insightsClient = com.example.mintline.data.provider.EspnInsightsClient()
    private val scores365 = com.example.mintline.data.provider.Scores365Client()
    private val apiSports = com.example.mintline.data.provider.ApiSportsClient(context)

    override suspend fun getLeagueTables(leagueId: String) = insightsClient.fetchStandings(leagueId)

    override suspend fun getLeagueLeaders(leagueId: String) = insightsClient.fetchLeaders(leagueId)

    /** Knockout draw from 365Scores, matched to our league by name (free, no quota). */
    override suspend fun getBracket(leagueId: String, leagueName: String): Result<com.example.mintline.model.Bracket?> {
        val compId = if (leagueId.startsWith("s365.")) leagueId.removePrefix("s365.").toIntOrNull()
        else BRACKET_IDS[leagueId] ?: scores365.competitionIdFor(FootballCatalog.leagueBySlug(leagueId)?.name ?: leagueName.substringBefore(" · "))
        return compId?.let { scores365.bracket(it) } ?: Result.success(null)
    }

    private companion object {
        /** 365Scores competition IDs for catalogue cups (their search is unreliable from devices). */
        val BRACKET_IDS = mapOf(
            "fifa.world" to 5930, "uefa.champions" to 572, "uefa.europa" to 573, "uefa.europa.conf" to 7685,
            "uefa.euro" to 6316, "conmebol.america" to 595, "caf.nations" to 167, "uefa.nations" to 7016,
            "conmebol.libertadores" to 102, "conmebol.sudamericana" to 389, "eng.fa" to 8, "esp.copa_del_rey" to 13,
            "ita.coppa_italia" to 20, "ger.dfb_pokal" to 28, "eng.league_cup" to 9, "fifa.wwc" to 597,
            "uefa.weuro" to 5549, "uefa.wchampions" to 6225, "eng.trophy" to 6986, "eng.w.fa" to 6217,
            "uefa.super_cup" to 472, "afc.champions" to 623, "caf.champions" to 624, "caf.confed" to 627,
            "concacaf.champions" to 171, "concacaf.gold" to 589, "afc.asian.cup" to 6196,
            "fifa.olympics" to 6370, "usa.open" to 348,
        )
    }

    override fun addFavorites(leagueIds: Set<String>, teamIds: Set<String>) {
        favoritesManager.addFavorites(leagueIds, teamIds)
    }

    override fun clearFavorites() {
        favoritesManager.clearAllFavorites()
    }
}
