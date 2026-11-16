package com.example.mintline.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.mintline.theme.Mint
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.example.mintline.ui.components.liquid.LiquidBottomTab

enum class MintlineTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    // The ball matches the app icon; the other three use rounded Material Symbols
    MATCHES("Matches", Icons.Filled.SportsSoccer, Icons.Outlined.SportsSoccer),
    LEAGUES("Leagues", Icons.Rounded.Leaderboard, Icons.Outlined.Leaderboard),
    NEWS("News", Icons.Rounded.Newspaper, Icons.Outlined.Newspaper),
    FAVORITES("Saved", Icons.Rounded.Bookmark, Icons.Outlined.BookmarkBorder),
    SETTINGS("Settings", Icons.Rounded.Tune, Icons.Outlined.Tune)
}

/**
 * Floating Liquid Glass tab bar — Kyant's LiquidBottomTabs (ported in
 * ui/components/liquid): lens + vibrancy bar, a glass droplet you can drag
 * between tabs that magnifies a tinted copy of the icons, press-to-swell
 * and an interactive specular highlight that follows your finger.
 */
@Composable
fun LiquidGlassBottomBar(
    currentTab: MintlineTab,
    onTabSelected: (MintlineTab) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val tabs = MintlineTab.entries
    val scheme = MaterialTheme.colorScheme
    // One stable lambda reading State: the ported tabs remember their index keyed on this
    // lambda, so a new lambda per recomposition would make taps update a stale copy.
    val selectedIndex = androidx.compose.runtime.rememberUpdatedState(tabs.indexOf(currentTab))
    val selectedProvider = androidx.compose.runtime.remember { { selectedIndex.value } }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        com.example.mintline.ui.components.liquid.LiquidBottomTabs(
            selectedTabIndex = selectedProvider,
            onTabSelected = { onTabSelected(tabs[it]) },
            backdrop = backdrop,
            tabsCount = tabs.size,
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            tabs.forEach { tab ->
                val selected = tab == currentTab
                    LiquidBottomTab(onClick = { onTabSelected(tab) }) {
                        Icon(
                            if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.title,
                            tint = scheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }
            }
        }
    }
}
