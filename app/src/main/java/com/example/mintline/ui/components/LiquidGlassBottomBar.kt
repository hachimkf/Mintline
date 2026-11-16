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

enum class MintlineTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    // The ball matches the app icon; the other three use rounded Material Symbols
    MATCHES("Matches", Icons.Filled.SportsSoccer, Icons.Outlined.SportsSoccer),
    LEAGUES("Leagues", Icons.Rounded.Leaderboard, Icons.Outlined.Leaderboard),
    FAVORITES("Saved", Icons.Rounded.Bookmark, Icons.Outlined.BookmarkBorder),
    SETTINGS("Settings", Icons.Rounded.Tune, Icons.Outlined.Tune)
}

private val BarHeight = 64.dp
private val BarInset = 5.dp

/**
 * Floating Liquid Glass tab bar built on Kyant's backdrop library
 * (github.com/Kyant0/AndroidLiquidGlass): the bar refracts what scrolls
 * beneath it through a real lens + vibrancy + blur, and the selection
 * droplet is its own lens with chromatic aberration. The droplet's two
 * edges ride separate springs so it stretches like liquid while moving.
 */
@Composable
fun LiquidGlassBottomBar(
    currentTab: MintlineTab,
    onTabSelected: (MintlineTab) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val tabs = MintlineTab.entries
    val selectedIndex = tabs.indexOf(currentTab)
    val reduced = Mint.reducedMotion
    val mint = Mint.colors
    val scheme = MaterialTheme.colorScheme
    val surfaceTint = if (mint.isDark) Color(0xFF121411).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .height(BarHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        vibrancy()
                        blur(6.dp.toPx())
                        lens(18.dp.toPx(), 30.dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow(alpha = if (mint.isDark) 0.6f else 0.18f) },
                    onDrawSurface = { drawRect(surfaceTint) }
                )
                .padding(BarInset)
        ) {
            val density = LocalDensity.current
            val slotPx = with(density) { maxWidth.toPx() } / tabs.size
            val left = remember { Animatable(selectedIndex * slotPx) }
            val right = remember { Animatable((selectedIndex + 1) * slotPx) }
            val press = remember { Animatable(0f) }

            LaunchedEffect(selectedIndex, slotPx) {
                val tl = selectedIndex * slotPx
                val tr = tl + slotPx
                if (reduced) { left.snapTo(tl); right.snapTo(tr); return@LaunchedEffect }
                val toRight = tl > left.value
                val fast = spring<Float>(dampingRatio = 0.72f, stiffness = 900f)
                val slow = spring<Float>(dampingRatio = 0.62f, stiffness = 260f)
                coroutineScope {
                    // the droplet swells into a lens while travelling, then settles
                    launch { press.animateTo(1f, spring(stiffness = 900f)); press.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 180f)) }
                    launch { left.animateTo(tl, if (toRight) slow else fast) }
                    launch { right.animateTo(tr, if (toRight) fast else slow) }
                }
            }

            // Selection droplet: its own lens over the backdrop, with chromatic aberration
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val w = (right.value - left.value).roundToInt().coerceAtLeast(1)
                        val p = measurable.measure(Constraints.fixed(w, constraints.maxHeight))
                        layout(constraints.maxWidth, constraints.maxHeight) { p.place(left.value.roundToInt(), 0) }
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(50) },
                        effects = {
                            vibrancy()
                            lens(
                                (10.dp.toPx() + 8.dp.toPx() * press.value),
                                (16.dp.toPx() + 10.dp.toPx() * press.value),
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.6f + 0.4f * press.value) },
                        layerBlock = {
                            val stretch = ((right.value - left.value) / slotPx - 1f).coerceIn(0f, 1.5f)
                            val s = 1f + 0.06f * press.value
                            scaleX = s
                            scaleY = s * (1f - 0.12f * stretch)
                        },
                        onDrawSurface = {
                            drawRect(scheme.primary.copy(alpha = if (mint.isDark) 0.22f else 0.18f))
                        }
                    )
                    .fillMaxHeight()
            )

            Row(
                Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavItem(tab, index == selectedIndex, { onTabSelected(tab) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NavItem(tab: MintlineTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = Mint.reducedMotion
    val scheme = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        if (selected) (if (Mint.colors.isDark) scheme.primary else scheme.onPrimaryContainer) else scheme.onSurfaceVariant,
        spring(stiffness = Spring.StiffnessMediumLow), label = "navTint"
    )
    val scale by animateFloatAsState(
        when { reduced -> 1f; pressed -> 0.84f; selected -> 1.08f; else -> 1f },
        spring(dampingRatio = 0.45f, stiffness = 500f), label = "navScale"
    )
    Box(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (selected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = tab.title,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
    }
}
