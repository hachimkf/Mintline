package com.example.mintline.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mintline.model.Competition

@Composable
fun CompetitionFilterRow(
    competitions: List<Competition>,
    selectedCompetitionId: String?,
    onSelectCompetition: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        item(key = "all") {
            FilterPill(
                title = "All football",
                logoUrl = null,
                isSelected = selectedCompetitionId == null || selectedCompetitionId == "all",
                onClick = { onSelectCompetition("all") }
            )
        }
        items(competitions, key = { it.id }) { comp ->
            FilterPill(
                title = comp.name,
                logoUrl = comp.logoUrl,
                isSelected = selectedCompetitionId == comp.id,
                onClick = { onSelectCompetition(comp.id) }
            )
        }
    }
}

@Composable
private fun FilterPill(
    title: String,
    logoUrl: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (isSelected) scheme.primary else scheme.surfaceContainerHigh,
        spring(stiffness = 500f), label = "pillBg"
    )
    val fg by animateColorAsState(
        if (isSelected) scheme.onPrimary else scheme.onSurface,
        spring(stiffness = 500f), label = "pillFg"
    )

    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .bouncyClickable(pressedScale = 0.92f, onClick = onClick)
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFFF2F3E9)),
            contentAlignment = Alignment.Center
        ) {
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(logoUrl).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(Icons.Default.SportsSoccer, null, tint = Color(0xFF1A1C16), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(end = 2.dp)
        )
    }
}
