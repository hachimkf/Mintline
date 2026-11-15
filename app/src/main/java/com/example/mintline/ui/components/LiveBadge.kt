package com.example.mintline.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mintline.theme.Mint

/** Pulsing coral dot. */
@Composable
fun LiveDot(modifier: Modifier = Modifier, size: Dp = 8.dp, color: Color = Mint.colors.live) {
    val reduced = Mint.reducedMotion
    val transition = rememberInfiniteTransition(label = "liveDot")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduced) 0f else 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse"
    )
    Canvas(modifier.size(size * 2)) {
        val r = size.toPx() / 2f
        drawCircle(color.copy(alpha = (1f - pulse) * 0.45f), radius = r + r * 1.6f * pulse)
        drawCircle(color, radius = r)
    }
}

/** Status pill: pulsing for live games, quiet tonal for everything else. */
@Composable
fun LivePillBadge(
    statusText: String,
    modifier: Modifier = Modifier,
    isLive: Boolean = true,
) {
    val mint = Mint.colors
    val scheme = MaterialTheme.colorScheme
    val bg = if (isLive) mint.live.copy(alpha = if (mint.isDark) 0.18f else 0.12f) else scheme.surfaceContainerHighest
    val fg = if (isLive) mint.live else scheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(start = if (isLive) 4.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLive) {
            LiveDot(size = 6.dp)
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text = statusText.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
fun HeroLiveTag(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Mint.colors.live)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "LIVE",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = Mint.colors.onLive
        )
    }
}
