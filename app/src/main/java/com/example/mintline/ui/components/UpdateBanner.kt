package com.example.mintline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mintline.data.UpdateInfo
import com.example.mintline.ui.components.glass.liquidGlass

/** Floating glass card announcing a new GitHub release, with download progress. */
@Composable
fun UpdateBanner(
    info: UpdateInfo,
    progress: Float?,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(30.dp), elevation = 14.dp)
            .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MintlineLogo(size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mintline ${info.version} is here", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text(
                    info.notes.lineSequence().firstOrNull { it.isNotBlank() }?.removePrefix("- ")
                        ?: "Tap update to download and install",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (progress == null) {
                Text(
                    "Update",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.primary)
                        .bouncyClickable(pressedScale = 0.92f, onClick = onUpdate)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
                Box(
                    Modifier.padding(start = 4.dp).size(32.dp).clip(CircleShape).bouncyClickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Later", tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            } else {
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
            }
        }
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            )
        }
    }
}
