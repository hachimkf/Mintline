package com.example.mintline.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mintline.MainActivity
import com.example.mintline.R
import com.example.mintline.data.timezone.TimeZoneManager
import com.example.mintline.model.Match
import com.example.mintline.model.MatchIncident

/**
 * Manages match alerts, kickoff reminders, and score notifications.
 */
object MatchNotificationManager {

    const val CHANNEL_ID = "mintline_matches"
    const val CHANNEL_NAME = "Match Alerts & Kickoffs"
    const val ACTION_MATCH_REMINDER = "com.example.mintline.ACTION_MATCH_REMINDER"

    const val EXTRA_MATCH_ID = "extra_match_id"
    const val EXTRA_HOME_TEAM = "extra_home_team"
    const val EXTRA_AWAY_TEAM = "extra_away_team"
    const val EXTRA_LEAGUE_NAME = "extra_league_name"
    const val EXTRA_KICKOFF_TIME = "extra_kickoff_time"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for match kickoffs and favorite team scores"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedules a kickoff reminder alarm for an upcoming match (e.g. 15 minutes before kickoff).
     */
    fun scheduleMatchReminder(
        context: Context,
        match: Match,
        minutesBefore: Int = 15
    ) {
        val kickoffMillis = TimeZoneManager.getKickoffEpochMillis(match.dateIso) ?: return
        val reminderMillis = kickoffMillis - (minutesBefore * 60 * 1000L)
        val now = System.currentTimeMillis()

        // Don't schedule reminders in the past
        if (reminderMillis <= now) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MatchAlarmReceiver::class.java).apply {
            action = ACTION_MATCH_REMINDER
            putExtra(EXTRA_MATCH_ID, match.id)
            putExtra(EXTRA_HOME_TEAM, match.homeTeam.name)
            putExtra(EXTRA_AWAY_TEAM, match.awayTeam.name)
            putExtra(EXTRA_LEAGUE_NAME, match.leagueName)
            putExtra(EXTRA_KICKOFF_TIME, match.kickoffFormatted)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            match.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, reminderMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Fallback for restricted exact alarm permissions
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminderMillis, pendingIntent)
        }
    }

    /**
     * Cancels a scheduled kickoff reminder for a match.
     */
    fun cancelMatchReminder(context: Context, matchId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MatchAlarmReceiver::class.java).apply {
            action = ACTION_MATCH_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            matchId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Posts a notification for kickoff or upcoming match.
     */
    fun postKickoffNotification(
        context: Context,
        matchId: String,
        homeTeam: String,
        awayTeam: String,
        leagueName: String,
        kickoffTime: String
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("nav_match_id", matchId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            matchId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚽ $homeTeam vs $awayTeam")
            .setContentText("Kickoff at $kickoffTime • $leagueName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(matchId.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Notification permission denied on Android 13+
        }
    }

    /**
     * Posts a live goal event notification.
     */
    fun postGoalNotification(
        context: Context,
        match: Match,
        incident: MatchIncident
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("nav_match_id", match.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            match.id.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val homeScore = match.homeScore ?: 0
        val awayScore = match.awayScore ?: 0
        val scorer = incident.primaryPlayer

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚽ GOAL! ${match.homeTeam.name} $homeScore - $awayScore ${match.awayTeam.name}")
            .setContentText("$scorer (${incident.minute}') • ${match.leagueName}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(match.id.hashCode() + 1, builder.build())
        } catch (e: SecurityException) {
            // Notification permission denied
        }
    }
}
