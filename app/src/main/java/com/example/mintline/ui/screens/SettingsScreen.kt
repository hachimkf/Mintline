package com.example.mintline.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mintline.theme.Mint
import com.example.mintline.theme.ThemeMode
import com.example.mintline.ui.components.MintlineLogo
import com.example.mintline.ui.components.SectionHeader
import com.example.mintline.ui.components.bouncyClickable
import com.example.mintline.ui.components.floatingCard

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    refreshIntervalSec: Int,
    onSelectRefreshInterval: (Int) -> Unit,
    reducedMotion: Boolean,
    onToggleReducedMotion: (Boolean) -> Unit,
    lastUpdatedText: String,
    onClearFavorites: () -> Unit,
    modifier: Modifier = Modifier,
    onEditFavorites: () -> Unit = {},
    matchNotificationsEnabled: Boolean = true,
    onToggleMatchNotifications: (Boolean) -> Unit = {},
    timeZoneLabel: String = "",
    isCustomTimeZone: Boolean = false,
    onCycleTimeZone: () -> Unit = {},
    onResetTimeZone: () -> Unit = {},
    appVersion: String = "",
    updateLabel: String? = null,
    onCheckUpdates: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenTitle("Settings", "Make Mintline yours") }

        item { SectionHeader("Appearance") }
        item {
            Column(
                Modifier.fillMaxWidth().floatingCard().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SlidingSegmented(
                    options = listOf(
                        Triple(ThemeMode.SYSTEM, "System", Icons.Default.PhoneAndroid),
                        Triple(ThemeMode.LIGHT, "Light", Icons.Default.LightMode),
                        Triple(ThemeMode.DARK, "Dark", Icons.Default.DarkMode),
                    ),
                    selected = themeMode,
                    onSelect = onSelectThemeMode
                )
                Text(
                    when (themeMode) {
                        ThemeMode.SYSTEM -> "Follows your phone's light or dark setting."
                        ThemeMode.LIGHT -> "Warm, daylight-friendly colours."
                        ThemeMode.DARK -> "Easy on the eyes for night games."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        item {
            ToggleRow(
                icon = Icons.Default.Animation,
                title = "Reduce motion",
                subtitle = "Calmer transitions, no bouncing or pulsing",
                checked = reducedMotion,
                onChange = onToggleReducedMotion
            )
        }

        item { SectionHeader("Live updates", Modifier.padding(top = 8.dp)) }
        item {
            Column(Modifier.fillMaxWidth().floatingCard().padding(16.dp)) {
                SlidingSegmented(
                    options = listOf(
                        Triple(15, "15s", null),
                        Triple(30, "30s", null),
                        Triple(60, "60s", null),
                        Triple(0, "Manual", null),
                    ),
                    selected = refreshIntervalSec,
                    onSelect = onSelectRefreshInterval
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Last synced $lastUpdatedText",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .floatingCard()
                    .bouncyClickable(onClick = onCycleTimeZone)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.Default.Schedule, scheme.primaryContainer, scheme.onPrimaryContainer)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Match time zone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = scheme.onSurface)
                    Text(
                        if (timeZoneLabel.isNotBlank()) {
                            if (isCustomTimeZone) timeZoneLabel else "$timeZoneLabel (Device)"
                        } else "Device time",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.primary
                    )
                }
                Text("Change", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            }
        }
        item {
            ToggleRow(
                icon = Icons.Default.Notifications,
                title = "Match reminders",
                subtitle = "Kickoff alerts and favorite team updates",
                checked = matchNotificationsEnabled,
                onChange = onToggleMatchNotifications
            )
        }

        item { SectionHeader("Your data", Modifier.padding(top = 8.dp)) }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .floatingCard()
                    .bouncyClickable(onClick = onEditFavorites)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.Default.Favorite, scheme.primaryContainer, scheme.onPrimaryContainer)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Edit favourites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = scheme.onSurface)
                    Text("Competitions, clubs and nations pinned to the top", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .floatingCard()
                    .bouncyClickable(onClick = onClearFavorites)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.Default.Delete, Mint.colors.live.copy(alpha = 0.15f), Mint.colors.live)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Clear saved items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = scheme.onSurface)
                    Text("Removes all starred matches and competitions", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }

        item { SectionHeader("About", Modifier.padding(top = 8.dp)) }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .floatingCard()
                    .bouncyClickable(onClick = onCheckUpdates)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MintlineLogo(size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mintline $appVersion", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    Text(
                        updateLabel ?: "Tap to check for updates",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.SystemUpdate, null, tint = scheme.onSurfaceVariant)
            }
        }

        item {
            Text(
                "Mintline · data inspired by the open-source EasySoccerData library",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 6.dp)
            )
        }
    }
}

/** Pill segmented control with a springy sliding selection. */
@Composable
private fun <T> SlidingSegmented(
    options: List<Triple<T, String, ImageVector?>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHigh)
            .padding(5.dp)
    ) {
        val slot = maxWidth / options.size
        val x by animateDpAsState(slot * index, spring(dampingRatio = 0.6f, stiffness = 420f), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .width(slot)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(scheme.primary)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { (value, label, icon) ->
                val sel = value == selected
                val fg by animateColorAsState(if (sel) scheme.onPrimary else scheme.onSurfaceVariant, label = "segFg")
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .bouncyClickable(pressedScale = 0.92f) { onSelect(value) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                        color = fg
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .floatingCard()
            .bouncyClickable(pressedScale = 0.98f) { onChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, scheme.secondaryContainer, scheme.onSecondaryContainer)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = scheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            thumbContent = if (checked) {
                { Icon(Icons.Default.Check, null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else null
        )
    }
}

@Composable
private fun IconBadge(icon: ImageVector, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
    }
}
