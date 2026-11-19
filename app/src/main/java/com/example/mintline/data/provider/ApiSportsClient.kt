package com.example.mintline.data.provider

import android.content.Context
import com.example.mintline.BuildConfig
import com.example.mintline.model.Match
import com.example.mintline.model.MatchDetail
import com.example.mintline.model.MatchStatus
import com.example.mintline.model.StandingGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * API-Football (API-Sports v3) used directly from the app, for the
 * tournaments ESPN and 365Scores don't cover well: AFCON, CAF club cups,
 * CHAN, Botola Pro, Egyptian Premier League, South African PSL and the
 * youth/international tournaments (U-20/U-17 World Cups, UEFA U-21, UEFA
 * Youth League, Maurice Revello, Olympics).
 *
 * Quota protection for the free plan (100 requests/day):
 *  - every response is cached on disk (survives restarts):
 *      fixtures / standings / leagues 12–24 h+, live status 3 min, finished match 24 h
 *  - a per-day request budget stops network calls at [DAILY_BUDGET] and serves
 *    the stale cache instead; the API's own "requests remaining" header is honoured
 *  - the full league list is fetched once a week and tournament IDs are resolved
 *    from it locally (falling back to /leagues?search= only for unknown names).
 *
 * Shares JSON parsing with [ApiFootballProxyClient] (same API format), so match
 * IDs use the same "af_" / "af." prefixes.
 */
class ApiSportsClient(
    context: Context,
    private val key: String = BuildConfig.APIFOOTBALL_KEY,
) {
    private val base = "https://v3.football.api-sports.io"
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val cacheDir = File(context.cacheDir, "apisports").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("apisports_budget", Context.MODE_PRIVATE)
    private val parser = ApiFootballProxyClient(baseUrl = "")
    private val lock = Mutex()

    val isEnabled: Boolean get() = key.isNotBlank()

    companion object {
        const val DAILY_BUDGET = 90 // keep 10 of the 100 free requests as headroom
        private const val MIN = 60_000L
        private const val HOUR = 60 * MIN
        private const val DAY = 24 * HOUR

        /** Tournaments this source supplies, with known IDs as a fallback if resolution fails. */
        val TRACKED: List<Pair<String, Int>> = listOf(
            "Africa Cup of Nations" to 6,
            "Africa Cup of Nations - Qualification" to 36,
            "CAF Champions League" to 12,
            "CAF Confederation Cup" to 20,
            "African Nations Championship" to 19,
            "Botola Pro" to 200,
            "Egyptian Premier League" to 233,
            "Premier Soccer League" to 288,
            "World Cup - U20" to 490,
            "World Cup - U17" to 587,
            "UEFA U21 Championship" to 38,
            "UEFA U21 Championship - Qualification" to 850,
            "UEFA Youth League" to 14,
            "Tournoi Maurice Revello" to 914,
            "Olympics Men" to 480,
            "World Cup - U20 - Women" to 920,
            "World Cup - U17 - Women" to 950,
            "Africa Cup of Nations - Women" to 922,
        )
    }

    // ---------- budget + cache ----------

    private fun today() = LocalDate.now(ZoneOffset.UTC).toString()

    private fun used(): Int = if (prefs.getString("day", "") == today()) prefs.getInt("used", 0) else 0

    private fun canSpend(): Boolean =
        isEnabled && used() < DAILY_BUDGET && prefs.getInt("remaining_${today()}", 100) > 5

    private fun recordRequest(remainingHeader: String?) {
        val t = today()
        val n = used() + 1
        prefs.edit().putString("day", t).putInt("used", n).apply {
            remainingHeader?.toIntOrNull()?.let { putInt("remaining_$t", it) }
        }.apply()
    }

    private fun cacheFile(path: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.json")
    }

    /**
     * Cached GET: returns fresh cache if younger than [ttlMs], otherwise calls the
     * API if the daily budget allows, otherwise returns stale cache (or null).
     */
    private suspend fun get(path: String, ttlMs: Long): JSONObject? = withContext(Dispatchers.IO) {
        val file = cacheFile(path)
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < ttlMs
        if (fresh) return@withContext runCatching { JSONObject(file.readText()) }.getOrNull()
        lock.withLock {
            // another coroutine may have refreshed it while we waited
            if (file.exists() && System.currentTimeMillis() - file.lastModified() < ttlMs) {
                return@withLock runCatching { JSONObject(file.readText()) }.getOrNull()
            }
            if (!canSpend()) return@withLock runCatching { JSONObject(file.readText()) }.getOrNull()
            val fromNetwork = runCatching {
                http.newCall(Request.Builder().url(base + path).header("x-apisports-key", key).build()).execute().use { r ->
                    recordRequest(r.header("x-ratelimit-requests-remaining"))
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    val body = r.body!!.string()
                    val json = JSONObject(body)
                    val errors = json.opt("errors")
                    val hasErrors = (errors is JSONObject && errors.length() > 0) || (errors is JSONArray && errors.length() > 0)
                    if (hasErrors) error("API error: $errors")
                    file.writeText(body)
                    json
                }
            }.getOrNull()
            fromNetwork ?: runCatching { JSONObject(file.readText()) }.getOrNull()
        }
    }

    // ---------- dynamic league resolution ----------

    /** All leagues, fetched at most once a week. */
    private suspend fun allLeagues(): JSONArray? = get("/leagues", 7 * DAY)?.optJSONArray("response")

    /**
     * Resolves a tournament name to its API-Football ID using the cached league
     * list; only unknown names hit /leagues?search=. Results are stored locally.
     */
    suspend fun resolveLeagueId(name: String): Int? {
        val stored = prefs.getInt("league_$name", -1)
        if (stored > 0) return stored
        val list = allLeagues()
        val fromList = list?.let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .firstOrNull { it.optJSONObject("league")?.optString("name").equals(name, true) }
                ?.optJSONObject("league")?.optInt("id")
        }
        val id = fromList ?: run {
            val q = java.net.URLEncoder.encode(name.take(40), "UTF-8")
            get("/leagues?search=$q", 30 * DAY)?.optJSONArray("response")?.optJSONObject(0)?.optJSONObject("league")?.optInt("id")
        }
        if (id != null && id > 0) prefs.edit().putInt("league_$name", id).apply()
        return id
    }

    private suspend fun trackedIds(): Set<Int> =
        TRACKED.map { (name, fallback) -> prefs.getInt("league_$name", -1).takeIf { it > 0 } ?: fallback }.toSet()

    /** Resolves every tracked tournament once (uses the weekly cached league list — at most 1 request). */
    suspend fun warmUpLeagueIds() {
        if (!isEnabled) return
        TRACKED.forEach { (name, _) -> if (prefs.getInt("league_$name", -1) <= 0) resolveLeagueId(name) }
    }

    // ---------- data ----------

    /** @param dateString yyyyMMdd; fixtures of tracked tournaments with live scores merged in. */
    suspend fun fetchFixtures(dateString: String?, zone: ZoneId): Result<List<Match>> = runCatching {
        if (!isEnabled) return@runCatching emptyList()
        warmUpLeagueIds() // resolves tournament IDs from the weekly league list (cached)
        val day = dateString?.takeIf { it.length == 8 }?.let { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) } ?: LocalDate.now(zone)
        val isToday = day == LocalDate.now(zone)
        val ttl = if (day.isBefore(LocalDate.now(zone))) DAY else 12 * HOUR
        val ids = trackedIds()
        val arr = get("/fixtures?date=$day", ttl)?.optJSONArray("response") ?: JSONArray()
        var fixtures = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optJSONObject("league")?.optInt("id") in ids }

        // Live scores: only poll /fixtures?live=all (3-min cache) when a tracked game is plausibly in play
        if (isToday && fixtures.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val inPlay = fixtures.any { f ->
                val ts = f.optJSONObject("fixture")?.optLong("timestamp")?.times(1000) ?: 0L
                val short = f.optJSONObject("fixture")?.optJSONObject("status")?.optString("short")
                now in ts..(ts + 3 * HOUR) && short !in setOf("FT", "AET", "PEN", "PST", "CANC", "ABD", "AWD", "WO")
            }
            if (inPlay) {
                val live = get("/fixtures?live=all", 3 * MIN)?.optJSONArray("response")
                if (live != null) {
                    val byId = (0 until live.length()).map { live.getJSONObject(it) }
                        .associateBy { it.optJSONObject("fixture")?.optInt("id") }
                    fixtures = fixtures.map { f -> byId[f.optJSONObject("fixture")?.optInt("id")] ?: f }
                }
            }
        }
        fixtures.mapNotNull { f ->
            val m = parser.parseMatch(f, zone) ?: return@mapNotNull null
            // women's tournaments: tag teams so they can't be confused with the men's sides
            if (f.optJSONObject("league")?.optString("name").orEmpty().contains("Women", true))
                m.copy(homeTeam = m.homeTeam.copy(name = m.homeTeam.name + " (W)", shortName = m.homeTeam.shortName + " (W)"),
                    awayTeam = m.awayTeam.copy(name = m.awayTeam.name + " (W)", shortName = m.awayTeam.shortName + " (W)"))
            else m
        }
    }

    /** Reads a cache entry without ever touching the network. */
    private fun readCache(name: String, ttlMs: Long): JSONObject? {
        val file = cacheFile(name)
        return if (file.exists() && System.currentTimeMillis() - file.lastModified() < ttlMs)
            runCatching { JSONObject(file.readText()) }.getOrNull() else null
    }

    suspend fun fetchDetail(matchId: String, zone: ZoneId): Result<MatchDetail> = runCatching {
        val id = matchId.removePrefix("af_")
        val doneKey = "local:finished-fixture:$id" // local-only cache key for finished games (24 h)
        val f = readCache(doneKey, DAY)?.optJSONArray("response")?.optJSONObject(0)
            ?: get("/fixtures?id=$id", 3 * MIN)?.optJSONArray("response")?.optJSONObject(0)
            ?: error("Fixture not found")
        val match = parser.parseMatch(f, zone) ?: error("Bad fixture")
        if (match.status == MatchStatus.FULL_TIME) {
            runCatching { cacheFile(doneKey).writeText(JSONObject().put("response", JSONArray().put(f)).toString()) }
        }
        val homeId = f.optJSONObject("teams")?.optJSONObject("home")?.optInt("id")
        val awayId = f.optJSONObject("teams")?.optJSONObject("away")?.optInt("id")
        // League table (24 h cache): the group containing either team
        val league = f.optJSONObject("league")
        val table = runCatching {
            fetchStandings(league!!.optInt("id"), league.optInt("season")).getOrNull()
                ?.firstOrNull { g -> g.rows.any { it.row.team.id == "af_$homeId" || it.row.team.id == "af_$awayId" } }
        }.getOrNull()
        MatchDetail(
            match = match,
            incidents = parser.parseEvents(f.optJSONArray("events"), homeId),
            statistics = parser.parseStats(f.optJSONArray("statistics"), homeId),
            homeLineup = parser.parseLineup(f.optJSONArray("lineups"), homeId, home = true),
            awayLineup = parser.parseLineup(f.optJSONArray("lineups"), homeId, home = false),
            referee = f.optJSONObject("fixture")?.optString("referee")?.takeIf { it.isNotBlank() && it != "null" },
            insights = table?.let { com.example.mintline.model.MatchInsights(table = it) },
        )
    }

    /** Standings for an "af.<id>" league; season taken from the cached fixture of that league. */
    suspend fun fetchStandings(leagueId: Int, season: Int): Result<List<StandingGroup>> = runCatching {
        val arr = get("/standings?league=$leagueId&season=$season", DAY)?.optJSONArray("response")
            ?.optJSONObject(0)?.optJSONObject("league")?.optJSONArray("standings") ?: return@runCatching emptyList()
        (0 until arr.length()).mapNotNull { gi ->
            val group = arr.getJSONArray(gi)
            val rows = (0 until group.length()).map { group.getJSONObject(it) }.map { r ->
                val t = r.optJSONObject("team")
                val all = r.optJSONObject("all")
                com.example.mintline.model.TableRow(
                    row = com.example.mintline.model.StandingRow(
                        rank = r.optInt("rank"),
                        team = com.example.mintline.model.Team(
                            id = "af_${t?.optInt("id")}", name = t?.optString("name").orEmpty(),
                            shortName = t?.optString("name").orEmpty(), abbreviation = "",
                            crestUrl = t?.optString("logo")
                        ),
                        played = all?.optInt("played") ?: 0, won = all?.optInt("win") ?: 0,
                        drawn = all?.optInt("draw") ?: 0, lost = all?.optInt("lose") ?: 0,
                        goalsFor = all?.optJSONObject("goals")?.optInt("for") ?: 0,
                        goalsAgainst = all?.optJSONObject("goals")?.optInt("against") ?: 0,
                        goalDifference = r.optInt("goalsDiff"), points = r.optInt("points"),
                    ),
                    zoneLabel = r.optString("description").takeIf { it.isNotBlank() && it != "null" },
                )
            }
            if (rows.isEmpty()) null
            else StandingGroup(group.optJSONObject(0)?.optString("group").orEmpty(), rows)
        }
    }

    /** Requests used today (for the Settings screen). */
    fun usedToday(): Int = used()
}
