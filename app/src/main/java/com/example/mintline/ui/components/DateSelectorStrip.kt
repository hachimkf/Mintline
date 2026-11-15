package com.example.mintline.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.mintline.model.DateItem
import com.example.mintline.theme.Mint
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

private val PillWidth = 56.dp
private val PillHeight = 74.dp
private val PillGap = 10.dp

/**
 * Curved day picker: capsules ride along an arc that dips away from the
 * centre, the selected day is a glossy pill, past days carry a tick.
 * Swipe to browse — it snaps a day to the centre.
 */
@Composable
fun DateSelectorStrip(
    dates: List<DateItem>,
    onDateSelected: (DateItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val selectedIndex = dates.indexOfFirst { it.isSelected }.coerceAtLeast(0)
    val todayIndex = dates.indexOfFirst { it.isToday }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fling = rememberSnapFlingBehavior(state)
    val reduced = Mint.reducedMotion

    LaunchedEffect(selectedIndex) {
        if (reduced) state.scrollToItem(selectedIndex) else state.animateScrollToItem(selectedIndex)
    }

    val monthLabel = remember(dates.getOrNull(selectedIndex)?.dateString) {
        dates.getOrNull(selectedIndex)?.dateString?.let {
            runCatching {
                val d = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(it)!!
                SimpleDateFormat("MMMM d", Locale.ENGLISH).format(d)
            }.getOrNull()
        } ?: ""
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selectedIndex == todayIndex) "Today's games" else "Games on this day",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHigh)
                    .bouncyClickable(pressedScale = 0.93f) {
                        dates.getOrNull(todayIndex)?.let(onDateSelected)
                    }
                    .padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(monthLabel, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                Icon(Icons.Default.ExpandMore, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().height(158.dp)) {
            val density = LocalDensity.current
            val side = (maxWidth - PillWidth) / 2
            val halfViewportPx = with(density) { maxWidth.toPx() } / 2f
            val dipPx = with(density) { 34.dp.toPx() }

            LazyRow(
                state = state,
                flingBehavior = fling,
                contentPadding = PaddingValues(horizontal = side),
                horizontalArrangement = Arrangement.spacedBy(PillGap),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                itemsIndexed(dates, key = { _, d -> d.dateString }) { index, item ->
                    DayPill(
                        item = item,
                        isPast = todayIndex >= 0 && index < todayIndex,
                        onClick = { onDateSelected(item) },
                        modifier = Modifier.graphicsLayer {
                            // position on the arc, read in the draw phase only
                            val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            if (info != null) {
                                val center = info.offset + info.size / 2f - state.layoutInfo.viewportStartOffset
                                val viewportCenter = (state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset) / 2f
                                val d = ((center - viewportCenter) / halfViewportPx).coerceIn(-1.3f, 1.3f)
                                translationY = d * d * dipPx
                                rotationZ = d * 10f
                                alpha = 1f - 0.45f * abs(d).coerceAtMost(1f)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayPill(item: DateItem, isPast: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val mint = Mint.colors
    val selected = item.isSelected
    // Light: glossy ink pill like the reference. Dark: glossy lime.
    val selColor = if (mint.isDark) scheme.primary else Color(0xFF1A1C16)
    val onSel = if (mint.isDark) scheme.onPrimary else Color.White
    val restColor = if (mint.isDark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest

    val bg by animateColorAsState(if (selected) selColor else restColor, spring(stiffness = 500f), label = "pillBg")
    val fg by animateColorAsState(if (selected) onSel else scheme.onSurface, spring(stiffness = 500f), label = "pillFg")
    val lift by animateFloatAsState(
        if (selected && !Mint.reducedMotion) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 380f), label = "lift"
    )

    Column(
        modifier.width(PillWidth).bouncyClickable(pressedScale = 0.9f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (item.isToday) "Today" else item.dayOfWeek,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Medium else null,
            color = if (selected || item.isToday) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .graphicsLayer {
                    val s = 1f + 0.06f * lift
                    scaleX = s; scaleY = s
                    translationY = -4.dp.toPx() * lift
                }
                .shadow(
                    elevation = if (mint.isDark) 0.dp else if (selected) 14.dp else 6.dp,
                    shape = RoundedCornerShape(50),
                    ambientColor = Color(0x33283018),
                    spotColor = Color(0x44283018)
                )
                .clip(RoundedCornerShape(50))
                .background(bg)
                .drawBehind {
                    if (selected) {
                        // glossy highlight + soft bottom reflection
                        drawRoundRect(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.32f),
                                0.45f to Color.White.copy(alpha = 0.04f),
                                1f to Color.Transparent
                            ),
                            cornerRadius = CornerRadius(size.width / 2)
                        )
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(size.width * 0.5f, size.height * 0.95f),
                                radius = size.width * 0.6f
                            ),
                            radius = size.width * 0.6f,
                            center = Offset(size.width * 0.5f, size.height * 0.95f)
                        )
                    }
                }
                .width(PillWidth)
                .height(PillHeight),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.dayOfMonth, style = MaterialTheme.typography.titleLarge, color = fg)
                if (isPast) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (selected) onSel.copy(alpha = 0.25f) else scheme.onSurfaceVariant.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = if (selected) onSel else restColor, modifier = Modifier.size(12.dp))
                    }
                } else if (item.isToday) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(mint.live))
                }
            }
        }
    }
}
