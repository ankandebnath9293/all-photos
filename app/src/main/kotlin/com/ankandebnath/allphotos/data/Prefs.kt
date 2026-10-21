package com.ankandebnath.allphotos.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the user has chosen. Kept on the phone in a small settings file. */
data class Settings(
    val onboarded: Boolean = false,
    /** 0 follow the phone, 1 light, 2 dark. */
    val themeMode: Int = 0,
    val dynamicColor: Boolean = true,
    val palette: Int = 0,
    val gridCols: Int = 4,
    val shotAutoTrash: Boolean = true,
    val shotDays: Int = 30,
    val shotNotify: Boolean = true,
    val whatsappRule: Boolean = true,
    val telegramRule: Boolean = true,
    val chatDays: Int = 30,
    val indexOnlyCharging: Boolean = true,
    val pinnedAlbums: Set<Long> = emptySet(),
    val facebookRule: Boolean = false,
    /** Group bursts of near-identical photos into one stack in the grid. */
    val stackSimilar: Boolean = false,
    val showDates: Boolean = true,
    /** 0 minimal, 1 standard, 2 rich. */
    val motion: Int = 1,
)

class Prefs private constructor(private val ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<Settings> = state.asStateFlow()
    val now: Settings get() = state.value

    private fun read() = Settings(
        onboarded = sp.getBoolean("onboarded", false),
        themeMode = sp.getInt("themeMode", 0),
        dynamicColor = sp.getBoolean("dynamicColor", true),
        palette = sp.getInt("palette", 0),
        gridCols = sp.getInt("gridCols", 4).coerceIn(2, 6),
        shotAutoTrash = sp.getBoolean("shotAutoTrash", true),
        shotDays = sp.getInt("shotDays", 30),
        shotNotify = sp.getBoolean("shotNotify", true),
        whatsappRule = sp.getBoolean("whatsappRule", true),
        telegramRule = sp.getBoolean("telegramRule", true),
        chatDays = sp.getInt("chatDays", 30),
        indexOnlyCharging = sp.getBoolean("indexOnlyCharging", true),
        pinnedAlbums = sp.getStringSet("pinnedAlbums", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
        facebookRule = sp.getBoolean("facebookRule", false),
        stackSimilar = sp.getBoolean("stackSimilar", false),
        showDates = sp.getBoolean("showDates", true),
        motion = sp.getInt("motion", 1).coerceIn(0, 2),
    )

    @Synchronized
    fun update(change: (Settings) -> Settings) {
        val s = change(state.value)
        sp.edit {
            putBoolean("onboarded", s.onboarded)
            putInt("themeMode", s.themeMode)
            putBoolean("dynamicColor", s.dynamicColor)
            putInt("palette", s.palette)
            putInt("gridCols", s.gridCols)
            putBoolean("shotAutoTrash", s.shotAutoTrash)
            putInt("shotDays", s.shotDays)
            putBoolean("shotNotify", s.shotNotify)
            putBoolean("whatsappRule", s.whatsappRule)
            putBoolean("telegramRule", s.telegramRule)
            putInt("chatDays", s.chatDays)
            putBoolean("indexOnlyCharging", s.indexOnlyCharging)
            putStringSet("pinnedAlbums", s.pinnedAlbums.map { it.toString() }.toSet())
            putBoolean("facebookRule", s.facebookRule)
            putBoolean("stackSimilar", s.stackSimilar)
            putBoolean("showDates", s.showDates)
            putInt("motion", s.motion)
        }
        state.value = s
    }

    /**
     * The clean-up rules only count files from this moment: the start of the day the app was first
     * installed. Older screenshots and chat files are never moved to the trash automatically.
     */
    var rulesStart: Long
        set(v) = sp.edit { putLong("rulesStart", v) }
        get() {
            val saved = sp.getLong("rulesStart", 0)
            if (saved > 0) return saved
            val installed = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).firstInstallTime }
                .getOrDefault(System.currentTimeMillis())
            val start = com.ankandebnath.allphotos.logic.Rules.startOfDay(installed)
            sp.edit { putLong("rulesStart", start) }
            return start
        }

    /** Set once the files that version 1.0 moved to the trash too early have been put back. */
    var restoredEarlyTrash: Boolean
        get() = sp.getBoolean("restoredEarlyTrash", false)
        set(v) = sp.edit { putBoolean("restoredEarlyTrash", v) }

    /** How many early-trashed files were put back, so the app can say so once. 0 once shown. */
    var restoredCount: Int
        get() = sp.getInt("restoredCount", 0)
        set(v) = sp.edit { putInt("restoredCount", v) }

    /** Small bits of state that are not settings. */
    var seenVersion: Int
        get() = sp.getInt("seenVersion", 0)
        set(v) = sp.edit { putInt("seenVersion", v) }

    /** Newest screenshot already handled, so each one is processed once. */
    var lastShotId: Long
        get() = sp.getLong("lastShotId", -1)
        set(v) = sp.edit { putLong("lastShotId", v) }

    var lastCleanupAt: Long
        get() = sp.getLong("lastCleanupAt", 0)
        set(v) = sp.edit { putLong("lastCleanupAt", v) }

    /** How many files the automatic rules moved to the trash, per rule, the last time they ran. */
    var lastCleanup: String
        get() = sp.getString("lastCleanup", "").orEmpty()
        set(v) = sp.edit { putString("lastCleanup", v) }

    /** Running totals of what the automatic rules have moved to the trash. */
    var cleanedTotal: Int
        get() = sp.getInt("cleanedTotal", 0)
        set(v) = sp.edit { putInt("cleanedTotal", v) }

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(ctx: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(ctx.applicationContext).also { instance = it }
        }
    }
}
