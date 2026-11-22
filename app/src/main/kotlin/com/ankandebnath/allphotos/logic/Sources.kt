package com.ankandebnath.allphotos.logic

/** One moment the screen changed: the app in front and, in a browser, the address shown. */
data class Foreground(val time: Long, val pkg: String, val url: String? = null)

/** Where a screenshot came from. */
data class Source(val pkg: String?, val url: String?, val how: Int) {
    companion object {
        const val NONE = 0
        /** Seen live by the screenshot watcher. */
        const val LIVE = 1
        /** Worked out later from the phone's app usage history. */
        const val USAGE = 2
        /** The address was read from the text in the screenshot. */
        const val TEXT = 3
        /** Looked for and nothing was found. */
        const val UNKNOWN = -1
    }
}

object Sources {
    /** Windows that sit on top of an app without replacing it. */
    private val ignored = setOf(
        "com.android.systemui", "android", "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard", "com.touchtype.swiftkey",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.google.android.as", "com.google.android.apps.screenshot",
    )

    fun isIgnored(pkg: String, ownPkg: String, keyboards: Set<String> = emptySet()): Boolean =
        pkg in ignored || pkg == ownPkg || pkg in keyboards

    /** Logged when the screenshot watcher stops, so an old entry is never taken as what was on screen later. */
    const val OFF = "(watcher off)"

    /** Opening Recents shows the launcher; a screenshot this soon after leaving an app is of that app. */
    const val RECENTS_MS = 10_000L

    /**
     * The app on screen when a screenshot was taken at [shotTime]: the last change before it.
     * [slackMs] covers the short gap between pressing the buttons and the file's timestamp.
     */
    fun attribute(log: List<Foreground>, shotTime: Long, ownPkg: String, slackMs: Long = 800, keyboards: Set<String> = emptySet()): Foreground? {
        val before = log.filter { it.time <= shotTime + slackMs && !isIgnored(it.pkg, ownPkg, keyboards) }.sortedBy { it.time }
        val last = before.lastOrNull() ?: return null
        if (last.pkg == OFF) return null
        // The Screenshot button in Recents: the launcher is in front, but the picture is of the app just left.
        if (isLauncher(last.pkg) && shotTime - last.time <= RECENTS_MS) {
            val prev = before.getOrNull(before.size - 2)
            if (prev != null && prev.pkg != OFF && !isLauncher(prev.pkg)) return prev
        }
        return last
    }

    /** Launchers mean the screenshot was of the home screen. */
    fun isLauncher(pkg: String): Boolean =
        pkg.contains("launcher") || pkg == "com.google.android.apps.nexuslauncher" || pkg == "com.sec.android.app.launcher"
}

/** Address bars of the browsers people use. */
object Browsers {
    private val ids = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.chrome.dev" to listOf("url_bar"),
        "com.chrome.canary" to listOf("url_bar"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view"),
        "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view", "display_url"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
        "com.opera.browser" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
        "com.mi.globalbrowser" to listOf("url"),
    )

    fun isBrowser(pkg: String): Boolean = pkg in ids

    /** Full view ids to look for, e.g. com.android.chrome:id/url_bar. */
    fun urlViewIds(pkg: String): List<String> = ids[pkg].orEmpty().map { "$pkg:id/$it" }
}
