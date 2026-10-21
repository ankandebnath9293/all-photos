package com.ankandebnath.allphotos.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ankandebnath.allphotos.App
import com.ankandebnath.allphotos.Demo
import com.ankandebnath.allphotos.data.Library
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.Prefs
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app = AppState()
    private lateinit var consent: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            lifecycleScope.launch {
                MediaRepo.refresh(this@MainActivity)
                MediaRepo.trashTick.value++
            }
        }
        app.askConsent = { pi -> runCatching { consent.launch(IntentSenderRequest.Builder(pi).build()) } }
        val prefs = Prefs.get(this)
        Demo.apply(this, intent, app)
        handleOpen(intent)
        val pick = intent.action == Intent.ACTION_PICK || intent.action == Intent.ACTION_GET_CONTENT
        val multiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        val pickType = intent.type.orEmpty()

        setContent {
            val s by prefs.settings.collectAsState()
            val dark = isDark(s.themeMode)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppTheme(s.themeMode, s.dynamicColor, s.palette) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val g = rememberGrants()
                    LaunchedEffect(g.storage) { if (g.storage) (application as App).start() }
                    when {
                        !s.onboarded -> Onboarding { }
                        !g.storage -> AccessNeeded()
                        pick -> Picker(pickType, multiple) { list -> deliver(list, multiple) }
                        else -> {
                            Home(app)
                            var whatsNew by remember { mutableStateOf(prefs.seenVersion in 1 until WhatsNew.VERSION) }
                            LaunchedEffect(Unit) { if (prefs.seenVersion == 0) prefs.seenVersion = WhatsNew.VERSION }
                            if (whatsNew) WhatsNewDialog {
                                whatsNew = false
                                prefs.seenVersion = WhatsNew.VERSION
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpen(intent)
    }

    /** Opens a screenshot from its notification. */
    private fun handleOpen(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_ID, -1L) ?: -1L
        if (id < 0) return
        intent?.removeExtra(EXTRA_OPEN_ID)
        lifecycleScope.launch {
            MediaRepo.refresh(this@MainActivity)
            val lib = MediaRepo.library.filterNotNull().first()
            val shots = lib.screenshots
            val i = shots.indexOfFirst { it.id == id }
            app.stack.clear()
            app.tab = 2
            if (i >= 0) app.view(shots, i)
        }
    }

    private fun deliver(list: List<Media>, multiple: Boolean) {
        if (list.isEmpty()) return
        val data = Intent()
        data.data = list[0].uri
        if (multiple || list.size > 1) {
            val clip = ClipData.newUri(contentResolver, "media", list[0].uri)
            for (m in list.drop(1)) clip.addItem(ClipData.Item(m.uri))
            data.clipData = clip
        }
        data.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {
        const val EXTRA_OPEN_ID = "open_id"
    }
}

@Composable
private fun AccessNeeded() {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        EmptyState(
            Icons.Rounded.Folder, "Access needed",
            "All Photos needs access to all files to show your photos and videos.",
            Modifier.padding(top = 120.dp),
        ) { Button(onClick = { openStorageAccess(ctx) }) { Text("Allow access") } }
    }
}

/** Lets another app pick photos or videos from here. */
@Composable
private fun Picker(type: String, multiple: Boolean, onPick: (List<Media>) -> Unit) {
    val libState by MediaRepo.library.collectAsState()
    val lib = libState ?: Library.EMPTY
    val items = remember(lib, type) {
        when {
            type.startsWith("image") || type.endsWith("/image") -> lib.items.filter { !it.isVideo }
            type.startsWith("video") || type.endsWith("/video") -> lib.items.filter { it.isVideo }
            else -> lib.items
        }
    }
    val app = remember { AppState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ctx = LocalContext.current
    val actions = remember { Actions(ctx.applicationContext, scope, app) }
    val selection = remember { Selection() }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        GridPage(
            items = items,
            title = if (multiple) "Choose photos" else "Choose a photo",
            sub = if (multiple) "Tap to select, then Done" else "Tap one to send it",
            app = app,
            actions = actions,
            bottom = bottom,
            state = rememberLazyGridState(),
            selection = selection,
            mode = SelectMode.PICK,
            onOpen = { list, i -> if (multiple) selection.toggle(list[i].id) else onPick(listOf(list[i])) },
            onPick = onPick,
        )
        ToastHost(bottom + 16.dp)
    }
}
