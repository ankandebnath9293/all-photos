package com.ankandebnath.allphotos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ankandebnath.allphotos.data.Library
import com.ankandebnath.allphotos.data.MediaRepo
import kotlinx.coroutines.launch

/** Remembers each tab's scroll position while you move between tabs. */
class TabStates(
    val photos: androidx.compose.foundation.lazy.grid.LazyGridState,
    val shots: androidx.compose.foundation.lazy.grid.LazyGridState,
    val albums: androidx.compose.foundation.lazy.grid.LazyGridState,
    val cleanup: androidx.compose.foundation.lazy.LazyListState,
)

private class Tab(val label: String, val on: androidx.compose.ui.graphics.vector.ImageVector, val off: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("Photos", Icons.Rounded.Photo, Icons.Outlined.Photo),
    Tab("Albums", Icons.Rounded.Collections, Icons.Outlined.Collections),
    Tab("Screenshots", Icons.Rounded.Screenshot, Icons.Outlined.Screenshot),
    Tab("Cleanup", Icons.Rounded.CleaningServices, Icons.Outlined.CleaningServices),
)

@Composable
fun Home(app: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = remember { Actions(ctx.applicationContext, scope, app) }
    val libState by MediaRepo.library.collectAsState()
    val lib = libState ?: Library.EMPTY
    val loading = libState == null
    val states = TabStates(rememberLazyGridState(), rememberLazyGridState(), rememberLazyGridState(), rememberLazyListState())
    val holder = rememberSaveableStateHolder()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = 80.dp + navBottom

    BackHandler(enabled = app.stack.isNotEmpty() || app.tab != 0) { app.back() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = app.tab,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "tabs",
        ) { t ->
            holder.SaveableStateProvider("tab$t") {
                when (t) {
                    0 -> PhotosTab(lib, loading, app, actions, states.photos, bottom)
                    1 -> AlbumsTab(lib, app, states.albums, bottom)
                    2 -> ScreenshotsTab(lib, app, actions, states.shots, bottom)
                    else -> CleanupTab(lib, app, actions, states.cleanup, bottom)
                }
            }
        }

        NavigationBar(modifier = Modifier.align(Alignment.BottomCenter), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            tabs.forEachIndexed { i, tab ->
                NavigationBarItem(
                    selected = app.tab == i,
                    onClick = {
                        if (app.tab == i) {
                            scope.launchScrollTop(states, i)
                        }
                        app.tab = i
                    },
                    icon = { Icon(if (app.tab == i) tab.on else tab.off, null) },
                    label = { Text(tab.label, maxLines = 1) },
                )
            }
        }

        // Pages opened on top of the tabs.
        AnimatedContent(
            targetState = app.stack.toList(),
            transitionSpec = {
                if (targetState.size > initialState.size) {
                    (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(200))) togetherWith fadeOut(tween(160))
                } else {
                    fadeIn(tween(160)) togetherWith (slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "pages",
        ) { list ->
            val d = list.lastOrNull()
            if (d != null) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    DestPage(d, lib, app, actions, navBottom)
                }
            }
        }

        AnimatedVisibility(
            visible = app.viewer != null,
            enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.94f),
            exit = fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.96f),
        ) {
            val req = app.viewer
            if (req != null) Viewer(req, lib, app, actions, onClose = { app.viewer = null })
        }

        val ed = app.editor
        AnimatedVisibility(visible = ed != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            if (ed != null) {
                if (ed.trim) TrimScreen(ed.media, onClose = { app.editor = null })
                else Editor(ed.media, onClose = { app.editor = null })
            }
        }

        ToastHost(bottom = if (app.viewer != null || app.editor != null) navBottom + 110.dp else bottom + 16.dp)
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchScrollTop(states: TabStates, i: Int) {
    launch {
        when (i) {
            0 -> states.photos.animateScrollToItem(0)
            1 -> states.albums.animateScrollToItem(0)
            2 -> states.shots.animateScrollToItem(0)
            else -> states.cleanup.animateScrollToItem(0)
        }
    }
}

/** A page opened from a tab: an album, a collection, a clean-up tool or settings. */
@Composable
fun DestPage(d: Dest, lib: Library, app: AppState, actions: Actions, navBottom: androidx.compose.ui.unit.Dp) {
    val back = { app.back(); Unit }
    when (d) {
        is Dest.Album -> {
            val items = remember(lib, d.bucketId) { lib.items.filter { it.bucketId == d.bucketId } }
            GridPage(items, d.name, countLabel(items), app, actions, navBottom, onBack = back, empty = { NoPhotos() })
        }
        is Dest.Group -> GroupPage(d, lib, app, actions, navBottom, back)
        Dest.Similar -> SimilarScreen(lib, app, actions, navBottom, back)
        Dest.Large -> LargeFilesScreen(lib, app, actions, navBottom, back)
        Dest.Trash -> TrashScreen(app, actions, navBottom, back)
        Dest.Settings -> SettingsScreen(app, navBottom, back)
        Dest.Search -> SearchScreen(lib, app, actions, navBottom, back)
    }
}

fun countLabel(items: List<com.ankandebnath.allphotos.data.Media>): String {
    val v = items.count { it.isVideo }
    val p = items.size - v
    return when {
        items.isEmpty() -> "Empty"
        v == 0 -> "$p photo${if (p == 1) "" else "s"}"
        p == 0 -> "$v video${if (v == 1) "" else "s"}"
        else -> "$p photo${if (p == 1) "" else "s"} · $v video${if (v == 1) "" else "s"}"
    }
}
