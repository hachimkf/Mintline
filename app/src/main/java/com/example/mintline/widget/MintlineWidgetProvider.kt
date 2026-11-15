package com.example.mintline.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.mintline.MainActivity
import com.example.mintline.R
import com.example.mintline.data.DefaultFootballDataRepository
import com.example.mintline.data.provider.DemoDataProvider
import com.example.mintline.model.Match
import com.example.mintline.model.MatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AppWidget for Android home screen displaying a card of live & today's games.
 * Styled with Mintline's dark pitch and mint-neon accents.
 */
class MintlineWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, MintlineWidgetProvider::class.java)
            val allIds = appWidgetManager.getAppWidgetIds(thisWidget)

            // Trigger background refresh
            CoroutineScope(Dispatchers.IO).launch {
                val repository = DefaultFootballDataRepository(context)
                val matches = repository.getMatches().getOrNull() ?: DemoDataProvider.getDemoMatches()
                for (id in allIds) {
                    renderWidget(context, appWidgetManager, id, matches)
                }
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = DefaultFootballDataRepository(context)
            val matches = repository.getMatches().getOrNull() ?: DemoDataProvider.getDemoMatches()
            renderWidget(context, appWidgetManager, appWidgetId, matches)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.mintline.ACTION_REFRESH_WIDGET"

        fun updateAllWidgets(context: Context, matches: List<Match>) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, MintlineWidgetProvider::class.java)
            val allIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (id in allIds) {
                renderWidget(context, appWidgetManager, id, matches)
            }
        }

        private fun renderWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            matches: List<Match>
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_matches_card)

            // Intent to launch app when clicking on root
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingLaunch = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingLaunch)

            // Intent to refresh
            val refreshIntent = Intent(context, MintlineWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val pendingRefresh = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, pendingRefresh)

            // Sort: LIVE first, then SCHEDULED/UPCOMING, then FINISHED
            val sorted = matches.sortedWith(
                compareByDescending<Match> { it.isLive }
                    .thenBy { it.status == MatchStatus.SCHEDULED }
                    .thenBy { it.kickoffFormatted }
            )

            if (sorted.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                views.setViewVisibility(R.id.widget_matches_container, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_empty_text, View.GONE)
                views.setViewVisibility(R.id.widget_matches_container, View.VISIBLE)

                bindRow(views, sorted.getOrNull(0), R.id.match_1_container, R.id.match_1_league, R.id.match_1_teams, R.id.match_1_score)
                bindRow(views, sorted.getOrNull(1), R.id.match_2_container, R.id.match_2_league, R.id.match_2_teams, R.id.match_2_score)
                bindRow(views, sorted.getOrNull(2), R.id.match_3_container, R.id.match_3_league, R.id.match_3_teams, R.id.match_3_score)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun bindRow(
            views: RemoteViews,
            match: Match?,
            containerId: Int,
            leagueId: Int,
            teamsId: Int,
            scoreId: Int
        ) {
            if (match == null) {
                views.setViewVisibility(containerId, View.GONE)
                return
            }

            views.setViewVisibility(containerId, View.VISIBLE)
            views.setTextViewText(leagueId, match.leagueName)

            val homeName = match.homeTeam.shortName.ifBlank { match.homeTeam.name }
            val awayName = match.awayTeam.shortName.ifBlank { match.awayTeam.name }
            views.setTextViewText(teamsId, "$homeName vs $awayName")

            val scoreText = when {
                match.isLive -> {
                    val min = match.minute?.let { " $it'" } ?: ""
                    "● ${match.homeScore ?: 0} - ${match.awayScore ?: 0}$min"
                }
                match.status.isFinished -> {
                    "FT ${match.homeScore ?: 0} - ${match.awayScore ?: 0}"
                }
                else -> {
                    match.kickoffFormatted
                }
            }
            views.setTextViewText(scoreId, scoreText)
        }
    }
}
