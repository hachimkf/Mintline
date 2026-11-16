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

    fun favoriteTeamId(ctx: Context): String? =
        ctx.getSharedPreferences("mintline_favorites", Context.MODE_PRIVATE)
            .getStringSet("fav_teams", emptySet())?.sorted()?.firstOrNull()

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
        listOf(NextMatchWidget::class.java, ResultFormWidget::class.java, LeagueTableWidget::class.java).forEach { cls ->
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

/** "Next Match" — the favourite team's next fixture (or live game). */
class NextMatchWidget : AsyncWidget() {
    override fun build(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_next_match)
        val teamId = WidgetData.favoriteTeamId(ctx) ?: return emptyState(ctx, "Pick a favourite team in Mintline")
        val json = WidgetData.json("https://site.api.espn.com/apis/site/v2/sports/soccer/all/teams/$teamId/schedule?fixture=true")
            ?: return emptyState(ctx, "Couldn't load fixtures")
        val events = json.optJSONArray("events") ?: return emptyState(ctx, "No upcoming games")
        val next = (0 until events.length()).map { events.getJSONObject(it) }
            .firstOrNull { it.optJSONArray("competitions")?.optJSONObject(0)?.optJSONObject("status")?.optJSONObject("type")?.optString("state") != "post" }
            ?: return emptyState(ctx, "No upcoming games")
        val (home, away) = WidgetData.sides(next) ?: return emptyState(ctx, "No upcoming games")
        val comp = next.optJSONArray("competitions")!!.getJSONObject(0)
        val state = comp.optJSONObject("status")?.optJSONObject("type")?.optString("state")
        val date = WidgetData.parseIso(next.optString("date"))
        v.setTextViewText(R.id.nm_title, if (state == "in") "Live now" else "Next Match")
        v.setTextViewText(R.id.nm_home, home.name)
        v.setTextViewText(R.id.nm_away, away.name)
        if (state == "in") {
            v.setTextViewText(R.id.nm_time, "${home.score ?: 0} – ${away.score ?: 0}")
            v.setTextViewText(R.id.nm_date, comp.optJSONObject("status")?.optString("displayClock") ?: "LIVE")
        } else {
            v.setTextViewText(R.id.nm_time, date?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "TBD")
            v.setTextViewText(R.id.nm_date, date?.let { SimpleDateFormat("d MMM", Locale.getDefault()).format(it) } ?: "")
        }
        v.setTextViewText(R.id.nm_venue, comp.optJSONObject("venue")?.optString("fullName").orEmpty())
        val league = FootballCatalog.leagueBySlug(next.optJSONObject("league")?.optString("slug"))
        v.setTextViewText(R.id.nm_league, league?.name ?: next.optJSONObject("league")?.optString("abbreviation").orEmpty())
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
        v.setTextViewText(R.id.rf_score, "${home.score ?: "-"} – ${away.score ?: "-"}")
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
        v.setTextViewText(R.id.lt_season, Regex("""\d{4}-\d{2}""").find(group.name)?.value ?: "")
        val rows = listOf(
            listOf(R.id.lt_row1, R.id.lt_r1, R.id.lt_r1_logo, R.id.lt_r1_team, R.id.lt_r1_p, R.id.lt_r1_w, R.id.lt_r1_d, R.id.lt_r1_l, R.id.lt_r1_pts),
            listOf(R.id.lt_row2, R.id.lt_r2, R.id.lt_r2_logo, R.id.lt_r2_team, R.id.lt_r2_p, R.id.lt_r2_w, R.id.lt_r2_d, R.id.lt_r2_l, R.id.lt_r2_pts),
            listOf(R.id.lt_row3, R.id.lt_r3, R.id.lt_r3_logo, R.id.lt_r3_team, R.id.lt_r3_p, R.id.lt_r3_w, R.id.lt_r3_d, R.id.lt_r3_l, R.id.lt_r3_pts),
            listOf(R.id.lt_row4, R.id.lt_r4, R.id.lt_r4_logo, R.id.lt_r4_team, R.id.lt_r4_p, R.id.lt_r4_w, R.id.lt_r4_d, R.id.lt_r4_l, R.id.lt_r4_pts),
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

internal fun emptyState(ctx: Context, message: String): RemoteViews =
    RemoteViews(ctx.packageName, R.layout.widget_empty).apply { setTextViewText(R.id.we_text, message) }
