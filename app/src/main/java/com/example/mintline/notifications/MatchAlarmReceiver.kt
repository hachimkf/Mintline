package com.example.mintline.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MatchAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!LiveMatchService.alertsEnabled(context)) return // respects the Settings switch
        if (intent.action == MatchNotificationManager.ACTION_MATCH_REMINDER) {
            val matchId = intent.getStringExtra(MatchNotificationManager.EXTRA_MATCH_ID) ?: return
            val homeTeam = intent.getStringExtra(MatchNotificationManager.EXTRA_HOME_TEAM) ?: "Home"
            val awayTeam = intent.getStringExtra(MatchNotificationManager.EXTRA_AWAY_TEAM) ?: "Away"
            val leagueName = intent.getStringExtra(MatchNotificationManager.EXTRA_LEAGUE_NAME) ?: "Match"
            val kickoffTime = intent.getStringExtra(MatchNotificationManager.EXTRA_KICKOFF_TIME) ?: ""

            MatchNotificationManager.postKickoffNotification(
                context = context,
                matchId = matchId,
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                leagueName = leagueName,
                kickoffTime = kickoffTime
            )
        }
    }
}
