package com.example.mintline.data.provider

import com.example.mintline.model.FormGame
import com.example.mintline.model.H2HGame
import com.example.mintline.model.LeagueLeaders
import com.example.mintline.model.MatchInsights
import com.example.mintline.model.PlayerLeader
import com.example.mintline.model.StandingGroup
import com.example.mintline.model.StandingRow
import com.example.mintline.model.TableRow
import com.example.mintline.model.Team
import com.example.mintline.model.TopPerformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * League tables, top scorers/assists and match context from ESPN's public
 * soccer endpoints (the same feeds the ESPN app uses):
 *  - /apis/v2/sports/soccer/{league}/standings
 *  - /apis/site/v2/sports/soccer/{league}/statistics
 *  - match summary: lastFiveGames, seasonseries, standings, leaders, commentary
 */
class EspnInsightsClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private fun get(url: String): JSONObject {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return JSONObject(resp.body!!.string())
        }
    }

    suspend fun fetchStandings(league: String): Result<List<StandingGroup>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = get("https://site.api.espn.com/apis/v2/sports/soccer/$league/standings")
            val groups = mutableListOf<StandingGroup>()
            val children = json.optJSONArray("children")
            if (children != null && children.length() > 0) {
                for (i in 0 until children.length()) {
                    val c = children.getJSONObject(i)
                    parseGroup(c.optString("name"), c.optJSONObject("standings"))?.let(groups::add)
                }
            } else {
                parseGroup(json.optString("name"), json.optJSONObject("standings"))?.let(groups::add)
            }
            groups
        }
    }

    suspend fun fetchLeaders(league: String): Result<LeagueLeaders> = withContext(Dispatchers.IO) {
        runCatching {
            val json = get("https://site.api.espn.com/apis/site/v2/sports/soccer/$league/statistics")
            val stats = json.optJSONArray("stats") ?: JSONArray()
            var goals = emptyList<PlayerLeader>()
            var assists = emptyList<PlayerLeader>()
            for (i in 0 until stats.length()) {
                val s = stats.getJSONObject(i)
                val list = parseLeaderList(s.optJSONArray("leaders"))
                when (s.optString("name")) {
                    "goalsLeaders" -> goals = list
                    "assistsLeaders" -> assists = list
                }
            }
            LeagueLeaders(goals, assists)
        }
    }

    private fun parseLeaderList(arr: JSONArray?): List<PlayerLeader> {
        arr ?: return emptyList()
        return (0 until minOf(arr.length(), 25)).mapNotNull { i ->
            val l = arr.getJSONObject(i)
            val a = l.optJSONObject("athlete") ?: return@mapNotNull null
            val team = a.optJSONObject("team")
            val matches = Regex("""Matches:\s*(\d+)""").find(l.optString("displayValue"))?.groupValues?.get(1)?.toIntOrNull()
            PlayerLeader(
                name = a.optString("displayName"),
                teamName = team?.optString("displayName").orEmpty(),
                teamLogo = team?.optJSONArray("logos")?.optJSONObject(0)?.optString("href"),
                value = l.optDouble("value", 0.0).toInt(),
                matches = matches,
            )
        }
    }

    companion object {
        /** Parses one ESPN standings block (used by league tables and match summaries). */
        fun parseGroup(name: String, standings: JSONObject?): StandingGroup? {
            val entries = standings?.optJSONArray("entries") ?: return null
            val rows = (0 until entries.length()).mapNotNull { i ->
                val e = entries.getJSONObject(i)
                val t = e.optJSONObject("team") ?: return@mapNotNull null
                val stats = mutableMapOf<String, String>()
                e.optJSONArray("stats")?.let { arr ->
                    for (k in 0 until arr.length()) {
                        val s = arr.getJSONObject(k)
                        val v = s.optString("displayValue", s.optString("value"))
                        s.optString("name").takeIf { it.isNotBlank() }?.let { stats[it] = v }
                        s.optString("abbreviation").takeIf { it.isNotBlank() }?.let { stats.putIfAbsent("abbr:$it", v) }
                    }
                }
                fun stat(name: String, abbr: String) =
                    (stats[name] ?: stats["abbr:$abbr"])?.replace("+", "")?.toDoubleOrNull()?.toInt() ?: 0
                val logo = t.optString("logo").takeIf { it.isNotBlank() }
                    ?: t.optJSONArray("logos")?.optJSONObject(0)?.optString("href")
                val note = e.optJSONObject("note")
                TableRow(
                    row = StandingRow(
                        rank = stat("rank", "R").takeIf { it > 0 } ?: (i + 1),
                        team = Team(
                            id = t.optString("id"),
                            name = t.optString("displayName"),
                            shortName = t.optString("shortDisplayName", t.optString("displayName")),
                            abbreviation = t.optString("abbreviation"),
                            crestUrl = logo,
                        ),
                        played = stat("gamesPlayed", "GP"),
                        won = stat("wins", "W"),
                        drawn = stat("ties", "D"),
                        lost = stat("losses", "L"),
                        goalsFor = stat("pointsFor", "F"),
                        goalsAgainst = stat("pointsAgainst", "A"),
                        goalDifference = stat("pointDifferential", "GD"),
                        points = stat("points", "P"),
                    ),
                    zoneColor = note?.optString("color")?.takeIf { it.isNotBlank() },
                    zoneLabel = note?.optString("description")?.takeIf { it.isNotBlank() },
                )
            }.sortedBy { it.row.rank }
            return if (rows.isEmpty()) null else StandingGroup(name, rows)
        }

        /** Form, head-to-head, table and top performers from a match summary. */
        fun parseMatchInsights(json: JSONObject, homeTeamId: String?): MatchInsights {
            fun form(teamIndex: Int): List<FormGame> {
                val arr = json.optJSONArray("lastFiveGames") ?: return emptyList()
                val block = (0 until arr.length()).map { arr.getJSONObject(it) }
                    .firstOrNull {
                        val id = it.optJSONObject("team")?.optString("id")
                        if (teamIndex == 0) id == homeTeamId else id != homeTeamId
                    } ?: return emptyList()
                val events = block.optJSONArray("events") ?: return emptyList()
                return (0 until events.length()).map { events.getJSONObject(it) }.map { e ->
                    val opp = e.optJSONObject("opponent")
                    FormGame(
                        result = e.optString("gameResult"),
                        score = e.optString("score"),
                        opponent = opp?.optString("displayName").orEmpty(),
                        opponentLogo = opp?.optString("logo")?.takeIf { it.isNotBlank() },
                        atHome = e.optString("atVs") != "@",
                        dateIso = e.optString("gameDate"),
                    )
                }
            }

            val h2h = mutableListOf<H2HGame>()
            json.optJSONArray("seasonseries")?.let { series ->
                for (s in 0 until series.length()) {
                    val events = series.getJSONObject(s).optJSONArray("events") ?: continue
                    for (i in 0 until events.length()) {
                        val ev = events.getJSONObject(i)
                        val comps = ev.optJSONArray("competitors") ?: continue
                        if (comps.length() < 2) continue
                        val list = (0 until comps.length()).map { comps.getJSONObject(it) }
                        val home = list.firstOrNull { it.optString("homeAway") == "home" } ?: list[0]
                        val away = list.firstOrNull { it.optString("homeAway") == "away" } ?: list[1]
                        fun name(c: JSONObject) = c.optJSONObject("team")?.let { it.optString("displayName").ifBlank { it.optString("abbreviation") } }.orEmpty()
                        fun logo(c: JSONObject) = c.optJSONObject("team")?.optString("logo")?.takeIf { it.isNotBlank() }
                        h2h += H2HGame(
                            dateIso = ev.optString("date"),
                            homeName = name(home), homeLogo = logo(home), homeScore = home.optString("score"),
                            awayName = name(away), awayLogo = logo(away), awayScore = away.optString("score"),
                        )
                    }
                }
            }

            val table = json.optJSONObject("standings")?.optJSONArray("groups")?.optJSONObject(0)?.let { g ->
                parseGroup(g.optString("header"), g.optJSONObject("standings"))
            }

            val performers = mutableListOf<TopPerformer>()
            json.optJSONArray("leaders")?.let { teams ->
                for (t in 0 until teams.length()) {
                    val block = teams.getJSONObject(t)
                    val team = block.optJSONObject("team")
                    val isHome = team?.optString("id") == homeTeamId
                    val cats = block.optJSONArray("leaders") ?: continue
                    for (c in 0 until cats.length()) {
                        val cat = cats.getJSONObject(c)
                        val top = cat.optJSONArray("leaders")?.optJSONObject(0) ?: continue
                        val player = top.optJSONObject("athlete")?.optString("displayName") ?: continue
                        performers += TopPerformer(
                            teamName = team?.optString("displayName").orEmpty(),
                            isHome = isHome,
                            category = cat.optString("displayName").ifBlank { prettify(cat.optString("name")) },
                            player = player,
                            value = top.optString("displayValue"),
                        )
                    }
                }
            }

            return MatchInsights(
                homeForm = form(0),
                awayForm = form(1),
                headToHead = h2h.sortedByDescending { it.dateIso }.take(6),
                table = table,
                performers = performers,
            )
        }

        /** "Lineups announced…" style play-by-play, newest first, with minute prefixes. */
        fun parseCommentary(json: JSONObject): List<String> {
            val arr = json.optJSONArray("commentary") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val c = arr.getJSONObject(i)
                val text = c.optString("text").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val minute = c.optJSONObject("time")?.optString("displayValue").orEmpty()
                if (minute.isBlank()) text else "$minute  $text"
            }.reversed()
        }

        private fun prettify(key: String) =
            key.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
    }
}
