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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.glass.GlassBackdrop
import com.example.mintline.ui.components.glass.liquidGlass
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class MintlineTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    MATCHES("Matches", Icons.Filled.SportsSoccer, Icons.Outlined.SportsSoccer),
    LEAGUES("Leagues", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
    FAVORITES("Saved", Icons.Filled.Star, Icons.Outlined.StarBorder),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

private val BarHeight = 64.dp
private val BarInset = 6.dp

/**
 * Floating Liquid Glass tab bar.
 *
 * The selection "droplet" is driven by two springs — the leading edge
 * moves fast, the trailing edge lags — so the pill stretches like liquid
 * while travelling and settles back to a round lens.
 */
@Composable
fun LiquidGlassBottomBar(
    currentTab: MintlineTab,
    onTabSelected: (MintlineTab) -> Unit,
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier
) {
    val tabs = MintlineTab.entries
    val selectedIndex = tabs.indexOf(currentTab)
    val reduced = Mint.reducedMotion
    val colors = MaterialTheme.colorScheme
    val mint = Mint.colors

    val barShape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .height(BarHeight)
                .liquidGlass(shape = barShape, backdrop = backdrop)
                .padding(BarInset)
        ) {
            val density = LocalDensity.current
            val slotWidthPx = with(density) { maxWidth.toPx() } / tabs.size

            val left = remember { Animatable(selectedIndex * slotWidthPx) }
            val right = remember { Animatable((selectedIndex + 1) * slotWidthPx) }

            LaunchedEffect(selectedIndex, slotWidthPx) {
                val targetL = selectedIndex * slotWidthPx
                val targetR = targetL + slotWidthPx
                if (reduced) {
                    left.snapTo(targetL); right.snapTo(targetR); return@LaunchedEffect
                }
                val movingRight = targetL > left.value
                val fast = spring<Float>(dampingRatio = 0.72f, stiffness = 900f)
                val slow = spring<Float>(dampingRatio = 0.62f, stiffness = 260f)
                coroutineScope {
                    launch { left.animateTo(targetL, if (movingRight) slow else fast) }
                    launch { right.animateTo(targetR, if (movingRight) fast else slow) }
                }
            }

            // Stretch amount squashes the droplet vertically while it travels
            val stretch = ((right.value - left.value) / slotWidthPx - 1f).coerceIn(0f, 1.5f)
            val indicatorColor = colors.primary.copy(alpha = if (mint.isDark) 0.22f else 0.20f)
            val indicatorRim = colors.primary.copy(alpha = 0.35f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .drawBehind {
                        val squash = stretch * 6.dp.toPx()
                        val top = squash / 2f
                        val h = size.height - squash
                        val r = CornerRadius(h / 2f, h / 2f)
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = Offset(left.value, top),
                            size = Size(right.value - left.value, h),
                            cornerRadius = r
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = if (mint.isDark) 0.18f else 0.55f), Color.Transparent),
                                startY = top, endY = top + h
                            ),
                            topLeft = Offset(left.value, top),
                            size = Size(right.value - left.value, h),
                            cornerRadius = r
                        )
                        drawRoundRect(
                            color = indicatorRim,
                            topLeft = Offset(left.value, top),
                            size = Size(right.value - left.value, h),
                            cornerRadius = r,
                            style = Stroke(1.dp.toPx())
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavItem(
                            tab = tab,
                            selected = index == selectedIndex,
                            onClick = { onTabSelected(tab) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: MintlineTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = Mint.reducedMotion
    val colors = MaterialTheme.colorScheme

    val tint by animateColorAsState(
        targetValue = if (selected) {
            if (Mint.colors.isDark) colors.primary else colors.onPrimaryContainer
        } else colors.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navTint"
    )
    val scale by animateFloatAsState(
        targetValue = when {
            reduced -> 1f
            pressed -> 0.86f
            selected -> 1.06f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "navScale"
    )
    val lift by animateFloatAsState(
        targetValue = if (selected && !reduced) -1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "navLift"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .scale(scale)
            .offset(y = lift.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = tab.title,
            tint = tint,
            modifier = Modifier.size(27.dp)
        )
    }
}
