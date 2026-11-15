package com.example.mintline.data.timezone

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Manages match time conversion and timezone preferences.
 * Inspired by Kickoff26's TimeZoneRepository, MatchTime, and TimeZoneFormat.
 * All kickoff times and matchday dates are localized to the user's selected
 * timezone (defaulting to system default).
 */
class TimeZoneManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mintline_timezone_prefs", Context.MODE_PRIVATE)

    private val _timeZone = MutableStateFlow(loadInitialZone())
    val timeZone: StateFlow<ZoneId> = _timeZone.asStateFlow()

    private val _isCustomZone = MutableStateFlow(prefs.getBoolean(KEY_IS_CUSTOM, false))
    val isCustomZone: StateFlow<Boolean> = _isCustomZone.asStateFlow()

    private fun loadInitialZone(): ZoneId {
        val isCustom = prefs.getBoolean(KEY_IS_CUSTOM, false)
        if (isCustom) {
            val savedId = prefs.getString(KEY_ZONE_ID, null)
            if (!savedId.isNullOrBlank()) {
                return runCatching { ZoneId.of(savedId) }.getOrElse { ZoneId.systemDefault() }
            }
        }
        return ZoneId.systemDefault()
    }

    fun setTimeZone(zoneId: ZoneId) {
        _timeZone.value = zoneId
        _isCustomZone.value = true
        prefs.edit()
            .putBoolean(KEY_IS_CUSTOM, true)
            .putString(KEY_ZONE_ID, zoneId.id)
            .apply()
    }

    fun resetToDeviceDefault() {
        val defaultZone = ZoneId.systemDefault()
        _timeZone.value = defaultZone
        _isCustomZone.value = false
        prefs.edit()
            .putBoolean(KEY_IS_CUSTOM, false)
            .putString(KEY_ZONE_ID, defaultZone.id)
            .apply()
    }

    companion object {
        private const val KEY_IS_CUSTOM = "is_custom_zone"
        private const val KEY_ZONE_ID = "selected_zone_id"

        val POPULAR_ZONES: List<ZoneId> by lazy {
            listOf(
                ZoneId.of("UTC"),
                ZoneId.of("Europe/London"),
                ZoneId.of("Europe/Paris"),
                ZoneId.of("Europe/Madrid"),
                ZoneId.of("Europe/Berlin"),
                ZoneId.of("Europe/Rome"),
                ZoneId.of("America/New_York"),
                ZoneId.of("America/Chicago"),
                ZoneId.of("America/Los_Angeles"),
                ZoneId.of("America/Mexico_City"),
                ZoneId.of("America/Sao_Paulo"),
                ZoneId.of("America/Buenos_Aires"),
                ZoneId.of("Asia/Tehran"),
                ZoneId.of("Asia/Riyadh"),
                ZoneId.of("Asia/Dubai"),
                ZoneId.of("Asia/Tokyo"),
                ZoneId.of("Australia/Sydney")
            )
        }

        /** Formats this zone's current UTC offset as "GMT{±H}" or "GMT{±H:mm}". */
        fun ZoneId.offsetLabel(instant: Instant = Instant.now()): String {
            val offset = rules.getOffset(instant)
            val totalMinutes = offset.totalSeconds / 60
            val sign = if (totalMinutes < 0) "-" else "+"
            val absMinutes = abs(totalMinutes)
            val hours = absMinutes / 60
            val minutes = absMinutes % 60
            return if (minutes == 0) "GMT$sign$hours" else "GMT$sign$hours:${minutes.toString().padStart(2, '0')}"
        }

        /** This zone id's last segment with '_' replaced by spaces, e.g. "New York". */
        fun ZoneId.cityLabel(): String {
            val name = id.substringAfterLast('/').replace('_', ' ')
            return if (name == "Z" || name == "UTC") "UTC" else name
        }

        /** Formats this zone as "GMT{±H[:mm]} ({City})", e.g. "GMT+3:30 (Tehran)". */
        fun ZoneId.toDisplayLabel(instant: Instant = Instant.now()): String =
            "${offsetLabel(instant)} (${cityLabel()})"

        /**
         * Parses an ISO 8601 UTC timestamp (e.g. "2026-11-15T19:45Z" or "2026-11-15T19:45:00Z")
         * and converts it to a [ZonedDateTime] in [targetZone].
         */
        fun parseIsoToZoned(dateIso: String, targetZone: ZoneId): ZonedDateTime? {
            if (dateIso.isBlank()) return null
            return runCatching {
                // ISO_DATE_TIME handles both "2026-11-15T19:45Z" (no seconds) and "2026-11-15T19:45:00Z"
                ZonedDateTime.parse(dateIso, DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(targetZone)
            }.recoverCatching {
                // Missing offset/timezone -> interpret as UTC, then shift to targetZone
                LocalDateTime.parse(dateIso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(targetZone)
            }.recoverCatching {
                // Try lenient instant parsing
                val cleaned = if (!dateIso.endsWith("Z") && !dateIso.contains("+") && !dateIso.contains("-")) {
                    dateIso + "Z"
                } else {
                    dateIso
                }
                Instant.parse(cleaned).atZone(targetZone)
            }.getOrNull()
        }

        /** Formats kickoff time as "HH:mm" zero-padded 24h in [targetZone]. */
        fun formatKickoffTime(dateIso: String, targetZone: ZoneId): String {
            val zdt = parseIsoToZoned(dateIso, targetZone)
            if (zdt != null) {
                return "${zdt.hour.toString().padStart(2, '0')}:${zdt.minute.toString().padStart(2, '0')}"
            }
            // Check if string itself already contains a time pattern like "19:45"
            val timeMatch = Regex("""\b(\d{1,2}:\d{2})\b""").find(dateIso)
            return timeMatch?.value ?: "TBD"
        }

        /** Formats match date as "yyyyMMdd" in [targetZone] for date strip filtering. */
        fun formatMatchDateIso(dateIso: String, targetZone: ZoneId): String {
            val zdt = parseIsoToZoned(dateIso, targetZone) ?: return dateIso.take(10).replace("-", "")
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)
            return zdt.format(formatter)
        }

        /** Formats a relative/friendly date header label (e.g. "Today", "Tomorrow", "Sun, Nov 15"). */
        fun formatRelativeDateLabel(dateIso: String, targetZone: ZoneId): String {
            val zdt = parseIsoToZoned(dateIso, targetZone) ?: return ""
            val matchDate = zdt.toLocalDate()
            val today = LocalDate.now(targetZone)

            return when {
                matchDate == today -> "Today"
                matchDate == today.plusDays(1) -> "Tomorrow"
                matchDate == today.minusDays(1) -> "Yesterday"
                else -> DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH).format(matchDate)
            }
        }

        /** Returns epoch milliseconds for scheduling alarms and notifications. */
        fun getKickoffEpochMillis(dateIso: String): Long? {
            return parseIsoToZoned(dateIso, ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        }
    }
}
