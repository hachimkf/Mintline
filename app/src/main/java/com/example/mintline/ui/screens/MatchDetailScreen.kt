package com.example.mintline.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mintline.model.FavoriteType
import com.example.mintline.model.IncidentType
import com.example.mintline.model.MatchDetail
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.AnimatedScore
import com.example.mintline.ui.components.HeroLiveTag
import com.example.mintline.ui.components.MatchProgressTrack
import com.example.mintline.ui.components.TeamCrest
import com.example.mintline.ui.components.bouncyClickable
import com.example.mintline.ui.components.floatingCard
import com.example.mintline.ui.components.glass.LocalGlassBackdrop
import com.example.mintline.ui.components.glass.edgeBlur
import com.example.mintline.ui.components.glass.glassSource
import com.example.mintline.ui.components.glass.liquidGlass
import com.example.mintline.ui.components.glass.rememberGlassBackdrop
import com.example.mintline.ui.components.liveMinute
import com.example.mintline.ui.components.match.BenchList
import com.example.mintline.ui.components.match.CommentaryCard
import com.example.mintline.ui.components.match.MatchInsightsSection
import com.example.mintline.ui.components.match.EventTimeline
import com.example.mintline.ui.components.match.FormationStrip
import com.example.mintline.ui.components.match.MatchStatsPanel
import com.example.mintline.ui.components.match.PitchLineup
import com.example.mintline.ui.components.staggeredEntrance

enum class DetailTab(val label: String) {
    EVENTS("Timeline"),
    STATS("Stats"),
    LINEUPS("Line-ups"),
    INFO("Info")
}

@Composable
fun MatchDetailScreen(
    detail: MatchDetail,
    onBack: () -> Unit,
    onToggleFavorite: (String, FavoriteType) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlayer: (lineupId: String, name: String, team: String?) -> Unit = { _, _, _ -> },
) {
    var selectedTab by rememberSaveable { mutableStateOf(DetailTab.EVENTS) }
    val match = detail.match
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    val backdrop = rememberGlassBackdrop()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .glassSource(backdrop)
                    .background(scheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(top = topInset + 68.dp, bottom = bottomInset + 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailHeroCard(detail, Modifier.padding(horizontal = 12.dp))

                DetailTabs(
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 4 * dir } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 * dir } + fadeOut())
                    },
                    label = "detailTab",
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) { tab ->
                    when (tab) {
                        DetailTab.EVENTS -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            TabCard {
                                if (detail.incidents.isEmpty()) {
                                    EmptyTabNotice(Icons.Default.Schedule, "No key moments yet", "Goals, cards and subs appear here as they happen.")
                                } else {
                                    EventTimeline(detail.incidents, finished = match.status.isFinished)
                                }
                            }
                            CommentaryCard(detail.commentary)
                        }
                        DetailTab.STATS -> TabCard {
                            if (detail.statistics.isEmpty()) {
                                EmptyTabNotice(Icons.Default.SportsSoccer, "Stats arrive at kick-off", "Possession, shots and more will animate in live.")
                            } else {
                                MatchStatsPanel(detail.statistics, match.homeTeam.shortName, match.awayTeam.shortName)
                            }
                        }
                        DetailTab.LINEUPS -> LineupsTab(detail, onOpenPlayer)
                        DetailTab.INFO -> InfoTab(detail)
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(topInset + 76.dp)
                    .edgeBlur(fromTop = true, scrimColor = scheme.background, scrimAlpha = 0.8f)
            )

            // Floating liquid-glass top bar over the scrolling content
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Spacer(Modifier.weight(1f))
                Text(
                    match.leagueName,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .liquidGlass(shape = RoundedCornerShape(50), elevation = 8.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
                Spacer(Modifier.weight(1f))
                if (match.isLive) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    GlassCircleButton(
                        Icons.Default.NotificationsActive,
                        "Follow live on lock screen",
                        { com.example.mintline.notifications.LiveMatchService.follow(ctx, match.id, match.leagueId) },
                        tint = Mint.colors.live
                    )
                    Spacer(Modifier.width(8.dp))
                }
                GlassCircleButton(
                    if (match.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    "Favorite",
                    { onToggleFavorite(match.id, FavoriteType.MATCH) },
                    tint = if (match.isFavorite) mint.gold else scheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun GlassCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        Modifier
            .size(48.dp)
            .liquidGlass(shape = CircleShape, elevation = 8.dp)
            .bouncyClickable(pressedScale = 0.86f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DetailHeroCard(detail: MatchDetail, modifier: Modifier = Modifier) {
    val match = detail.match
    val mint = Mint.colors
    val goals = detail.incidents.filter { it.type == IncidentType.GOAL || it.type == IncidentType.PENALTY_SCORED }
    fun scorerLines(home: Boolean): List<String> = goals.filter { it.isHomeTeam == home }
        .groupBy { it.primaryPlayer }
        .map { (player, list) ->
            val surname = player.trim().split(" ").last()
            surname + " " + list.joinToString(", ") { g ->
                g.minute + when {
                    g.detail == "Own goal" -> " (OG)"
                    g.type == IncidentType.PENALTY_SCORED -> " (P)"
                    else -> ""
                }
            }
        }

    Box(
        modifier
            .fillMaxWidth()
            .floatingCard(shape = RoundedCornerShape(40.dp), color = mint.heroEnd, elevation = 18.dp)
            .background(Brush.linearGradient(listOf(mint.heroStart, mint.heroEnd)))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val c = mint.onHero.copy(alpha = 0.06f)
            drawCircle(c, radius = size.width * 0.42f, center = Offset(size.width / 2f, size.height * 0.42f), style = Stroke(2.dp.toPx()))
            drawLine(c, Offset(0f, size.height * 0.42f), Offset(size.width, size.height * 0.42f), 2.dp.toPx())
        }
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Top) {
                HeroSide(match.homeTeam.name, match.homeTeam.crestUrl, scorerLines(home = true), Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1.1f).padding(top = 6.dp)
                ) {
                    if (match.isLive) HeroLiveTag() else Text(
                        match.statusText.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = mint.onHeroMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    if (match.homeScore != null && match.awayScore != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedScore(match.homeScore, mint.onHero)
                            Text(":", style = MaterialTheme.typography.displaySmall, color = mint.onHeroMuted, modifier = Modifier.padding(horizontal = 4.dp))
                            AnimatedScore(match.awayScore, mint.onHero)
                        }
                    } else {
                        Text(
                            match.kickoffFormatted,
                            style = MaterialTheme.typography.headlineLarge,
                            color = mint.onHero,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    if (match.isLive) {
                        Text(
                            match.statusText,
                            style = MaterialTheme.typography.labelLarge,
                            color = mint.onHero,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(mint.onHero.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                HeroSide(match.awayTeam.name, match.awayTeam.crestUrl, scorerLines(home = false), Modifier.weight(1f))
            }

            if (match.isLive) {
                Spacer(Modifier.height(18.dp))
                MatchProgressTrack(
                    minute = match.liveMinute() ?: 0,
                    trackColor = mint.onHero.copy(alpha = 0.12f),
                    fillColor = mint.onHero,
                    labelColor = mint.onHeroMuted
                )
            }

            match.venue?.let {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(mint.onHero.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Stadium, null, tint = mint.onHeroMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = mint.onHeroMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun HeroSide(name: String, crest: String?, scorers: List<String>, modifier: Modifier) {
    val mint = Mint.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamCrest(crestUrl = crest, teamName = name, size = 76.dp, plate = Color.White.copy(alpha = 0.92f))
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = mint.onHero,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (scorers.isNotEmpty()) Spacer(Modifier.height(4.dp))
        scorers.take(5).forEach {
            Row(
                Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SportsSoccer, null, tint = mint.onHeroMuted, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = mint.onHero,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Segmented tabs with a springy sliding pill. */
@Composable
private fun DetailTabs(selected: DetailTab, onSelect: (DetailTab) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tabs = DetailTab.entries
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHigh)
            .padding(5.dp)
    ) {
        val slot = maxWidth / tabs.size
        val x by animateDpAsState(
            slot * selected.ordinal,
            spring(dampingRatio = 0.62f, stiffness = 420f),
            label = "tabX"
        )
        Box(
            Modifier
                .offset(x = x)
                .width(slot)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(scheme.primary)
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEach { tab ->
                val isSel = tab == selected
                val color by animateColorAsState(
                    if (isSel) scheme.onPrimary else scheme.onSurfaceVariant,
                    spring(stiffness = 600f), label = "tabColor"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .bouncyClickable(pressedScale = 0.92f) { onSelect(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal,
                        color = color,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TabCard(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .floatingCard(shape = RoundedCornerShape(36.dp))
            .padding(18.dp)
    ) { content() }
}

@Composable
private fun LineupsTab(detail: MatchDetail, onOpenPlayer: (String, String, String?) -> Unit) {
    val home = detail.homeLineup
    val away = detail.awayLineup
    val mint = Mint.colors
    if (home == null && away == null) {
        TabCard {
            EmptyTabNotice(Icons.Default.Person, "Line-ups not out yet", "Teams are usually confirmed about an hour before kick-off.")
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .floatingCard(shape = RoundedCornerShape(36.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            away?.let { FormationStrip(it, mint.away, Modifier.padding(horizontal = 6.dp)) }
            PitchLineup(homeLineup = home, awayLineup = away, onOpenPlayer = { p, team -> onOpenPlayer(p.id, p.name, team) })
            home?.let { FormationStrip(it, mint.home, Modifier.padding(horizontal = 6.dp)) }
        }

        val hasBench = (home?.bench?.isNotEmpty() == true) || (away?.bench?.isNotEmpty() == true)
        if (hasBench) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .floatingCard(shape = RoundedCornerShape(36.dp))
                    .padding(18.dp)
            ) {
                Text("Substitutes", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    home?.let { l -> BenchList(l, mint.home, Modifier.weight(1f)) { p -> onOpenPlayer(p.id, p.name, l.teamName) } }
                    away?.let { l -> BenchList(l, mint.away, Modifier.weight(1f)) { p -> onOpenPlayer(p.id, p.name, l.teamName) } }
                }
            }
        }
    }
}

@Composable
private fun InfoTab(detail: MatchDetail) {
    val match = detail.match
    val tiles = buildList {
        add(Triple(Icons.Default.EmojiEvents, "Competition", match.leagueName))
        add(Triple(Icons.Default.Schedule, "Kick-off", match.kickoffFormatted))
        match.venue?.let { add(Triple(Icons.Default.Stadium, "Venue", it)) }
        detail.referee?.let { add(Triple(Icons.Default.Person, "Referee", it)) }
        add(Triple(Icons.Default.SportsSoccer, "Status", match.statusText))
        add(Triple(Icons.Default.Storage, "Source", "EasySoccerData"))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        detail.insights?.takeIf { !it.isEmpty }?.let { ins ->
            MatchInsightsSection(
                insights = ins,
                homeName = match.homeTeam.shortName,
                awayName = match.awayTeam.shortName,
                homeTeamId = match.homeTeam.id,
                awayTeamId = match.awayTeam.id,
            )
        }
        tiles.chunked(2).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { c, (icon, label, value) ->
                    InfoTile(icon, label, value, Modifier.weight(1f).staggeredEntrance(r * 2 + c))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .floatingCard(shape = RoundedCornerShape(30.dp))
            .padding(16.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyTabNotice(icon: ImageVector, title: String, message: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(24.dp)).background(scheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = scheme.onSurface)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
