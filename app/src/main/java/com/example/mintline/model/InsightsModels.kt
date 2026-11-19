package com.example.mintline.model

/** One table (a league, or a group/phase of a cup). */
data class StandingGroup(
    val name: String,
    val rows: List<TableRow>,
)

/** A standings row plus ESPN's qualification zone (e.g. Champions League, relegation). */
data class TableRow(
    val row: StandingRow,
    val zoneColor: String? = null,
    val zoneLabel: String? = null,
)

data class PlayerLeader(
    val name: String,
    val teamName: String,
    val teamLogo: String?,
    val value: Int,
    val matches: Int?,
)

data class LeagueLeaders(
    val goals: List<PlayerLeader> = emptyList(),
    val assists: List<PlayerLeader> = emptyList(),
)

/** A recent result from a team's point of view. */
data class FormGame(
    val result: String,          // W / D / L
    val score: String,
    val opponent: String,
    val opponentLogo: String?,
    val atHome: Boolean,
    val dateIso: String,
)

data class H2HGame(
    val dateIso: String,
    val homeName: String,
    val homeLogo: String?,
    val homeScore: String,
    val awayName: String,
    val awayLogo: String?,
    val awayScore: String,
)

data class TopPerformer(
    val teamName: String,
    val isHome: Boolean,
    val category: String,
    val player: String,
    val value: String,
)

/** Extra context ESPN publishes with a match summary. Everything may be empty. */
data class MatchInsights(
    val homeForm: List<FormGame> = emptyList(),
    val awayForm: List<FormGame> = emptyList(),
    val headToHead: List<H2HGame> = emptyList(),
    val table: StandingGroup? = null,
    val performers: List<TopPerformer> = emptyList(),
) {
    val isEmpty: Boolean
        get() = homeForm.isEmpty() && awayForm.isEmpty() && headToHead.isEmpty() && table == null && performers.isEmpty()
}

/** Knockout draw: rounds from first knockout round to the final. */
data class Bracket(val title: String, val rounds: List<BracketRound>)

data class BracketRound(val name: String, val ties: List<BracketTie>)

data class BracketTie(
    val homeName: String,
    val homeLogo: String?,
    val awayName: String,
    val awayLogo: String?,
    val homeScore: String?,
    val awayScore: String?,
    val homeAdvanced: Boolean?,   // null = not decided yet
    val note: String?,            // e.g. "After ET", "Pens", date
)
