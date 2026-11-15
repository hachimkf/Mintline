package com.example.mintline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mintline.theme.Mint

/**
 * Club crest sitting on a soft round plate. Crests are mostly designed for
 * light backgrounds, so the plate stays light-ish in both themes.
 */
@Composable
fun TeamCrest(
    crestUrl: String?,
    teamName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    plate: Color? = null,
) {
    if (isFlagUrl(crestUrl)) {
        FlagCircle(url = crestUrl!!, size = size, modifier = modifier)
        return
    }
    val mint = Mint.colors
    val plateColor = plate ?: if (mint.isDark) Color(0xFF2B2F27) else Color(0xFFF2F3E9)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(plateColor)
            .border(1.dp, Color.White.copy(alpha = if (mint.isDark) 0.08f else 0.6f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!crestUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(crestUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "$teamName crest",
                modifier = Modifier.size(size * 0.66f),
                contentScale = ContentScale.Fit
            )
        } else {
            val initials = teamName.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercase() }
                .joinToString("")
                .ifEmpty { "FC" }
            Text(
                text = initials,
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun isFlagUrl(url: String?): Boolean = url?.contains("/countries/") == true

/**
 * Full-bleed circular flag (like flat flag icon sets). ESPN serves flags
 * letterboxed in a square, so the image is zoomed to fill the circle.
 */
@Composable
fun FlagCircle(url: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFE9EAE2)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = 1.9f; scaleY = 1.9f }
        )
        // faint inner rim so white flags don't melt into light cards
        Box(Modifier.size(size).border(1.dp, Color.Black.copy(alpha = 0.06f), CircleShape))
    }
}
