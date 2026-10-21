package com.ankandebnath.allphotos.ui

import android.content.ContentUris
import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ankandebnath.allphotos.Access
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.Prefs

/**
 * Opens a photo or video handed over by another app (a file manager, a chat, the camera). Files
 * already in the gallery open with their whole folder, so you can swipe to the neighbours.
 */
class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT))
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        val secure = intent.action == "android.provider.action.REVIEW_SECURE"
        if (secure) setShowWhenLocked(true)
        val mime = intent.type ?: runCatching { contentResolver.getType(uri) }.getOrNull()
        val prefs = Prefs.get(this)
        if (Access.storage(this)) MediaRepo.start(this)
        setContent {
            val s by prefs.settings.collectAsState()
            AppTheme(s.themeMode, s.dynamicColor, s.palette, forceDark = true) {
                OpenedFile(uri, mime, secure) { finish() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}

/** The gallery id of a content:// address from the media index, if it is one. */
private fun mediaId(uri: Uri): Long? {
    if (uri.authority != MediaStore.AUTHORITY) return null
    return runCatching { ContentUris.parseId(uri) }.getOrNull()?.takeIf { it > 0 }
}

@Composable
private fun OpenedFile(uri: Uri, mime: String?, secure: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val lib by MediaRepo.library.collectAsState()
    val app = remember { AppState() }
    val scope = rememberCoroutineScope()
    val actions = remember { Actions(ctx.applicationContext, scope, app) }
    val id = remember(uri) { mediaId(uri) }
    val waiting = id != null && !secure && Access.storage(ctx) && lib == null
    val req = remember(uri, lib) {
        val l = lib
        val inLib = if (id != null && l != null && !secure) l.byId[id] else null
        if (inLib != null && l != null) {
            val folder = l.items.filter { it.bucketId == inLib.bucketId }
            ViewerReq(folder.map { it.id }, folder.indexOfFirst { it.id == inLib.id }.coerceAtLeast(0))
        } else {
            val m: Media = externalMedia(uri, mime)
            ViewerReq(listOf(m.id), 0, explicit = listOf(m))
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (waiting) {
            LoadingIndicator(Modifier.align(Alignment.Center))
        } else {
            Viewer(req, lib ?: com.ankandebnath.allphotos.data.Library.EMPTY, app, actions, onClose = onClose)
        }
        val ed = app.editor
        AnimatedVisibility(visible = ed != null, enter = fadeIn(), exit = fadeOut()) {
            if (ed != null) {
                if (ed.trim) TrimScreen(ed.media, onClose = { app.editor = null }) else Editor(ed.media, onClose = { app.editor = null })
            }
        }
        ToastHost(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 110.dp)
    }
}
