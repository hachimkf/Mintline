package com.example.mintline.data.provider

import com.example.mintline.model.NewsArticle
import com.example.mintline.model.PlayerHit
import com.example.mintline.model.PlayerProfile
import com.example.mintline.model.PlayerRecentMatch
import com.example.mintline.model.PlayerSeasonRow
import com.example.mintline.model.PlayerTransfer
import com.example.mintline.model.PlayerTrophy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Player profiles (365Scores athletes — photo, details, season stats,
 * transfers with fees, trophies, recent ratings) and football news (ESPN).
 * Transfermarkt has no public API, so this gives the same kind of profile
 * from sources the app can read freely.
 */
class PlayersNewsClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val base = "https://webws.365scores.com/web"
    private val lang: Int get() = if (Locale.getDefault().language == "ar") 27 else 1
    private val params get() = "appTypeId=5&langId=$lang&timezoneName=UTC&userCountryId=-1"

    private fun get(url: String): JSONObject {
        client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return JSONObject(r.body!!.string())
        }
    }

    fun photo(id: Int, v: Int = 1) =
        "https://imagecache.365scores.com/image/upload/f_png,w_240,h_240,c_limit,q_auto:eco,dpr_2,d_Athletes:default.png/v$v/Athletes/$id"

    private fun clubLogo(id: Int, v: Int = 1) =
        "https://imagecache.365scores.com/image/upload/f_png,w_96,h_96,c_limit,q_auto:eco,dpr_2,d_Competitors:default1.png/v$v/Competitors/$id"

    suspend fun searchPlayers(query: String): Result<List<PlayerHit>> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val json = get("$base/search/?$params&query=$q")
            val clubs = json.optJSONArray("competitors").byId()
            val arr = json.optJSONArray("athletes") ?: JSONArray()
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.optInt("sportId", 1) == 1 }
                .take(15)
                .map { a ->
                    val club = clubs[a.optInt("clubId")]
                    PlayerHit(
                        id = a.optInt("id"),
                        name = a.optString("name"),
                        clubName = club?.optString("name"),
                        clubLogo = club?.let { clubLogo(it.optInt("id"), it.optInt("imageVersion", 1)) },
                        position = a.optJSONObject("position")?.optString("name"),
                        photoUrl = photo(a.optInt("id"), a.optInt("imageVersion", 1)),
                    )
                }
        }
    }

    /**
     * Finds the 365Scores player for a line-up name from ESPN, preferring the
     * one whose club matches the team being shown.
     */
    suspend fun resolvePlayer(name: String, teamName: String?): Result<Int?> {
        val hits = searchPlayers(name).getOrElse { return Result.failure(it) }
        val team = teamName?.lowercase()?.replace(Regex("\\s*\\(w\\)$"), "")
        val best = hits.firstOrNull { h ->
            val c = h.clubName?.lowercase() ?: return@firstOrNull false
            team != null && (c.contains(team) || team.contains(c) || c.split(" ").any { it.length > 3 && team.contains(it) })
        } ?: hits.firstOrNull()
        return Result.success(best?.id)
    }

    suspend fun profile(id: Int): Result<PlayerProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val json = get("$base/athletes/?$params&athletes=$id&fullDetails=true")
            val a = json.optJSONArray("athletes")?.optJSONObject(0) ?: error("Player not found")
            val clubs = json.optJSONArray("competitors").byId()
            fun clubName(cid: Int) = clubs[cid]?.optString("name")
            fun clubLogoOf(cid: Int) = clubs[cid]?.let { clubLogo(cid, it.optInt("imageVersion", 1)) }

            // "playerDetails": [{title:"20/12/1998", value:"27 years"}, {title:"Height", value:"1.80"}, ...]
            var birth: String? = null; var height: String? = null; var foot: String? = null
            a.optJSONArray("playerDetails")?.let { pd ->
                for (i in 0 until pd.length()) {
                    val o = pd.getJSONObject(i)
                    val t = o.optString("title"); val v = o.optString("value")
                    when {
                        Regex("""\d{1,2}/\d{1,2}/\d{4}""").matches(t) -> birth = t
                        t.equals("Height", true) -> height = "$v m"
                        t.contains("Foot", true) -> foot = v
                    }
                }
            }

            // Current season stats tables
            val season = a.optJSONObject("careerStats")?.optJSONArray("seasons")?.optJSONObject(0)
            val stats = season?.optJSONObject("stats")
            val categories = stats?.optJSONArray("categories")
            val tables = stats?.optJSONArray("tables")
            val seasonTables = (0 until (tables?.length() ?: 0)).mapNotNull { ti ->
                val t = tables!!.getJSONObject(ti)
                val cols = t.optJSONArray("columns") ?: return@mapNotNull null
                val colByName = (0 until cols.length()).associate { cols.getJSONObject(it).let { c -> c.optString("name") to c.optInt("num") } }
                fun cell(row: JSONObject, col: String): String {
                    val num = colByName[col] ?: return "–"
                    val vals = row.optJSONArray("values") ?: return "–"
                    for (k in 0 until vals.length()) vals.getJSONObject(k).let { if (it.optInt("columnNum") == num) return it.optString("value") }
                    return "–"
                }
                val rows = t.optJSONArray("rows") ?: return@mapNotNull null
                val list = (0 until rows.length()).map { rows.getJSONObject(it) }.map { r ->
                    PlayerSeasonRow(
                        competition = r.optString("title"),
                        appearances = cell(r, "Appearances"), starts = cell(r, "Lineups"),
                        goals = cell(r, "Goals"), assists = cell(r, "Assists"),
                        yellow = cell(r, "Yellow Cards"), red = cell(r, "Red Cards"),
                    )
                }
                val title = categories?.optJSONObject(ti)?.optString("name") ?: "Season"
                if (list.isEmpty()) null else title to list
            }

            val recent = a.optJSONObject("lastMatches")?.optJSONArray("games")?.let { games ->
                (0 until minOf(games.length(), 6)).mapNotNull { i ->
                    val item = games.getJSONObject(i)
                    val g = item.optJSONObject("game") ?: return@mapNotNull null
                    val h = g.optJSONObject("homeCompetitor") ?: return@mapNotNull null
                    val aw = g.optJSONObject("awayCompetitor") ?: return@mapNotNull null
                    val st = item.optJSONArray("athleteStats")
                    var minutes: String? = null; var rating: String? = null; var color: String? = null
                    if (st != null) for (k in 0 until st.length()) {
                        val s = st.getJSONObject(k)
                        if (s.optInt("type") == 229) minutes = s.optString("value").takeIf { it.isNotBlank() }
                        if (s.has("bgColor")) { rating = s.optString("value"); color = s.optString("bgColor") }
                    }
                    fun sc(o: JSONObject) = o.optDouble("score", -1.0).takeIf { it >= 0 }?.toInt()?.toString() ?: "-"
                    PlayerRecentMatch(
                        gameId = g.optInt("id"), dateIso = g.optString("startTime"),
                        competition = g.optString("competitionDisplayName"),
                        homeName = h.optString("name"), homeLogo = clubLogo(h.optInt("id"), h.optInt("imageVersion", 1)), homeScore = sc(h),
                        awayName = aw.optString("name"), awayLogo = clubLogo(aw.optInt("id"), aw.optInt("imageVersion", 1)), awayScore = sc(aw),
                        minutes = minutes, rating = rating, ratingColor = color,
                    )
                }
            }.orEmpty()

            val transfers = a.optJSONArray("transfers")?.let { t ->
                (0 until t.length()).map { t.getJSONObject(it) }.map { o ->
                    PlayerTransfer(
                        clubName = clubName(o.optInt("competitorId")) ?: "—",
                        clubLogo = clubLogoOf(o.optInt("competitorId")),
                        dateIso = o.optString("date"),
                        type = o.optString("transferTitle"),
                        fee = o.optString("price").takeIf { it.isNotBlank() },
                    )
                }
            }.orEmpty()

            val trophies = a.optJSONObject("trophies")?.optJSONArray("categories")?.let { cats ->
                (0 until cats.length()).flatMap { ci ->
                    val list = cats.getJSONObject(ci).optJSONArray("trophies") ?: return@flatMap emptyList()
                    (0 until list.length()).map { list.getJSONObject(it) }
                        .map { PlayerTrophy(it.optString("displayName").ifBlank { it.optString("name") }, it.optInt("count")) }
                }
            }.orEmpty().sortedByDescending { it.count }

            val clubId = a.optInt("clubId")
            PlayerProfile(
                id = id,
                name = a.optString("name"),
                photoUrl = photo(id, a.optInt("imageVersion", 1)),
                clubName = clubName(clubId),
                clubLogo = clubLogoOf(clubId),
                nationality = a.optString("nationalityName").takeIf { it.isNotBlank() },
                position = a.optJSONObject("position")?.optString("name"),
                jersey = a.optInt("jerseyNum").takeIf { it > 0 }?.toString(),
                age = a.optInt("age").takeIf { it > 0 },
                birthDate = birth,
                height = height,
                foot = foot,
                contractUntil = a.optString("contractUntil").takeIf { it.length >= 10 }?.substring(0, 10),
                bio = a.optString("shortBio").takeIf { it.isNotBlank() },
                seasonName = season?.optString("name"),
                seasonTables = seasonTables,
                recentMatches = recent,
                transfers = transfers,
                trophies = trophies,
            )
        }
    }

    /** ESPN football news for a league slug ("all" = general football). */
    suspend fun news(league: String): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = get("https://site.api.espn.com/apis/site/v2/sports/soccer/$league/news?limit=30")
            val arr = json.optJSONArray("articles") ?: JSONArray()
            (0 until arr.length()).map { arr.getJSONObject(it) }.map { a ->
                NewsArticle(
                    headline = a.optString("headline"),
                    description = a.optString("description").takeIf { it.isNotBlank() },
                    imageUrl = a.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                    url = a.optJSONObject("links")?.optJSONObject("web")?.optString("href"),
                    publishedIso = a.optString("published").takeIf { it.isNotBlank() },
                    isVideo = a.optString("type") == "Media",
                )
            }.filter { it.headline.isNotBlank() }
        }
    }

    private fun JSONArray?.byId(): Map<Int, JSONObject> {
        this ?: return emptyMap()
        return (0 until length()).associate { getJSONObject(it).let { o -> o.optInt("id") to o } }
    }
}
