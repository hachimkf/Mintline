package com.example.mintline.data.provider

import com.example.mintline.model.Competition
import com.example.mintline.model.IncidentType
import com.example.mintline.model.LineupPlayer
import com.example.mintline.model.Match
import com.example.mintline.model.MatchDetail
import com.example.mintline.model.MatchIncident
import com.example.mintline.model.MatchStatistic
import com.example.mintline.model.MatchStatus
import com.example.mintline.model.Team
import com.example.mintline.model.TeamLineup

object DemoDataProvider {

    val competitions = listOf(
        Competition(
            id = "uefa.champions",
            name = "Champions League",
            code = "UCL",
            logoUrl = "https://a.espncdn.com/i/leaguelogos/soccer/500/2.png",
            country = "Europe",
            matchCount = 4
        ),
        Competition(
            id = "eng.1",
            name = "Premier League",
            code = "EPL",
            logoUrl = "https://a.espncdn.com/i/leaguelogos/soccer/500/23.png",
            country = "England",
            matchCount = 6
        ),
        Competition(
            id = "esp.1",
            name = "La Liga",
            code = "ESP",
            logoUrl = "https://a.espncdn.com/i/leaguelogos/soccer/500/15.png",
            country = "Spain",
            matchCount = 4
        ),
        Competition(
            id = "ita.1",
            name = "Serie A",
            code = "ITA",
            logoUrl = "https://a.espncdn.com/i/leaguelogos/soccer/500/12.png",
            country = "Italy",
            matchCount = 3
        ),
        Competition(
            id = "ger.1",
            name = "Bundesliga",
            code = "GER",
            logoUrl = "https://a.espncdn.com/i/leaguelogos/soccer/500/10.png",
            country = "Germany",
            matchCount = 3
        ),
        Competition(
            id = "usa.1",
            name = "MLS",
            code = "MLS",
            logoUrl = "https://a.espncdn.com/i/leaguelogos/soccer/500/19.png",
            country = "USA",
            matchCount = 2
        )
    )

    fun getDemoMatches(): List<Match> {
        return listOf(
            // Top Live Event (Featured in User Mockup)
            Match(
                id = "m_ucl_1",
                leagueId = "uefa.champions",
                leagueName = "Champions League",
                leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500/2.png",
                homeTeam = Team(
                    id = "psg",
                    name = "Paris Saint-Germain",
                    shortName = "PSG",
                    abbreviation = "PSG",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/160.png",
                    form = "WWWDW"
                ),
                awayTeam = Team(
                    id = "rma",
                    name = "Real Madrid",
                    shortName = "Real Madrid",
                    abbreviation = "RMA",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/86.png",
                    form = "WWLWW"
                ),
                homeScore = 3,
                awayScore = 2,
                status = MatchStatus.LIVE,
                statusText = "2ND HALF 58'",
                kickoffFormatted = "20:00",
                dateIso = "2026-11-15T20:00Z",
                venue = "Parc des Princes, Paris",
                isLive = true,
                minute = 58
            ),

            // Live Match 2 (From User Mockup)
            Match(
                id = "m_epl_1",
                leagueId = "eng.1",
                leagueName = "Premier League",
                leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500/23.png",
                homeTeam = Team(
                    id = "che",
                    name = "Chelsea",
                    shortName = "Chelsea",
                    abbreviation = "CHE",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/363.png",
                    form = "DWWWL"
                ),
                awayTeam = Team(
                    id = "mci",
                    name = "Manchester City",
                    shortName = "Man City",
                    abbreviation = "MCI",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/382.png",
                    form = "WWWWW"
                ),
                homeScore = 2,
                awayScore = 0,
                status = MatchStatus.LIVE,
                statusText = "1ST HALF 35'",
                kickoffFormatted = "17:30",
                dateIso = "2026-11-15T17:30Z",
                venue = "Stamford Bridge, London",
                isLive = true,
                minute = 35
            ),

            // Live Match 3 (From User Mockup)
            Match(
                id = "m_ucl_2",
                leagueId = "uefa.champions",
                leagueName = "Champions League",
                leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500/2.png",
                homeTeam = Team(
                    id = "mun",
                    name = "Manchester United",
                    shortName = "Man United",
                    abbreviation = "MUN",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/360.png",
                    form = "WLDWL"
                ),
                awayTeam = Team(
                    id = "bar",
                    name = "FC Barcelona",
                    shortName = "Barcelona",
                    abbreviation = "FCB",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/83.png",
                    form = "WWWWW"
                ),
                homeScore = 1,
                awayScore = 2,
                status = MatchStatus.LIVE,
                statusText = "1ST HALF 34'",
                kickoffFormatted = "20:00",
                dateIso = "2026-11-15T20:00Z",
                venue = "Old Trafford, Manchester",
                isLive = true,
                minute = 34
            ),

            // Live Match 4
            Match(
                id = "m_epl_2",
                leagueId = "eng.1",
                leagueName = "Premier League",
                leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500/23.png",
                homeTeam = Team(
                    id = "ars",
                    name = "Arsenal",
                    shortName = "Arsenal",
                    abbreviation = "ARS",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png",
                    form = "WWWDW"
                ),
                awayTeam = Team(
                    id = "liv",
                    name = "Liverpool",
                    shortName = "Liverpool",
                    abbreviation = "LIV",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/364.png",
                    form = "WWWWL"
                ),
                homeScore = 1,
                awayScore = 1,
                status = MatchStatus.LIVE,
                statusText = "2ND HALF 74'",
                kickoffFormatted = "16:30",
                dateIso = "2026-11-15T16:30Z",
                venue = "Emirates Stadium, London",
                isLive = true,
                minute = 74
            ),

            // Scheduled Match 1
            Match(
                id = "m_esp_1",
                leagueId = "esp.1",
                leagueName = "La Liga",
                leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500/15.png",
                homeTeam = Team(
                    id = "atm",
                    name = "Atlético Madrid",
                    shortName = "Atlético",
                    abbreviation = "ATM",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/1068.png",
                    form = "DWWDW"
                ),
                awayTeam = Team(
                    id = "sev",
                    name = "Sevilla FC",
                    shortName = "Sevilla",
                    abbreviation = "SEV",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/243.png",
                    form = "LDWLL"
                ),
                homeScore = null,
                awayScore = null,
                status = MatchStatus.SCHEDULED,
                statusText = "21:00",
                kickoffFormatted = "21:00",
                dateIso = "2026-11-15T21:00Z",
                venue = "Cívitas Metropolitano, Madrid",
                isLive = false
            ),

            // Scheduled Match 2
            Match(
                id = "m_ita_1",
                leagueId = "ita.1",
                leagueName = "Serie A",
                leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500/12.png",
                homeTeam = Team(
                    id = "int",
                    name = "Inter Milan",
                    shortName = "Inter",
                    abbreviation = "INT",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/110.png",
                    form = "WWWWW"
                ),
                awayTeam = Team(
                    id = "juv",
                    name = "Juventus",
                    shortName = "Juventus",
                    abbreviation = "JUV",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/111.png",
                    form = "DDWWD"
                ),
                homeScore = null,
                awayScore = null,
                status = MatchStatus.SCHEDULED,
                statusText = "20:45",
                kickoffFormatted = "20:45",
                dateIso = "2026-11-15T20:45Z",
                venue = "San Siro, Milan",
                isLive = false
            ),

            // Scheduled Match 3
            Match(
                id = "m_ger_1",
                leagueId = "ger.1",
                leagueName = "Bundesliga",
                leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500/10.png",
                homeTeam = Team(
                    id = "bay",
                    name = "Bayern Munich",
                    shortName = "Bayern",
                    abbreviation = "BAY",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/132.png",
                    form = "WWWDW"
                ),
                awayTeam = Team(
                    id = "bvb",
                    name = "Borussia Dortmund",
                    shortName = "Dortmund",
                    abbreviation = "BVB",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/124.png",
                    form = "LWWDL"
                ),
                homeScore = null,
                awayScore = null,
                status = MatchStatus.SCHEDULED,
                statusText = "18:30",
                kickoffFormatted = "18:30",
                dateIso = "2026-11-15T18:30Z",
                venue = "Allianz Arena, Munich",
                isLive = false
            ),

            // Finished Match 1
            Match(
                id = "m_epl_3",
                leagueId = "eng.1",
                leagueName = "Premier League",
                leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500/23.png",
                homeTeam = Team(
                    id = "tot",
                    name = "Tottenham Hotspur",
                    shortName = "Tottenham",
                    abbreviation = "TOT",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/367.png",
                    form = "WLWWL"
                ),
                awayTeam = Team(
                    id = "ast",
                    name = "Aston Villa",
                    shortName = "Aston Villa",
                    abbreviation = "AVL",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/362.png",
                    form = "DWLWW"
                ),
                homeScore = 3,
                awayScore = 1,
                status = MatchStatus.FULL_TIME,
                statusText = "FT",
                kickoffFormatted = "14:00",
                dateIso = "2026-11-15T14:00Z",
                venue = "Tottenham Hotspur Stadium, London",
                isLive = false
            ),

            // Finished Match 2
            Match(
                id = "m_ita_2",
                leagueId = "ita.1",
                leagueName = "Serie A",
                leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500/12.png",
                homeTeam = Team(
                    id = "acm",
                    name = "AC Milan",
                    shortName = "Milan",
                    abbreviation = "ACM",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/103.png",
                    form = "WWDWL"
                ),
                awayTeam = Team(
                    id = "nap",
                    name = "Napoli",
                    shortName = "Napoli",
                    abbreviation = "NAP",
                    crestUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/114.png",
                    form = "WWWWL"
                ),
                homeScore = 2,
                awayScore = 0,
                status = MatchStatus.FULL_TIME,
                statusText = "FT",
                kickoffFormatted = "15:00",
                dateIso = "2026-11-15T15:00Z",
                venue = "San Siro, Milan",
                isLive = false
            )
        )
    }

    fun getDemoMatchDetail(matchId: String): MatchDetail {
        val allMatches = getDemoMatches()
        val match = allMatches.find { it.id == matchId } ?: allMatches.first()

        val incidents = listOf(
            MatchIncident(
                id = "inc_1",
                minute = "12'",
                type = IncidentType.GOAL,
                teamId = match.homeTeam.id,
                isHomeTeam = true,
                primaryPlayer = "Ousmane Dembélé",
                secondaryPlayer = "Vitinha",
                detail = "Left-footed strike into top corner"
            ),
            MatchIncident(
                id = "inc_2",
                minute = "27'",
                type = IncidentType.GOAL,
                teamId = match.awayTeam.id,
                isHomeTeam = false,
                primaryPlayer = "Vinícius Júnior",
                secondaryPlayer = "Jude Bellingham",
                detail = "Counter-attack clinical finish"
            ),
            MatchIncident(
                id = "inc_3",
                minute = "34'",
                type = IncidentType.YELLOW_CARD,
                teamId = match.homeTeam.id,
                isHomeTeam = true,
                primaryPlayer = "Achraf Hakimi",
                detail = "Tactical foul"
            ),
            MatchIncident(
                id = "inc_4",
                minute = "42'",
                type = IncidentType.GOAL,
                teamId = match.homeTeam.id,
                isHomeTeam = true,
                primaryPlayer = "Bradley Barcola",
                secondaryPlayer = "João Neves",
                detail = "Header from close range"
            ),
            MatchIncident(
                id = "inc_5",
                minute = "51'",
                type = IncidentType.GOAL,
                teamId = match.awayTeam.id,
                isHomeTeam = false,
                primaryPlayer = "Kylian Mbappé",
                secondaryPlayer = "Federico Valverde",
                detail = "Penalty converted"
            ),
            MatchIncident(
                id = "inc_6",
                minute = "56'",
                type = IncidentType.GOAL,
                teamId = match.homeTeam.id,
                isHomeTeam = true,
                primaryPlayer = "Kang-in Lee",
                secondaryPlayer = "Nuno Mendes",
                detail = "Long range curl past Courtois"
            )
        )

        val statistics = listOf(
            MatchStatistic(label = "Possession", homeValue = "56%", awayValue = "44%", homeRatio = 0.56f),
            MatchStatistic(label = "Total Shots", homeValue = "14", awayValue = "11", homeRatio = 0.56f),
            MatchStatistic(label = "Shots on Target", homeValue = "6", awayValue = "4", homeRatio = 0.60f),
            MatchStatistic(label = "Corner Kicks", homeValue = "7", awayValue = "4", homeRatio = 0.63f),
            MatchStatistic(label = "Fouls", homeValue = "10", awayValue = "12", homeRatio = 0.45f),
            MatchStatistic(label = "Yellow Cards", homeValue = "1", awayValue = "2", homeRatio = 0.33f),
            MatchStatistic(label = "Offsides", homeValue = "2", awayValue = "1", homeRatio = 0.66f),
            MatchStatistic(label = "Goalkeeper Saves", homeValue = "2", awayValue = "3", homeRatio = 0.40f)
        )

        val homeLineup = TeamLineup(
            teamName = match.homeTeam.name,
            formation = "4-3-3",
            starters = listOf(
                LineupPlayer("p1", "Gianluigi Donnarumma", "1", "GK"),
                LineupPlayer("p2", "Achraf Hakimi", "2", "RB"),
                LineupPlayer("p3", "Marquinhos", "5", "CB", isCaptain = true),
                LineupPlayer("p4", "Willian Pacho", "51", "CB"),
                LineupPlayer("p5", "Nuno Mendes", "25", "LB"),
                LineupPlayer("p6", "Vitinha", "17", "CM"),
                LineupPlayer("p7", "Warren Zaïre-Emery", "33", "CM"),
                LineupPlayer("p8", "João Neves", "87", "CM"),
                LineupPlayer("p9", "Ousmane Dembélé", "10", "RW"),
                LineupPlayer("p10", "Marco Asensio", "11", "ST"),
                LineupPlayer("p11", "Bradley Barcola", "29", "LW")
            ),
            bench = listOf(
                LineupPlayer("b1", "Arnau Tenas", "80", "GK", isSubstitute = true),
                LineupPlayer("b2", "Lucas Beraldo", "35", "DF", isSubstitute = true),
                LineupPlayer("b3", "Fabián Ruiz", "8", "MF", isSubstitute = true),
                LineupPlayer("b4", "Kang-in Lee", "19", "FW", isSubstitute = true),
                LineupPlayer("b5", "Randal Kolo Muani", "23", "FW", isSubstitute = true)
            ),
            coach = "Luis Enrique"
        )

        val awayLineup = TeamLineup(
            teamName = match.awayTeam.name,
            formation = "4-2-3-1",
            starters = listOf(
                LineupPlayer("a1", "Thibaut Courtois", "1", "GK"),
                LineupPlayer("a2", "Lucas Vázquez", "17", "RB"),
                LineupPlayer("a3", "Éder Militão", "3", "CB"),
                LineupPlayer("a4", "Antonio Rüdiger", "22", "CB"),
                LineupPlayer("a5", "Ferland Mendy", "23", "LB"),
                LineupPlayer("a6", "Federico Valverde", "8", "CM"),
                LineupPlayer("a7", "Aurélien Tchouaméni", "14", "CM"),
                LineupPlayer("a8", "Rodrygo", "11", "RW"),
                LineupPlayer("a9", "Jude Bellingham", "5", "CAM"),
                LineupPlayer("a10", "Vinícius Júnior", "7", "LW"),
                LineupPlayer("a11", "Kylian Mbappé", "9", "ST")
            ),
            bench = listOf(
                LineupPlayer("ab1", "Andriy Lunin", "13", "GK", isSubstitute = true),
                LineupPlayer("ab2", "Luka Modrić", "10", "MF", isSubstitute = true),
                LineupPlayer("ab3", "Eduardo Camavinga", "6", "MF", isSubstitute = true),
                LineupPlayer("ab4", "Brahim Díaz", "21", "FW", isSubstitute = true),
                LineupPlayer("ab5", "Endrick", "16", "FW", isSubstitute = true)
            ),
            coach = "Carlo Ancelotti"
        )

        return MatchDetail(
            match = match,
            incidents = incidents,
            statistics = statistics,
            homeLineup = homeLineup,
            awayLineup = awayLineup,
            commentary = listOf(
                "58' Intensifying duel in midfield as both managers prepare fresh changes.",
                "56' GOAL! Kang-in Lee cuts inside onto his left foot and unleashes an unstoppable strike!",
                "51' GOAL! Kylian Mbappé keeps his composure and sends Donnarumma the wrong way from the spot.",
                "45' Half-time whistle blown after an electric first period in Paris."
            ),
            referee = "Szymon Marciniak (Poland)"
        )
    }
}
