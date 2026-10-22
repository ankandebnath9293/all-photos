package com.ankandebnath.allphotos.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ankandebnath.allphotos.capture.Apps
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.Library
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.logic.Links
import com.ankandebnath.allphotos.media.DetailsReader
import com.ankandebnath.allphotos.media.Share
import com.ankandebnath.allphotos.media.VideoFacts
import com.ankandebnath.allphotos.work.Cleaner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val titleFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/** Draws the page as it looks in [frame]: moved, scaled and clipped. */
private fun Modifier.heroTransform(frame: () -> Frame): Modifier = this
    .drawWithContent {
        val f = frame()
        val r = f.clip
        val whole = f.corner < 0.5f && r.left <= 0.5f && r.top <= 0.5f && r.right >= size.width - 0.5f && r.bottom >= size.height - 0.5f
        if (whole) {
            drawContent()
        } else {
            val p = Path().apply { addRoundRect(RoundRect(r, CornerRadius(f.corner))) }
            clipPath(p) { this@drawWithContent.drawContent() }
        }
    }
    .graphicsLayer {
        val f = frame()
        scaleX = f.scale
        scaleY = f.scale
        translationX = f.center.x - size.width / 2f
        translationY = f.center.y - size.height / 2f
        alpha = f.alpha
    }

/** Where the finger has moved the photo. Updated straight away on every move, animated back on release. */
@androidx.compose.runtime.Stable
private class DragOffset {
    var value by mutableStateOf(Offset.Zero)

    suspend fun animateTo(target: Offset, spec: androidx.compose.animation.core.AnimationSpec<Offset>) {
        androidx.compose.animation.core.animate(Offset.VectorConverter, value, target, animationSpec = spec) { v, _ -> value = v }
    }
}

/** A vertical drag on a video page, handed to the viewer like a photo's. */
private fun Modifier.verticalDrag(enabled: Boolean, onDelta: (Offset) -> Unit, onEnd: () -> Unit): Modifier =
    if (!enabled) this else this.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var total = Offset.Zero
            var dragging = false
            do {
                val ev = awaitPointerEvent()
                if (ev.changes.count { it.pressed } > 1) break
                val ch = ev.changes.firstOrNull() ?: break
                val d = ch.positionChange()
                total += d
                if (!dragging && abs(total.y) > slop * 1.5f && abs(total.y) > abs(total.x) * 1.4f && !ch.isConsumed) {
                    dragging = true
                    onDelta(total)
                } else if (dragging) {
                    onDelta(d)
                }
                if (dragging) ch.consume()
            } while (ev.changes.any { it.pressed })
            if (dragging) onEnd()
        }
    }

/**
 * The full-screen viewer. Opens by growing out of the tapped thumbnail and closes by shrinking
 * back into it (back, swipe down, or the arrow). Swipe up shows the details.
 */
@Composable
fun Viewer(req: ViewerReq, lib: Library, app: AppState, actions: Actions, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val view = LocalView.current
    val density = LocalDensity.current
    val motion = LocalMotion.current
    val darkTheme = LocalDark.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val db = remember { Db.get(ctx) }
    val important by db.important.collectAsState()
    val sources by db.sources.collectAsState()
    val prefs = remember { com.ankandebnath.allphotos.data.Prefs.get(ctx) }
    val settings by prefs.settings.collectAsState()
    val rulesStart = remember { prefs.rulesStart }

    // The list follows the library, so deleted items drop out while you browse.
    val items = remember(req, lib) {
        if (req.explicit.isNotEmpty()) req.explicit else req.ids.mapNotNull { lib.byId[it] }
    }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pager = rememberPagerState(initialPage = req.start.coerceIn(0, items.lastIndex)) { items.size }
    val current = items.getOrNull(pager.currentPage.coerceIn(0, items.lastIndex))
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmForever by remember { mutableStateOf(false) }
    var textMode by remember { mutableStateOf(false) }
    var resetZoom by remember { mutableIntStateOf(0) }
    val hdrPages = remember(req) { mutableStateMapOf<Long, Boolean>() }
    val player = remember { PlayerHolder(ctx.applicationContext) }
    var landscape by remember { mutableStateOf(false) }

    // The hero animation: from the tapped cell into place, and back out on close.
    val startId = items.getOrNull(req.start.coerceIn(0, items.lastIndex))?.id
    val origin = remember { if (motion.minimal || req.explicit.isNotEmpty()) null else startId?.let { HeroBounds.get(it) } }
    val enter = remember { Animatable(if (origin != null) 0f else 1f) }
    val appear = remember { Animatable(if (origin != null) 1f else 0f) }
    val drag = remember { DragOffset() }
    val back = remember { Animatable(0f) }
    var closing by remember { mutableStateOf<Pair<Frame, Frame>?>(null) }
    val closeT = remember { Animatable(0f) }
    val corner = with(density) { 3.dp.toPx() }

    DisposableEffect(Unit) {
        android.util.Log.i("AllPhotos", "Viewer opened on ${items.getOrNull(req.start)?.name}")
        HeroBounds.hidden = startId.takeIf { origin != null }
        val w = activity?.window
        val c = w?.let { WindowCompat.getInsetsController(it, view) }
        c?.isAppearanceLightStatusBars = false
        c?.isAppearanceLightNavigationBars = false
        onDispose {
            player.release()
            HeroBounds.hidden = null
            activity?.window?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { win ->
                val ic = WindowCompat.getInsetsController(win, view)
                ic.show(WindowInsetsCompat.Type.systemBars())
                ic.isAppearanceLightStatusBars = !darkTheme
                ic.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    LaunchedEffect(Unit) {
        if (enter.value < 1f) enter.animateTo(1f, motion.hero())
        if (appear.value < 1f) appear.animateTo(1f, tween(if (motion.minimal) 120 else 180))
        HeroBounds.hidden = null
    }

    // Light up the screen for HDR photos; videos handle HDR on their own surface.
    val hdr = current != null && !current.isVideo && hdrPages[current.id] == true
    LaunchedEffect(hdr) {
        activity?.window?.colorMode = if (hdr) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
    }

    // Hide the status and navigation bars along with the controls.
    LaunchedEffect(chrome) {
        android.util.Log.i("AllPhotos", "Viewer controls ${if (chrome) "shown" else "hidden"}")
        val w = activity?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(w, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chrome) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
    }

    // Play the video on the page you land on; stop when you move to a photo.
    LaunchedEffect(current?.id, pager.isScrollInProgress) {
        if (pager.isScrollInProgress) return@LaunchedEffect
        if (current != null && current.isVideo) {
            if (player.uri != current.uri) player.load(current, null)
        } else {
            player.stop()
        }
    }
    LaunchedEffect(player.wantsPlay, chrome, current?.id, app.details) {
        if (player.wantsPlay && chrome && current?.isVideo == true && app.details == null) {
            kotlinx.coroutines.delay(3000)
            if (player.wantsPlay && app.details == null) chrome = false
        }
    }

    val facts by produceState<VideoFacts?>(null, current?.id) {
        val m = current
        value = if (m != null && m.isVideo) withContext(Dispatchers.IO) { DetailsReader.readVideo(ctx, m.uri) } else null
    }
    val fps = facts?.fps ?: 0f
    val videoHdr = facts?.let { DetailsReader.hdrLabel(it) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val aspectNow by rememberUpdatedState(
            if (current?.isVideo == true && player.aspect > 0f && player.uri == current.uri) player.aspect else current?.aspect ?: 1f,
        )
        val closeAt = with(density) { 110.dp.toPx() }
        val detailsAt = with(density) { 70.dp.toPx() }

        // Where the current photo is right now, computed when drawing so animations never recompose the page.
        fun live(): Frame {
            val d = drag.value
            val down = d.y.coerceAtLeast(0f)
            val p = (down / (screen.height * 0.5f)).coerceIn(0f, 1f)
            val b = back.value
            val shrink = 1f - 0.28f * p - 0.12f * b
            val move = Offset(d.x * if (down > 0f) 1f else 0.2f, if (d.y < 0f) d.y * 0.3f else d.y)
            val full = Frame.full(screen, aspectNow, move, shrink, backdrop = (1f - p * 0.95f - b * 0.35f).coerceIn(0f, 1f))
            val e = enter.value
            val o = origin
            return if (e < 1f && o != null) Frame.lerpFrame(Frame.cell(screen, aspectNow, o, corner), full, e) else full
        }
        val frame: () -> Frame = {
            val c = closing
            if (c != null) Frame.lerpFrame(c.first, c.second, closeT.value) else live()
        }

        fun requestClose() {
            if (closing != null) return
            android.util.Log.i("AllPhotos", "Viewer closing")
            textMode = false
            scope.launch {
                val m = items.getOrNull(pager.currentPage)
                if (m != null && !zoomed && !motion.minimal && req.explicit.isEmpty()) {
                    runCatching { app.reveal?.invoke(m.id) }
                    withFrameNanos { }
                    withFrameNanos { }
                }
                val target = m?.let { HeroBounds.get(it.id) }.takeIf { !zoomed && !motion.minimal && req.explicit.isEmpty() }
                val start = live()
                val end = if (target != null) {
                    Frame.cell(screen, aspectNow, target, corner)
                } else {
                    start.copy(scale = start.scale * 0.9f, backdrop = 0f, alpha = 0f)
                }
                HeroBounds.hidden = if (target != null) m?.id?.let { HeroBounds.alias[it] ?: it } else null
                closing = start to end
                closeT.snapTo(0f)
                closeT.animateTo(1f, if (target != null) motion.hero() else tween(170))
                onClose()
            }
        }

        fun endDrag() {
            val d = drag.value
            val now = items.getOrNull(pager.currentPage)
            when {
                d.y > closeAt -> requestClose()
                d.y < -detailsAt && now != null && now.external == null -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    app.details = now
                    scope.launch { drag.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessMedium)) }
                }
                else -> scope.launch { drag.animateTo(Offset.Zero, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) }
            }
        }

        PredictiveBackHandler(enabled = closing == null && !textMode && app.details == null) { progress ->
            try {
                progress.collect { e -> back.snapTo(e.progress) }
                requestClose()
            } catch (e: CancellationException) {
                scope.launch { back.animateTo(0f) }
                throw e
            }
        }
        BackHandler(enabled = textMode) { textMode = false }

        val dragging by remember { derivedStateOf { drag.value != Offset.Zero || back.value > 0f } }
        val entered by remember { derivedStateOf { enter.value >= 1f } }
        val chromeAlpha: () -> Float = {
            if (closing != null) 0f else {
                val d = drag.value
                val p = (abs(d.y) / (screen.height * 0.15f)).coerceIn(0f, 1f)
                (enter.value * appear.value * (1f - p) * (1f - back.value * 2f)).coerceIn(0f, 1f)
            }
        }

        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = appear.value }
                .drawBehind { drawRect(Color.Black.copy(alpha = frame().backdrop)) },
        ) {
            // The one video surface sits under the pager: the pages stay on top so taps, double taps and
            // swipes (sideways, down to close, up for details) work on videos exactly as on photos.
            val vid = current?.takeIf { it.isVideo }
            val settled = vid != null && !pager.isScrollInProgress && player.uri == vid.uri && entered && closing == null && !dragging
            val showingVideo = settled && player.firstFrame
            if (vid != null) {
                LaunchedEffect(settled) {
                    if (!settled) {
                        player.firstFrame = false
                    } else if (!player.player.playWhenReady) {
                        // A paused video needs one frame drawn on the new surface.
                        player.player.seekTo(player.player.currentPosition)
                    }
                }
                Box(Modifier.fillMaxSize().heroTransform(frame), contentAlignment = Alignment.Center) {
                    val ar = aspectNow
                    Box(if (ar > 0f) Modifier.fillMaxWidth().aspectRatio(ar, matchHeightConstraintsFirst = ar < screen.width / screen.height) else Modifier.fillMaxSize()) {
                        SharedVideoSurface(player, visible = settled, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            HorizontalPager(
                state = pager,
                key = { items[it].id },
                beyondViewportPageCount = 1,
                pageSpacing = 16.dp,
                userScrollEnabled = !zoomed && !textMode && !dragging && closing == null,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val m = items[page]
                val isCur = page == pager.currentPage
                val placeholder: @Composable () -> Unit = { Thumb(m, 640, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, bg = Color.Transparent) }
                Box(Modifier.fillMaxSize().then(if (isCur) Modifier.heroTransform(frame) else Modifier)) {
                    if (m.isVideo) {
                        Box(
                            Modifier.fillMaxSize()
                                .verticalDrag(enabled = isCur && closing == null, onDelta = { d -> drag.value += d }, onEnd = { endDrag() })
                                .pointerInput(m.id) {
                                    detectTapGestures(
                                        onTap = { chrome = !chrome },
                                        onDoubleTap = { p ->
                                            val now = player.player.currentPosition
                                            player.seek(if (p.x < size.width / 2f) now - 10_000 else now + 10_000, exact = false)
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Until the first frame arrives the thumbnail covers the surface, so there is never a black flash.
                            if (!(isCur && showingVideo)) placeholder()
                            val err = player.error
                            if (isCur && err != null && player.uri == m.uri) VideoError(err)
                        }
                    } else {
                        ZoomImage(
                            uri = m.uri,
                            animate = m.isGif || m.mime == "image/webp",
                            placeholder = placeholder,
                            onTap = { chrome = !chrome },
                            onZoomed = { z -> zoomed = z; if (z) chrome = false },
                            onHdr = { h -> hdrPages[m.id] = h },
                            onDragDelta = { d -> drag.value += d },
                            onDragEnd = { endDrag() },
                            interactive = !textMode && closing == null,
                            resetKey = if (isCur) resetZoom else 0,
                        )
                    }
                }
            }

            val cur = current
            if (textMode && cur != null && !cur.isVideo) {
                TextOverlay(cur) { textMode = false }
            }

            val m = current
            val showChrome = chrome && !textMode
            AnimatedVisibility(showChrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = chromeAlpha() }) {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { requestClose() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                        if (m != null) {
                            val z = Instant.ofEpochMilli(m.taken).atZone(ZoneId.systemDefault())
                            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                Text(titleFmt.format(z), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${timeFmt.format(z)} · ${m.bucket}", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            val src = sources[m.id]
                            if (m.isScreenshot && src?.pkg != null) SourceChip(src.pkg, src.url)
                            val hdrTag = if (m.isVideo) videoHdr else if (hdr) "HDR" else null
                            if (hdrTag != null) SmallTag(hdrTag, Modifier.padding(start = 6.dp), bg = Color.White.copy(alpha = 0.2f))
                            if (!req.fromTrash && m.external == null) {
                                val imp = m.id in important
                                IconButton(onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    actions.important(listOf(m), !imp)
                                }) {
                                    Icon(
                                        if (imp) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                        if (imp) "Remove from important" else "Mark as important",
                                        tint = if (imp) Color(0xFFFFC94A) else Color.White,
                                    )
                                }
                            }
                            if (!req.fromTrash) {
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Color.White) }
                                    ViewerMenu(menu, m, sources[m.id]?.url, actions, onDismiss = { menu = false }, onDetails = { app.details = m })
                                }
                            }
                        }
                    }
                    // How long until a clean-up rule moves this to the trash; tap to keep it.
                    val left = if (m == null || req.fromTrash) null else Cleaner.daysLeft(m, settings, important, rulesStart)
                    AnimatedVisibility(left != null, enter = fadeIn() + androidx.compose.animation.expandVertically(), exit = fadeOut() + androidx.compose.animation.shrinkVertically()) {
                        val d = left ?: 0
                        LaunchedEffect(m?.id, d) { android.util.Log.i("AllPhotos", "Viewer countdown: $d days") }
                        Row(
                            Modifier.padding(start = 16.dp, bottom = 10.dp).clip(RoundedCornerShape(50))
                                .background(if (d <= 3) Color(0xFFB3261E).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f))
                                .clickable {
                                    if (m != null) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        actions.important(listOf(m), true)
                                    }
                                }
                                .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.HourglassBottom, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            GapW(6.dp)
                            Text(
                                when {
                                    d <= 0 -> "Moves to trash today"
                                    d == 1 -> "Moves to trash tomorrow"
                                    else -> "Moves to trash in $d days"
                                },
                                color = Color.White, style = MaterialTheme.typography.labelMedium,
                            )
                            GapW(10.dp)
                            Text("Keep", color = Color(0xFFFFC94A), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (m != null && m.isVideo) {
                CenterPlay(player, showChrome && player.uri == m.uri && !dragging, Modifier.align(Alignment.Center).graphicsLayer { alpha = chromeAlpha() })
                AnimatedVisibility(
                    showChrome && player.uri == m.uri,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 76.dp).graphicsLayer { alpha = chromeAlpha() },
                ) {
                    VideoControls(
                        player, m, fps,
                        onRotate = {
                            landscape = !landscape
                            activity?.requestedOrientation =
                                if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        },
                    )
                }
            }

            AnimatedVisibility(
                showChrome,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = chromeAlpha() },
            ) {
                Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                        .navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (m == null) return@Row
                    if (req.fromTrash) {
                        BarAction(Icons.Rounded.RestoreFromTrash, "Restore") { actions.restore(listOf(m)) { onClose() } }
                        BarAction(Icons.Rounded.DeleteForever, "Delete for good") { confirmForever = true }
                    } else {
                        BarAction(Icons.Rounded.Share, "Share") { actions.share(listOf(m)) }
                        BarAction(Icons.Rounded.Edit, "Edit") { app.editor = EditReq(m, trim = m.isVideo) }
                        if (!m.isVideo) BarAction(Icons.Rounded.TextFields, "Text") {
                            resetZoom++
                            textMode = true
                        }
                        if (m.external == null) BarAction(Icons.Rounded.Delete, "Delete") { actions.trash(listOf(m)) }
                    }
                }
            }
        }
    }

    app.details?.let { d -> DetailsSheet(d) { app.details = null } }

    if (confirmForever && current != null) {
        AlertDialog(
            onDismissRequest = { confirmForever = false },
            title = { Text("Delete for good?") },
            text = { Text("This can't be brought back.") },
            confirmButton = { TextButton(onClick = { confirmForever = false; actions.deleteForever(listOf(current)) { onClose() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmForever = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint)
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Where a screenshot came from: the app's icon and the website (or app name), in the top bar. */
@Composable
private fun SourceChip(pkg: String?, url: String?) {
    val ctx = LocalContext.current
    val icon by produceState(Apps.icon(ctx, null), pkg) { value = withContext(Dispatchers.IO) { Apps.icon(ctx, pkg) } }
    val label = remember(pkg) { Apps.label(ctx, pkg) }
    val site = Links.domain(url)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))
            .clickable {
                val link = Links.openable(url)
                if (link != null) Share.openLink(ctx, link) else Toasts.show("Taken in $label")
            }
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val img = icon
        if (img != null) Image(img, null, Modifier.size(22.dp).clip(CircleShape))
        else Icon(Icons.Rounded.Language, null, tint = Color.White, modifier = Modifier.size(22.dp))
        GapW(6.dp)
        Text(
            site ?: label,
            color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 104.dp),
        )
    }
}

@Composable
private fun ViewerMenu(open: Boolean, m: Media, url: String?, actions: Actions, onDismiss: () -> Unit, onDetails: () -> Unit) {
    val ctx = LocalContext.current
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Details") }, leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = { onDismiss(); onDetails() })
        if (m.isHeic) {
            DropdownMenuItem(text = { Text("Share as JPG") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { onDismiss(); actions.shareAsJpg(listOf(m)) })
            DropdownMenuItem(text = { Text("Save a JPG copy") }, leadingIcon = { Icon(Icons.Rounded.Transform, null) }, onClick = { onDismiss(); actions.convert(listOf(m)) })
        }
        val link = Links.openable(url)
        if (link != null) {
            DropdownMenuItem(text = { Text("Open website") }, leadingIcon = { Icon(Icons.Rounded.Language, null) }, onClick = { onDismiss(); Share.openLink(ctx, link) })
        }
        DropdownMenuItem(text = { Text("Open with…") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) }, onClick = { onDismiss(); runCatching { Share.openWith(ctx, m) } })
        if (!m.isVideo) {
            DropdownMenuItem(text = { Text("Set as wallpaper") }, leadingIcon = { Icon(Icons.Rounded.Wallpaper, null) }, onClick = { onDismiss(); Share.setAs(ctx, m) })
        }
    }
}

/** For files opened from other apps that are not in the phone's media index. */
fun externalMedia(uri: Uri, mime: String?): Media = Media(
    id = -1L - (uri.hashCode().toLong() and 0xffffffL),
    isVideo = mime?.startsWith("video/") == true,
    mime = mime.orEmpty(),
    name = uri.lastPathSegment?.substringAfterLast('/') ?: "File",
    taken = System.currentTimeMillis(),
    added = System.currentTimeMillis(),
    modified = 0,
    size = 0,
    width = 0,
    height = 0,
    durationMs = 0,
    orientation = 0,
    bucketId = 0,
    bucket = "Opened file",
    relPath = "",
    path = "",
    favorite = false,
    owner = null,
    isScreenshot = false,
    chat = null,
    external = uri,
)
