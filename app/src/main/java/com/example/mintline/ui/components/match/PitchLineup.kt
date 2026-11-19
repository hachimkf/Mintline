package com.example.mintline.ui.components.match

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mintline.model.LineupPlayer
import com.example.mintline.model.TeamLineup
import com.example.mintline.theme.Mint
import com.example.mintline.ui.components.bouncyClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A player with a position on their own half: x 0..1 (screen left→right), depth 0 (own goal) ..1 (halfway). */
private data class Slot(val player: LineupPlayer, val x: Float, val depth: Float)

private fun sideRank(pos: String): Int {
    val p = pos.uppercase()
    return when {
        p.startsWith("R") -> 0
        p.startsWith("L") -> 2
        else -> 1
    }
}

private fun lineOf(pos: String): Int {
    val p = pos.uppercase()
    return when {
        p == "GK" || p == "G" -> 0
        p.endsWith("B") || p == "D" || p.startsWith("DF") || p == "SW" -> 1
        p.contains("DM") -> 2
        p.contains("AM") || p.endsWith("W") || p == "W" -> 3
        p.contains("M") -> 2
        else -> 4 // ST, CF, F, FW
    }
}

/**
 * Turns a lineup into pitch slots. Uses the formation string when present
 * (e.g. "4-2-3-1"), otherwise groups by position codes.
 */
private fun layoutTeam(lineup: TeamLineup, mirrored: Boolean): List<Slot> {
    val starters = lineup.starters.take(11)
    if (starters.isEmpty()) return emptyList()
    val gk = starters.firstOrNull { lineOf(it.position) == 0 } ?: starters.first()
    val outfield = starters.filter { it !== gk }

    val formationRows = lineup.formation
        ?.split("-", " ")
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.takeIf { it.sum() == outfield.size && it.isNotEmpty() }

    val rows: List<List<LineupPlayer>> = if (formationRows != null) {
        var i = 0
        formationRows.map { n -> outfield.subList(i, i + n).also { i += n } }
    } else {
        outfield.groupBy { lineOf(it.position) }.toSortedMap().values.toList()
    }

    val slots = mutableListOf(Slot(gk, 0.5f, 0.07f))
    val first = 0.27f
    val last = 0.76f
    rows.forEachIndexed { r, row ->
        val depth = if (rows.size == 1) 0.55f else first + (last - first) * r / (rows.size - 1)
        // keep list order but nudge by side hints (R…, centre, L…)
        val ordered = row.withIndex().sortedWith(compareBy({ sideRank(it.value.position) }, { it.index })).map { it.value }
        ordered.forEachIndexed { i, p ->
            // team's right-to-left; home attacks up so team-right is screen-right
            val teamFrac = (i + 1f) / (ordered.size + 1f)
            val x = if (mirrored) teamFrac else 1f - teamFrac
            slots += Slot(p, x, depth)
        }
    }
    return slots
}

private fun surname(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> name
        parts.size == 1 -> parts[0]
        else -> parts.last().let { if (it.length <= 3 && parts.size > 2) parts.takeLast(2).joinToString(" ") else it }
    }
}

/**
 * Full-length vertical pitch with both XIs. Pitch markings draw themselves
 * in, then players spring from the halfway line onto their spots, keeper first.
 * Tap a player to see their card below.
 */
/** Player photo when the source has one (365Scores / API-Football line-ups); ESPN has none. */
fun lineupPhotoUrl(player: LineupPlayer): String? = when {
    player.id.startsWith("s365a_") ->
        "https://imagecache.365scores.com/image/upload/f_png,w_120,h_120,c_limit,q_auto:eco,dpr_2,d_Athletes:default.png/v1/Athletes/${player.id.removePrefix("s365a_")}"
    player.id.startsWith("af_p_") -> "https://media.api-sports.io/football/players/${player.id.removePrefix("af_p_")}.png"
    else -> null
}

/**
 * One team on a full pitch (switch with the Home / Away pills): markings draw
 * themselves in, then players spring onto their spots. Each token shows the
 * player's photo where available — otherwise a silhouette (women's for
 * women's teams) — with a shirt-number pill, a stem and their name.
 */
@Composable
fun PitchLineup(
    homeLineup: TeamLineup?,
    awayLineup: TeamLineup?,
    modifier: Modifier = Modifier,
    women: Boolean = false,
    onOpenPlayer: (LineupPlayer, String) -> Unit = { _, _ -> },
) {
    val mint = Mint.colors
    val reduced = Mint.reducedMotion
    val scheme = MaterialTheme.colorScheme
    var showHome by remember { mutableStateOf(homeLineup != null) }
    val lineup = if (showHome) homeLineup else awayLineup
    val slots = remember(lineup) { lineup?.let { layoutTeam(it, mirrored = false) } ?: emptyList() }
    var selected by remember { mutableStateOf<LineupPlayer?>(null) }
    val isWomen = women || lineup?.teamName?.let { it.endsWith("(W)") || it.contains("Women", true) } == true

    val linesProgress = remember { Animatable(if (reduced) 1f else 0f) }
    val tokenAnims = remember(slots) { List(slots.size) { Animatable(if (reduced) 1f else 0f) } }
    LaunchedEffect(tokenAnims) {
        if (reduced) return@LaunchedEffect
        launch { linesProgress.animateTo(1f, tween(1000)) }
        delay(250)
        tokenAnims.forEachIndexed { n, anim ->
            launch {
                delay(n * 45L)
                anim.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 220f))
            }
        }
    }

    Column(modifier) {
        // Home / Away switch
        if (homeLineup != null && awayLineup != null) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHigh).padding(4.dp)
            ) {
                listOf(true to homeLineup.teamName, false to awayLineup.teamName).forEach { (home, name) ->
                    val sel = home == showHome
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (sel) scheme.onPrimary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                            .background(if (sel) scheme.primary else Color.Transparent)
                            .bouncyClickable(pressedScale = 0.94f) { showHome = home; selected = null }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
        ) {
            val w = maxWidth
            val h = w * 1.42f
            val density = LocalDensity.current
            val wPx = with(density) { w.toPx() }
            val hPx = with(density) { h.toPx() }
            val inset = with(density) { 14.dp.toPx() }

            Box(Modifier.fillMaxWidth().height(h)) {
                PitchCanvas(progress = linesProgress.value, modifier = Modifier.fillMaxSize())
                Text(
                    listOfNotNull(lineup?.teamName, lineup?.formation?.takeIf { it.isNotBlank() }).joinToString("  ·  "),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp)
                )

                val fieldW = wPx - inset * 2
                val fieldH = hPx - inset * 2
                // whole pitch for one team: keeper near the bottom goal, forwards up front
                fun pos(s: Slot) = Offset(inset + s.x * fieldW, hPx - inset - (0.11f + (s.depth - 0.07f) / 0.72f * 0.74f) * fieldH)
                slots.forEachIndexed { i, s ->
                    val target = pos(s)
                    PlayerToken(
                        player = s.player,
                        target = target,
                        from = Offset(target.x, hPx / 2f),
                        progress = tokenAnims.getOrNull(i)?.value ?: 1f,
                        ring = if (showHome) mint.home else mint.away,
                        women = isWomen,
                        selected = selected?.id == s.player.id,
                        onClick = { selected = s.player }
                    )
                }
            }
        }

        AnimatedContent(
            targetState = selected,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() },
            label = "selectedPlayer"
        ) { sel ->
            if (sel != null) {
                val team = lineup?.teamName.orEmpty()
                SelectedPlayerCard(
                    player = sel,
                    teamName = team,
                    color = if (showHome) mint.home else mint.away,
                    onColor = if (showHome) mint.onHome else mint.onAway,
                    modifier = Modifier.padding(top = 12.dp).bouncyClickable(pressedScale = 0.97f) { onOpenPlayer(sel, team) }
                )
            } else {
                Text(
                    "Tap a player for details",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PitchCanvas(progress: Float, modifier: Modifier = Modifier) {
    val mint = Mint.colors
    Canvas(modifier) {
        val inset = 14.dp.toPx()
        val stroke = 2.dp.toPx()
        // grass + mowing stripes
        drawRect(mint.pitchGrass)
        val bands = 14
        val bandH = size.height / bands
        for (b in 0 until bands step 2) {
            drawRect(mint.pitchGrassStripe, Offset(0f, b * bandH), Size(size.width, bandH))
        }
        // soft vignette light from the centre
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                center = center, radius = size.maxDimension * 0.6f
            ),
            radius = size.maxDimension
        )

        val l = inset
        val t = inset
        val r = size.width - inset
        val btm = size.height - inset
        val fw = r - l
        val fh = btm - t
        val cx = size.width / 2f
        val cy = size.height / 2f

        val paths = buildList {
            add(Path().apply { addRect(Rect(l, t, r, btm)) })
            add(Path().apply { moveTo(l, cy); lineTo(r, cy) })
            add(Path().apply { addOval(Rect(Offset(cx, cy), fw * 0.135f)) })
            // penalty + goal areas, both ends
            val boxW = fw * 0.593f
            val boxD = fh * 0.157f
            val gaW = fw * 0.269f
            val gaD = fh * 0.052f
            add(Path().apply { addRect(Rect(cx - boxW / 2, t, cx + boxW / 2, t + boxD)) })
            add(Path().apply { addRect(Rect(cx - boxW / 2, btm - boxD, cx + boxW / 2, btm)) })
            add(Path().apply { addRect(Rect(cx - gaW / 2, t, cx + gaW / 2, t + gaD)) })
            add(Path().apply { addRect(Rect(cx - gaW / 2, btm - gaD, cx + gaW / 2, btm)) })
            // penalty arcs ("the D")
            val spot = fh * 0.105f
            val arcR = fw * 0.135f
            val a = Math.toDegrees(kotlin.math.asin(((boxD - spot) / arcR).coerceIn(0f, 1f).toDouble())).toFloat()
            add(Path().apply {
                arcTo(Rect(Offset(cx, t + spot), arcR), a, 180f - 2 * a, true)
            })
            add(Path().apply {
                arcTo(Rect(Offset(cx, btm - spot), arcR), 180f + a, 180f - 2 * a, true)
            })
            // corner arcs
            val cr = 8.dp.toPx()
            add(Path().apply { arcTo(Rect(Offset(l, t), cr), 0f, 90f, true) })
            add(Path().apply { arcTo(Rect(Offset(r, t), cr), 90f, 90f, true) })
            add(Path().apply { arcTo(Rect(Offset(l, btm), cr), 270f, 90f, true) })
            add(Path().apply { arcTo(Rect(Offset(r, btm), cr), 180f, 90f, true) })
        }

        val measure = PathMeasure()
        val seg = Path()
        paths.forEach { p ->
            if (progress >= 1f) {
                drawPath(p, mint.pitchLine, style = Stroke(stroke, cap = StrokeCap.Round))
            } else {
                measure.setPath(p, false)
                seg.reset()
                measure.getSegment(0f, measure.length * progress, seg, true)
                drawPath(seg, mint.pitchLine, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        // spots
        val dotA = mint.pitchLine.copy(alpha = mint.pitchLine.alpha * progress)
        drawCircle(dotA, 3.dp.toPx(), Offset(cx, cy))
        drawCircle(dotA, 2.5.dp.toPx(), Offset(cx, t + fh * 0.105f))
        drawCircle(dotA, 2.5.dp.toPx(), Offset(cx, btm - fh * 0.105f))
        // goals
        val goalW = fw * 0.108f
        drawRoundRect(
            dotA, Offset(cx - goalW / 2, t - 6.dp.toPx()), Size(goalW, 6.dp.toPx()),
            androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(stroke)
        )
        drawRoundRect(
            dotA, Offset(cx - goalW / 2, btm), Size(goalW, 6.dp.toPx()),
            androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(stroke)
        )
    }
}

@Composable
private fun PlayerToken(
    player: LineupPlayer,
    target: Offset,
    from: Offset,
    progress: Float,
    ring: Color,
    women: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokenSize = 40.dp
    val tokenPx = with(LocalDensity.current) { tokenSize.toPx() }
    val selScale by animateFloatAsState(
        if (selected) 1.15f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "sel"
    )
    val p = progress
    val x = from.x + (target.x - from.x) * p
    val y = from.y + (target.y - from.y) * p
    val photo = remember(player.id) { lineupPhotoUrl(player) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place((x - placeable.width / 2f).roundToInt(), (y - tokenPx / 2f).roundToInt())
                }
            }
            .graphicsLayer {
                alpha = p.coerceIn(0f, 1f)
                val s = (0.4f + 0.6f * p) * selScale
                scaleX = s; scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.2f)
            }
            .bouncyClickable(pressedScale = 0.85f, onClick = onClick)
    ) {
        Box {
            Box(
                Modifier
                    .size(tokenSize)
                    .clip(CircleShape)
                    .background(Color(0xFFE9ECE3))
                    .border(2.dp, if (selected) ring else Color.White, CircleShape),
                contentAlignment = Alignment.BottomCenter
            ) {
                // silhouette underneath; the photo (if any) loads on top of it
                Icon(
                    painterResource(if (women) com.example.mintline.R.drawable.ic_player_female else com.example.mintline.R.drawable.ic_player_male),
                    contentDescription = null,
                    tint = ring.copy(alpha = 0.85f),
                    modifier = Modifier.size(tokenSize * 0.86f)
                )
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = player.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
            // shirt number pill (top-left), captain badge (top-right)
            Text(
                player.jersey.ifBlank { "–" },
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-8).dp, y = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xE6101010))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
            if (player.isCaptain) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(13.dp)
                        .clip(CircleShape).background(Mint.colors.gold).border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("C", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A2A00))
                }
            }
        }
        // stem + dot like a pin on the grass
        Box(Modifier.width(1.5.dp).height(7.dp).background(Color.White.copy(alpha = 0.8f)))
        Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.height(2.dp))
        Text(
            surname(player.name),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 64.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun SelectedPlayerCard(
    player: LineupPlayer,
    teamName: String,
    color: Color,
    onColor: Color,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(player.jersey, color = onColor, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = scheme.onSurface)
            Text(
                listOfNotNull(teamName.ifBlank { null }, if (player.isCaptain) "Captain" else null, "Tap for profile ›").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        Text(
            player.position,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSecondaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(scheme.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** Team strip shown above/below the pitch. */
@Composable
fun FormationStrip(
    lineup: TeamLineup,
    color: Color,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                lineup.teamName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            lineup.coach?.let {
                Text("Coach · $it", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        lineup.formation?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHighest)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/** Bench list for one team. */
@Composable
fun BenchList(
    lineup: TeamLineup,
    color: Color,
    modifier: Modifier = Modifier,
    onOpen: (LineupPlayer) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            lineup.teamName,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        lineup.bench.forEach { p ->
            Row(Modifier.bouncyClickable(pressedScale = 0.96f) { onOpen(p) }, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(p.jersey, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (Mint.colors.isDark) color else scheme.onSurface)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        surname(p.name),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(p.position, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}
