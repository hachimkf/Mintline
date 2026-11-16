package com.example.mintline.data.provider

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Arab-world and African leagues from 365Scores' public web API (the same
 * source the open-source live_score app uses). No key needed.
 *
 * Only competitions ESPN doesn't cover are returned. IDs are prefixed so they
 * never collide with ESPN: match "s365_<gameId>", league "s365.<competitionId>",
 * team "s365_<competitorId>". Team/league names come in Arabic when the phone
 * language is Arabic.
 */
class Scores365Client(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val base = "https://webws.365scores.com/web"

    // Matched against 365Scores' English URL slugs ("united-arab-emirates" -> "united arab emirates")
    private val wantedCountries = setOf(
        // Arab world
        "morocco", "egypt", "algeria", "tunisia", "libya", "sudan", "mauritania", "saudi arabia",
        "qatar", "united arab emirates", "uae", "kuwait", "bahrain", "oman", "iraq", "jordan",
        "lebanon", "syria", "palestine", "yemen",
        // Africa
        "ghana", "senegal", "ivory coast", "cote divoire", "cameroon", "kenya", "tanzania", "uganda",
        "ethiopia", "zambia", "zimbabwe", "angola", "mali", "burkina faso", "dr congo", "congo dr",
        "rwanda", "benin", "togo", "guinea", "gabon", "botswana", "namibia", "mozambique", "malawi"
    )
    private val internationalHints = listOf("arab", "caf", "african", "afcon", "gulf", "chan", "cosafa", "wafu", "cecafa")

    /** ESPN already has these top flights; skip them here to avoid duplicates. */
    private fun coveredByEspn(country: String, competition: String): Boolean {
        val c = competition.lowercase()
        return country == "saudi arabia" && (c.contains("saudi league") || c.contains("pro league"))
    }

    private val lang: Int get() = if (Locale.getDefault().language == "ar") 27 else 1

    private fun params(zone: ZoneId) = "appTypeId=5&langId=$lang&timezoneName=${zone.id}&userCountryId=-1"

    private fun get(url: String): JSONObject {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("365Scores HTTP ${r.code}")
            return JSONObject(r.body!!.string())
        }
    }

    private fun teamLogo(id: Int, v: Int) =
        "https://imagecache.365scores.com/image/upload/f_png,w_96,h_96,c_limit,q_auto:eco,dpr_2,d_Competitors:default1.png/v$v/Competitors/$id"

    private fun competitionLogo(id: Int, v: Int) =
        "https://imagecache.365scores.com/image/upload/f_png,w_64,h_64,c_limit,q_auto:eco,dpr_2,d_Countries:round:default.png/v$v/Competitions/light/$id"

    /** @param dateString yyyyMMdd (the app's date format); null = today in [zone]. */
    suspend fun fetchFixtures(dateString: String?, zone: ZoneId): Result<List<Match>> = withContext(Dispatchers.IO) {
        runCatching {
            val day = dateString?.takeIf { it.length == 8 }
                ?.let { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) } ?: LocalDate.now(zone)
            val d = day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            val json = get("$base/games/allscores/?${params(zone)}&sports=1&startDate=$d&endDate=$d")
            val countries = json.optJSONArray("countries").byId()
            val competitions = json.optJSONArray("competitions").byId()
            val games = json.optJSONArray("games") ?: JSONArray()
            (0 until games.length()).mapNotNull { i ->
                val g = games.getJSONObject(i)
                val comp = competitions[g.optInt("competitionId")] ?: return@mapNotNull null
                val country = countries[comp.optInt("countryId")]
                // Match on the English URL slug so it works in Arabic mode too
                val countryName = country?.optString("name").orEmpty()
                val countrySlug = country?.optString("nameForURL").orEmpty().replace('-', ' ').lowercase()
                val compSlug = comp.optString("nameForURL").replace('-', ' ').lowercase()
                val international = country?.optBoolean("isInternational") == true
                val keep = if (international) internationalHints.any { compSlug.contains(it) }
                    else countrySlug in wantedCountries
                val youth = Regex("\\bu\\s?\\d{2}\\b|youth|reserve|junior").containsMatchIn(compSlug)
                if (!keep || youth || coveredByEspn(countrySlug, compSlug)) return@mapNotNull null
                parseGame(g, comp, countryName, international, zone)
            }
        }
    }

    suspend fun fetchDetail(matchId: String, zone: ZoneId): Result<MatchDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val id = matchId.removePrefix("s365_")
            coroutineScope {
                val gameJob = async { get("$base/game/?${params(zone)}&gameId=$id") }
                val statsJob = async { runCatching { get("$base/game/stats/?${params(zone)}&games=$id") }.getOrNull() }
                val g = gameJob.await().optJSONObject("game") ?: error("Game not found")
                val stats = statsJob.await()
                val comp = JSONObject().put("id", g.optInt("competitionId")).put("name", g.optString("competitionDisplayName"))
                val match = parseGame(g, comp, "", false, zone) ?: error("Bad game")
                val homeId = g.optJSONObject("homeCompetitor")?.optInt("id")
                val members = g.optJSONArray("members").byId()
                MatchDetail(
                    match = match.copy(venue = g.optJSONObject("venue")?.optString("name")?.takeIf { it.isNotBlank() } ?: match.venue),
                    incidents = parseEvents(g.optJSONArray("events"), homeId, members),
                    statistics = parseStats(stats?.optJSONArray("statistics"), homeId),
                    homeLineup = parseLineup(g.optJSONObject("homeCompetitor"), members),
                    awayLineup = parseLineup(g.optJSONObject("awayCompetitor"), members),
                    referee = g.optJSONArray("officials")?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    private fun parseGame(g: JSONObject, comp: JSONObject, countryName: String, international: Boolean, zone: ZoneId): Match? {
        val home = g.optJSONObject("homeCompetitor") ?: return null
        val away = g.optJSONObject("awayCompetitor") ?: return null
        val group = g.optInt("statusGroup")
        val short = g.optString("shortStatusText").ifBlank { g.optString("statusText") }
        val statusText0 = g.optString("statusText")
        val status = when {
            group == 3 && (short.equals("HT", true) || statusText0.contains("Half", true) && statusText0.contains("Time", true)) -> MatchStatus.HALFTIME
            group == 3 -> MatchStatus.LIVE
            group == 4 && (short.startsWith("Post", true) || statusText0.contains("Postponed", true)) -> MatchStatus.POSTPONED
            group == 4 && (short.startsWith("Canc", true) || statusText0.contains("Abandon", true)) -> MatchStatus.CANCELLED
            group == 4 -> MatchStatus.FULL_TIME
            else -> MatchStatus.SCHEDULED
        }
        val minute = g.optDouble("gameTime", -1.0).takeIf { it >= 0 }?.toInt()
        val statusText = when (status) {
            MatchStatus.LIVE -> g.optString("gameTimeDisplay").ifBlank { minute?.let { "$it'" } ?: "LIVE" }
            MatchStatus.HALFTIME -> "HT"
            MatchStatus.FULL_TIME -> when {
                short.contains("Pen", true) -> "FT (pens)"
                short.contains("ET", true) -> "AET"
                else -> "FT"
            }
            MatchStatus.POSTPONED -> "Postponed"
            MatchStatus.CANCELLED -> "Cancelled"
            else -> "Scheduled"
        }
        val iso = g.optString("startTime")
        val kickoff = runCatching {
            OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrDefault("")
        val played = status != MatchStatus.SCHEDULED && status != MatchStatus.POSTPONED && status != MatchStatus.CANCELLED
        fun team(o: JSONObject): Team {
            val id = o.optInt("id")
            val name = o.optString("name")
            return Team(
                id = "s365_$id", name = name, shortName = name,
                abbreviation = o.optString("symbolicName").ifBlank { name.take(3).uppercase() },
                crestUrl = teamLogo(id, o.optInt("imageVersion", 1)),
                colorHex = o.optString("color").removePrefix("#").takeIf { it.length == 6 }
            )
        }
        fun score(o: JSONObject) = if (played) o.optDouble("score", -1.0).takeIf { it >= 0 }?.toInt() else null
        val compName = comp.optString("name")
        return Match(
            id = "s365_${g.optInt("id")}",
            leagueId = "s365.${comp.optInt("id")}",
            leagueName = if (countryName.isNotBlank() && !international) "$compName · $countryName" else compName,
            leagueLogo = competitionLogo(comp.optInt("id"), comp.optInt("imageVersion", 1)),
            homeTeam = team(home),
            awayTeam = team(away),
            homeScore = score(home),
            awayScore = score(away),
            status = status,
            statusText = statusText,
            kickoffFormatted = kickoff,
            dateIso = iso,
            minute = if (status == MatchStatus.LIVE) minute else null,
        )
    }

    private fun parseEvents(arr: JSONArray?, homeId: Int?, members: Map<Int, JSONObject>): List<MatchIncident> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.getJSONObject(i)
            val t = e.optJSONObject("eventType") ?: return@mapNotNull null
            val name = t.optString("name").lowercase()
            val sub = t.optString("subTypeName").lowercase()
            val type = when {
                name.contains("goal") && sub.contains("penalty") && sub.contains("miss") -> IncidentType.PENALTY_MISSED
                name.contains("goal") && sub.contains("penalty") -> IncidentType.PENALTY_SCORED
                name.contains("goal") -> IncidentType.GOAL
                name.contains("red") || name.contains("second yellow") -> IncidentType.RED_CARD
                name.contains("yellow") -> IncidentType.YELLOW_CARD
                name.contains("substitution") -> IncidentType.SUBSTITUTION
                name.contains("var") -> IncidentType.VAR_DECISION
                else -> return@mapNotNull null
            }
            val player = members[e.optInt("playerId")]?.optString("name") ?: return@mapNotNull null
            val extra = e.optJSONArray("extraPlayers")?.optInt(0)?.let { members[it]?.optString("name") }
            val competitorId = e.optInt("competitorId")
            MatchIncident(
                id = "s365_ev_$i",
                minute = e.optString("gameTimeDisplay"),
                type = type,
                teamId = "s365_$competitorId",
                isHomeTeam = competitorId == homeId,
                primaryPlayer = player,
                secondaryPlayer = extra,
                detail = when {
                    sub.contains("own") -> "Own goal"
                    type == IncidentType.PENALTY_SCORED -> "Penalty"
                    type == IncidentType.PENALTY_MISSED -> "Penalty missed"
                    else -> null
                }
            )
        }
    }

    private fun parseStats(arr: JSONArray?, homeId: Int?): List<MatchStatistic> {
        arr ?: return emptyList()
        val all = (0 until arr.length()).map { arr.getJSONObject(it) }
        val byStat = all.groupBy { it.optInt("id") }
        return byStat.values.mapNotNull { pair ->
            val h = pair.firstOrNull { it.optInt("competitorId") == homeId } ?: return@mapNotNull null
            val a = pair.firstOrNull { it.optInt("competitorId") != homeId } ?: return@mapNotNull null
            val hv = h.optString("value")
            val av = a.optString("value")
            val hf = hv.substringBefore('/').removeSuffix("%").trim().toFloatOrNull() ?: return@mapNotNull null
            val af = av.substringBefore('/').removeSuffix("%").trim().toFloatOrNull() ?: return@mapNotNull null
            Triple(h.optInt("order"), h.optString("name"), MatchStatistic(h.optString("name"), hv, av, if (hf + af > 0) hf / (hf + af) else 0.5f))
        }.sortedBy { it.first }.map { it.third }
    }

    private fun parseLineup(competitor: JSONObject?, members: Map<Int, JSONObject>): TeamLineup? {
        val lu = competitor?.optJSONObject("lineups") ?: return null
        val list = lu.optJSONArray("members") ?: return null
        val all = (0 until list.length()).map { list.getJSONObject(it) }
        fun toPlayer(m: JSONObject, sub: Boolean): LineupPlayer {
            val info = members[m.optInt("id")]
            val pos = m.optJSONObject("position")?.optString("name").orEmpty().lowercase()
            // "s365a_<athleteId>" lets the line-up open the player's profile directly
            val athleteId = info?.optInt("athleteId")?.takeIf { it > 0 }
            return LineupPlayer(
                id = athleteId?.let { "s365a_$it" } ?: "s365_p_${m.optInt("id")}",
                name = info?.optString("name").orEmpty(),
                jersey = info?.optInt("jerseyNumber")?.takeIf { it > 0 }?.toString() ?: "",
                position = when {
                    pos.startsWith("goal") -> "GK"
                    pos.startsWith("def") -> "D"
                    pos.startsWith("mid") -> "M"
                    pos.startsWith("att") || pos.startsWith("for") -> "F"
                    else -> "M"
                },
                isSubstitute = sub
            )
        }
        // order starters by pitch line, then across, so formation rows map cleanly onto the pitch
        val starters = all.filter { it.optInt("status") == 1 }
            .sortedWith(compareBy({ it.optJSONObject("yardFormation")?.optInt("line") ?: 9 }, { it.optJSONObject("yardFormation")?.optInt("fieldPosition") ?: 0 }))
            .map { toPlayer(it, false) }
        if (starters.isEmpty()) return null
        return TeamLineup(
            teamName = competitor.optString("name"),
            formation = lu.optString("formation").takeIf { it.isNotBlank() },
            starters = starters,
            bench = all.filter { it.optInt("status") == 2 }.map { toPlayer(it, true) },
        )
    }

    private fun JSONArray?.byId(): Map<Int, JSONObject> {
        this ?: return emptyMap()
        return (0 until length()).associate { getJSONObject(it).let { o -> o.optInt("id") to o } }
    }
}
