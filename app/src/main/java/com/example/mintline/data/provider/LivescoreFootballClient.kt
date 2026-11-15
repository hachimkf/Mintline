package com.example.mintline.data.provider

import com.example.mintline.data.FootballCatalog
import com.example.mintline.data.timezone.TimeZoneManager
import com.example.mintline.model.Match
import com.example.mintline.model.MatchStatus
import com.example.mintline.model.StandingRow
import com.example.mintline.model.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Client for the livescoreFootball REST API (https://github.com/rezarahiminia/livescoreFootball)
 * hosted at https://worldcup26.ir/get/soccer.
 *
 * Provides scores, fixtures, and standings for 35+ verified club and international leagues
 * alongside the ESPN public provider to expand competition coverage.
 */
class LivescoreFootballClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val baseUrl = "https://worldcup26.ir/get/soccer"

    suspend fun fetchScoreboard(
        league: String,
        dateString: String? = null,
        targetZone: ZoneId = ZoneId.systemDefault()
    ): Result<List<Match>> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("$baseUrl/$league/scoreboard")
            if (!dateString.isNullOrBlank()) {
                urlBuilder.append("?dates=$dateString")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "Mintline-Android/1.2")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("LivescoreFootball HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
            val json = JSONObject(body)
            val eventsArray = json.optJSONArray("events") ?: JSONArray()

            val matches = mutableListOf<Match>()
            for (i in 0 until eventsArray.length()) {
                val eventObj = eventsArray.getJSONObject(i)
                parseEvent(eventObj, targetZone)?.let { matches.add(it) }
            }

            Result.success(matches)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchFixtures(
        league: String,
        targetZone: ZoneId = ZoneId.systemDefault()
    ): Result<List<Match>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/$league/fixtures?status=all&limit=50"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mintline-Android/1.2")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("LivescoreFootball HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
            val json = JSONObject(body)
            val eventsArray = json.optJSONArray("events") ?: JSONArray()

            val matches = mutableListOf<Match>()
            for (i in 0 until eventsArray.length()) {
                val eventObj = eventsArray.getJSONObject(i)
                parseEvent(eventObj, targetZone)?.let { matches.add(it) }
            }

            Result.success(matches)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchStandings(league: String): Result<List<StandingRow>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/$league/standings"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mintline-Android/1.2")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("LivescoreFootball HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
            val json = JSONObject(body)
            val children = json.optJSONArray("children") ?: JSONArray()
            if (children.length() == 0) return@withContext Result.success(emptyList())

            val firstChild = children.getJSONObject(0)
            val standingsObj = firstChild.optJSONObject("standings") ?: JSONObject()
            val entries = standingsObj.optJSONArray("entries") ?: JSONArray()

            val rows = mutableListOf<StandingRow>()
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                val teamObj = entry.optJSONObject("team") ?: JSONObject()
                val teamId = teamObj.optString("id", "$i")
                val teamName = teamObj.optString("name", "Team")
                val shortName = teamObj.optString("shortDisplayName", teamName)
                val abbr = teamObj.optString("abbreviation", teamName.take(3).uppercase())
                val logo = teamObj.optString("logo").ifBlank {
                    "https://a.espncdn.com/i/teamlogos/soccer/500/$teamId.png"
                }

                val statsArray = entry.optJSONArray("stats") ?: JSONArray()
                var p = 0
                var w = 0
                var d = 0
                var l = 0
                var gf = 0
                var ga = 0
                var gd = 0
                var pts = 0

                for (s in 0 until statsArray.length()) {
                    val st = statsArray.getJSONObject(s)
                    val name = st.optString("name")
                    val v = st.optInt("value", 0)
                    when (name) {
                        "gamesPlayed" -> p = v
                        "wins" -> w = v
                        "ties" -> d = v
                        "losses" -> l = v
                        "pointsFor" -> gf = v
                        "pointsAgainst" -> ga = v
                        "pointDifferential" -> gd = v
                        "points" -> pts = v
                    }
                }

                rows.add(
                    StandingRow(
                        rank = i + 1,
                        team = Team(
                            id = teamId,
                            name = teamName,
                            shortName = shortName,
                            abbreviation = abbr,
                            crestUrl = logo
                        ),
                        played = p,
                        won = w,
                        drawn = d,
                        lost = l,
                        goalsFor = gf,
                        goalsAgainst = ga,
                        goalDifference = gd,
                        points = pts
                    )
                )
            }

            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseEvent(event: JSONObject, targetZone: ZoneId): Match? {
        val id = event.optString("id")
        val dateIso = event.optString("date")
        val competitions = event.optJSONArray("competitions") ?: return null
        if (competitions.length() == 0) return null
        val comp = competitions.getJSONObject(0)

        val statusObj = comp.optJSONObject("status")
        val typeObj = statusObj?.optJSONObject("type")
        val state = typeObj?.optString("state", "pre") ?: "pre"
        val completed = typeObj?.optBoolean("completed", false) ?: false
        val displayClock = statusObj?.optString("displayClock", "") ?: ""
        val shortDetail = typeObj?.optString("shortDetail", "") ?: ""

        val status = when {
            completed -> MatchStatus.FULL_TIME
            state == "in" && shortDetail.contains("HT", ignoreCase = true) -> MatchStatus.HALFTIME
            state == "in" -> MatchStatus.LIVE
            shortDetail.contains("Postp", ignoreCase = true) -> MatchStatus.POSTPONED
            shortDetail.contains("Canc", ignoreCase = true) -> MatchStatus.CANCELLED
            else -> MatchStatus.SCHEDULED
        }

        val clockMinutes = displayClock.substringBefore(":").toIntOrNull()
        val minute = if (status == MatchStatus.LIVE) clockMinutes else null

        val statusText = when (status) {
            MatchStatus.LIVE -> if (displayClock.isNotBlank()) displayClock else "LIVE"
            MatchStatus.HALFTIME -> "HT"
            MatchStatus.FULL_TIME -> "FT"
            MatchStatus.POSTPONED -> "Postponed"
            MatchStatus.CANCELLED -> "Cancelled"
            MatchStatus.SCHEDULED -> shortDetail
        }

        val competitors = comp.optJSONArray("competitors") ?: return null
        if (competitors.length() < 2) return null

        var homeComp: JSONObject? = null
        var awayComp: JSONObject? = null
        for (i in 0 until competitors.length()) {
            val c = competitors.getJSONObject(i)
            if (c.optString("homeAway") == "home") homeComp = c
            else if (c.optString("homeAway") == "away") awayComp = c
        }
        if (homeComp == null || awayComp == null) {
            homeComp = competitors.getJSONObject(0)
            awayComp = competitors.getJSONObject(1)
        }

        val homeTeam = parseTeam(homeComp)
        val awayTeam = parseTeam(awayComp)

        val homeScoreStr = homeComp.optString("score")
        val awayScoreStr = awayComp.optString("score")
        val hasScore = status != MatchStatus.SCHEDULED && status != MatchStatus.POSTPONED && status != MatchStatus.CANCELLED
        val homeScore = if (hasScore) homeScoreStr.toIntOrNull() else null
        val awayScore = if (hasScore) awayScoreStr.toIntOrNull() else null

        val venueObj = comp.optJSONObject("venue")
        val venueName = venueObj?.optString("fullName", venueObj.optString("displayName", null))

        val leagueObj = comp.optJSONObject("league")
        val numericLeague = Regex("""~l:(\d+)""").find(event.optString("uid"))?.groupValues?.get(1)
        val known = FootballCatalog.leagueByEspnId(numericLeague)
            ?: FootballCatalog.leagueBySlug(leagueObj?.optString("slug"))

        val leagueId = known?.slug ?: leagueObj?.optString("slug")?.takeIf { it.isNotBlank() } ?: FootballCatalog.OTHER_ID
        val leagueName = known?.name ?: leagueObj?.optString("name")?.takeIf { it.isNotBlank() } ?: "Other competitions"
        val leagueLogo = known?.logoUrl ?: leagueObj?.optJSONArray("logos")?.optJSONObject(0)?.optString("href")

        val kickoffFormatted = TimeZoneManager.formatKickoffTime(dateIso, targetZone)
        val finalStatusText = if (status == MatchStatus.SCHEDULED) kickoffFormatted else statusText

        return Match(
            id = id,
            leagueId = leagueId,
            leagueName = leagueName,
            leagueLogo = leagueLogo,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homeScore = homeScore,
            awayScore = awayScore,
            status = status,
            statusText = finalStatusText,
            kickoffFormatted = kickoffFormatted,
            dateIso = dateIso,
            venue = venueName,
            isLive = status == MatchStatus.LIVE || status == MatchStatus.HALFTIME,
            minute = minute
        )
    }

    private fun parseTeam(comp: JSONObject): Team {
        val teamObj = comp.optJSONObject("team") ?: JSONObject()
        val id = teamObj.optString("id", comp.optString("id", "0"))
        val name = teamObj.optString("displayName", teamObj.optString("name", "Team"))
        val shortName = teamObj.optString("shortDisplayName", teamObj.optString("name", name))
        val abbreviation = teamObj.optString("abbreviation", name.take(3).uppercase())
        val logo = teamObj.optString("logo").takeIf { it.isNotBlank() }
            ?: teamObj.optJSONArray("logos")?.optJSONObject(0)?.optString("href")?.takeIf { it.isNotBlank() }
            ?: "https://a.espncdn.com/i/teamlogos/soccer/500/$id.png"

        return Team(
            id = id,
            name = name,
            shortName = shortName,
            abbreviation = abbreviation,
            crestUrl = logo
        )
    }
}
