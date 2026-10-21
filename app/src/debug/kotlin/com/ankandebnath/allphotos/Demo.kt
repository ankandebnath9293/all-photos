package com.ankandebnath.allphotos

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.Prefs
import com.ankandebnath.allphotos.ui.AppState
import com.ankandebnath.allphotos.ui.Dest
import com.ankandebnath.allphotos.ui.EditReq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug-only switches for the emulator tests: skip the first-run pages, open a tab or a screen,
 * pick a theme, and run the on-device self test.
 */
object Demo {
    fun apply(activity: ComponentActivity, intent: Intent?, app: AppState) {
        val i = intent ?: return
        val prefs = Prefs.get(activity)
        if (i.getBooleanExtra("skip", false)) prefs.update { it.copy(onboarded = true) }
        if (i.hasExtra("theme")) prefs.update { it.copy(themeMode = i.getIntExtra("theme", 0)) }
        if (i.hasExtra("dynamic")) prefs.update { it.copy(dynamicColor = i.getBooleanExtra("dynamic", true)) }
        if (i.hasExtra("cols")) prefs.update { it.copy(gridCols = i.getIntExtra("cols", 4)) }
        if (i.hasExtra("seen")) prefs.seenVersion = i.getIntExtra("seen", 0)
        app.tab = i.getIntExtra("tab", 0)
        when (i.getStringExtra("open")) {
            "similar" -> app.open(Dest.Similar)
            "large" -> app.open(Dest.Large)
            "trash" -> app.open(Dest.Trash)
            "settings" -> app.open(Dest.Settings)
            "search" -> app.open(Dest.Search)
        }
        val view = i.getIntExtra("view", -1)
        val wantVideo = i.getBooleanExtra("video", false)
        val wantPhoto = i.getBooleanExtra("photo", false)
        val details = i.getBooleanExtra("details", false)
        val edit = i.getBooleanExtra("edit", false)
        val trim = i.getBooleanExtra("trim", false)
        if (view >= 0 || wantVideo || wantPhoto || edit || trim) {
            activity.lifecycleScope.launch {
                val lib = MediaRepo.library.filterNotNull().first { it.items.isNotEmpty() }
                val list = lib.items
                val idx = if (wantVideo || trim) list.indexOfFirst { it.isVideo } else if (edit || wantPhoto) list.indexOfFirst { !it.isVideo && !it.isScreenshot } else view
                if (idx < 0) return@launch
                when {
                    edit -> app.editor = EditReq(list[idx], trim = false)
                    trim -> app.editor = EditReq(list[idx], trim = true)
                    else -> {
                        app.view(list, idx)
                        if (details) {
                            delay(1500)
                            app.details = list[idx]
                            Log.i("AllPhotos", "Demo opened details for ${list[idx].name}")
                        }
                    }
                }
            }
        }
        if (i.getBooleanExtra("selftest", false)) {
            activity.lifecycleScope.launch(Dispatchers.IO) {
                val line = runCatching { SelfTest.run(activity.applicationContext) }.getOrElse { "error=${it.javaClass.simpleName}:${it.message}" }
                Log.i("ALLPHOTOS_SELFTEST", line)
            }
        }
    }
}
