package com.example.mintline.data.provider

import com.example.mintline.BuildConfig
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
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Arab-world and African leagues from API-Football, through Mintline's own
 * Cloudflare Worker proxy (proxy/ in this repo), which keeps the API key
 * secret and caches responses for all users.
 *
 * IDs are prefixed so they never collide with ESPN data:
 *   match "af_<fixtureId>", league "af.<leagueId>", team "af_<teamId>".
 * Disabled (returns empty) when no proxy URL is configured at build time.
 */
class ApiFootballProxyClient(
    private val baseUrl: String = BuildConfig.MINTLINE_PROXY_URL.trimEnd('/'),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    val isEnabled: Boolean get() = baseUrl.startsWith("https://")

    private fun get(path: String): JSONObject {
        val req = Request.Builder().url(baseUrl + path).header("Accept", "application/json").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Proxy HTTP ${r.code}")
            return JSONObject(r.body!!.string())
        }
    }

    /** @param dateString yyyyMMdd (the app's date format) */
    suspend fun fetchFixtures(dateString: String?, zone: ZoneId): Result<List<Match>> = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext Result.success(emptyList())
        runCatching {
            val day = dateString?.takeIf { it.length == 8 }
                ?.let { "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)}" }
                ?: java.time.LocalDate.now(zone).toString()
            val arr = get("/v1/fixtures?date=$day").optJSONArray("fixtures") ?: JSONArray()
            (0 until arr.length()).mapNotNull { parseMatch(arr.getJSONObject(it), zone) }
        }
    }

    suspend fun fetchDetail(matchId: String, zone: ZoneId): Result<MatchDetail> = withContext(Dispatchers.IO) {
        runCatching {
            check(isEnabled) { "Proxy not configured" }
            val id = matchId.removePrefix("af_")
            val f = get("/v1/fixture?id=$id").optJSONObject("fixture") ?: error("Fixture not found")
            val match = parseMatch(f, zone) ?: error("Bad fixture")
            val homeId = f.optJSONObject("teams")?.optJSONObject("home")?.optInt("id")
            MatchDetail(
                match = match,
                incidents = parseEvents(f.optJSONArray("events"), homeId),
                statistics = parseStats(f.optJSONArray("statistics"), homeId),
                homeLineup = parseLineup(f.optJSONArray("lineups"), homeId, home = true),
                awayLineup = parseLineup(f.optJSONArray("lineups"), homeId, home = false),
                referee = f.optJSONObject("fixture")?.optString("referee")?.takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }

    internal fun parseMatch(f: JSONObject, zone: ZoneId): Match? {
        val fx = f.optJSONObject("fixture") ?: return null
        val league = f.optJSONObject("league") ?: return null
        val teams = f.optJSONObject("teams") ?: return null
        val goals = f.optJSONObject("goals")
        val st = fx.optJSONObject("status")
        val short = st?.optString("short").orEmpty()
        val elapsed = st?.optInt("elapsed", -1)?.takeIf { it >= 0 }
        val extra = st?.optInt("extra", 0) ?: 0
        val status = when (short) {
            "1H", "2H", "ET", "BT", "P", "LIVE", "INT", "SUSP" -> MatchStatus.LIVE
            "HT" -> MatchStatus.HALFTIME
            "FT", "AET", "PEN", "AWD", "WO" -> MatchStatus.FULL_TIME
            "PST" -> MatchStatus.POSTPONED
            "CANC", "ABD" -> MatchStatus.CANCELLED
            else -> MatchStatus.SCHEDULED
        }
        val statusText = when (status) {
            MatchStatus.LIVE -> elapsed?.let { if (extra > 0) "$it'+$extra'" else "$it'" } ?: "LIVE"
            MatchStatus.HALFTIME -> "HT"
            MatchStatus.FULL_TIME -> if (short == "PEN") "FT (pens)" else if (short == "AET") "AET" else "FT"
            MatchStatus.POSTPONED -> "Postponed"
            MatchStatus.CANCELLED -> "Cancelled"
            else -> "Scheduled"
        }
        val iso = fx.optString("date")
        val kickoff = runCatching {
            OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrDefault("")
        val played = status != MatchStatus.SCHEDULED && status != MatchStatus.POSTPONED && status != MatchStatus.CANCELLED
        fun team(o: JSONObject?): Team {
            val id = o?.optInt("id") ?: 0
            val name = o?.optString("name").orEmpty()
            return Team(id = "af_$id", name = name, shortName = name, abbreviation = name.take(3).uppercase(),
                crestUrl = o?.optString("logo")?.takeIf { it.isNotBlank() })
        }
        val country = league.optString("country").replace('-', ' ')
        return Match(
            id = "af_${fx.optInt("id")}",
            leagueId = "af.${league.optInt("id")}",
            leagueName = league.optString("name").let { if (country.isNotBlank() && country != "World") "$it · $country" else it },
            leagueLogo = league.optString("logo").takeIf { it.isNotBlank() },
            homeTeam = team(teams.optJSONObject("home")),
            awayTeam = team(teams.optJSONObject("away")),
            homeScore = if (played) goals?.optInt("home", -1)?.takeIf { it >= 0 } else null,
            awayScore = if (played) goals?.optInt("away", -1)?.takeIf { it >= 0 } else null,
            status = status,
            statusText = statusText,
            kickoffFormatted = kickoff,
            dateIso = iso,
            venue = fx.optJSONObject("venue")?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
            minute = if (status == MatchStatus.LIVE) elapsed else null,
        )
    }

    internal fun parseEvents(arr: JSONArray?, homeId: Int?): List<MatchIncident> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.getJSONObject(i)
            val type = e.optString("type").lowercase()
            val detail = e.optString("detail").lowercase()
            val incident = when {
                type == "goal" && detail.contains("missed") -> IncidentType.PENALTY_MISSED
                type == "goal" && detail.contains("penalty") -> IncidentType.PENALTY_SCORED
                type == "goal" -> IncidentType.GOAL
                type == "card" && (detail.contains("red") || detail.contains("second")) -> IncidentType.RED_CARD
                type == "card" -> IncidentType.YELLOW_CARD
                type == "subst" -> IncidentType.SUBSTITUTION
                type == "var" -> IncidentType.VAR_DECISION
                else -> return@mapNotNull null
            }
            val teamId = e.optJSONObject("team")?.optInt("id")
            val ownGoal = detail.contains("own goal")
            val time = e.optJSONObject("time")
            val minute = time?.optInt("elapsed")?.let { el ->
                val ex = time.optInt("extra", 0)
                if (ex > 0) "$el'+$ex'" else "$el'"
            } ?: ""
            MatchIncident(
                id = "af_ev_$i",
                minute = minute,
                type = incident,
                teamId = "af_$teamId",
                // API-Football lists own goals under the scorer's team: credit the opponent
                isHomeTeam = (teamId == homeId) != ownGoal,
                primaryPlayer = e.optJSONObject("player")?.optString("name")?.takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null,
                secondaryPlayer = e.optJSONObject("assist")?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
                detail = when {
                    ownGoal -> "Own goal"
                    incident == IncidentType.PENALTY_SCORED -> "Penalty"
                    incident == IncidentType.PENALTY_MISSED -> "Penalty missed"
                    else -> null
                }
            )
        }
    }

    internal fun parseStats(arr: JSONArray?, homeId: Int?): List<MatchStatistic> {
        if (arr == null || arr.length() < 2) return emptyList()
        val blocks = (0 until arr.length()).map { arr.getJSONObject(it) }
        val home = blocks.firstOrNull { it.optJSONObject("team")?.optInt("id") == homeId } ?: blocks[0]
        val away = blocks.firstOrNull { it !== home } ?: return emptyList()
        fun map(b: JSONObject) = b.optJSONArray("statistics")?.let { s ->
            (0 until s.length()).associate { s.getJSONObject(it).let { o -> o.optString("type") to o.opt("value") } }
        }.orEmpty()
        val h = map(home)
        val a = map(away)
        return h.keys.mapNotNull { key ->
            val hv = h[key]?.takeIf { it != JSONObject.NULL }?.toString() ?: "0"
            val av = a[key]?.takeIf { it != JSONObject.NULL }?.toString() ?: "0"
            val hf = hv.removeSuffix("%").toFloatOrNull() ?: return@mapNotNull null
            val af = av.removeSuffix("%").toFloatOrNull() ?: return@mapNotNull null
            val label = if (key.equals("Ball Possession", true)) "Possession" else key
            MatchStatistic(label, hv, av, if (hf + af > 0) hf / (hf + af) else 0.5f)
        }
    }

    internal fun parseLineup(arr: JSONArray?, homeId: Int?, home: Boolean): TeamLineup? {
        arr ?: return null
        val block = (0 until arr.length()).map { arr.getJSONObject(it) }
            .firstOrNull { (it.optJSONObject("team")?.optInt("id") == homeId) == home } ?: return null
        fun players(key: String, sub: Boolean) = block.optJSONArray(key)?.let { list ->
            (0 until list.length()).mapNotNull { i ->
                val p = list.getJSONObject(i).optJSONObject("player") ?: return@mapNotNull null
                LineupPlayer(
                    id = "af_p_${p.optInt("id")}",
                    name = p.optString("name"),
                    jersey = p.optInt("number").takeIf { it > 0 }?.toString() ?: "",
                    position = when (p.optString("pos")) { "G" -> "GK"; "D" -> "D"; "M" -> "M"; "F" -> "F"; else -> p.optString("pos") },
                    isSubstitute = sub
                )
            }
        }.orEmpty()
        val starters = players("startXI", false)
        if (starters.isEmpty()) return null
        return TeamLineup(
            teamName = block.optJSONObject("team")?.optString("name").orEmpty(),
            formation = block.optString("formation").takeIf { it.isNotBlank() && it != "null" },
            starters = starters,
            bench = players("substitutes", true),
            coach = block.optJSONObject("coach")?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }
        )
    }
}
