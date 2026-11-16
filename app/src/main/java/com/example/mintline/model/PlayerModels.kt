package com.example.mintline.model

/** A player found by search (365Scores athlete). */
data class PlayerHit(
    val id: Int,
    val name: String,
    val clubName: String?,
    val clubLogo: String?,
    val position: String?,
    val photoUrl: String,
)

data class PlayerSeasonRow(
    val competition: String,
    val appearances: String,
    val starts: String,
    val goals: String,
    val assists: String,
    val yellow: String,
    val red: String,
)

data class PlayerTransfer(
    val clubName: String,
    val clubLogo: String?,
    val dateIso: String,
    val type: String,
    val fee: String?,
)

data class PlayerRecentMatch(
    val gameId: Int,
    val dateIso: String,
    val competition: String,
    val homeName: String,
    val homeLogo: String?,
    val homeScore: String,
    val awayName: String,
    val awayLogo: String?,
    val awayScore: String,
    val minutes: String?,
    val rating: String?,
    val ratingColor: String?,
)

data class PlayerTrophy(val name: String, val count: Int)

/** Transfermarkt-style profile built from 365Scores' public athlete data. */
data class PlayerProfile(
    val id: Int,
    val name: String,
    val photoUrl: String,
    val clubName: String?,
    val clubLogo: String?,
    val nationality: String?,
    val position: String?,
    val jersey: String?,
    val age: Int?,
    val birthDate: String?,
    val height: String?,
    val foot: String?,
    val contractUntil: String?,
    val bio: String?,
    val seasonName: String?,
    val seasonTables: List<Pair<String, List<PlayerSeasonRow>>>, // e.g. "Club (Real Madrid)" -> rows
    val recentMatches: List<PlayerRecentMatch>,
    val transfers: List<PlayerTransfer>,
    val trophies: List<PlayerTrophy>,
)

data class NewsArticle(
    val headline: String,
    val description: String?,
    val imageUrl: String?,
    val url: String?,
    val publishedIso: String?,
    val isVideo: Boolean,
)
