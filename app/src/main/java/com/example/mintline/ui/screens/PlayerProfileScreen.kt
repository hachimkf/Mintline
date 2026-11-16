package com.example.mintline.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mintline.model.PlayerProfile
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.LoadingStateView
import com.example.mintline.ui.components.TeamCrest
import com.example.mintline.ui.components.bouncyClickable
import com.example.mintline.ui.components.floatingCard
import com.example.mintline.ui.components.glass.LocalGlassBackdrop
import com.example.mintline.ui.components.glass.edgeBlur
import com.example.mintline.ui.components.glass.glassSource
import com.example.mintline.ui.components.glass.liquidGlass
import com.example.mintline.ui.components.glass.rememberGlassBackdrop
import com.example.mintline.ui.components.staggeredEntrance

@Composable
fun PlayerProfileScreen(
    profile: PlayerProfile?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when {
                    profile != null -> ProfileContent(profile)
                    loading -> LoadingStateView(message = "Finding the player…")
                    else -> Text(
                        error ?: "Player not found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(40.dp)
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(top + 76.dp).edgeBlur(fromTop = true, scrimColor = scheme.background, scrimAlpha = 0.8f))
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(p: PlayerProfile) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors

    // Hero
    Column(
        Modifier
            .fillMaxWidth()
            .floatingCard(shape = RoundedCornerShape(40.dp), color = mint.heroEnd, elevation = 16.dp)
            .background(Brush.linearGradient(listOf(mint.heroStart, mint.heroEnd)))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(p.photoUrl).crossfade(true).build(),
            contentDescription = p.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(128.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f))
        )
        Spacer(Modifier.height(12.dp))
        Text(p.name, style = MaterialTheme.typography.headlineMedium, color = mint.onHero, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(mint.onHero.copy(alpha = 0.08f)).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamCrest(crestUrl = p.clubLogo, teamName = p.clubName ?: "", size = 26.dp, plate = Color.White.copy(alpha = 0.92f))
            Spacer(Modifier.width(8.dp))
            Text(p.clubName ?: "Free agent", style = MaterialTheme.typography.labelLarge, color = mint.onHero)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            listOfNotNull(p.position, p.jersey?.let { "#$it" }, p.nationality).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = mint.onHeroMuted
        )
    }

    // Key facts
    val facts = listOfNotNull(
        p.age?.let { Triple(Icons.Rounded.Cake, "Age", "$it" + (p.birthDate?.let { b -> "  ($b)" } ?: "")) },
        p.height?.let { Triple(Icons.Rounded.Height, "Height", it) },
        p.foot?.let { Triple(Icons.Rounded.SportsSoccer, "Foot", it) },
        p.contractUntil?.let { Triple(Icons.Rounded.HistoryEdu, "Contract until", it) },
    )
    facts.chunked(2).forEachIndexed { r, row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEachIndexed { c, (icon, label, value) -> FactTile(icon, label, value, Modifier.weight(1f).staggeredEntrance(r * 2 + c)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    p.bio?.let {
        Text(
            it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().floatingCard(shape = RoundedCornerShape(28.dp)).padding(16.dp)
        )
    }

    // Season stats
    if (p.seasonTables.isNotEmpty()) {
        SectionCard("Season ${p.seasonName ?: ""}".trim()) {
            p.seasonTables.forEach { (title, rows) ->
                Text(title, style = MaterialTheme.typography.labelLarge, color = scheme.primary, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                StatRow("Competition", "Apps", "G", "A", "YC", "RC", header = true)
                rows.forEach { StatRow(it.competition, it.appearances, it.goals, it.assists, it.yellow, it.red) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    // Recent matches with rating
    if (p.recentMatches.isNotEmpty()) {
        SectionCard("Recent matches") {
            p.recentMatches.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp))
                        .background(scheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeamCrest(crestUrl = m.homeLogo, teamName = m.homeName, size = 24.dp)
                    Text("  ${m.homeScore} – ${m.awayScore}  ", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                    TeamCrest(crestUrl = m.awayLogo, teamName = m.awayName, size = 24.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${m.homeName} v ${m.awayName}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(m.competition.takeIf { it.isNotBlank() }, m.minutes?.let { "$it'" }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    m.rating?.let { r ->
                        val c = m.ratingColor?.removePrefix("#")?.takeIf { it.length == 6 }?.let { Color(("FF$it").toLong(16)) } ?: scheme.primary
                        Text(
                            r, style = MaterialTheme.typography.labelLarge, color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c).padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }

    // Transfer history
    if (p.transfers.isNotEmpty()) {
        SectionCard("Transfer history") {
            p.transfers.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamCrest(crestUrl = t.clubLogo, teamName = t.clubName, size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.clubName, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                        Text(
                            listOfNotNull(t.dateIso.take(10).takeIf { it.length == 10 }, t.type.takeIf { it.isNotBlank() }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
                        )
                    }
                    t.fee?.let {
                        Text(
                            it, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(scheme.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                if (i < p.transfers.lastIndex) Box(Modifier.padding(start = 16.dp).width(2.dp).height(10.dp).background(scheme.outlineVariant))
            }
        }
    }

    // Trophies
    if (p.trophies.isNotEmpty()) {
        SectionCard("Trophies") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.trophies.forEach { t ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.EmojiEvents, null, tint = mint.gold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${t.name}  ×${t.count}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().floatingCard(shape = RoundedCornerShape(32.dp)).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun FactTile(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.floatingCard(shape = RoundedCornerShape(26.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(14.dp)).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 2)
        }
    }
}

@Composable
private fun StatRow(comp: String, apps: String, g: String, a: String, yc: String, rc: String, header: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val color = if (header) scheme.onSurfaceVariant else scheme.onSurface
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(comp, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        listOf(apps, g, a, yc, rc).forEachIndexed { i, v ->
            Text(
                v, style = style, color = color, textAlign = TextAlign.Center,
                fontWeight = if (!header && i == 1) FontWeight.Medium else null,
                modifier = Modifier.width(38.dp)
            )
        }
    }
}
