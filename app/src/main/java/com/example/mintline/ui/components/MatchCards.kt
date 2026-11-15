package com.example.mintline.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mintline.model.Match
import com.example.mintline.model.MatchStatus
import com.example.mintline.theme.Mint

/** Best-effort match minute for progress visuals. */
fun Match.liveMinute(): Int? = minute ?: Regex("""(\d{1,3})""").find(statusText)?.value?.toIntOrNull()
    ?.takeIf { isLive }
    ?: if (status == MatchStatus.HALFTIME) 45 else null

/**
 * Featured live match: big floating lime card with faint pitch markings,
 * an animated score and a match-progress track with a rolling ball.
 */
@Composable
fun HeroLiveMatchCard(
    match: Match,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mint = Mint.colors
    val favScale by animateFloatAsState(
        targetValue = if (match.isFavorite) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "favScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .floatingCard(shape = RoundedCornerShape(36.dp), color = mint.heroEnd, elevation = 16.dp)
            .background(Brush.linearGradient(listOf(mint.heroStart, mint.heroEnd)))
            .bouncyClickable(pressedScale = 0.98f, onClick = onClick)
    ) {
        // Faint pitch markings in the background
        Canvas(Modifier.matchParentSize()) {
            val line = mint.onHero.copy(alpha = 0.07f)
            val stroke = Stroke(1.5.dp.toPx())
            val cx = size.width / 2f
            val cy = size.height * 0.46f
            drawLine(line, Offset(cx, 0f), Offset(cx, size.height), 1.5.dp.toPx())
            drawCircle(line, radius = size.height * 0.30f, center = Offset(cx, cy), style = stroke)
            drawCircle(line, radius = 3.dp.toPx(), center = Offset(cx, cy))
            val boxH = size.height * 0.62f
            drawRect(line, Offset(-1f, cy - boxH / 2f), Size(size.width * 0.13f, boxH), style = stroke)
            drawRect(
                line, Offset(size.width - size.width * 0.13f + 1f, cy - boxH / 2f),
                Size(size.width * 0.13f, boxH), style = stroke
            )
        }

        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = match.leagueName,
                    style = MaterialTheme.typography.labelLarge,
                    color = mint.onHero,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(50))
                        .background(mint.onHero.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(Modifier.weight(1f))
                if (match.isLive) HeroLiveTag() else Text(
                    match.statusText.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = mint.onHeroMuted
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(mint.onHero.copy(alpha = 0.08f))
                        .bouncyClickable(pressedScale = 0.85f, onClick = onToggleFavorite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (match.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = mint.onHero,
                        modifier = Modifier.size(20.dp).scale(favScale)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroTeam(match.homeTeam.shortName, match.homeTeam.crestUrl, match.homeTeam.name, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.1f)) {
                    if (match.homeScore != null && match.awayScore != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedScore(match.homeScore, mint.onHero)
                            Text(
                                ":",
                                style = MaterialTheme.typography.displaySmall,
                                color = mint.onHeroMuted,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            AnimatedScore(match.awayScore, mint.onHero)
                        }
                    } else {
                        Text(
                            match.kickoffFormatted,
                            style = MaterialTheme.typography.headlineMedium,
                            color = mint.onHero,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = if (match.statusText != match.kickoffFormatted) match.statusText else "Upcoming",
                        style = MaterialTheme.typography.labelMedium,
                        color = mint.onHero,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(mint.onHero.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                HeroTeam(match.awayTeam.shortName, match.awayTeam.crestUrl, match.awayTeam.name, Modifier.weight(1f))
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
        }
    }
}

@Composable
private fun HeroTeam(shortName: String, crest: String?, fullName: String, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        TeamCrest(crestUrl = crest, teamName = fullName, size = 64.dp, plate = Color.White.copy(alpha = 0.92f))
        Spacer(Modifier.height(8.dp))
        Text(
            text = shortName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = Mint.colors.onHero,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AnimatedScore(score: Int, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = score,
        transitionSpec = {
            (slideInVertically(spring(dampingRatio = 0.6f, stiffness = 300f)) { -it } + fadeIn()) togetherWith
                (slideOutVertically { it } + fadeOut())
        },
        label = "score",
        modifier = modifier
    ) { s ->
        Text("$s", style = MaterialTheme.typography.displayMedium, color = color)
    }
}

/** 0'–90' track with an HT notch and a ball that rolls to the current minute. */
@Composable
fun MatchProgressTrack(
    minute: Int,
    trackColor: Color,
    fillColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    val reduced = Mint.reducedMotion
    val target = (minute.coerceIn(0, 90) / 90f)
    val progress = remember { Animatable(if (reduced) target else 0f) }
    LaunchedEffect(target) {
        progress.animateTo(target, if (reduced) tween(0) else spring(dampingRatio = 0.8f, stiffness = 40f))
    }
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
            val w = maxWidth
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(trackColor)
            )
            Box(
                Modifier
                    .width(w * progress.value)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(fillColor)
            )
            // half-time notch
            Box(
                Modifier
                    .offset(x = w / 2 - 1.dp)
                    .width(2.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(labelColor.copy(alpha = 0.5f))
            )
            val ballSize = 22.dp
            val px = with(LocalDensity.current) { (w - ballSize).toPx() }
            Box(
                Modifier
                    .offset(x = (w - ballSize) * progress.value)
                    .size(ballSize)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SportsSoccer, null,
                    tint = Color(0xFF1A1C16),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(1.dp)
                        .rotate(progress.value * px / 4f)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("0'", style = MaterialTheme.typography.labelSmall, color = labelColor)
            Spacer(Modifier.weight(1f))
            Text("HT", style = MaterialTheme.typography.labelSmall, color = labelColor)
            Spacer(Modifier.weight(1f))
            Text("90'", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

/**
 * Floating fixture card in the spirit of the references: crest + name on
 * each side, a centred score / kickoff capsule, generous 30dp corners.
 */
@Composable
fun StandardMatchCard(
    match: Match,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    val favScale by animateFloatAsState(
        targetValue = if (match.isFavorite) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "favScaleStandard"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .floatingCard(shape = RoundedCornerShape(30.dp))
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardTeam(match.homeTeam.shortName, match.homeTeam.crestUrl, match.homeTeam.name, match.homeTeam.form, Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1.15f)
            ) {
                when {
                    match.homeScore != null && match.awayScore != null -> {
                        val winnerHome = match.status.isFinished && match.homeScore > match.awayScore
                        val winnerAway = match.status.isFinished && match.awayScore > match.homeScore
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (match.isLive) mint.live.copy(alpha = 0.12f) else scheme.surfaceContainerHigh)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${match.homeScore}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (winnerAway) scheme.onSurfaceVariant else scheme.onSurface
                            )
                            Text(
                                " – ",
                                style = MaterialTheme.typography.titleLarge,
                                color = scheme.onSurfaceVariant
                            )
                            Text(
                                "${match.awayScore}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (winnerHome) scheme.onSurfaceVariant else scheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        if (match.isLive) {
                            LivePillBadge(statusText = match.statusText, isLive = true)
                        } else {
                            Text(
                                if (match.status == MatchStatus.FULL_TIME) "Full time" else match.statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        Text(
                            match.kickoffFormatted,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(scheme.surfaceContainerHigh)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (match.statusText != match.kickoffFormatted) match.statusText else "Upcoming",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            CardTeam(match.awayTeam.shortName, match.awayTeam.crestUrl, match.awayTeam.name, match.awayTeam.form, Modifier.weight(1f))
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-8).dp)
                .size(32.dp)
                .clip(CircleShape)
                .bouncyClickable(pressedScale = 0.8f, onClick = onToggleFavorite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (match.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "Favorite",
                tint = if (match.isFavorite) mint.gold else scheme.outline,
                modifier = Modifier.size(18.dp).scale(favScale)
            )
        }
    }
}

@Composable
private fun CardTeam(shortName: String, crest: String?, name: String, form: String?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        TeamCrest(crestUrl = crest, teamName = name, size = 48.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            shortName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (!form.isNullOrBlank()) {
            FormDots(form, Modifier.padding(top = 4.dp))
        }
    }
}

/** W/D/L form as tiny coloured dots. */
@Composable
fun FormDots(form: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        form.filter { it.isLetter() }.take(5).forEach { c ->
            val color = when (c.uppercaseChar()) {
                'W' -> scheme.primary
                'L' -> mint.live
                else -> scheme.outline
            }
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        }
    }
}
