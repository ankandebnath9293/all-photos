package com.ankandebnath.allphotos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.BatteryChargingFull
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
    const val VERSION = 4
    const val NAME = "1.2.1"

    val changes = listOf(
        Change(Icons.Rounded.BatteryChargingFull, "Lighter on battery", "The screenshot watcher now only follows the screen closely while a browser is open, and the app no longer re-reads your whole library in the background. Everything works the same."),
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
