package com.example.mintline.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mintline.data.FootballCatalog
import com.example.mintline.model.Competition
import com.example.mintline.model.NewsArticle
import com.example.mintline.ui.components.LoadingStateView
import com.example.mintline.ui.components.bouncyClickable
import com.example.mintline.ui.components.floatingCard
import com.example.mintline.ui.components.staggeredEntrance
import java.time.Duration
import java.time.Instant

private fun ago(iso: String?): String = runCatching {
    val d = Duration.between(Instant.parse(iso), Instant.now())
    when {
        d.toMinutes() < 60 -> "${d.toMinutes().coerceAtLeast(1)}m ago"
        d.toHours() < 24 -> "${d.toHours()}h ago"
        else -> "${d.toDays()}d ago"
    }
}.getOrDefault("")

/** Football news from ESPN: one featured story, then compact cards; filter by league. */
@Composable
fun NewsScreen(
    articles: List<NewsArticle>,
    loading: Boolean,
    league: String,
    favoriteLeagues: List<Competition>,
    onSelectLeague: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    fun open(a: NewsArticle) {
        a.url?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
    }
    val chips = listOf("all" to "All football") +
        (favoriteLeagues.map { it.id to it.name } + listOf("eng.1", "esp.1", "uefa.champions", "ita.1", "ger.1", "fra.1")
            .mapNotNull { id -> FootballCatalog.leagueBySlug(id)?.let { id to it.name } })
            .distinctBy { it.first }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle("News", "Latest football stories from ESPN", Modifier.padding(horizontal = 16.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chips, key = { it.first }) { (id, name) ->
                    val sel = id == league
                    val bg by animateColorAsState(if (sel) scheme.primary else scheme.surfaceContainerHigh, label = "chip")
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (sel) scheme.onPrimary else scheme.onSurface,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg)
                            .bouncyClickable(pressedScale = 0.92f) { onSelectLeague(id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
        if (loading && articles.isEmpty()) {
            item { LoadingStateView(message = "Fetching the latest stories…") }
            return@LazyColumn
        }
        articles.firstOrNull()?.let { lead ->
            item(key = "lead_${lead.headline}") {
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                        .floatingCard(shape = RoundedCornerShape(32.dp))
                        .bouncyClickable(pressedScale = 0.98f) { open(lead) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(lead.imageUrl).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)
                    )
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        Text(lead.headline, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(ago(lead.publishedIso), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                    }
                    if (lead.isVideo) Icon(Icons.Rounded.PlayCircle, "Video", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(34.dp))
                }
            }
        }
        itemsIndexed(articles.drop(1), key = { i, a -> "${i}_${a.headline}" }) { i, a ->
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .floatingCard(shape = RoundedCornerShape(28.dp))
                    .bouncyClickable(pressedScale = 0.98f) { open(a) }
                    .padding(10.dp)
                    .staggeredEntrance(i, stepMs = 30, key = league),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(a.imageUrl).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 104.dp, height = 78.dp).clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerHigh)
                    )
                    if (a.isVideo) Icon(Icons.Rounded.PlayCircle, "Video", tint = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.headline, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(ago(a.publishedIso), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        if (!loading && articles.isEmpty()) {
            item { Text("No stories right now.", color = scheme.onSurfaceVariant, modifier = Modifier.padding(32.dp)) }
        }
    }
}
