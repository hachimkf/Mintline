package com.example.mintline.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mintline.theme.Mint

/** A ball that bounces with squash & stretch over its shadow. */
@Composable
fun LoadingStateView(
    modifier: Modifier = Modifier,
    message: String = "Warming up the pitch…"
) {
    val reduced = Mint.reducedMotion
    val t = rememberInfiniteTransition(label = "ball")
    val height by t.animateFloat(
        initialValue = 0f,
        targetValue = if (reduced) 0f else 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 900
                0f at 0 using LinearOutSlowInEasing
                1f at 450 using FastOutLinearInEasing
                0f at 900
            },
            RepeatMode.Restart
        ),
        label = "h"
    )
    val spin by t.animateFloat(
        initialValue = 0f, targetValue = if (reduced) 0f else 360f,
        animationSpec = infiniteRepeatable(tween(1800)), label = "spin"
    )
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(width = 80.dp, height = 110.dp), contentAlignment = Alignment.BottomCenter) {
            val squash = if (height < 0.12f) 1f - (0.12f - height) * 1.6f else 1f
            Canvas(Modifier.size(width = 60.dp, height = 10.dp)) {
                val w = size.width * (0.55f + 0.45f * (1f - height))
                drawOval(
                    color = scheme.onSurface.copy(alpha = 0.10f + 0.12f * (1f - height)),
                    topLeft = Offset((size.width - w) / 2f, 0f),
                    size = Size(w, size.height)
                )
            }
            Box(
                Modifier
                    .padding(bottom = 8.dp)
                    .graphicsLayer {
                        translationY = -height * 60.dp.toPx()
                        scaleY = squash
                        scaleX = 2f - squash
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .rotate((height - 0.5f) * 14f),
                contentAlignment = Alignment.Center
            ) {
                MintlineLogo(size = 52.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ErrorOrOfflineBanner(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .floatingCard(shape = CardShapeSmall, color = scheme.secondaryContainer, elevation = 0.dp)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(scheme.surface.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CloudOff, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Showing offline fixtures",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSecondaryContainer
            )
            Text(
                errorMessage.ifBlank { "Live source unavailable" },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSecondaryContainer.copy(alpha = 0.75f),
                maxLines = 1
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(scheme.primary)
                .bouncyClickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Refresh, null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Retry", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
        }
    }
}

@Composable
fun EmptyFavoritesView(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.StarBorder, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("Nothing saved yet", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap the star on any match or competition and it will live here for quick access.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Normal
        )
    }
}
