package com.ankandebnath.allphotos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ankandebnath.allphotos.data.Library
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.Ops
import com.ankandebnath.allphotos.logic.Fmt
import com.ankandebnath.allphotos.logic.Rules
import com.ankandebnath.allphotos.logic.SimilarGroup
import com.ankandebnath.allphotos.logic.Timeline
import com.ankandebnath.allphotos.work.Indexer
import com.ankandebnath.allphotos.work.SimilarData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SimilarScreen(lib: Library, app: AppState, actions: Actions, bottom: Dp, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var scanning by remember { mutableStateOf(true) }
    var version by remember { mutableIntStateOf(0) }
    val progress by Indexer.progress.collectAsState()
    LaunchedEffect(Unit) {
        // Fingerprint any photos the background index has not reached yet. A few ms each.
        withContext(Dispatchers.IO) { Indexer.fingerprints(ctx.applicationContext) }
        scanning = false
        version++
    }
    val summary by produceState<SimilarData.Summary?>(null, lib, version) { value = SimilarData.compute(ctx.applicationContext, lib) }
    // Which photos are set to go; by default everything except the best of each set.
    var drop by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var initialised by remember { mutableStateOf(-1) }
    val groups = summary?.groups.orEmpty()
    LaunchedEffect(summary) {
        val sm = summary ?: return@LaunchedEffect
        if (initialised != version) {
            drop = sm.groups.flatMap { it.ids.drop(1) }.toSet()
            initialised = version
        }
    }
    val dropItems = remember(drop, lib) { drop.mapNotNull { lib.byId[it] } }
    val px = with(LocalDensity.current) { 120.dp.roundToPx() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottom + 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PageHeader(
                    "Similar photos",
                    when {
                        summary == null -> "Looking…"
                        groups.isEmpty() -> "Nothing to clean"
                        else -> "${groups.size} sets · ${groups.sumOf { it.ids.size }} photos"
                    },
                    onBack = onBack,
                )
            }
            if (scanning) {
                item {
                    Panel {
                        Text("Comparing photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Gap(10.dp)
                        val p = if (progress.total > 0) progress.done.toFloat() / progress.total else 0f
                        LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        Gap(6.dp)
                        Text("Runs on the phone; nothing is uploaded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!scanning && groups.isEmpty() && summary != null) {
                item { EmptyState(Icons.Rounded.AutoAwesomeMosaic, "No repeated shots", "When several photos of the same moment are taken, they'll be grouped here so you can keep just the best one.") }
            }
            items(groups, key = { it.ids.first() }) { g ->
                SimilarCard(g, lib, drop, px, onToggle = { id -> drop = if (id in drop) drop - id else drop + id }, onView = { i ->
                    val ms = g.ids.mapNotNull { lib.byId[it] }
                    app.view(ms, i)
                })
            }
        }
        AnimatedVisibility(
            dropItems.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 16.dp),
        ) {
            Button(onClick = {
                actions.trash(dropItems) { drop = emptySet(); version++ }
            }) {
                Icon(Icons.Rounded.DeleteSweep, null)
                GapW(8.dp)
                Text("Delete ${dropItems.size} · free ${Fmt.size(dropItems.sumOf { it.size })}")
            }
        }
    }
}

@Composable
private fun SimilarCard(g: SimilarGroup, lib: Library, drop: Set<Long>, px: Int, onToggle: (Long) -> Unit, onView: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    val first = lib.byId[g.ids.first()]
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    first?.let { Timeline.dayTitle(Timeline.dayOf(it.taken, java.time.ZoneId.systemDefault()), java.time.LocalDate.now()) } ?: "",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    (if (g.exact) "Exact copies" else "${g.ids.size} similar shots") + " · ${Fmt.size(g.extraBytes)} extra",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
        }
        Gap(10.dp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(g.ids, key = { _, id -> id }) { i, id ->
                val m = lib.byId[id] ?: return@itemsIndexed
                val gone = id in drop
                val dim by animateFloatAsState(if (gone) 0.55f else 1f, label = "dim")
                Box(
                    Modifier.size(120.dp).clip(RoundedCornerShape(18.dp))
                        .then(if (!gone) Modifier.border(3.dp, c.primary, RoundedCornerShape(18.dp)) else Modifier)
                        .combinedClickable(onClick = { onToggle(id) }, onLongClick = { onView(i) }),
                ) {
                    Thumb(m, px, Modifier.fillMaxSize().graphicsLayer { alpha = dim })
                    if (i == 0) SmallTag("Best", Modifier.align(Alignment.TopStart).padding(6.dp), bg = c.primary, fg = c.onPrimary)
                    Icon(
                        if (gone) Icons.Rounded.Delete else Icons.Rounded.CheckCircle,
                        if (gone) "Will be deleted" else "Keep",
                        tint = if (gone) c.onError else c.onPrimary,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(26.dp)
                            .background(if (gone) c.error else c.primary, CircleShape).padding(4.dp),
                    )
                }
            }
        }
        Gap(6.dp)
        Text("Tap to keep or delete · hold to view", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    }
}

@Composable
fun LargeFilesScreen(lib: Library, app: AppState, actions: Actions, bottom: Dp, onBack: () -> Unit) {
    var seg by remember { mutableIntStateOf(0) }
    val list = remember(lib, seg) {
        lib.items.asSequence()
            .filter { seg == 0 || (seg == 1) == it.isVideo }
            .filter { it.size >= 10L * 1024 * 1024 }
            .sortedByDescending { it.size }
            .take(400)
            .toList()
    }
    var picked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val pickedItems = remember(picked, list) { list.filter { it.id in picked } }
    val px = with(LocalDensity.current) { 72.dp.roundToPx() }
    val c = MaterialTheme.colorScheme
    BackHandler(enabled = picked.isNotEmpty()) { picked = emptySet() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottom + 110.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                PageHeader("Large files", "${list.size} over 10 MB · ${Fmt.size(list.sumOf { it.size })}", onBack = onBack, extra = {
                    Segmented(listOf("All", "Videos", "Photos"), seg) { seg = it; picked = emptySet() }
                })
            }
            if (list.isEmpty()) item { EmptyState(Icons.Rounded.FolderZip, "No large files", "Nothing here is bigger than 10 MB.") }
            itemsIndexed(list, key = { _, m -> m.id }) { i, m ->
                val on = m.id in picked
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(if (on) c.secondaryContainer else c.surfaceContainer)
                        .combinedClickable(
                            onClick = { picked = if (on) picked - m.id else picked + m.id },
                            onLongClick = { app.view(list, i) },
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))) {
                        Thumb(m, px, Modifier.fillMaxSize())
                        if (m.isVideo) VideoBadge(m.durationMs, Modifier.align(Alignment.BottomCenter).padding(3.dp))
                    }
                    GapW(12.dp)
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.size(m.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(m.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                if (m.isVideo) Fmt.videoClass(m.width, m.height) else Fmt.megapixels(m.width, m.height),
                                if (m.isVideo) Fmt.duration(m.durationMs) else null,
                                m.bucket,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, maxLines = 1,
                        )
                    }
                    Icon(
                        if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                        tint = if (on) c.primary else c.outline,
                    )
                }
            }
        }
        AnimatedVisibility(
            picked.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 16.dp),
        ) {
            Button(onClick = { actions.trash(pickedItems) { picked = emptySet() } }) {
                Icon(Icons.Rounded.Delete, null)
                GapW(8.dp)
                Text("Delete ${pickedItems.size} · ${Fmt.size(pickedItems.sumOf { it.size })}")
            }
        }
    }
}

@Composable
fun TrashScreen(app: AppState, actions: Actions, bottom: Dp, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tick by MediaRepo.trashTick.collectAsState()
    val items by produceState<List<Media>?>(null, tick) { value = MediaRepo.trashed(ctx) }
    var confirm by remember { mutableStateOf(false) }
    val list = items.orEmpty()
    val now = System.currentTimeMillis()
    val badge: @Composable BoxScope.(Media) -> Unit = { m ->
        val left = if (m.expires > 0) ((m.expires - now + Rules.DAY_MS - 1) / Rules.DAY_MS).toInt().coerceAtLeast(0) else Ops.TRASH_DAYS
        SmallTag(if (left <= 1) "Today" else "${left}d", Modifier.align(Alignment.BottomStart).padding(5.dp))
    }
    GridPage(
        items = list,
        title = "Trash",
        sub = if (items == null) "Loading…" else if (list.isEmpty()) "Empty" else "${list.size} · ${Fmt.size(list.sumOf { it.size })} · each kept for 30 days",
        app = app,
        actions = actions,
        bottom = bottom,
        mode = SelectMode.TRASH,
        onBack = onBack,
        trailing = {
            if (list.isNotEmpty()) TextButton(onClick = { confirm = true }) { Text("Empty") }
        },
        badge = badge,
        empty = { EmptyState(Icons.Rounded.Delete, "Trash is empty", "Deleted photos stay here for 30 days before they're gone for good.") },
        onOpen = { all, i -> app.viewer = ViewerReq(all.map { it.id }, i, fromTrash = true, explicit = all) },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null) },
            title = { Text("Empty the trash?") },
            text = { Text("${list.size} items (${Fmt.size(list.sumOf { it.size })}) will be deleted for good.") },
            confirmButton = { TextButton(onClick = { confirm = false; actions.deleteForever(list) }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
