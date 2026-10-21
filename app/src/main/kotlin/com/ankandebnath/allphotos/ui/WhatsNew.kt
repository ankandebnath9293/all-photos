package com.ankandebnath.allphotos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
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
    const val VERSION = 1
    const val NAME = "1.0"

    val changes = listOf(
        Change(Icons.Rounded.TravelExplore, "Screenshots know where they came from", "The app and website are saved with each screenshot, and the Screenshots tab groups them for you."),
        Change(Icons.Rounded.AutoDelete, "Clean-up that runs by itself", "Old screenshots and WhatsApp or Telegram media go to the trash after 30 days, unless marked important."),
        Change(Icons.Rounded.ImageSearch, "Search inside photos", "Find a photo by the words in it or what's in it. Everything is read on the phone."),
        Change(Icons.Rounded.CameraAlt, "Save the exact frame", "Pause a video, step frame by frame and save the frame you need in full quality."),
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
