package com.ankandebnath.allphotos

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Bundle
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
        registerActivityLifecycleCallbacks(ScreenCounter())
        // The process is often started in the background (by the screenshot watcher), so the library is read when a screen opens.
        if (Access.storage(this)) start(readNow = false)
    }

    /** Starts reading the library and the background jobs, once storage access is granted. */
    fun start(readNow: Boolean = true) {
        MediaRepo.start(this, readNow)
        ShotWatch.start(this)
        // Marks where screenshot handling starts, so only new screenshots get a notification.
        scope.launch(Dispatchers.IO) { runCatching { com.ankandebnath.allphotos.capture.ShotTracker.process(this@App) } }
        if (Prefs.get(this).now.onboarded) Work.schedule(this)
        // Put back files version 1.0 moved to the trash from before the app was installed.
        scope.launch(Dispatchers.IO) {
            val n = runCatching { com.ankandebnath.allphotos.work.Cleaner.restoreEarly(this@App) }.getOrDefault(0)
            if (n > 0) {
                com.ankandebnath.allphotos.ui.Toasts.show("Put back $n older file${if (n == 1) "" else "s"} from the trash")
                Prefs.get(this@App).restoredCount = 0
            }
        }
    }

    /** Tells the library whether any screen of the app is on show. */
    private inner class ScreenCounter : ActivityLifecycleCallbacks {
        private var started = 0
        override fun onActivityStarted(a: Activity) {
            if (started++ == 0) MediaRepo.setVisible(this@App, true)
        }
        override fun onActivityStopped(a: Activity) {
            started = (started - 1).coerceAtLeast(0)
            if (started == 0) MediaRepo.setVisible(this@App, false)
        }
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityResumed(a: Activity) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
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
