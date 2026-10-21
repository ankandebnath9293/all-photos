package com.ankandebnath.allphotos.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ankandebnath.allphotos.capture.Apps
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.media.Details
import com.ankandebnath.allphotos.media.DetailsReader

/**
 * Details in two levels: the key facts (resolution, size, frame rate) first, and everything else
 * (codec, bitrate, camera settings, file path) behind "Show more".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsSheet(m: Media, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var more by rememberSaveable(m.id) { mutableStateOf(false) }
    val details by produceState<Details?>(null, m.id, m.modified) {
        val src = Db.get(ctx).sources.value[m.id]
        val label = src?.pkg?.let { Apps.label(ctx, it) }
        value = DetailsReader.read(ctx.applicationContext, m, src, label)
    }
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            Text(m.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
            Gap(14.dp)
            val d = details
            if (d == null) {
                Loading()
                return@Column
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.primary.forEach { f ->
                    Column(
                        Modifier.widthIn(min = 100.dp).clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh)
                            .combinedClickable(onClick = {}, onLongClick = { copy(ctx, f.value) })
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(f.label, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                        Text(f.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Gap(14.dp)
            FilledTonalButton(onClick = { more = !more }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(if (more) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                GapW(6.dp)
                Text(if (more) "Show less" else "Show more")
            }
            AnimatedVisibility(more, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 8.dp)) {
                    d.more.forEach { g ->
                        SectionTitle(g.title)
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(vertical = 4.dp)) {
                            g.facts.forEach { f ->
                                Row(
                                    Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { copy(ctx, f.value) })
                                        .padding(horizontal = 16.dp, vertical = 9.dp),
                                ) {
                                    Text(f.label, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.weight(0.4f))
                                    Text(f.value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                                }
                            }
                        }
                    }
                    Gap(8.dp)
                    Text("Hold any value to copy it.", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                }
            }
        }
    }
}

private fun copy(ctx: android.content.Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("detail", text))
}
