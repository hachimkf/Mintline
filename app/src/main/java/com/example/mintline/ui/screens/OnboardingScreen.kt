package com.example.mintline.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mintline.data.FootballCatalog
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.FlagCircle
import com.example.mintline.ui.components.MintlineLogo
import com.example.mintline.ui.components.isFlagUrl
import com.example.mintline.ui.components.bouncyClickable
import com.example.mintline.ui.components.glass.glassSource
import com.example.mintline.ui.components.glass.liquidGlass
import com.example.mintline.ui.components.glass.rememberGlassBackdrop
import com.example.mintline.ui.components.staggeredEntrance

private data class PickItem(val id: String, val name: String, val logo: String, val caption: String?)

private enum class Step(val title: String, val subtitle: String) {
    LEAGUES("Your competitions", "Pick the leagues and cups you never want to miss."),
    CLUBS("Your clubs", "Their games will always sit at the top of Matchday."),
    NATIONS("Your nations", "Follow national teams through qualifiers and tournaments."),
}

/**
 * First-run picker. Selections become favourites: competitions are pinned
 * above other leagues, clubs and nations form the "Your teams" section.
 */
@Composable
fun OnboardingScreen(
    initialLeagues: Set<String>,
    initialTeams: Set<String>,
    onDone: (leagues: Set<String>, teams: Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var leagues by remember { mutableStateOf(initialLeagues) }
    var teams by remember { mutableStateOf(initialTeams) }
    val scheme = MaterialTheme.colorScheme
    val current = Step.entries[step]

    val items = remember(current) {
        when (current) {
            Step.LEAGUES -> FootballCatalog.leagues.take(32).map { PickItem(it.slug, it.name, it.logoUrl, it.country) }
            Step.CLUBS -> FootballCatalog.clubs.map { PickItem(it.espnId, it.name, it.logoUrl, null) }
            Step.NATIONS -> FootballCatalog.nations.map { PickItem(it.espnId, it.name, it.logoUrl, null) }
        }
    }
    val selected = if (current == Step.LEAGUES) leagues else teams
    fun toggle(id: String) {
        if (current == Step.LEAGUES) leagues = if (id in leagues) leagues - id else leagues + id
        else teams = if (id in teams) teams - id else teams + id
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val backdrop = rememberGlassBackdrop()
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().glassSource(backdrop).background(scheme.background).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MintlineLogo(size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Step.entries.forEachIndexed { i, _ ->
                        val fill by animateFloatAsState(
                            if (i <= step) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 300f), label = "seg"
                        )
                        Box(
                            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHighest)
                        ) {
                            Box(
                                Modifier.fillMaxWidth(fill).height(6.dp).clip(RoundedCornerShape(50)).background(scheme.primary)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .bouncyClickable { onDone(leagues, teams) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 320f)) { it / 3 * dir } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 * dir } + fadeOut())
                },
                label = "step",
                modifier = Modifier.weight(1f)
            ) { s ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset + 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(start = 4.dp, bottom = 10.dp)) {
                            Text(s.title, style = MaterialTheme.typography.headlineLarge, color = scheme.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Text(s.subtitle, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                        }
                    }
                    itemsIndexed(items, key = { _, it -> "${s.name}_${it.id}" }) { i, item ->
                        PickTile(
                            item = item,
                            selected = item.id in selected,
                            round = s != Step.LEAGUES,
                            onClick = { toggle(item.id) },
                            modifier = Modifier.staggeredEntrance(i, stepMs = 25, key = s)
                        )
                    }
                }
            }
        }

        // Floating glass action bar
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .liquidGlass(shape = RoundedCornerShape(50), backdrop = backdrop, elevation = 12.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selected.isEmpty()) "Nothing picked yet" else "${selected.size} selected",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = 16.dp)
            )
            if (step > 0) {
                Text(
                    "Back",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .bouncyClickable { step-- }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(scheme.primary)
                    .bouncyClickable(pressedScale = 0.93f) {
                        if (step < Step.entries.lastIndex) step++ else onDone(leagues, teams)
                    }
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (step < Step.entries.lastIndex) "Next" else "Let's go",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onPrimary
                )
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PickTile(
    item: PickItem,
    selected: Boolean,
    round: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    val bg by animateColorAsState(
        if (selected) scheme.primaryContainer else if (mint.isDark) scheme.surfaceContainer else scheme.surfaceContainerLowest,
        spring(stiffness = 500f), label = "tileBg"
    )
    val pop by animateFloatAsState(
        if (selected) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "pop"
    )
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(bg)
                .border(2.dp, if (selected) scheme.primary else Color.Transparent, RoundedCornerShape(28.dp))
                .bouncyClickable(pressedScale = 0.92f, onClick = onClick)
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isFlagUrl(item.logo)) {
                FlagCircle(url = item.logo, size = 60.dp)
            } else Box(
                Modifier
                    .size(56.dp)
                    .clip(if (round) CircleShape else RoundedCornerShape(18.dp))
                    .background(Color(0xFFF2F3E9)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(item.logo).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.size(if (round) 40.dp else 36.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                item.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            item.caption?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop }
                .size(24.dp)
                .clip(CircleShape)
                .background(scheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}
