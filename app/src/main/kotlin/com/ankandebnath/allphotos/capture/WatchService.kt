package com.ankandebnath.allphotos.capture

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        keyboards = runCatching {
            getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.toSet()
        }.getOrNull().orEmpty()
        ShotWatch.start(this)
        FgLog.prune(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (Sources.isIgnored(pkg, packageName, keyboards)) return
        val stateChange = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (stateChange) FgLog.app(this, pkg)
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
        ShotWatch.stop(this)
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
