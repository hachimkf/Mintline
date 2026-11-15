package com.example.mintline.model

/**
 * League / tournament table standings row.
 * Modeled after Kickoff26's StandingRowDto and GroupsRepositoryImpl.
 */
data class StandingRow(
    val rank: Int,
    val team: Team,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int,
    val points: Int
)

/**
 * Computes official standings tables from a list of match results.
 * Identical rules to Kickoff26:
 * - 3 pts for a win, 1 pt for a draw, 0 for loss
 * - Tiebreakers: Points > Goal Difference > Goals For > Team Name
 */
object StandingsCalculator {

    private class TeamTotals(
        var played: Int = 0,
        var won: Int = 0,
        var drawn: Int = 0,
        var lost: Int = 0,
        var goalsFor: Int = 0,
        var goalsAgainst: Int = 0
    )

    fun compute(matches: List<Match>): List<StandingRow> {
        val teamsMap = mutableMapOf<String, Team>()
        val totalsMap = mutableMapOf<String, TeamTotals>()

        for (m in matches) {
            teamsMap[m.homeTeam.id] = m.homeTeam
            teamsMap[m.awayTeam.id] = m.awayTeam
            totalsMap.getOrPut(m.homeTeam.id) { TeamTotals() }
            totalsMap.getOrPut(m.awayTeam.id) { TeamTotals() }
        }

        // Only finished matches count toward standings
        val finished = matches.filter { it.status == MatchStatus.FULL_TIME || it.status.isFinished }

        for (m in finished) {
            val hScore = m.homeScore ?: continue
            val aScore = m.awayScore ?: continue

            val homeTotals = totalsMap.getValue(m.homeTeam.id)
            val awayTotals = totalsMap.getValue(m.awayTeam.id)

            homeTotals.played += 1
            awayTotals.played += 1
            homeTotals.goalsFor += hScore
            homeTotals.goalsAgainst += aScore
            awayTotals.goalsFor += aScore
            awayTotals.goalsAgainst += hScore

            when {
                hScore > aScore -> {
                    homeTotals.won += 1
                    awayTotals.lost += 1
                }
                hScore < aScore -> {
                    awayTotals.won += 1
                    homeTotals.lost += 1
                }
                else -> {
                    homeTotals.drawn += 1
                    awayTotals.drawn += 1
                }
            }
        }

        return totalsMap.keys.map { teamId ->
            val totals = totalsMap.getValue(teamId)
            val team = teamsMap.getValue(teamId)
            val gd = totals.goalsFor - totals.goalsAgainst
            val pts = totals.won * 3 + totals.drawn

            StandingRow(
                rank = 0,
                team = team,
                played = totals.played,
                won = totals.won,
                drawn = totals.drawn,
                lost = totals.lost,
                goalsFor = totals.goalsFor,
                goalsAgainst = totals.goalsAgainst,
                goalDifference = gd,
                points = pts
            )
        }.sortedWith(
            compareByDescending<StandingRow> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
                .thenBy { it.team.name }
        ).mapIndexed { idx, row ->
            row.copy(rank = idx + 1)
        }
    }
}
