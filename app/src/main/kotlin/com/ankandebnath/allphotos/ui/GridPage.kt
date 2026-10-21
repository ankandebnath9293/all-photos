package com.ankandebnath.allphotos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.data.Prefs

/** The big title at the top of a page, with an optional back arrow and buttons. */
@Composable
fun PageHeader(
    title: String,
    sub: String?,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            }
            Column(Modifier.weight(1f).padding(start = if (onBack == null) 4.dp else 0.dp)) {
                Text(
                    title,
                    style = if (onBack == null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sub != null) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            trailing?.invoke(this)
        }
        if (extra != null) {
            Gap(12.dp)
            Box(Modifier.padding(end = 8.dp)) { extra() }
        }
    }
}

/** One button of the floating selection toolbar. */
class DockAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** The floating pill of actions shown while photos are selected. */
@Composable
fun SelectionDock(actions: List<DockAction>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            actions.forEach { a ->
                Column(
                    Modifier.clip(RoundedCornerShape(50)).clickable(onClick = a.onClick).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(a.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(a.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
                }
            }
        }
    }
}

/** The top bar while selecting: how many, select all, done. */
@Composable
fun SelectionTop(count: Int, onClose: () -> Unit, onAll: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 3.dp) {
        Row(Modifier.statusBarsPadding().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Stop selecting") }
            Text("$count selected", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onAll) { Icon(Icons.Rounded.SelectAll, "Select all") }
        }
    }
}

/** What a page does with a selection. */
enum class SelectMode { NORMAL, TRASH, PICK }

/**
 * A full page of photos: header, grid, selection bar and actions. Used for the Photos tab, albums,
 * screenshot groups and search results.
 */
@Composable
fun GridPage(
    items: List<Media>,
    title: String,
    sub: String?,
    app: AppState,
    actions: Actions,
    bottom: Dp,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    selection: Selection = remember { Selection() },
    headers: Boolean = true,
    mode: SelectMode = SelectMode.NORMAL,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
    badge: (@Composable BoxScope.(Media) -> Unit)? = null,
    empty: (@Composable () -> Unit)? = null,
    onOpen: ((List<Media>, Int) -> Unit)? = null,
    onPick: ((List<Media>) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val s by prefs.settings.collectAsState()
    val important by remember { Db.get(ctx).important }.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(enabled = selection.active) { selection.clear() }

    Box(modifier.fillMaxSize()) {
        MediaGrid(
            items = items,
            state = state,
            cols = s.gridCols,
            selection = selection,
            important = important,
            onCols = { n -> prefs.update { it.copy(gridCols = n) } },
            headers = headers,
            contentPadding = PaddingValues(bottom = bottom + 96.dp),
            top = {
                Column {
                    PageHeader(title, sub, onBack, trailing, extra)
                    if (items.isEmpty() && empty != null) empty()
                }
            },
            badge = badge,
            onOpen = { i ->
                when {
                    onOpen != null -> onOpen(items, i)
                    mode == SelectMode.PICK -> onPick?.invoke(listOf(items[i]))
                    else -> app.view(items, i)
                }
            },
        )

        AnimatedVisibility(
            selection.active,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SelectionTop(
                selection.ids.size,
                onClose = { selection.clear() },
                onAll = {
                    if (selection.ids.size == items.size) selection.clear() else selection.set(items.map { it.id }, true)
                },
            )
        }

        val picked = selection.pick(items)
        val dock = when (mode) {
            SelectMode.TRASH -> listOf(
                DockAction(Icons.Rounded.RestoreFromTrash, "Restore") { actions.restore(picked) { selection.clear() } },
                DockAction(Icons.Rounded.DeleteForever, "Delete") { confirmDelete = true },
            )
            SelectMode.PICK -> listOf(DockAction(Icons.Rounded.Done, "Done") { onPick?.invoke(picked) })
            SelectMode.NORMAL -> buildList {
                add(DockAction(Icons.Rounded.Share, "Share") { actions.share(picked) })
                val allImportant = picked.isNotEmpty() && picked.all { it.id in important }
                add(
                    DockAction(if (allImportant) Icons.Rounded.StarOutline else Icons.Rounded.Star, if (allImportant) "Unmark" else "Important") {
                        actions.important(picked, !allImportant)
                        selection.clear()
                    },
                )
                if (picked.any { it.isHeic }) add(DockAction(Icons.Rounded.Transform, "To JPG") { actions.convert(picked); selection.clear() })
                add(DockAction(Icons.Rounded.Delete, "Delete") { actions.trash(picked) { selection.clear() } })
            }
        }
        AnimatedVisibility(
            selection.active,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 12.dp),
        ) {
            SelectionDock(dock)
        }
    }

    if (confirmDelete) {
        val picked = selection.pick(items)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null) },
            title = { Text("Delete ${picked.size} for good?") },
            text = { Text("These can't be brought back after this.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.deleteForever(picked) { selection.clear() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Space taken by the status bar, for pages that draw their own header. */
@Composable
fun statusTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
fun NoPhotos(text: String = "Nothing here yet") {
    EmptyState(Icons.Rounded.Image, text, "Photos and videos will show up here.")
}

@Composable
fun Modifier.pageBackground(): Modifier = this.background(MaterialTheme.colorScheme.background)

@Composable
fun SmallIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, desc, modifier = Modifier.size(24.dp)) }
}
