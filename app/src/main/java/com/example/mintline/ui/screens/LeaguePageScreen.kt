package com.example.mintline.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mintline.data.FootballCatalog
import com.example.mintline.model.LeagueLeaders
import com.example.mintline.model.StandingGroup
import com.example.mintline.ui.components.LoadingStateView
import com.example.mintline.ui.components.bouncyClickable
import com.example.mintline.ui.components.floatingCard
import com.example.mintline.ui.components.glass.LocalGlassBackdrop
import com.example.mintline.ui.components.glass.edgeBlur
import com.example.mintline.ui.components.glass.glassSource
import com.example.mintline.ui.components.glass.liquidGlass
import com.example.mintline.ui.components.glass.rememberGlassBackdrop
import com.example.mintline.ui.components.match.LeadersList
import com.example.mintline.ui.components.match.StandingsTable

private val PageTabs = listOf("Table", "Top scorers", "Assists")

@Composable
fun LeaguePageScreen(
    leagueId: String,
    tables: List<StandingGroup>,
    leaders: LeagueLeaders?,
    loading: Boolean,
    bracket: com.example.mintline.model.Bracket? = null,
    favoriteTeamIds: Set<String>,
    onBack: () -> Unit,
    onShowMatches: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val info = FootballCatalog.leagueBySlug(leagueId)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val backdrop = rememberGlassBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .glassSource(backdrop)
                    .background(scheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(top = top + 68.dp, bottom = bottom + 32.dp, start = 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header card
                Row(
                    Modifier.fillMaxWidth().floatingCard(shape = RoundedCornerShape(36.dp)).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFF2F3E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(info?.logoUrl).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info?.name ?: leagueId, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                        Text(info?.country.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(scheme.primary)
                            .bouncyClickable(pressedScale = 0.92f, onClick = onShowMatches)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SportsSoccer, null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Matches", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
                    }
                }

                val tabs = if (bracket != null) PageTabs + "Bracket" else PageTabs
                PillTabs(tabs, tab.coerceAtMost(tabs.lastIndex)) { tab = it }

                AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "leagueTab") { t ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when {
                            loading && tables.isEmpty() && leaders == null -> LoadingStateView(message = "Loading the table…")
                            t == 3 && bracket != null -> Column(Modifier.fillMaxWidth().floatingCard(shape = RoundedCornerShape(32.dp)).padding(14.dp)) {
                                com.example.mintline.ui.components.match.BracketView(bracket)
                            }
                            t == 0 && tables.isEmpty() -> Empty("No table for this competition yet")
                            t == 0 -> tables.forEach { g ->
                                Column(Modifier.fillMaxWidth().floatingCard(shape = RoundedCornerShape(32.dp)).padding(10.dp)) {
                                    if (tables.size > 1 || g.name.isNotBlank()) {
                                        Text(g.name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.padding(8.dp))
                                    }
                                    StandingsTable(g, highlightTeamIds = favoriteTeamIds)
                                }
                            }
                            else -> {
                                val list = if (t == 1) leaders?.goals.orEmpty() else leaders?.assists.orEmpty()
                                if (list.isEmpty()) Empty("No player stats published yet")
                                else Column(Modifier.fillMaxWidth().floatingCard(shape = RoundedCornerShape(32.dp)).padding(8.dp)) {
                                    LeadersList(list, unit = if (t == 1) "goals" else "assists")
                                }
                            }
                        }
                    }
                }
            }

            Box(
                Modifier.fillMaxWidth().height(top + 76.dp)
                    .edgeBlur(fromTop = true, scrimColor = scheme.background, scrimAlpha = 0.8f)
            )
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).liquidGlass(shape = CircleShape, elevation = 8.dp).bouncyClickable(pressedScale = 0.86f, onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = scheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp)
    )
}

/** Springy sliding pill tabs (shared look with the match screen). */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHigh).padding(5.dp)
    ) {
        val slot = maxWidth / labels.size
        val x by animateDpAsState(slot * selected, spring(dampingRatio = 0.62f, stiffness = 420f), label = "pillX")
        Box(Modifier.offset(x = x).width(slot).fillMaxHeight().clip(RoundedCornerShape(50)).background(scheme.primary))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val c by animateColorAsState(if (i == selected) scheme.onPrimary else scheme.onSurfaceVariant, label = "pillC")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).bouncyClickable(pressedScale = 0.92f) { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = c, maxLines = 1)
                }
            }
        }
    }
}
