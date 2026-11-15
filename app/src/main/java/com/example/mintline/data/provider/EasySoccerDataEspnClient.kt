package com.example.mintline.data.provider

import com.example.mintline.data.FootballCatalog
import com.example.mintline.data.timezone.TimeZoneManager
import com.example.mintline.model.Competition
import com.example.mintline.model.IncidentType
import com.example.mintline.model.LineupPlayer
import com.example.mintline.model.Match
import com.example.mintline.model.MatchDetail
import com.example.mintline.model.MatchIncident
import com.example.mintline.model.MatchStatistic
import com.example.mintline.model.MatchStatus
import com.example.mintline.model.Team
import com.example.mintline.model.TeamLineup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Android implementation of the ESPN soccer provider from the EasySoccerData library.
 * EasySoccerData exposes live soccer scores, match statistics, events, and rosters
 * from ESPN's public endpoints.
 *
 * Reference: https://github.com/manucabral/EasySoccerData/tree/main/esd/espn
 */
class EasySoccerDataEspnClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val baseUrl = "https://site.api.espn.com/apis/site/v2/sports/soccer"

    suspend fun fetchScoreboard(
        league: String = "all",
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
                .header("User-Agent", "Mintline-Android/1.0 (EasySoccerData-Client)")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
            val json = JSONObject(body)
            val eventsArray = json.optJSONArray("events") ?: JSONArray()

            val matches = mutableListOf<Match>()
            for (i in 0 until eventsArray.length()) {
                val eventObj = eventsArray.getJSONObject(i)
                parseEventToMatch(eventObj, targetZone)?.let { matches.add(it) }
            }

            Result.success(matches)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMatchSummary(
        eventId: String,
        league: String = "all",
        targetZone: ZoneId = ZoneId.systemDefault()
    ): Result<MatchDetail> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/$league/summary?event=$eventId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mintline-Android/1.0 (EasySoccerData-Client)")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty summary response"))
            val json = JSONObject(body)
            val matchDetail = parseSummaryToDetail(eventId, json, targetZone)
            Result.success(matchDetail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseEventToMatch(event: JSONObject, targetZone: ZoneId = ZoneId.systemDefault()): Match? {
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

        val statusText = when (status) {
            MatchStatus.LIVE -> if (displayClock.isNotBlank()) displayClock else "LIVE"
            MatchStatus.HALFTIME -> "HT"
            MatchStatus.FULL_TIME -> "FT"
            else -> if (shortDetail.isNotBlank() && !shortDetail.contains("Scheduled")) shortDetail else "Scheduled"
        }

        val competitors = comp.optJSONArray("competitors") ?: return null
        if (competitors.length() < 2) return null

        var homeComp: JSONObject? = null
        var awayComp: JSONObject? = null

        for (i in 0 until competitors.length()) {
            val c = competitors.getJSONObject(i)
            val homeAway = c.optString("homeAway")
            if (homeAway == "home") homeComp = c
            else if (homeAway == "away") awayComp = c
        }

        if (homeComp == null || awayComp == null) {
            homeComp = competitors.getJSONObject(0)
            awayComp = competitors.getJSONObject(1)
        }

        // Women's sides share names with men's clubs (e.g. Barcelona), so tag them via their competition
        val numericLeagueForTeams = Regex("""~l:(\d+)""").find(event.optString("uid"))?.groupValues?.get(1)
        val women = FootballCatalog.leagueByEspnId(numericLeagueForTeams)?.isWomen == true
        fun Team.tagged() = if (women) copy(name = "$name (W)", shortName = "$shortName (W)") else this
        val homeTeam = parseTeam(homeComp).tagged()
        val awayTeam = parseTeam(awayComp).tagged()

        val homeScoreStr = homeComp.optString("score")
        val awayScoreStr = awayComp.optString("score")
        // ESPN reports "0" for games that have not started; hide those scores.
        val hasScore = status != MatchStatus.SCHEDULED && status != MatchStatus.POSTPONED && status != MatchStatus.CANCELLED
        val homeScore = if (hasScore) homeScoreStr.toIntOrNull() else null
        val awayScore = if (hasScore) awayScoreStr.toIntOrNull() else null

        val venueObj = comp.optJSONObject("venue")
        val venueName = venueObj?.optString("fullName", venueObj.optString("displayName", null))

        // Extract league details
        // The combined feed has no league object; the numeric league id lives in the uid ("…~l:775~…").
        val leagueObj = comp.optJSONObject("league")
        val numericLeague = Regex("""~l:(\d+)""").find(event.optString("uid"))?.groupValues?.get(1)
        val known = FootballCatalog.leagueByEspnId(numericLeague)
            ?: FootballCatalog.leagueBySlug(leagueObj?.optString("slug"))
        val leagueId = known?.slug ?: leagueObj?.optString("slug")?.takeIf { it.isNotBlank() } ?: FootballCatalog.OTHER_ID
        val leagueName = known?.name ?: leagueObj?.optString("name")?.takeIf { it.isNotBlank() } ?: "Other competitions"
        val leagueLogo = known?.logoUrl ?: leagueObj?.optJSONArray("logos")?.optJSONObject(0)?.optString("href")

        // Parse formatted kickoff time from ISO string localized to targetZone (Kickoff26 standard)
        val kickoffFormatted = formatTimeFromIso(dateIso, targetZone)
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
            isLive = status == MatchStatus.LIVE || status == MatchStatus.HALFTIME
        )
    }

    private fun parseTeam(comp: JSONObject): Team {
        val teamObj = comp.optJSONObject("team") ?: JSONObject()
        val id = teamObj.optString("id", comp.optString("id", "0"))
        val name = teamObj.optString("displayName", teamObj.optString("name", "Team"))
        val shortName = teamObj.optString("shortDisplayName", teamObj.optString("name", name))
        val abbreviation = teamObj.optString("abbreviation", name.take(3).uppercase())
        // Scoreboard gives "logo"; match summaries give a "logos" array instead
        val logo = teamObj.optString("logo").takeIf { it.isNotBlank() }
            ?: teamObj.optJSONArray("logos")?.optJSONObject(0)?.optString("href")?.takeIf { it.isNotBlank() }
        val colorHex = teamObj.optString("color", null)
        val form = comp.optString("form", null)

        return Team(
            id = id,
            name = name,
            shortName = shortName,
            abbreviation = abbreviation,
            crestUrl = logo,
            colorHex = colorHex,
            form = form
        )
    }

    private fun parseSummaryToDetail(
        eventId: String,
        json: JSONObject,
        targetZone: ZoneId = ZoneId.systemDefault()
    ): MatchDetail {
        val boxscore = json.optJSONObject("boxscore")
        val header = json.optJSONObject("header")
        val competitions = header?.optJSONArray("competitions")
        val comp = competitions?.optJSONObject(0)

        // Parse incidents / key events
        val incidents = mutableListOf<MatchIncident>()
        val keyEvents = json.optJSONArray("keyEvents")
        // Which side is home, so every event can be attributed by team id (not by position)
        var homeTeamId: String? = null
        comp?.optJSONArray("competitors")?.let { cs ->
            for (c in 0 until cs.length()) {
                val o = cs.getJSONObject(c)
                if (o.optString("homeAway") == "home") homeTeamId = o.optJSONObject("team")?.optString("id")
            }
        }
        if (keyEvents != null) {
            for (i in 0 until keyEvents.length()) {
                val ev = keyEvents.getJSONObject(i)
                val slug = ev.optJSONObject("type")?.optString("type", "")?.lowercase() ?: ""
                val typeText = ev.optJSONObject("type")?.optString("text", "")?.lowercase() ?: ""
                val scoring = ev.optBoolean("scoringPlay", false)
                val isOwnGoal = slug.contains("own-goal") || typeText.contains("own goal")

                val incidentType = when {
                    isOwnGoal -> IncidentType.GOAL
                    slug.contains("penalty") && (slug.contains("miss") || slug.contains("saved")) -> IncidentType.PENALTY_MISSED
                    slug.contains("penalty") && scoring -> IncidentType.PENALTY_SCORED
                    slug == "goal" || slug.startsWith("goal") || scoring -> IncidentType.GOAL
                    slug.contains("red-card") || slug.contains("second-yellow") -> IncidentType.RED_CARD
                    slug.contains("yellow-card") -> IncidentType.YELLOW_CARD
                    slug.contains("substitution") -> IncidentType.SUBSTITUTION
                    slug.contains("var") -> IncidentType.VAR_DECISION
                    else -> null // kickoff, halftime, start/end delay, full time… are not incidents
                } ?: continue

                val teamId = ev.optJSONObject("team")?.optString("id").orEmpty()
                if (teamId.isBlank()) continue
                // An own goal is listed under the scorer's team but counts for the opponent
                val forHome = (teamId == homeTeamId) != isOwnGoal

                val participants = ev.optJSONArray("participants")
                fun name(idx: Int) = participants?.optJSONObject(idx)?.optJSONObject("athlete")
                    ?.optString("displayName")?.takeIf { it.isNotBlank() }
                val p1 = name(0) ?: continue
                val p2 = name(1)

                incidents.add(
                    MatchIncident(
                        id = ev.optString("id", "inc_$i"),
                        minute = ev.optJSONObject("clock")?.optString("displayValue")?.takeIf { it.isNotBlank() } ?: "",
                        type = incidentType,
                        teamId = teamId,
                        isHomeTeam = forHome,
                        primaryPlayer = p1,
                        secondaryPlayer = p2,
                        detail = when {
                            isOwnGoal -> "Own goal"
                            incidentType == IncidentType.PENALTY_SCORED -> "Penalty"
                            incidentType == IncidentType.PENALTY_MISSED -> "Penalty missed"
                            else -> null
                        }
                    )
                )
            }
        }

        // Parse statistics
        val statsList = mutableListOf<MatchStatistic>()
        val teams = boxscore?.optJSONArray("teams")
        if (teams != null && teams.length() >= 2) {
            val homeStats = teams.getJSONObject(0).optJSONArray("statistics")
            val awayStats = teams.getJSONObject(1).optJSONArray("statistics")

            if (homeStats != null && awayStats != null) {
                val count = minOf(homeStats.length(), awayStats.length())
                for (s in 0 until count) {
                    val hs = homeStats.getJSONObject(s)
                    val asStat = awayStats.getJSONObject(s)
                    val label = hs.optString("label", hs.optString("name", "Stat"))
                    val hVal = hs.optString("displayValue", "0")
                    val aVal = asStat.optString("displayValue", "0")

                    val hFloat = hVal.replace("%", "").toFloatOrNull() ?: 0f
                    val aFloat = aVal.replace("%", "").toFloatOrNull() ?: 0f
                    val total = hFloat + aFloat
                    val ratio = if (total > 0f) hFloat / total else 0.5f

                    statsList.add(
                        MatchStatistic(
                            label = label,
                            homeValue = hVal,
                            awayValue = aVal,
                            homeRatio = ratio
                        )
                    )
                }
            }
        }

        // Parse Rosters / Lineups
        val rosters = json.optJSONArray("rosters")
        var homeLineup: TeamLineup? = null
        var awayLineup: TeamLineup? = null

        if (rosters != null && rosters.length() >= 2) {
            homeLineup = parseTeamRoster(rosters.getJSONObject(0))
            awayLineup = parseTeamRoster(rosters.getJSONObject(1))
        }

        val gameInfo = json.optJSONObject("gameInfo")
        val venue = gameInfo?.optJSONObject("venue")?.optString("fullName", null)

        // ESPN data only: anything ESPN doesn't provide yet (stats, line-ups, events before kick-off) stays empty
        val finalMatch = header?.let { parseEventToMatch(it, targetZone) }
            ?: throw IllegalStateException("Match summary unavailable")

        return MatchDetail(
            match = finalMatch.copy(venue = venue ?: finalMatch.venue),
            incidents = if (finalMatch.status == MatchStatus.SCHEDULED) emptyList() else incidents,
            statistics = if (finalMatch.status == MatchStatus.SCHEDULED) emptyList() else statsList,
            homeLineup = homeLineup?.takeIf { it.starters.isNotEmpty() },
            awayLineup = awayLineup?.takeIf { it.starters.isNotEmpty() },
            commentary = emptyList(),
            referee = gameInfo?.optJSONArray("officials")?.optJSONObject(0)?.optString("displayName", null)
        )
    }

    private fun parseTeamRoster(rosterObj: JSONObject): TeamLineup {
        val teamObj = rosterObj.optJSONObject("team") ?: JSONObject()
        val teamName = teamObj.optString("displayName", "Team")
        val formation = rosterObj.optString("formation", null)
        val playersArray = rosterObj.optJSONArray("roster") ?: JSONArray()

        val starters = mutableListOf<LineupPlayer>()
        val bench = mutableListOf<LineupPlayer>()

        for (i in 0 until playersArray.length()) {
            val p = playersArray.getJSONObject(i)
            val athlete = p.optJSONObject("athlete") ?: p
            val id = athlete.optString("id", "p_$i")
            val name = athlete.optString("displayName", athlete.optString("fullName", "Player"))
            val jersey = athlete.optString("jersey", "${i + 1}")
            val posObj = athlete.optJSONObject("position")
            val pos = posObj?.optString("abbreviation", "SUB") ?: "SUB"
            val isStarter = p.optBoolean("starter", i < 11)

            val lineupPlayer = LineupPlayer(
                id = id,
                name = name,
                jersey = jersey,
                position = pos,
                isSubstitute = !isStarter
            )

            if (isStarter) starters.add(lineupPlayer) else bench.add(lineupPlayer)
        }

        return TeamLineup(
            teamName = teamName,
            formation = formation,
            starters = starters,
            bench = bench
        )
    }

    private fun formatTimeFromIso(isoString: String, targetZone: ZoneId = ZoneId.systemDefault()): String {
        return TimeZoneManager.formatKickoffTime(isoString, targetZone)
    }
}
