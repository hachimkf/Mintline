package com.example.mintline.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Mintline palette — warm, human, football-first.
 *
 * Neutrals carry a slight grass/olive warmth instead of cold slate blue,
 * the accent is a soft lime (less neon than before), the away side uses a
 * friendly sky blue, and "live" is a warm coral instead of an alarm red.
 */

// ---------- Light scheme ----------
internal val LightPrimary = Color(0xFF3B6A00)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFC9F27A)
internal val LightOnPrimaryContainer = Color(0xFF102000)
internal val LightSecondary = Color(0xFF56624A)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFDAE7C9)
internal val LightOnSecondaryContainer = Color(0xFF141E0C)
internal val LightTertiary = Color(0xFF2F6A98)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFCDE5FF)
internal val LightOnTertiaryContainer = Color(0xFF001D32)
internal val LightError = Color(0xFFBA1A1A)
internal val LightBackground = Color(0xFFF7F7EE)
internal val LightOnBackground = Color(0xFF1A1C16)
internal val LightSurface = Color(0xFFF7F7EE)
internal val LightOnSurface = Color(0xFF1A1C16)
internal val LightSurfaceVariant = Color(0xFFE1E4D4)
internal val LightOnSurfaceVariant = Color(0xFF45483D)
internal val LightOutline = Color(0xFF75796C)
internal val LightOutlineVariant = Color(0xFFC5C8B9)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFF2F2E8)
internal val LightSurfaceContainer = Color(0xFFECEDE2)
internal val LightSurfaceContainerHigh = Color(0xFFE6E7DC)
internal val LightSurfaceContainerHighest = Color(0xFFE0E2D6)

// ---------- Dark scheme ----------
internal val DarkPrimary = Color(0xFFC3EE6E)
internal val DarkOnPrimary = Color(0xFF1C3700)
internal val DarkPrimaryContainer = Color(0xFF2C5000)
internal val DarkOnPrimaryContainer = Color(0xFFDDF8A6)
internal val DarkSecondary = Color(0xFFBECBAE)
internal val DarkOnSecondary = Color(0xFF29341F)
internal val DarkSecondaryContainer = Color(0xFF3F4A34)
internal val DarkOnSecondaryContainer = Color(0xFFDAE7C9)
internal val DarkTertiary = Color(0xFF9ACBFF)
internal val DarkOnTertiary = Color(0xFF003352)
internal val DarkTertiaryContainer = Color(0xFF0E4A73)
internal val DarkOnTertiaryContainer = Color(0xFFCDE5FF)
internal val DarkError = Color(0xFFFFB4AB)
internal val DarkBackground = Color(0xFF000000) // true black: OLED pixels off
internal val DarkOnBackground = Color(0xFFE3E4D8)
internal val DarkSurface = Color(0xFF000000)
internal val DarkOnSurface = Color(0xFFE3E4D8)
internal val DarkSurfaceVariant = Color(0xFF45483D)
internal val DarkOnSurfaceVariant = Color(0xFFC5C8B9)
internal val DarkOutline = Color(0xFF8F9284)
internal val DarkOutlineVariant = Color(0xFF2E302A)
internal val DarkSurfaceContainerLowest = Color(0xFF000000)
internal val DarkSurfaceContainerLow = Color(0xFF0B0C0A)
internal val DarkSurfaceContainer = Color(0xFF121411)
internal val DarkSurfaceContainerHigh = Color(0xFF1B1D19)
internal val DarkSurfaceContainerHighest = Color(0xFF252723)

/** Football-specific colours that Material's scheme does not cover. */
@Immutable
data class MintColors(
    val isDark: Boolean,
    val live: Color,
    val onLive: Color,
    val liveContainer: Color,
    val home: Color,
    val onHome: Color,
    val away: Color,
    val onAway: Color,
    val gold: Color,
    val yellowCard: Color,
    val redCard: Color,
    val goal: Color,
    val sub: Color,
    val pitchGrass: Color,
    val pitchGrassStripe: Color,
    val pitchLine: Color,
    val glassTint: Color,
    val glassHighlight: Color,
    val glassEdge: Color,
    val glassShadow: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val onHeroMuted: Color,
)

internal val LightMintColors = MintColors(
    isDark = false,
    live = Color(0xFFE5483A),
    onLive = Color.White,
    liveContainer = Color(0xFFFFDAD5),
    home = LightPrimary,
    onHome = Color.White,
    away = LightTertiary,
    onAway = Color.White,
    gold = Color(0xFFE0A100),
    yellowCard = Color(0xFFF5C518),
    redCard = Color(0xFFE5483A),
    goal = Color(0xFF3B6A00),
    sub = Color(0xFF2F6A98),
    pitchGrass = Color(0xFF5E9E4C),
    pitchGrassStripe = Color(0xFF57954A),
    pitchLine = Color(0xD9FFFFFF),
    glassTint = Color(0x8CFFFFFF),
    glassHighlight = Color(0xE6FFFFFF),
    glassEdge = Color(0x1F000000),
    glassShadow = Color(0x33283018),
    heroStart = Color(0xFFD4F58A),
    heroEnd = Color(0xFFA8DE5E),
    onHero = Color(0xFF102000),
    onHeroMuted = Color(0xB3102000),
)

internal val DarkMintColors = MintColors(
    isDark = true,
    live = Color(0xFFFF7A6B),
    onLive = Color(0xFF3A0905),
    liveContainer = Color(0xFF5C1A13),
    home = DarkPrimary,
    onHome = DarkOnPrimary,
    away = DarkTertiary,
    onAway = DarkOnTertiary,
    gold = Color(0xFFFFCF5C),
    yellowCard = Color(0xFFFFD54A),
    redCard = Color(0xFFFF6B5E),
    goal = DarkPrimary,
    sub = DarkTertiary,
    pitchGrass = Color(0xFF2E5527),
    pitchGrassStripe = Color(0xFF2A4E24),
    pitchLine = Color(0x99FFFFFF),
    glassTint = Color(0x59121411),
    glassHighlight = Color(0x59FFFFFF),
    glassEdge = Color(0x1AFFFFFF),
    glassShadow = Color(0x80000000),
    heroStart = Color(0xFF2F5A06),
    heroEnd = Color(0xFF1B3305),
    onHero = Color(0xFFEFFAD9),
    onHeroMuted = Color(0xB3EFFAD9),
)

val LocalMintColors = staticCompositionLocalOf { DarkMintColors }
