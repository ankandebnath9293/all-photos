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
    const val VERSION = 2
    const val NAME = "1.1"

    val changes = listOf(
        Change(Icons.Rounded.AutoDelete, "Clean-up starts from install day", "Screenshots and chat files from before you installed All Photos are never cleaned up. Older files that were moved to the trash are back."),
        Change(Icons.Rounded.Animation, "Smoother everywhere", "Photos grow out of the grid and shrink back when you swipe down. Pages scale with the back gesture. Pick Minimal, Standard or Rich in Settings."),
        Change(Icons.Rounded.TextFields, "Copy text from photos", "Tap Text in the viewer, then tap any line to copy it, call a number or open a link. Works offline."),
        Change(Icons.Rounded.Description, "Documents", "Receipts, payment cards, IDs, tickets, notes, books and menus are sorted for you in Albums."),
        Change(Icons.Rounded.AutoAwesome, "New filters", "Vivid, Dramatic and black-and-white filters inspired by the iPhone, more adjustments, and filters for videos."),
        Change(Icons.Rounded.CleaningServices, "Tidier viewer and video player", "Fewer buttons, the star and source in the top bar, smooth scrubbing, and stacks for similar photos."),
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
