package com.ankandebnath.allphotos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.Library
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.Prefs
import com.ankandebnath.allphotos.logic.Chat
import com.ankandebnath.allphotos.logic.Fmt
import com.ankandebnath.allphotos.work.Cleaner
import com.ankandebnath.allphotos.work.SimilarData
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private class Slice(val label: String, val bytes: Long, val color: Color)

@Composable
fun CleanupTab(lib: Library, app: AppState, actions: Actions, state: LazyListState, bottom: Dp) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val s by prefs.settings.collectAsState()
    val important by remember { Db.get(ctx).important }.collectAsState()
    val tick by MediaRepo.trashTick.collectAsState()
    val plan = remember(lib, s, important) { Cleaner.plan(lib, s, important) }
    val soon = remember(lib, s, important) { Cleaner.upcoming(lib, s, important, 7) }
    val trash by produceState(emptyList<com.ankandebnath.allphotos.data.Media>(), tick) { value = MediaRepo.trashed(ctx) }
    val similar by produceState<SimilarData.Summary?>(null, lib) { value = SimilarData.compute(ctx.applicationContext, lib) }
    val c = MaterialTheme.colorScheme
    val big = remember(lib) { lib.items.filter { it.size >= 100L * 1024 * 1024 } }
    val wa = remember(lib) { lib.items.filter { it.chat == Chat.WHATSAPP } }
    val tg = remember(lib) { lib.items.filter { it.chat == Chat.TELEGRAM } }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                "Cleanup", "${Fmt.size(lib.totalBytes)} of photos and videos",
                trailing = { SmallIconButton(Icons.Rounded.Settings, "Settings") { app.open(Dest.Settings) } },
            )
        }
        item { StorageCard(lib, wa.sumOf { it.size }, tg.sumOf { it.size }) }

        item { SectionTitle("Free up space") }
        item {
            val sm = similar
            ToolCard(
                Icons.Rounded.AutoAwesomeMosaic, MaterialShapes.Clover4Leaf.toShape(), c.primaryContainer, c.onPrimaryContainer,
                "Similar photos",
                when {
                    sm == null -> "Looking…"
                    sm.groups.isEmpty() && sm.scanned < sm.total -> "Checking ${sm.total - sm.scanned} photos"
                    sm.groups.isEmpty() -> "No repeated shots found"
                    else -> "${sm.groups.size} sets · keep the best, free ${Fmt.size(sm.extraBytes)}"
                },
            ) { app.open(Dest.Similar) }
        }
        item {
            ToolCard(
                Icons.Rounded.FolderZip, MaterialShapes.Cookie9Sided.toShape(), c.secondaryContainer, c.onSecondaryContainer,
                "Large files",
                if (big.isEmpty()) "Nothing over 100 MB" else "${big.size} over 100 MB · ${Fmt.size(big.sumOf { it.size })}",
            ) { app.open(Dest.Large) }
        }
        item {
            ToolCard(
                Icons.Rounded.Delete, MaterialShapes.Cookie6Sided.toShape(), c.errorContainer, c.onErrorContainer,
                "Trash",
                if (trash.isEmpty()) "Empty" else "${trash.size} items · ${Fmt.size(trash.sumOf { it.size })} · kept 30 days",
            ) { app.open(Dest.Trash) }
        }

        item { SectionTitle("Automatic clean-up") }
        item {
            RuleCard(
                Icons.Rounded.Screenshot, "Old screenshots",
                if (!s.shotAutoTrash) "Off" else buildString {
                    append("After ${s.shotDays} days, unless marked important")
                    if (soon > 0) append(" · $soon due this week")
                },
                s.shotAutoTrash,
            ) { on -> prefs.update { it.copy(shotAutoTrash = on) } }
        }
        item {
            RuleCard(
                Icons.Rounded.Forum, "WhatsApp photos and videos",
                if (!s.whatsappRule) "Off · ${wa.size} files, ${Fmt.size(wa.sumOf { it.size })}" else "After ${s.chatDays} days · ${wa.size} files now",
                s.whatsappRule,
            ) { on -> prefs.update { it.copy(whatsappRule = on) } }
        }
        item {
            RuleCard(
                Icons.AutoMirrored.Rounded.Send, "Telegram photos and videos",
                if (!s.telegramRule) "Off · ${tg.size} files, ${Fmt.size(tg.sumOf { it.size })}" else "After ${s.chatDays} days · ${tg.size} files now",
                s.telegramRule,
            ) { on -> prefs.update { it.copy(telegramRule = on) } }
        }
        item {
            val due = plan.all
            Panel {
                Text(
                    if (due.isEmpty()) "All clean" else "${due.size} files are due · ${Fmt.size(plan.bytes)}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    lastRunText(prefs.lastCleanupAt),
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
                if (due.isNotEmpty()) {
                    Gap(12.dp)
                    Button(onClick = { actions.trash(due) }) { Text("Move them to trash now") }
                }
                Gap(6.dp)
                Text(
                    "Files marked important are never touched. Everything goes to the trash first and can be restored for 30 days.",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
        }
    }
}

private val runFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.US)

private fun lastRunText(at: Long): String =
    if (at <= 0) "Runs by itself twice a day" else "Runs by itself twice a day · last ran ${runFmt.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))}"

@Composable
private fun StorageCard(lib: Library, waBytes: Long, tgBytes: Long) {
    val c = MaterialTheme.colorScheme
    val shots = lib.screenshots.sumOf { it.size }
    val videos = lib.items.filter { it.isVideo && it.chat == null }.sumOf { it.size }
    val photos = (lib.totalBytes - shots - videos - waBytes - tgBytes).coerceAtLeast(0)
    val slices = listOf(
        Slice("Photos", photos, c.primary),
        Slice("Videos", videos, c.tertiary),
        Slice("Screenshots", shots, c.secondary),
        Slice("WhatsApp", waBytes, Color(0xFF25A35A)),
        Slice("Telegram", tgBytes, Color(0xFF2AA3E0)),
    ).filter { it.bytes > 0 }
    val total = slices.sumOf { it.bytes }.coerceAtLeast(1)
    Panel {
        Text(Fmt.size(lib.totalBytes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("${lib.items.size} files on this phone", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        Gap(14.dp)
        val track = c.surfaceContainerHighest
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
            var x = 0f
            val gap = 3.dp.toPx()
            for (sl in slices) {
                val w = size.width * sl.bytes / total
                if (w > gap) drawRoundRect(sl.color, Offset(x, 0f), Size(w - gap, size.height), CornerRadius(size.height / 2))
                x += w
            }
        }
        Gap(12.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.chunked(2).forEach { pair ->
                Row {
                    pair.forEach { sl ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(sl.color))
                            GapW(8.dp)
                            Text("${sl.label} ", style = MaterialTheme.typography.bodyMedium)
                            Text(Fmt.size(sl.bytes), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolCard(icon: ImageVector, shape: Shape, bg: Color, fg: Color, title: String, sub: String, onClick: () -> Unit) {
    Panel(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, shape, bg, fg, 48.dp)
            GapW(14.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RuleCard(icon: ImageVector, title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Panel(onClick = { onChange(!on) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, CircleShape, if (on) c.primary else c.surfaceContainerHighest, if (on) c.onPrimary else c.onSurfaceVariant, 44.dp)
            GapW(14.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            GapW(8.dp)
            Switch(checked = on, onCheckedChange = onChange)
        }
    }
}
