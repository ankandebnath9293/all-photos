package com.ankandebnath.allphotos.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val titleFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/** The full-screen viewer: swipe between photos and videos, zoom, play, and act on them. */
@Composable
fun Viewer(req: ViewerReq, lib: Library, app: AppState, actions: Actions, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val view = LocalView.current
    val db = remember { Db.get(ctx) }
    val important by db.important.collectAsState()
    val sources by db.sources.collectAsState()

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
    var dismiss by remember { mutableFloatStateOf(0f) }
    var menu by remember { mutableStateOf(false) }
    var confirmForever by remember { mutableStateOf(false) }
    val hdrPages = remember(req) { mutableStateMapOf<Long, Boolean>() }
    val player = remember { PlayerHolder(ctx.applicationContext) }
    var landscape by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            activity?.window?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { w -> WindowCompat.getInsetsController(w, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    // Light up the screen for HDR photos; videos handle HDR on their own surface.
    val hdr = current != null && !current.isVideo && hdrPages[current.id] == true
    LaunchedEffect(hdr) {
        activity?.window?.colorMode = if (hdr) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
    }

    // Hide the status and navigation bars along with the controls.
    LaunchedEffect(chrome) {
        val w = activity?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(w, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chrome) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
    }

    // Play the video on the page you land on; stop when you move to a photo.
    LaunchedEffect(current?.id, pager.isScrollInProgress) {
        if (pager.isScrollInProgress) return@LaunchedEffect
        if (current != null && current.isVideo) player.load(current, null) else player.stop()
    }
    LaunchedEffect(player.wantsPlay, chrome, current?.id) {
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

    BackHandler { onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - dismiss * 0.85f))) {
        HorizontalPager(
            state = pager,
            key = { items[it].id },
            beyondViewportPageCount = 1,
            pageSpacing = 16.dp,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val m = items[page]
            val isCurrent = page == pager.currentPage && !pager.isScrollInProgress
            val placeholder: @Composable () -> Unit = { Thumb(m, 640, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, bg = Color.Transparent) }
            if (m.isVideo) {
                VideoSurface(player, current = isCurrent && player.uri == m.uri, placeholder = placeholder, onTap = { chrome = !chrome })
            } else {
                ZoomImage(
                    uri = m.uri,
                    animate = m.isGif || m.mime == "image/webp",
                    placeholder = placeholder,
                    onTap = { chrome = !chrome },
                    onZoomed = { z -> zoomed = z; if (z) chrome = false },
                    onHdr = { h -> hdrPages[m.id] = h },
                    onDrag = { p -> dismiss = p },
                    onDismiss = onClose,
                )
            }
        }

        val m = current
        val showChrome = chrome && dismiss < 0.05f
        AnimatedVisibility(showChrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))),
            ) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                    if (m != null) {
                        val z = Instant.ofEpochMilli(m.taken).atZone(ZoneId.systemDefault())
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(titleFmt.format(z), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${timeFmt.format(z)} · ${m.bucket}", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        val hdrTag = if (m.isVideo) videoHdr else if (hdr) "HDR" else null
                        if (hdrTag != null) SmallTag(hdrTag, Modifier.padding(end = 4.dp), bg = Color.White.copy(alpha = 0.2f))
                        if (!req.fromTrash) {
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Color.White) }
                                ViewerMenu(menu, m, sources[m.id]?.url, actions, onDismiss = { menu = false }, onDetails = { app.details = m })
                            }
                        }
                    }
                }
            }
        }

        // Where a screenshot came from.
        val src = m?.let { sources[it.id] }
        AnimatedVisibility(
            showChrome && m != null && m.isScreenshot && src?.pkg != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (m?.isVideo == true) 200.dp else 96.dp),
        ) {
            if (m != null && src != null) SourceChip(src.pkg, src.url)
        }

        if (m != null && m.isVideo) {
            CenterPlay(player, showChrome && player.uri == m.uri, Modifier.align(Alignment.Center))
            AnimatedVisibility(
                showChrome && player.uri == m.uri,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 76.dp),
            ) {
                VideoControls(
                    player, m, fps,
                    onTrim = { app.editor = EditReq(m, trim = true) },
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
            modifier = Modifier.align(Alignment.BottomCenter),
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
                } else if (m.external != null) {
                    BarAction(Icons.Rounded.Share, "Share") { actions.share(listOf(m)) }
                    if (!m.isVideo) BarAction(Icons.Rounded.Edit, "Edit") { app.editor = EditReq(m, trim = false) }
                    BarAction(Icons.Rounded.Info, "Details") { app.details = m }
                } else {
                    val imp = m.id in important
                    BarAction(Icons.Rounded.Share, "Share") { actions.share(listOf(m)) }
                    BarAction(Icons.Rounded.Edit, if (m.isVideo) "Trim" else "Edit") { app.editor = EditReq(m, trim = m.isVideo) }
                    BarAction(if (imp) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (imp) "Important" else "Mark", tint = if (imp) Color(0xFFFFC94A) else Color.White) {
                        actions.important(listOf(m), !imp)
                    }
                    BarAction(Icons.Rounded.Info, "Details") { app.details = m }
                    BarAction(Icons.Rounded.Delete, "Delete") { actions.trash(listOf(m)) }
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
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint)
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun SourceChip(pkg: String?, url: String?) {
    val ctx = LocalContext.current
    val icon by produceState(Apps.icon(ctx, null), pkg) { value = withContext(Dispatchers.IO) { Apps.icon(ctx, pkg) } }
    val label = remember(pkg) { Apps.label(ctx, pkg) }
    val site = Links.clean(url)
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.55f),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = site != null) { Links.openable(url)?.let { Share.openLink(ctx, it) } },
    ) {
        Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val img = icon
            if (img != null) Image(img, null, Modifier.size(26.dp).clip(CircleShape))
            else Icon(Icons.Rounded.Language, null, tint = Color.White, modifier = Modifier.size(26.dp))
            GapW(8.dp)
            Text(
                if (site != null) "$label · $site" else label,
                color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
            if (site != null) {
                GapW(6.dp)
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open website", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
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
