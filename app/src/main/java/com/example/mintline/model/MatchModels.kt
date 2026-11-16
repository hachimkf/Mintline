package com.example.mintline.model

enum class MatchStatus {
    SCHEDULED,
    LIVE,
    HALFTIME,
    FULL_TIME,
    POSTPONED,
    CANCELLED;

    val isOngoing: Boolean
        get() = this == LIVE || this == HALFTIME

    val isFinished: Boolean
        get() = this == FULL_TIME
}

data class Team(
    val id: String,
    val name: String,
    val shortName: String,
    val abbreviation: String,
    val crestUrl: String?,
    val colorHex: String? = null,
    val form: String? = null
)

data class Match(
    val id: String,
    val leagueId: String,
    val leagueName: String,
    val leagueLogo: String? = null,
    val homeTeam: Team,
    val awayTeam: Team,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: MatchStatus,
    val statusText: String,
    val kickoffFormatted: String,
    val dateIso: String,
    val venue: String? = null,
    val isLive: Boolean = status.isOngoing,
    val minute: Int? = null,
    val aggregateScore: String? = null,
    val isFavorite: Boolean = false
)

enum class IncidentType {
    GOAL,
    YELLOW_CARD,
    RED_CARD,
    SUBSTITUTION,
    PENALTY_SCORED,
    PENALTY_MISSED,
    VAR_DECISION
}

data class MatchIncident(
    val id: String,
    val minute: String,
    val type: IncidentType,
    val teamId: String,
    val isHomeTeam: Boolean,
    val primaryPlayer: String,
    val secondaryPlayer: String? = null,
    val detail: String? = null
)

data class MatchStatistic(
    val label: String,
    val homeValue: String,
    val awayValue: String,
    val homeRatio: Float // 0.0f .. 1.0f (home fraction of total)
)

data class LineupPlayer(
    val id: String,
    val name: String,
    val jersey: String,
    val position: String,
    val isCaptain: Boolean = false,
    val isSubstitute: Boolean = false
)

data class TeamLineup(
    val teamName: String,
    val formation: String?,
    val starters: List<LineupPlayer>,
    val bench: List<LineupPlayer>,
    val coach: String? = null
)

data class MatchDetail(
    val match: Match,
    val incidents: List<MatchIncident>,
    val statistics: List<MatchStatistic>,
    val homeLineup: TeamLineup?,
    val awayLineup: TeamLineup?,
    val commentary: List<String> = emptyList(),
    val referee: String? = null,
    val insights: MatchInsights? = null
)

data class Competition(
    val id: String,
    val name: String,
    val code: String,
    val logoUrl: String?,
    val country: String,
    val matchCount: Int = 0,
    val isFavorite: Boolean = false
)

enum class FavoriteType {
    MATCH,
    TEAM,
    LEAGUE
}

data class DateItem(
    val dateString: String, // YYYYMMDD
    val dayOfWeek: String,  // Wed
    val dayOfMonth: String, // 15
    val isToday: Boolean,
    val isSelected: Boolean
)
