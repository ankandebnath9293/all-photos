package com.ankandebnath.allphotos.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ankandebnath.allphotos.data.Library
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Remembers each tab's scroll position while you move between tabs. */
class TabStates(
    val photos: androidx.compose.foundation.lazy.grid.LazyGridState,
    val shots: androidx.compose.foundation.lazy.grid.LazyGridState,
    val albums: androidx.compose.foundation.lazy.grid.LazyGridState,
    val cleanup: androidx.compose.foundation.lazy.LazyListState,
)

private val tabs = listOf(
    NavTab("Photos", Icons.Rounded.Photo, Icons.Outlined.Photo),
    NavTab("Albums", Icons.Rounded.Collections, Icons.Outlined.Collections),
    NavTab("Screenshots", Icons.Rounded.Screenshot, Icons.Outlined.Screenshot),
    NavTab("Cleanup", Icons.Rounded.CleaningServices, Icons.Outlined.CleaningServices),
)

@Composable
fun Home(app: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val actions = remember { Actions(ctx.applicationContext, scope, app) }
    val libState by MediaRepo.library.collectAsState()
    val lib = libState ?: Library.EMPTY
    val loading = libState == null
    val states = TabStates(rememberLazyGridState(), rememberLazyGridState(), rememberLazyGridState(), rememberLazyListState())
    val holder = rememberSaveableStateHolder()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The floating bar's height plus its margin; tabs scroll underneath it.
    val bottom = 84.dp + navBottom
    val bg = MaterialTheme.colorScheme.background
    val back = remember { Animatable(0f) }
    var swipeEdge = remember { floatArrayOf(0f) }

    // Tell once that the files version 1.0 trashed too early were put back.
    LaunchedEffect(Unit) {
        val prefs = Prefs.get(ctx)
        val n = prefs.restoredCount
        if (n > 0) {
            Toasts.show("Put back $n older file${if (n == 1) "" else "s"} from the trash")
            prefs.restoredCount = 0
        }
    }

    // Back from a page: the page shrinks with the gesture, then slides away. From another tab: go to Photos.
    val leaveBack = remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = app.stack.isNotEmpty() || (app.tab != 0 && app.viewer == null)) { progress ->
        val onPage = app.stack.isNotEmpty()
        try {
            progress.collect { e ->
                swipeEdge[0] = if (e.swipeEdge == 0) 1f else -1f
                if (onPage && !motion.minimal) back.snapTo(e.progress)
            }
            // The leaving page keeps the size the gesture gave it while it slides out.
            leaveBack.floatValue = back.value
            back.snapTo(0f)
            app.back()
        } catch (e: CancellationException) {
            scope.launch { back.animateTo(0f) }
            throw e
        }
    }
    LaunchedEffect(app.stack.size) {
        kotlinx.coroutines.delay(700)
        leaveBack.floatValue = 0f
    }
    // The tabs drift left under the first page, like the page below any other page.
    val under by animateFloatAsState(
        if (app.stack.isEmpty() || motion.minimal) 0f else 1f,
        if (app.stack.isEmpty()) motion.slideOut() else motion.slideIn(),
        label = "under",
    )
    val scrim = MaterialTheme.colorScheme.scrim

    Box(Modifier.fillMaxSize().background(bg)) {
        AnimatedContent(
            targetState = app.tab,
            transitionSpec = { motion.tab() },
            label = "tabs",
            modifier = Modifier.graphicsLayer { translationX = -size.width * motion.parallax * under },
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

        // Photos run edge to edge; a soft fade keeps the clock readable.
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(statusTop() + 20.dp)
                .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.92f), bg.copy(alpha = 0.6f), bg.copy(alpha = 0f)))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(navBottom + 56.dp)
                .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg.copy(alpha = 0.7f)))),
        )

        AnimatedVisibility(
            visible = app.stack.isEmpty(),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        ) {
            FloatingNav(
                tabs, app.tab,
                onSelect = { i ->
                    if (app.tab == i) scope.launchScrollTop(states, i)
                    app.tab = i
                },
            )
        }

        // Pages opened on top of the tabs.
        AnimatedContent(
            targetState = app.stack.toList(),
            transitionSpec = {
                val z = targetState.size.toFloat()
                (if (targetState.size > initialState.size) motion.pageOpen() else motion.pageClose()).apply { targetContentZIndex = z }
            },
            label = "pages",
        ) { list ->
            val d = list.lastOrNull()
            if (d != null) {
                val isTop = list.size == app.stack.size
                val leaving = list.size > app.stack.size
                // A soft shadow over whatever is underneath, deepening as this page covers it.
                val cover = remember { Animatable(0f) }
                LaunchedEffect(leaving) {
                    if (leaving) cover.animateTo(0f, motion.slideOut()) else cover.animateTo(1f, motion.slideIn())
                }
                Box(
                    Modifier.fillMaxSize()
                        .drawBehind {
                            if (!motion.minimal && cover.value > 0f) {
                                drawRect(scrim.copy(alpha = 0.32f * cover.value), topLeft = Offset(-size.width * 2f, 0f), size = Size(size.width * 2f, size.height))
                            }
                        }
                        .graphicsLayer {
                            val p = when {
                                isTop -> back.value
                                leaving -> leaveBack.floatValue
                                else -> 0f
                            }
                            if (p > 0f) {
                                val s = 1f - 0.1f * p
                                scaleX = s
                                scaleY = s
                                translationX = swipeEdge[0] * p * with(density) { 24.dp.toPx() }
                                transformOrigin = TransformOrigin(if (swipeEdge[0] > 0) 0.9f else 0.1f, 0.5f)
                                shape = androidx.compose.foundation.shape.RoundedCornerShape((p * 32).dp)
                                clip = true
                            } else {
                                shape = RectangleShape
                                clip = false
                            }
                        }
                        .background(bg),
                ) {
                    DestPage(d, lib, app, actions, navBottom)
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(statusTop() + 16.dp)
                            .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.92f), bg.copy(alpha = 0f)))),
                    )
                }
            }
        }

        val req = app.viewer
        if (req != null) Viewer(req, lib, app, actions, onClose = { app.viewer = null })

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
