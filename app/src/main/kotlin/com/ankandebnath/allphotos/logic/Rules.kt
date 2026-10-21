package com.ankandebnath.allphotos.logic

/** Which chat app a file was saved by. */
enum class Chat(val label: String) { WHATSAPP("WhatsApp"), TELEGRAM("Telegram"), FACEBOOK("Facebook & Messenger") }

/** How files are recognised, and when the automatic clean-up rules apply. */
object Rules {
    const val DAY_MS = 24L * 60 * 60 * 1000

    /** True for anything the phone saved as a screenshot. */
    fun isScreenshot(relativePath: String?, bucket: String?, name: String?): Boolean {
        val rp = relativePath.orEmpty().lowercase()
        if (rp.endsWith("screenshots/") || rp.contains("/screenshots/")) return true
        if (bucket.equals("Screenshots", ignoreCase = true)) return true
        val n = name.orEmpty()
        return n.startsWith("Screenshot_") && (rp.startsWith("pictures/") || rp.startsWith("dcim/"))
    }

    // Only received photos, videos and GIFs. Stickers, profile photos and voice notes are left alone.
    private val whatsappFolders = listOf(
        "whatsapp images", "whatsapp video", "whatsapp animated gifs",
        "whatsapp business images", "whatsapp business video", "whatsapp business animated gifs",
    )
    private val telegramFolders = listOf("telegram images", "telegram video")
    private val facebookFolders = listOf(
        "pictures/messenger/", "pictures/facebook/", "dcim/facebook/", "dcim/messenger/",
        "movies/messenger/", "movies/facebook/", "android/media/com.facebook.orca/", "android/media/com.facebook.katana/",
        "pictures/facebook lite/", "pictures/messenger lite/",
    )
    private val facebookApps = setOf("com.facebook.orca", "com.facebook.katana", "com.facebook.lite", "com.facebook.mlite")

    /** The chat app whose media folder this file lives in, or null. */
    fun chatOf(relativePath: String?, owner: String? = null): Chat? {
        val rp = relativePath.orEmpty().lowercase()
        if (whatsappFolders.any { rp.contains("/$it/") || rp.startsWith("$it/") }) return Chat.WHATSAPP
        if (telegramFolders.any { rp.contains("/$it/") || rp.startsWith("$it/") }) return Chat.TELEGRAM
        if (rp.startsWith("pictures/telegram/") || rp.startsWith("movies/telegram/")) return Chat.TELEGRAM
        if (rp.contains("telegram") && (owner == "org.telegram.messenger" || owner == "org.telegram.messenger.web")) return Chat.TELEGRAM
        if (facebookFolders.any { rp.startsWith(it) }) return Chat.FACEBOOK
        // Photos Messenger or Facebook saved somewhere else, but never screenshots.
        if (owner in facebookApps && !rp.contains("screenshots")) return Chat.FACEBOOK
        return null
    }

    /** Whole days until a file is due (0 means due now). */
    fun daysLeft(timeMs: Long, nowMs: Long, keepDays: Int): Int {
        val due = timeMs + keepDays * DAY_MS
        if (due <= nowMs) return 0
        return ((due - nowMs + DAY_MS - 1) / DAY_MS).toInt()
    }

    fun isDue(timeMs: Long, nowMs: Long, keepDays: Int): Boolean =
        timeMs > 0 && nowMs - timeMs >= keepDays * DAY_MS

    /**
     * Which files an automatic rule should move to the trash now. Important files are never touched,
     * and nothing from before [startMs] (the day the app was installed) is ever counted.
     * [time] is the moment the rule counts from (when a screenshot was taken, when a chat file arrived).
     */
    fun <T> due(items: List<T>, nowMs: Long, keepDays: Int, important: Set<Long>, startMs: Long, id: (T) -> Long, time: (T) -> Long): List<T> =
        items.filter { id(it) !in important && time(it) >= startMs && isDue(time(it), nowMs, keepDays) }

    /** Whether a file falls under the clean-up rules at all (taken or received after [startMs]). */
    fun counts(timeMs: Long, startMs: Long): Boolean = timeMs >= startMs

    /** Midnight (in [zone]) at the start of the day containing [ms]. */
    fun startOfDay(ms: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}
