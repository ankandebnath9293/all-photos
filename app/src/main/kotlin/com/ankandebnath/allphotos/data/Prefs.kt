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
)

class Prefs private constructor(ctx: Context) {
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
        }
        state.value = s
    }

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
