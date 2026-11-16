package com.example.mintline.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.mintline.MainActivity
import com.example.mintline.R
import com.example.mintline.widget.WidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Follows live matches: keeps an ongoing scorecard notification (crests,
 * score, minute, progress) that is visible on the lock screen, and posts a
 * heads-up alert on every goal. Polls ESPN every 30 s; stops by itself when
 * the followed games end or when match alerts are switched off in Settings.
 */
class LiveMatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val followed = linkedMapOf<String, String>() // eventId -> league slug ("all" if unknown)
    private val lastScores = mutableMapOf<String, Pair<Int, Int>>()
    private val crestCache = mutableMapOf<String, Bitmap?>()
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!alertsEnabled(this)) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            ACTION_FOLLOW -> {
                val id = intent.getStringExtra(EXTRA_MATCH_ID) ?: return START_NOT_STICKY
                followed[id] = intent.getStringExtra(EXTRA_LEAGUE) ?: "all"
            }
            ACTION_UNFOLLOW -> {
                intent.getStringExtra(EXTRA_MATCH_ID)?.let {
                    followed.remove(it)
                    NotificationManagerCompat.from(this).cancel(notifId(it))
                }
            }
        }
        if (followed.isEmpty()) { stopSelf(); return START_NOT_STICKY }

        createChannels(this)
        val first = followed.keys.first()
        val placeholder = baseBuilder(first).setContentTitle("Following live").setContentText("Getting the score…").build()
        ServiceCompat.startForeground(
            this, notifId(first), placeholder,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        if (loop?.isActive != true) loop = scope.launch { pollLoop() }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        while (scope.isActive && followed.isNotEmpty()) {
            if (!alertsEnabled(this)) break
            for ((id, league) in followed.toMap()) {
                val json = WidgetData.json("https://site.api.espn.com/apis/site/v2/sports/soccer/$league/summary?event=$id")
                    ?: WidgetData.json("https://site.api.espn.com/apis/site/v2/sports/soccer/all/summary?event=$id")
                    ?: continue
                update(id, json)
            }
            delay(30_000)
        }
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun update(id: String, json: JSONObject) {
        val comp = json.optJSONObject("header")?.optJSONArray("competitions")?.optJSONObject(0) ?: return
        val status = comp.optJSONObject("status")
        val type = status?.optJSONObject("type")
        val state = type?.optString("state") ?: "pre"
        val clock = status?.optString("displayClock").orEmpty()
        val detail = type?.optString("shortDetail").orEmpty()
        val (home, away) = WidgetData.sides(JSONObject().put("competitions", org.json.JSONArray().put(comp))) ?: return
        val hs = home.score?.toIntOrNull() ?: 0
        val aw = away.score?.toIntOrNull() ?: 0
        val league = json.optJSONObject("header")?.optJSONObject("league")?.optString("name").orEmpty()

        // Goal alert when the score moves
        lastScores[id]?.let { (ph, pa) ->
            if (hs > ph || aw > pa) postGoal(id, home.name, away.name, hs, aw, scorerFor(json, hs > ph), if (hs > ph) home.name else away.name, clock)
        }
        lastScores[id] = hs to aw

        val minute = Regex("""(\d+)""").find(clock)?.value?.toIntOrNull() ?: 0
        val finished = state == "post"
        val small = RemoteViews(packageName, R.layout.notification_live_match_small).apply {
            setTextViewText(R.id.ln_home, home.name)
            setTextViewText(R.id.ln_away, away.name)
            setTextViewText(R.id.ln_score, if (state == "pre") "vs" else "$hs – $aw")
            setTextViewText(R.id.ln_clock, when { finished -> "FT"; state == "in" -> clock; else -> "" })
            crest(home.logo)?.let { setImageViewBitmap(R.id.ln_home_logo, it) }
            crest(away.logo)?.let { setImageViewBitmap(R.id.ln_away_logo, it) }
        }
        val views = RemoteViews(packageName, R.layout.notification_live_match).apply {
            setTextViewText(R.id.ln_league, league)
            setTextViewText(R.id.ln_home, home.name)
            setTextViewText(R.id.ln_away, away.name)
            setTextViewText(R.id.ln_score, if (state == "pre") "vs" else "$hs – $aw")
            setTextViewText(R.id.ln_clock, when { finished -> "Full time"; state == "in" -> clock.ifBlank { detail }; else -> detail })
            setProgressBar(R.id.ln_progress, 90, minute.coerceIn(0, 90), false)
            setViewVisibility(R.id.ln_progress, if (state == "in") View.VISIBLE else View.GONE)
            crest(home.logo)?.let { setImageViewBitmap(R.id.ln_home_logo, it) }
            crest(away.logo)?.let { setImageViewBitmap(R.id.ln_away_logo, it) }
        }
        val n = baseBuilder(id)
            .setContentTitle("${home.name} $hs – $aw ${away.name}")
            .setContentText(if (finished) "Full time · $league" else "$clock · $league")
            .setCustomContentView(small)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(!finished)
            .setAutoCancel(finished)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(notifId(id), n) }

        if (finished) {
            followed.remove(id)
            if (followed.isEmpty()) {
                // keep the final score on screen after the service stops
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }
    }

    private fun scorerFor(json: JSONObject, homeScored: Boolean): String? {
        val events = json.optJSONArray("keyEvents") ?: return null
        val homeId = json.optJSONObject("header")?.optJSONArray("competitions")?.optJSONObject(0)
            ?.optJSONArray("competitors")?.let { c ->
                (0 until c.length()).map { c.getJSONObject(it) }.firstOrNull { it.optString("homeAway") == "home" }?.optJSONObject("team")?.optString("id")
            }
        for (i in events.length() - 1 downTo 0) {
            val e = events.getJSONObject(i)
            if (!e.optBoolean("scoringPlay")) continue
            val teamId = e.optJSONObject("team")?.optString("id")
            if ((teamId == homeId) == homeScored) {
                return e.optJSONArray("participants")?.optJSONObject(0)?.optJSONObject("athlete")?.optString("displayName")
            }
        }
        return null
    }

    private fun postGoal(id: String, home: String, away: String, hs: Int, aw: Int, scorer: String?, team: String, clock: String) {
        val n = NotificationCompat.Builder(this, GOAL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ball)
            .setColor(0xFF3B6A00.toInt())
            .setContentTitle("GOAL · $team")
            .setContentText("$home $hs – $aw $away" + (scorer?.let { " · $it" } ?: "") + if (clock.isNotBlank()) " $clock" else "")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openMatch(id))
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(notifId(id) + 7, n) }
    }

    private fun crest(url: String?): Bitmap? = url?.let { crestCache.getOrPut(it) { WidgetData.bitmap(it) } }

    private fun baseBuilder(id: String) = NotificationCompat.Builder(this, LIVE_CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_ball)
        .setColor(0xFF3B6A00.toInt())
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // full scorecard on the lock screen
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(openMatch(id))
        .addAction(0, "Stop following", PendingIntent.getService(
            this, notifId(id),
            Intent(this, LiveMatchService::class.java).setAction(ACTION_UNFOLLOW).putExtra(EXTRA_MATCH_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ))

    private fun openMatch(id: String) = PendingIntent.getActivity(
        this, notifId(id),
        Intent(this, MainActivity::class.java).putExtra("nav_match_id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val LIVE_CHANNEL = "mintline_live"
        const val GOAL_CHANNEL = "mintline_goals"
        private const val ACTION_FOLLOW = "follow"
        private const val ACTION_UNFOLLOW = "unfollow"
        private const val EXTRA_MATCH_ID = "match_id"
        private const val EXTRA_LEAGUE = "league"

        private fun notifId(id: String) = 40_000 + (id.hashCode() and 0xFFFF)

        fun alertsEnabled(ctx: Context) =
            ctx.getSharedPreferences("mintline_prefs", Context.MODE_PRIVATE).getBoolean("match_notifications", true)

        fun follow(ctx: Context, matchId: String, leagueId: String) {
            if (!alertsEnabled(ctx)) return
            val i = Intent(ctx, LiveMatchService::class.java).setAction(ACTION_FOLLOW)
                .putExtra(EXTRA_MATCH_ID, matchId).putExtra(EXTRA_LEAGUE, leagueId)
            runCatching { ContextCompat.startForegroundService(ctx, i) }
        }

        fun stopAll(ctx: Context) {
            ctx.stopService(Intent(ctx, LiveMatchService::class.java))
        }

        fun createChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(LIVE_CHANNEL, "Live scores", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing scorecard for matches you follow, shown on the lock screen"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            })
            nm.createNotificationChannel(NotificationChannel(GOAL_CHANNEL, "Goals", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Heads-up alert when a followed match has a goal"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
    }
}
