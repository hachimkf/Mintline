package com.example.mintline.data

import com.example.mintline.model.Competition

/**
 * Static knowledge about ESPN soccer competitions and popular teams.
 *
 * ESPN's combined "all" scoreboard only tags each event with a numeric league
 * id (inside its uid, e.g. "s:600~l:775~e:…"), so this table turns that id
 * into a readable name, logo and an importance rank used to order the feed.
 */
data class LeagueInfo(
    val slug: String,
    val espnId: String,
    val name: String,
    val country: String,
    val logoNumber: String,
    val rank: Int,
    val isCup: Boolean = false,
    val isWomen: Boolean = false,
) {
    val logoUrl: String get() = "https://a.espncdn.com/i/leaguelogos/soccer/500/$logoNumber.png"
}

data class TeamPick(
    val espnId: String,
    val name: String,
    val logoUrl: String,
)

object FootballCatalog {

    const val OTHER_ID = "other"

    /** Ordered by importance: rank decides the order of league groups on Home. */
    val leagues: List<LeagueInfo> = listOf(
        LeagueInfo("fifa.world", "606", "FIFA World Cup", "World", "4", 1, isCup = true),
        LeagueInfo("uefa.champions", "775", "Champions League", "Europe", "2", 2, isCup = true),
        LeagueInfo("uefa.euro", "781", "EURO", "Europe", "74", 3, isCup = true),
        LeagueInfo("conmebol.america", "780", "Copa América", "South America", "83", 4, isCup = true),
        LeagueInfo("eng.1", "700", "Premier League", "England", "23", 5),
        LeagueInfo("esp.1", "740", "LaLiga", "Spain", "15", 6),
        LeagueInfo("ita.1", "730", "Serie A", "Italy", "12", 7),
        LeagueInfo("ger.1", "720", "Bundesliga", "Germany", "10", 8),
        LeagueInfo("fra.1", "710", "Ligue 1", "France", "9", 9),
        LeagueInfo("uefa.europa", "776", "Europa League", "Europe", "2310", 10, isCup = true),
        LeagueInfo("caf.nations", "3908", "Africa Cup of Nations", "Africa", "76", 11, isCup = true),
        LeagueInfo("fifa.cwc", "5501", "Club World Cup", "World", "1932", 12, isCup = true),
        LeagueInfo("uefa.nations", "2395", "Nations League", "Europe", "2395", 13, isCup = true),
        LeagueInfo("fifa.worldq.uefa", "786", "WC Qualifiers · UEFA", "Europe", "67", 14, isCup = true),
        LeagueInfo("fifa.worldq.conmebol", "787", "WC Qualifiers · CONMEBOL", "South America", "65", 15, isCup = true),
        LeagueInfo("fifa.worldq.caf", "790", "WC Qualifiers · CAF", "Africa", "63", 16, isCup = true),
        LeagueInfo("fifa.worldq.afc", "789", "WC Qualifiers · AFC", "Asia", "62", 17, isCup = true),
        LeagueInfo("fifa.worldq.concacaf", "788", "WC Qualifiers · Concacaf", "North America", "64", 18, isCup = true),
        LeagueInfo("uefa.europa.conf", "20296", "Conference League", "Europe", "20296", 19, isCup = true),
        LeagueInfo("conmebol.libertadores", "783", "Copa Libertadores", "South America", "58", 20, isCup = true),
        LeagueInfo("eng.fa", "3918", "FA Cup", "England", "40", 21, isCup = true),
        LeagueInfo("esp.copa_del_rey", "3951", "Copa del Rey", "Spain", "80", 22, isCup = true),
        LeagueInfo("ita.coppa_italia", "3956", "Coppa Italia", "Italy", "2192", 23, isCup = true),
        LeagueInfo("ger.dfb_pokal", "3954", "DFB-Pokal", "Germany", "2061", 24, isCup = true),
        LeagueInfo("fra.coupe_de_france", "3952", "Coupe de France", "France", "182", 25, isCup = true),
        LeagueInfo("eng.league_cup", "3920", "Carabao Cup", "England", "41", 26, isCup = true),
        LeagueInfo("por.1", "715", "Liga Portugal", "Portugal", "14", 27),
        LeagueInfo("ned.1", "725", "Eredivisie", "Netherlands", "11", 28),
        LeagueInfo("ksa.1", "21231", "Saudi Pro League", "Saudi Arabia", "2488", 29),
        LeagueInfo("tur.1", "3946", "Süper Lig", "Türkiye", "18", 30),
        LeagueInfo("usa.1", "770", "MLS", "USA", "19", 31),
        LeagueInfo("mex.1", "760", "Liga MX", "Mexico", "22", 32),
        LeagueInfo("bra.1", "630", "Brasileirão", "Brazil", "85", 33),
        LeagueInfo("arg.1", "745", "Liga Profesional", "Argentina", "1", 34),
        LeagueInfo("sco.1", "735", "Scottish Premiership", "Scotland", "45", 35),
        LeagueInfo("bel.1", "3901", "Belgian Pro League", "Belgium", "6", 36),
        LeagueInfo("eng.2", "3914", "Championship", "England", "24", 37),
        LeagueInfo("fifa.friendly", "3922", "International Friendlies", "World", "53", 38),
        LeagueInfo("fifa.wwc", "795", "Women's World Cup", "World", "60", 40, isCup = true, isWomen = true),
        LeagueInfo("uefa.weuro", "17915", "Women's EURO", "Europe", "2381", 41, isCup = true, isWomen = true),
        LeagueInfo("uefa.wchampions", "19483", "Women's Champions League", "Europe", "2408", 42, isCup = true, isWomen = true),
        LeagueInfo("eng.w.1", "8097", "Women's Super League", "England", "2314", 43, isWomen = true),
        LeagueInfo("usa.nwsl", "8301", "NWSL (Women)", "USA", "2323", 44, isWomen = true),
        LeagueInfo("fifa.friendly.w", "3923", "Women's Friendlies", "World", "70", 45, isWomen = true),
    )

    private val byEspnId = leagues.associateBy { it.espnId }
    private val bySlug = leagues.associateBy { it.slug }

    fun leagueByEspnId(id: String?): LeagueInfo? = id?.let { byEspnId[it] }
    fun leagueBySlug(slug: String?): LeagueInfo? = slug?.let { bySlug[it] }

    /** Rank for any league id used in the app (slug); unknown leagues sink to the bottom. */
    fun rankOf(leagueId: String): Int = bySlug[leagueId]?.rank ?: 1000

    /** Competitions shown in filters, the Leagues tab and the first-run picker. */
    fun competitions(): List<Competition> = leagues.map {
        Competition(id = it.slug, name = it.name, code = it.slug.substringBefore('.').uppercase(), logoUrl = it.logoUrl, country = it.country)
    }

    private fun club(id: String, name: String) =
        TeamPick(id, name, "https://a.espncdn.com/i/teamlogos/soccer/500/$id.png")

    private fun nation(id: String, name: String, code: String) =
        TeamPick(id, name, "https://a.espncdn.com/i/teamlogos/countries/500/$code.png")

    /** ESPN team ids, so favourites match live data directly. */
    val clubs: List<TeamPick> = listOf(
        club("86", "Real Madrid"),
        club("83", "Barcelona"),
        club("382", "Man City"),
        club("364", "Liverpool"),
        club("359", "Arsenal"),
        club("360", "Man United"),
        club("363", "Chelsea"),
        club("367", "Tottenham"),
        club("132", "Bayern Munich"),
        club("124", "Dortmund"),
        club("160", "PSG"),
        club("1068", "Atlético Madrid"),
        club("111", "Juventus"),
        club("110", "Inter"),
        club("103", "AC Milan"),
        club("114", "Napoli"),
        club("139", "Ajax"),
        club("1929", "Benfica"),
        club("437", "FC Porto"),
        club("817", "Al Nassr"),
        club("20232", "Inter Miami"),
    )

    val nations: List<TeamPick> = listOf(
        nation("2869", "Morocco", "mar"),
        nation("205", "Brazil", "bra"),
        nation("202", "Argentina", "arg"),
        nation("478", "France", "fra"),
        nation("448", "England", "eng"),
        nation("164", "Spain", "esp"),
        nation("481", "Germany", "ger"),
        nation("482", "Portugal", "por"),
        nation("162", "Italy", "ita"),
        nation("449", "Netherlands", "ned"),
        nation("459", "Belgium", "bel"),
        nation("477", "Croatia", "cro"),
        nation("660", "United States", "usa"),
        nation("203", "Mexico", "mex"),
        nation("206", "Canada", "can"),
        nation("212", "Uruguay", "uru"),
        nation("208", "Colombia", "col"),
        nation("624", "Algeria", "alg"),
        nation("2620", "Egypt", "egy"),
        nation("654", "Senegal", "sen"),
        nation("659", "Tunisia", "tun"),
        nation("657", "Nigeria", "nga"),
        nation("4789", "Ivory Coast", "civ"),
        nation("655", "Saudi Arabia", "ksa"),
        nation("627", "Japan", "jpn"),
        nation("451", "South Korea", "kors"),
    )

    private val teamsById = (clubs + nations).associateBy { it.espnId }
    fun teamById(id: String): TeamPick? = teamsById[id]
}
