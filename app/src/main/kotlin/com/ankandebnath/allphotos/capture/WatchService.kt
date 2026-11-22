package com.ankandebnath.allphotos.capture

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.ankandebnath.allphotos.logic.Browsers
import com.ankandebnath.allphotos.logic.Sources

/**
 * Watches which app is in front and, in browsers, the address bar, so each screenshot can be
 * labelled with where it came from. Nothing else on screen is read.
 */
class WatchService : AccessibilityService() {
    private var lastRead = 0L
    private var keyboards: Set<String> = emptySet()

    /**
     * Whether screen-content changes are being delivered. They are only needed to follow the
     * address bar in a browser, so outside browsers only app switches are delivered; that keeps
     * the phone from waking this app for every scroll and animation in every other app.
     */
    private var watchingContent: Boolean? = null

    private fun watchContent(on: Boolean) {
        if (watchingContent == on) return
        val info = serviceInfo ?: return
        info.eventTypes = if (on) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        } else {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }
        runCatching { serviceInfo = info }.onSuccess { watchingContent = on }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        keyboards = runCatching {
            getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.toSet()
        }.getOrNull().orEmpty()
        ShotWatch.start(this)
        FgLog.prune(this)
        // A browser may already be on screen when the service starts.
        val front = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        watchContent(front != null && Browsers.isBrowser(front))
    }

    private val handler = Handler(Looper.getMainLooper())

    /**
     * A second look shortly after each change: the app whose window is actually active. Overlays
     * such as Circle to Search or chat heads send their own change but sit on top of the real app,
     * and closing them sends nothing, so without this the overlay would be taken as the source.
     */
    private val recheck = Runnable {
        val front = activeApp() ?: return@Runnable
        if (Sources.isIgnored(front, packageName, keyboards)) return@Runnable
        FgLog.app(this, front)
        watchContent(Browsers.isBrowser(front))
    }

    private fun activeApp(): String? = runCatching {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        (apps.firstOrNull { it.isActive } ?: apps.firstOrNull { it.isFocused })?.root?.packageName?.toString()
    }.getOrNull()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        val stateChange = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (stateChange) {
            handler.removeCallbacks(recheck)
            handler.postDelayed(recheck, 1_000)
        }
        if (Sources.isIgnored(pkg, packageName, keyboards)) return
        if (stateChange) {
            FgLog.app(this, pkg)
            watchContent(Browsers.isBrowser(pkg))
        }
        if (Browsers.isBrowser(pkg)) {
            val now = SystemClock.uptimeMillis()
            if (stateChange || now - lastRead > 700) {
                lastRead = now
                readUrl(pkg)
            }
        }
    }

    private fun readUrl(pkg: String) {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        if (root.packageName?.toString() != pkg) return
        for (id in Browsers.urlViewIds(pkg)) {
            val node = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull()?.firstOrNull() ?: continue
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                FgLog.url(this, pkg, text)
                return
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(recheck)
        FgLog.off(this)
        // The screenshot observer belongs to the whole app (App.start also uses it), so it keeps running.
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
            private set

        /** True when the user has switched the service on in the phone's Accessibility settings. */
        fun enabled(ctx: Context): Boolean {
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val me = ComponentName(ctx, WatchService::class.java)
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
