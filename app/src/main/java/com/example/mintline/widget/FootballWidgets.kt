package com.example.mintline.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import com.example.mintline.MainActivity
import com.example.mintline.R
import com.example.mintline.data.FootballCatalog
import com.example.mintline.data.provider.EspnInsightsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Shared plumbing for Mintline's home-screen widgets: which team / league to
 * show (the user's first favourites), ESPN fetches, crest bitmaps.
 */
internal object WidgetData {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun favoriteTeamIds(ctx: Context): Set<String> =
        ctx.getSharedPreferences("mintline_favorites", Context.MODE_PRIVATE)
            .getStringSet("fav_teams", emptySet()).orEmpty()

    /** Main team for single-team widgets: first favourite club (catalog order), else a nation. */
    fun favoriteTeamId(ctx: Context): String? {
        val favs = favoriteTeamIds(ctx)
        return FootballCatalog.clubs.firstOrNull { it.espnId in favs }?.espnId
            ?: FootballCatalog.nations.firstOrNull { it.espnId in favs }?.espnId
            ?: favs.sorted().firstOrNull()
    }

    fun favoriteLeagueId(ctx: Context): String =
        ctx.getSharedPreferences("mintline_favorites", Context.MODE_PRIVATE)
            .getStringSet("fav_leagues", emptySet())
            ?.sortedBy { FootballCatalog.rankOf(it) }
            ?.firstOrNull { FootballCatalog.leagueBySlug(it)?.isCup == false }
            ?: "eng.1"

    fun json(url: String): JSONObject? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) JSONObject(r.body!!.string()) else null
        }
    }.getOrNull()

    fun bitmap(url: String?): Bitmap? = url?.takeIf { it.isNotBlank() }?.let { u ->
        runCatching {
            http.newCall(Request.Builder().url(u).build()).execute().use { r ->
                r.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()?.let { Bitmap.createScaledBitmap(it, 96, 96, true) }
    }

    fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun parseIso(iso: String): java.util.Date? = runCatching {
        val f = if (iso.count { it == ':' } >= 2) "yyyy-MM-dd'T'HH:mm:ss" else "yyyy-MM-dd'T'HH:mm"
        SimpleDateFormat(f, Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso.removeSuffix("Z"))
    }.getOrNull()

    data class Side(val name: String, val logo: String?, val score: String?, val id: String)

    fun sides(event: JSONObject): Pair<Side, Side>? {
        val comps = event.optJSONArray("competitions")?.optJSONObject(0)?.optJSONArray("competitors") ?: return null
        val list = (0 until comps.length()).map { comps.getJSONObject(it) }
        fun side(o: JSONObject): Side {
            val t = o.optJSONObject("team") ?: JSONObject()
            val score = when (val s = o.opt("score")) {
                is JSONObject -> s.optString("displayValue")
                is String -> s
                else -> null
            }
            val logo = t.optJSONArray("logos")?.optJSONObject(0)?.optString("href") ?: t.optString("logo")
            return Side(t.optString("shortDisplayName", t.optString("displayName")), logo, score, t.optString("id"))
        }
        val home = list.firstOrNull { it.optString("homeAway") == "home" } ?: return null
        val away = list.firstOrNull { it.optString("homeAway") == "away" } ?: return null
        return side(home) to side(away)
    }

    fun refreshAll(ctx: Context) {
        listOf(
            NextMatchWidget::class.java, ResultFormWidget::class.java, LeagueTableWidget::class.java,
            FullTimeWidget::class.java, PlayerWidget::class.java, TeamStatsWidget::class.java
        ).forEach { cls ->
            val ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isNotEmpty()) {
                ctx.sendBroadcast(Intent(ctx, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
            }
        }
    }
}

/** Base: runs [build] off the main thread and pushes the RemoteViews. */
abstract class AsyncWidget : AppWidgetProvider() {
    abstract fun build(ctx: Context): RemoteViews

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        WidgetData.scope.launch {
            try {
                val views = runCatching { build(context) }.getOrElse { RemoteViews(context.packageName, R.layout.widget_empty) }
                views.setOnClickPendingIntent(R.id.widget_root, WidgetData.openAppIntent(context))
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Match centre: the main live game right now (favourite teams first, then
 * favourite leagues, then the biggest competition playing). With nothing
 * live, the soonest upcoming game of any favourite team, with a countdown.
 */
class NextMatchWidget : AsyncWidget() {
    override fun build(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_next_match)
        val favTeams = WidgetData.favoriteTeamIds(ctx)
        val favLeagues = ctx.getSharedPreferences("mintline_favorites", Context.MODE_PRIVATE).getStringSet("fav_leagues", emptySet()).orEmpty()

        // 1) live now
        val board = WidgetData.json("https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard")
        val live = board?.optJSONArray("events")?.let { ev -> (0 until ev.length()).map { ev.getJSONObject(it) } }.orEmpty()
            .filter { it.optJSONArray("competitions")?.optJSONObject(0)?.optJSONObject("status")?.optJSONObject("type")?.optString("state") == "in" }
        if (live.isNotEmpty()) {
            fun leagueOf(e: JSONObject) = FootballCatalog.leagueByEspnId(Regex("""~l:(\d+)""").find(e.optString("uid"))?.groupValues?.get(1))
            val best = live.minByOrNull { e ->
                val (h, a) = WidgetData.sides(e) ?: return@minByOrNull 9999
                val lg = leagueOf(e)
                when {
                    h.id in favTeams || a.id in favTeams -> 0
                    lg?.slug in favLeagues -> 100 + (lg?.rank ?: 0)
                    else -> 1000 + (lg?.rank ?: 500)
                }
            }!!
            val (home, away) = WidgetData.sides(best)!!
            val comp = best.optJSONArray("competitions")!!.getJSONObject(0)
            val league = leagueOf(best)
            v.setTextViewText(R.id.nm_title, "Live now")
            v.setTextViewText(R.id.nm_home, home.name)
            v.setTextViewText(R.id.nm_away, away.name)
            v.setTextViewText(R.id.nm_time, "${home.score ?: 0} - ${away.score ?: 0}")
            v.setTextViewText(R.id.nm_date, "● " + (comp.optJSONObject("status")?.optString("displayClock") ?: "LIVE"))
            v.setTextColor(R.id.nm_date, 0xFFFF6B5E.toInt())
            v.setTextViewText(R.id.nm_venue, comp.optJSONObject("venue")?.optString("fullName").orEmpty())
            v.setViewVisibility(R.id.nm_countdown_box, View.GONE)
            v.setTextViewText(R.id.nm_league, league?.name?.uppercase() ?: "LIVE")
            WidgetData.bitmap(home.logo)?.let { v.setImageViewBitmap(R.id.nm_home_logo, it) }
            WidgetData.bitmap(away.logo)?.let { v.setImageViewBitmap(R.id.nm_away_logo, it) }
            WidgetData.bitmap(league?.logoUrl)?.let { v.setImageViewBitmap(R.id.nm_league_logo, it) }
            return v
        }

        // 2) soonest upcoming game across all favourite teams
        if (favTeams.isEmpty()) return emptyState(ctx, "Pick a favourite team in Mintline")
        val now = System.currentTimeMillis()
        val next = favTeams.take(6).mapNotNull { id ->
            val json = WidgetData.json("https://site.api.espn.com/apis/site/v2/sports/soccer/all/teams/$id/schedule?fixture=true") ?: return@mapNotNull null
            val ev = json.optJSONArray("events") ?: return@mapNotNull null
            (0 until ev.length()).map { ev.getJSONObject(it) }
                .filter { it.optJSONArray("competitions")?.optJSONObject(0)?.optJSONObject("status")?.optJSONObject("type")?.optString("state") == "pre" }
                .mapNotNull { e -> WidgetData.parseIso(e.optString("date"))?.let { e to it.time } }
                .filter { it.second > now }
                .minByOrNull { it.second }
        }.minByOrNull { it.second } ?: return emptyState(ctx, "No upcoming games for your teams")
        val (event, kickoff) = next
        val (home, away) = WidgetData.sides(event) ?: return emptyState(ctx, "No upcoming games")
        val comp = event.optJSONArray("competitions")!!.getJSONObject(0)
        val date = java.util.Date(kickoff)
        v.setTextViewText(R.id.nm_title, "Next Match")
        v.setTextViewText(R.id.nm_home, home.name)
        v.setTextViewText(R.id.nm_away, away.name)
        v.setTextViewText(R.id.nm_time, SimpleDateFormat("HH:mm", Locale.getDefault()).format(date))
        v.setTextViewText(R.id.nm_date, SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(date))
        v.setTextViewText(R.id.nm_venue, comp.optJSONObject("venue")?.optString("fullName").orEmpty())
        // countdown: ticking clock inside 24h, otherwise days + hours
        val left = kickoff - now
        v.setViewVisibility(R.id.nm_countdown_box, View.VISIBLE)
        if (left < 24 * 3600_000L) {
            v.setViewVisibility(R.id.nm_countdown, View.VISIBLE)
            v.setViewVisibility(R.id.nm_countdown_days, View.GONE)
            v.setChronometerCountDown(R.id.nm_countdown, true)
            v.setChronometer(R.id.nm_countdown, android.os.SystemClock.elapsedRealtime() + left, null, true)
        } else {
            v.setViewVisibility(R.id.nm_countdown, View.GONE)
            v.setViewVisibility(R.id.nm_countdown_days, View.VISIBLE)
            val d = left / (24 * 3600_000L); val h = (left / 3600_000L) % 24
            v.setTextViewText(R.id.nm_countdown_days, "${d}d ${h}h")
        }
        val league = FootballCatalog.leagueBySlug(event.optJSONObject("league")?.optString("slug"))
        v.setTextViewText(R.id.nm_league, league?.name?.uppercase() ?: event.optJSONObject("league")?.optString("abbreviation").orEmpty())
        WidgetData.bitmap(home.logo)?.let { v.setImageViewBitmap(R.id.nm_home_logo, it) }
        WidgetData.bitmap(away.logo)?.let { v.setImageViewBitmap(R.id.nm_away_logo, it) }
        WidgetData.bitmap(league?.logoUrl)?.let { v.setImageViewBitmap(R.id.nm_league_logo, it) }
        return v
    }
}

/** Last result with the five-game form strip. */
class ResultFormWidget : AsyncWidget() {
    override fun build(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_result_form)
        val teamId = WidgetData.favoriteTeamId(ctx) ?: return emptyState(ctx, "Pick a favourite team in Mintline")
        val json = WidgetData.json("https://site.api.espn.com/apis/site/v2/sports/soccer/all/teams/$teamId/schedule")
            ?: return emptyState(ctx, "Couldn't load results")
        val events = json.optJSONArray("events") ?: return emptyState(ctx, "No results yet")
        val played = (0 until events.length()).map { events.getJSONObject(it) }
            .filter { it.optJSONArray("competitions")?.optJSONObject(0)?.optJSONObject("status")?.optJSONObject("type")?.optString("state") == "post" }
            .sortedByDescending { it.optString("date") }
        val last = played.firstOrNull() ?: return emptyState(ctx, "No results yet")
        val (home, away) = WidgetData.sides(last) ?: return emptyState(ctx, "No results yet")
        v.setTextViewText(R.id.rf_score, "${home.score ?: "-"} - ${away.score ?: "-"}")
        v.setTextViewText(R.id.rf_home, home.name)
        v.setTextViewText(R.id.rf_away, away.name)
        WidgetData.bitmap(home.logo)?.let { v.setImageViewBitmap(R.id.rf_home_logo, it) }
        WidgetData.bitmap(away.logo)?.let { v.setImageViewBitmap(R.id.rf_away_logo, it) }

        val dots = listOf(R.id.rf_f1, R.id.rf_f2, R.id.rf_f3, R.id.rf_f4, R.id.rf_f5)
        val form = played.take(5).reversed().map { e ->
            val (h, a) = WidgetData.sides(e) ?: return@map "D"
            val mine = if (h.id == teamId) h else a
            val theirs = if (h.id == teamId) a else h
            val m = mine.score?.toIntOrNull() ?: 0
            val t = theirs.score?.toIntOrNull() ?: 0
            when { m > t -> "W"; m < t -> "L"; else -> "D" }
        }
        dots.forEachIndexed { i, id ->
            val r = form.getOrNull(i)
            if (r == null) v.setViewVisibility(id, View.INVISIBLE) else {
                v.setViewVisibility(id, View.VISIBLE)
                v.setTextViewText(id, r)
                v.setInt(id, "setBackgroundResource", when (r) {
                    "W" -> R.drawable.widget_form_w
                    "L" -> R.drawable.widget_form_l
                    else -> R.drawable.widget_form_d
                })
            }
        }
        return v
    }
}

/** Top of the favourite league's table. */
class LeagueTableWidget : AsyncWidget() {
    override fun build(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_league_table)
        val leagueId = WidgetData.favoriteLeagueId(ctx)
        val json = WidgetData.json("https://site.api.espn.com/apis/v2/sports/soccer/$leagueId/standings")
            ?: return emptyState(ctx, "Couldn't load the table")
        val children = json.optJSONArray("children")
        val block = children?.optJSONObject(0) ?: json
        val group = EspnInsightsClient.parseGroup(block.optString("name"), block.optJSONObject("standings"))
            ?: return emptyState(ctx, "No table yet")
        val league = FootballCatalog.leagueBySlug(leagueId)
        v.setTextViewText(R.id.lt_title, "${league?.name ?: "League"} table")
        v.setTextViewText(R.id.lt_season, (Regex("""\d{4}-\d{2}""").find(group.name)?.value?.replace("-", "/") ?: "Season") + " ⌄")
        val rows = listOf(
            listOf(R.id.lt_row1, R.id.lt_r1, R.id.lt_r1_logo, R.id.lt_r1_team, R.id.lt_r1_p, R.id.lt_r1_w, R.id.lt_r1_d, R.id.lt_r1_l, R.id.lt_r1_pts),
            listOf(R.id.lt_row2, R.id.lt_r2, R.id.lt_r2_logo, R.id.lt_r2_team, R.id.lt_r2_p, R.id.lt_r2_w, R.id.lt_r2_d, R.id.lt_r2_l, R.id.lt_r2_pts),
            listOf(R.id.lt_row3, R.id.lt_r3, R.id.lt_r3_logo, R.id.lt_r3_team, R.id.lt_r3_p, R.id.lt_r3_w, R.id.lt_r3_d, R.id.lt_r3_l, R.id.lt_r3_pts),
        )
        rows.forEachIndexed { i, ids ->
            val tr = group.rows.getOrNull(i)
            if (tr == null) { v.setViewVisibility(ids[0], View.GONE); return@forEachIndexed }
            val r = tr.row
            v.setTextViewText(ids[1], "${r.rank}")
            WidgetData.bitmap(r.team.crestUrl)?.let { v.setImageViewBitmap(ids[2], it) }
            v.setTextViewText(ids[3], r.team.shortName)
            v.setTextViewText(ids[4], "${r.played}")
            v.setTextViewText(ids[5], "${r.won}")
            v.setTextViewText(ids[6], "${r.drawn}")
            v.setTextViewText(ids[7], "${r.lost}")
            v.setTextViewText(ids[8], "${r.points}")
        }
        return v
    }
}

/** League (slug) the team most recently played a domestic league game in. */
private fun teamLeague(teamId: String): String? {
    val json = WidgetData.json("https://site.api.espn.com/apis/site/v2/sports/soccer/all/teams/$teamId/schedule") ?: return null
    val ev = json.optJSONArray("events") ?: return null
    return (0 until ev.length()).map { ev.getJSONObject(it).optJSONObject("league")?.optString("slug") }
        .firstOrNull { slug -> slug != null && FootballCatalog.leagueBySlug(slug)?.isCup == false }
}

private fun leagueTag(slug: String?): String =
    FootballCatalog.leagueBySlug(slug)?.name?.uppercase() ?: ""

/** Full-time card: last result of the favourite team with the scorers. */
class FullTimeWidget : AsyncWidget() {
    override fun build(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_full_time)
        val teamId = WidgetData.favoriteTeamId(ctx) ?: return emptyState(ctx, "Pick a favourite team in Mintline")
        val json = WidgetData.json("https://site.api.espn.com/apis/site/v2/sports/soccer/all/teams/$teamId/schedule")
            ?: return emptyState(ctx, "Couldn't load results")
        val events = json.optJSONArray("events") ?: return emptyState(ctx, "No results yet")
        val last = (0 until events.length()).map { events.getJSONObject(it) }
            .filter { it.optJSONArray("competitions")?.optJSONObject(0)?.optJSONObject("status")?.optJSONObject("type")?.optString("state") == "post" }
            .maxByOrNull { it.optString("date") } ?: return emptyState(ctx, "No results yet")
        val (home, away) = WidgetData.sides(last) ?: return emptyState(ctx, "No results yet")
        val slug = last.optJSONObject("league")?.optString("slug")
        v.setTextViewText(R.id.ft_league, leagueTag(slug).ifBlank { last.optJSONObject("league")?.optString("abbreviation").orEmpty() })
        v.setTextViewText(R.id.ft_date, WidgetData.parseIso(last.optString("date"))?.let { java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault()).format(it) } ?: "")
        v.setTextViewText(R.id.ft_score, "${home.score ?: "-"} - ${away.score ?: "-"}")
        WidgetData.bitmap(home.logo)?.let { v.setImageViewBitmap(R.id.ft_home_logo, it) }
        WidgetData.bitmap(away.logo)?.let { v.setImageViewBitmap(R.id.ft_away_logo, it) }
        WidgetData.bitmap(FootballCatalog.leagueBySlug(slug)?.logoUrl)?.let { v.setImageViewBitmap(R.id.ft_league_logo, it) }
        // scorers from the match summary
        val summary = WidgetData.json("https://site.api.espn.com/apis/site/v2/sports/soccer/${slug ?: "all"}/summary?event=${last.optString("id")}")
        val goals = summary?.optJSONArray("keyEvents")?.let { ke ->
            (0 until ke.length()).map { ke.getJSONObject(it) }.filter { it.optBoolean("scoringPlay") }.mapNotNull { e ->
                val name = e.optJSONArray("participants")?.optJSONObject(0)?.optJSONObject("athlete")?.optString("displayName") ?: return@mapNotNull null
                "${name.substringAfterLast(' ')} ${e.optJSONObject("clock")?.optString("displayValue").orEmpty()}"
            }
        }.orEmpty()
        v.setTextViewText(R.id.ft_scorers, goals.take(3).joinToString("\n"))
        return v
    }
}

/** Top-scorer card for the favourite team (ESPN has no soccer headshots, so initials). */
class PlayerWidget : AsyncWidget() {
    override fun build(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_player)
        val teamId = WidgetData.favoriteTeamId(ctx) ?: return emptyState(ctx, "Pick a favourite team in Mintline")
        val league = teamLeague(teamId) ?: return emptyState(ctx, "No league stats yet")
        val json = WidgetData.json("https://site.api.espn.com/apis/site/v2/sports/soccer/$league/statistics")
            ?: return emptyState(ctx, "Couldn't load player stats")
        val stats = json.optJSONArray("stats") ?: return emptyState(ctx, "No player stats yet")
        fun list(name: String) = (0 until stats.length()).map { stats.getJSONObject(it) }
            .firstOrNull { it.optString("name") == name }?.optJSONArray("leaders")
        val goals = list("goalsLeaders") ?: return emptyState(ctx, "No player stats yet")
        val top = (0 until goals.length()).map { goals.getJSONObject(it) }
            .firstOrNull { it.optJSONObject("athlete")?.optJSONObject("team")?.optString("id") == teamId }
            ?: return emptyState(ctx, "No scorer from your team yet")
        val athlete = top.getJSONObject("athlete")
        val name = athlete.optString("displayName")
        val matches = Regex("""Matches:\s*(\d+)""").find(top.optString("displayValue"))?.groupValues?.get(1) ?: "–"
        val assists = list("assistsLeaders")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optJSONObject("athlete")?.optString("id") == athlete.optString("id") }
                ?.optDouble("value")?.toInt()
        } ?: 0
        val team = athlete.optJSONObject("team")
        v.setTextViewText(R.id.pl_team, team?.optString("displayName").orEmpty())
        v.setTextViewText(R.id.pl_name, name.replace(" ", "\n", ignoreCase = false).let { if (name.count { it == ' ' } == 1) it else name })
        v.setTextViewText(R.id.pl_games, "Games: $matches")
        v.setTextViewText(R.id.pl_goals, "Goals: ${top.optDouble("value").toInt()}")
        v.setTextViewText(R.id.pl_assists, "Assists: $assists")
        v.setTextViewText(R.id.pl_initials, name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() })
        WidgetData.bitmap(team?.optJSONArray("logos")?.optJSONObject(0)?.optString("href"))?.let { v.setImageViewBitmap(R.id.pl_team_logo, it) }
        return v
    }
}

/** Season stats card: matches, goals, yellow and red cards for the favourite team. */
class TeamStatsWidget : AsyncWidget() {
    override fun build(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_team_stats)
        val teamId = WidgetData.favoriteTeamId(ctx) ?: return emptyState(ctx, "Pick a favourite team in Mintline")
        val league = teamLeague(teamId) ?: return emptyState(ctx, "No league stats yet")
        val season = WidgetData.json("https://site.api.espn.com/apis/site/v2/sports/soccer/$league/statistics")
            ?.optJSONObject("season")?.optInt("year") ?: return emptyState(ctx, "No season stats yet")
        val json = WidgetData.json("https://sports.core.api.espn.com/v2/sports/soccer/leagues/$league/seasons/$season/types/1/teams/$teamId/statistics")
            ?: return emptyState(ctx, "Couldn't load team stats")
        val values = mutableMapOf<String, String>()
        json.optJSONObject("splits")?.optJSONArray("categories")?.let { cats ->
            for (c in 0 until cats.length()) {
                val st = cats.getJSONObject(c).optJSONArray("stats") ?: continue
                for (i in 0 until st.length()) st.getJSONObject(i).let { values.putIfAbsent(it.optString("name"), it.optString("displayValue")) }
            }
        }
        val teamJson = WidgetData.json("https://site.api.espn.com/apis/site/v2/sports/soccer/all/teams/$teamId")?.optJSONObject("team")
        v.setTextViewText(R.id.ts_team, teamJson?.optString("displayName") ?: FootballCatalog.teamById(teamId)?.name.orEmpty())
        v.setTextViewText(R.id.ts_league, leagueTag(league))
        v.setTextViewText(R.id.ts_season, "$season/${(season + 1) % 100} ⌄")
        v.setTextViewText(R.id.ts_matches, values["appearances"] ?: values["gamesPlayed"] ?: "–")
        v.setTextViewText(R.id.ts_goals, values["totalGoals"] ?: "–")
        v.setTextViewText(R.id.ts_yellow, values["yellowCards"] ?: "–")
        v.setTextViewText(R.id.ts_red, values["redCards"] ?: "–")
        WidgetData.bitmap(teamJson?.optJSONArray("logos")?.optJSONObject(0)?.optString("href") ?: FootballCatalog.teamById(teamId)?.logoUrl)
            ?.let { v.setImageViewBitmap(R.id.ts_logo, it) }
        WidgetData.bitmap(FootballCatalog.leagueBySlug(league)?.logoUrl)?.let { v.setImageViewBitmap(R.id.ts_league_logo, it) }
        return v
    }
}

internal fun emptyState(ctx: Context, message: String): RemoteViews =
    RemoteViews(ctx.packageName, R.layout.widget_empty).apply { setTextViewText(R.id.we_text, message) }
