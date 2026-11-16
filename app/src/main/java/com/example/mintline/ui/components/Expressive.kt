package com.example.mintline.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mintline.theme.LocalMintColors
import com.example.mintline.theme.LocalReducedMotion
import com.example.mintline.theme.Mint
import kotlinx.coroutines.delay

val CardShape = RoundedCornerShape(32.dp)
val CardShapeSmall = RoundedCornerShape(26.dp)

/**
 * Floating card: very rounded, soft ambient shadow in light mode,
 * hairline highlight rim in dark mode.
 */
fun Modifier.floatingCard(
    shape: Shape = CardShape,
    color: Color? = null,
    elevation: Dp = 10.dp,
): Modifier = composed {
    val mint = LocalMintColors.current
    val scheme = MaterialTheme.colorScheme
    val fill = color ?: if (mint.isDark) scheme.surfaceContainer else scheme.surfaceContainerLowest
    this
        .shadow(
            elevation = if (mint.isDark) 0.dp else elevation,
            shape = shape,
            ambientColor = Color(0x2E283018),
            spotColor = Color(0x38283018)
        )
        .clip(shape)
        .background(fill)
        .then(
            if (mint.isDark) Modifier.border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))),
                shape
            ) else Modifier
        )
}

/** Clickable with an expressive squish on press and no ripple. */
fun Modifier.bouncyClickable(
    pressedScale: Float = 0.96f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "bouncy"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * Staggered entrance: fades, lifts and springs content in after [index] * [stepMs].
 */
fun Modifier.staggeredEntrance(index: Int, stepMs: Int = 45, key: Any? = Unit): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val progress = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduced) {
            delay((index.coerceAtMost(12) * stepMs).toLong())
            progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 260f))
        }
    }
    this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 36.dp.toPx()
        val s = 0.94f + 0.06f * p
        scaleX = s; scaleY = s
    }
}

/**
 * Text that counts up from 0 to the numeric part of [value], keeping any
 * suffix such as "%". Non-numeric strings are shown as-is.
 */
@Composable
fun AnimatedCountText(
    value: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    durationMs: Int = 900,
    fontWeight: FontWeight? = null,
) {
    val match = remember(value) { Regex("""^(-?\d+(?:\.\d+)?)(.*)$""").find(value.trim()) }
    val reduced = Mint.reducedMotion
    if (match == null) {
        Text(value, style = style, color = color, modifier = modifier, fontWeight = fontWeight)
        return
    }
    val number = match.groupValues[1].toFloat()
    val decimals = match.groupValues[1].substringAfter('.', "").length
    val suffix = match.groupValues[2]
    val anim = remember(value) { Animatable(if (reduced) number else 0f) }
    LaunchedEffect(value) {
        if (!reduced) {
            delay(delayMs.toLong())
            anim.animateTo(number, tween(durationMs, easing = FastOutSlowInEasing))
        }
    }
    val shown = if (decimals == 0) anim.value.toInt().toString() else "%.${decimals}f".format(anim.value)
    Text(shown + suffix, style = style, color = color, modifier = modifier, fontWeight = fontWeight)
}

/** Round tonal icon button used in headers. */
@Composable
fun TonalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .bouncyClickable(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/** Section title with an optional trailing slot. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** The Mintline app mark (pitch + star ball) as a rounded tile. */
@Composable
fun MintlineLogo(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.example.mintline.R.drawable.app_logo),
        contentDescription = "Mintline",
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
    )
}
