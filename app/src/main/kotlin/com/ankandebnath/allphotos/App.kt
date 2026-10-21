package com.ankandebnath.allphotos

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Environment
import com.ankandebnath.allphotos.capture.ShotWatch
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.Prefs
import com.ankandebnath.allphotos.media.Thumbs
import com.ankandebnath.allphotos.notify.Notifier
import com.ankandebnath.allphotos.work.Work
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifier.channels(this)
        scope.launch(Dispatchers.IO) { runCatching { Db.get(this@App).warm() } }
        if (Access.storage(this)) start()
    }

    /** Starts reading the library and the background jobs, once storage access is granted. */
    fun start() {
        MediaRepo.start(this)
        ShotWatch.start(this)
        // Marks where screenshot handling starts, so only new screenshots get a notification.
        scope.launch(Dispatchers.IO) { runCatching { com.ankandebnath.allphotos.capture.ShotTracker.process(this@App) } }
        if (Prefs.get(this).now.onboarded) Work.schedule(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) Thumbs.clear()
    }
}

/** What the phone currently lets the app do. */
object Access {
    fun storage(ctx: android.content.Context): Boolean = Environment.isExternalStorageManager()

    fun manageMedia(ctx: android.content.Context): Boolean =
        runCatching { android.provider.MediaStore.canManageMedia(ctx) }.getOrDefault(false)
}
