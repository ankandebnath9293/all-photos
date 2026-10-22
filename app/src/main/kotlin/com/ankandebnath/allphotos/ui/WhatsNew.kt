package com.ankandebnath.allphotos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class Change(val icon: ImageVector, val title: String, val detail: String)

/** What changed in each version. Shown once after an update. */
object WhatsNew {
    const val VERSION = 3
    const val NAME = "1.2"

    val changes = listOf(
        Change(Icons.Rounded.HourglassBottom, "Countdown on screenshots", "Open a screenshot (or a chat file) to see how many days are left before it moves to the trash. Tap it to keep the file."),
        Change(Icons.Rounded.Animation, "Pages slide in", "Settings and every other page slide in from the side over the one you were on, the way Google Photos does."),
        Change(Icons.Rounded.Search, "Search at the top", "Search sits next to Settings at the top of every tab, and the tabs at the bottom stay still with their names showing."),
        Change(Icons.Rounded.PlayCircle, "Better videos", "Tap a video to show or hide its controls, and swipe down to go back to the grid, just like photos."),
        Change(Icons.Rounded.FolderZip, "Preview large files", "In Large files, tap a thumbnail to play or view it before you pick it."),
    )
}

@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's new in All Photos ${WhatsNew.NAME}") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                WhatsNew.changes.forEach { ch ->
                    Row(verticalAlignment = Alignment.Top) {
                        IconBadge(ch.icon, MaterialShapes.Cookie6Sided.toShape(), c.primaryContainer, c.onPrimaryContainer, 36.dp)
                        GapW(12.dp)
                        Column(Modifier.weight(1f)) {
                            Text(ch.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(ch.detail, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Nice") } },
    )
}
