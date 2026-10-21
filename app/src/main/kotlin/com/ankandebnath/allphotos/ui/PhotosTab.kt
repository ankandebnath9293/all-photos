package com.ankandebnath.allphotos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ankandebnath.allphotos.capture.Apps
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.Library
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.logic.Links
import com.ankandebnath.allphotos.work.Indexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun PhotosTab(lib: Library, loading: Boolean, app: AppState, actions: Actions, state: LazyGridState, bottom: androidx.compose.ui.unit.Dp) {
    if (loading) {
        Column(Modifier.fillMaxSize()) {
            PageHeader("Photos", null)
            Loading(Modifier.padding(top = 80.dp))
        }
        return
    }
    GridPage(
        items = lib.items,
        title = "Photos",
        sub = String.format(Locale.US, "%,d items", lib.items.size),
        app = app,
        actions = actions,
        bottom = bottom,
        state = state,
        trailing = { SmallIconButton(Icons.Rounded.Settings, "Settings") { app.open(Dest.Settings) } },
        extra = { FakeSearch("Search text, places, things…") { app.open(Dest.Search) } },
        empty = { NoPhotos("No photos yet") },
    )
}

/** Looks like a search field; opens the search page. */
@Composable
fun FakeSearch(hint: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50)).background(c.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant)
        GapW(12.dp)
        Text(hint, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
}

/** Finds photos by the text in them, what is in them, their name, folder, or the app or website they came from. */
fun searchLibrary(ctx: android.content.Context, lib: Library, q: String): List<Media> {
    val query = q.trim()
    if (query.length < 2) return emptyList()
    val db = Db.get(ctx)
    val hits = db.search(query)
    val lower = query.lowercase()
    val sources = db.sources.value
    return lib.items.filter { m ->
        if (m.id in hits) return@filter true
        if (m.name.lowercase().contains(lower) || m.bucket.lowercase().contains(lower)) return@filter true
        val s = sources[m.id] ?: return@filter false
        (s.pkg != null && Apps.label(ctx, s.pkg).lowercase().contains(lower)) ||
            (Links.domain(s.url)?.contains(lower) == true)
    }
}

@Composable
fun SearchScreen(lib: Library, app: AppState, actions: Actions, bottom: androidx.compose.ui.unit.Dp, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val grid = rememberLazyGridState()
    val index by Indexer.progress.collectAsState()
    val results by produceState(emptyList<Media>(), q, lib) {
        delay(180)
        value = withContext(Dispatchers.Default) { searchLibrary(ctx.applicationContext, lib, q) }
    }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focus.requestFocus() }
    }
    val counts = remember(lib) { Db.get(ctx).counts() }
    val photos = remember(lib) { lib.items.count { !it.isVideo } }
    GridPage(
        items = if (q.trim().length < 2) emptyList() else results,
        title = "Search",
        sub = when {
            q.trim().length >= 2 -> "${results.size} found"
            counts.second < photos -> "Text read in ${counts.second} of $photos photos" + if (index.running) " · working" else ""
            else -> "Every photo is searchable"
        },
        app = app,
        actions = actions,
        bottom = bottom,
        state = grid,
        onBack = onBack,
        extra = {
            Column {
                SearchPill(q, { q = it }, "Words in a photo, \"dog\", a website…", Modifier.focusRequester(focus))
                if (q.isBlank()) {
                    Gap(12.dp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Receipt", "Food", "Sky", "Car", "Document", "Dog", "Flower", "Selfie")) { s ->
                            AssistChip(onClick = { q = s }, label = { Text(s) }, leadingIcon = { Icon(Icons.Rounded.ImageSearch, null) })
                        }
                    }
                }
            }
        },
        empty = {
            if (q.trim().length >= 2) {
                EmptyState(Icons.Rounded.Search, "Nothing found", "Text and labels are read on the phone, a little at a time, while it charges. New screenshots are read right away.")
            }
        },
    )
}
