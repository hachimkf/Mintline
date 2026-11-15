package com.example.mintline.data

import android.content.Context
import android.content.SharedPreferences
import com.example.mintline.model.FavoriteType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FavoritesData(
    val matchIds: Set<String> = emptySet(),
    val teamIds: Set<String> = emptySet(),
    val leagueIds: Set<String> = emptySet()
)

class FavoritesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mintline_favorites", Context.MODE_PRIVATE)

    private val _favoritesFlow = MutableStateFlow(loadFavorites())
    val favoritesFlow: StateFlow<FavoritesData> = _favoritesFlow.asStateFlow()

    private fun loadFavorites(): FavoritesData {
        val matches = prefs.getStringSet("fav_matches", emptySet()) ?: emptySet()
        val teams = prefs.getStringSet("fav_teams", emptySet()) ?: emptySet()
        val leagues = prefs.getStringSet("fav_leagues", emptySet()) ?: emptySet()
        return FavoritesData(
            matchIds = matches.toSet(),
            teamIds = teams.toSet(),
            leagueIds = leagues.toSet()
        )
    }

    fun isFavorite(id: String, type: FavoriteType): Boolean {
        val current = _favoritesFlow.value
        return when (type) {
            FavoriteType.MATCH -> current.matchIds.contains(id)
            FavoriteType.TEAM -> current.teamIds.contains(id)
            FavoriteType.LEAGUE -> current.leagueIds.contains(id)
        }
    }

    fun toggleFavorite(id: String, type: FavoriteType): Boolean {
        val current = _favoritesFlow.value
        var isNowFav = false
        val newFavorites = when (type) {
            FavoriteType.MATCH -> {
                val updated = current.matchIds.toMutableSet()
                if (updated.contains(id)) {
                    updated.remove(id)
                    isNowFav = false
                } else {
                    updated.add(id)
                    isNowFav = true
                }
                prefs.edit().putStringSet("fav_matches", updated).apply()
                current.copy(matchIds = updated)
            }
            FavoriteType.TEAM -> {
                val updated = current.teamIds.toMutableSet()
                if (updated.contains(id)) {
                    updated.remove(id)
                    isNowFav = false
                } else {
                    updated.add(id)
                    isNowFav = true
                }
                prefs.edit().putStringSet("fav_teams", updated).apply()
                current.copy(teamIds = updated)
            }
            FavoriteType.LEAGUE -> {
                val updated = current.leagueIds.toMutableSet()
                if (updated.contains(id)) {
                    updated.remove(id)
                    isNowFav = false
                } else {
                    updated.add(id)
                    isNowFav = true
                }
                prefs.edit().putStringSet("fav_leagues", updated).apply()
                current.copy(leagueIds = updated)
            }
        }
        _favoritesFlow.value = newFavorites
        return isNowFav
    }

    /** Merges picks from the first-run picker into the saved favourites. */
    fun addFavorites(leagueIds: Set<String>, teamIds: Set<String>) {
        val current = _favoritesFlow.value
        val leagues = current.leagueIds + leagueIds
        val teams = current.teamIds + teamIds
        prefs.edit().putStringSet("fav_leagues", leagues).putStringSet("fav_teams", teams).apply()
        _favoritesFlow.value = current.copy(leagueIds = leagues, teamIds = teams)
    }

    fun clearAllFavorites() {
        prefs.edit().clear().apply()
        _favoritesFlow.value = FavoritesData()
    }
}
