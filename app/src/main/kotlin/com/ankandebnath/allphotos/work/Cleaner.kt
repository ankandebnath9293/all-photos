package com.ankandebnath.allphotos.work

import android.content.Context
import android.util.Log
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.Library
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.Ops
import com.ankandebnath.allphotos.data.Prefs
import com.ankandebnath.allphotos.data.Settings
import com.ankandebnath.allphotos.logic.Chat
import com.ankandebnath.allphotos.logic.Rules

/** What the automatic rules would move to the trash right now. */
class DuePlan(val shots: List<Media>, val whatsapp: List<Media>, val telegram: List<Media>) {
    val all: List<Media> get() = (shots + whatsapp + telegram).distinctBy { it.id }
    val bytes: Long get() = all.sumOf { it.size }
}

/** The automatic clean-up: old screenshots and old WhatsApp and Telegram media go to the trash. */
object Cleaner {
    private const val TAG = "AllPhotos"

    fun plan(lib: Library, s: Settings, important: Set<Long>, now: Long = System.currentTimeMillis()): DuePlan {
        val shots = if (s.shotAutoTrash) Rules.due(lib.screenshots, now, s.shotDays, important, { it.id }, { it.taken }) else emptyList()
        val chats = lib.items.filter { it.chat != null }
        // Chat files count from when they arrived on the phone; their camera date is often missing or old.
        val wa = if (s.whatsappRule) Rules.due(chats.filter { it.chat == Chat.WHATSAPP }, now, s.chatDays, important, { it.id }, { it.added }) else emptyList()
        val tg = if (s.telegramRule) Rules.due(chats.filter { it.chat == Chat.TELEGRAM }, now, s.chatDays, important, { it.id }, { it.added }) else emptyList()
        return DuePlan(shots, wa, tg)
    }

    /** Screenshots that will go to the trash within [days] days. */
    fun upcoming(lib: Library, s: Settings, important: Set<Long>, days: Int, now: Long = System.currentTimeMillis()): Int {
        if (!s.shotAutoTrash) return 0
        return lib.screenshots.count { it.id !in important && Rules.daysLeft(it.taken, now, s.shotDays) in 1..days }
    }

    /** Runs the rules. Returns how many files were moved to the trash. */
    suspend fun run(ctx: Context): Int {
        val app = ctx.applicationContext
        if (MediaRepo.library.value == null) MediaRepo.refresh(app)
        val lib = MediaRepo.library.value ?: return 0
        val prefs = Prefs.get(app)
        val db = Db.get(app)
        db.warm()
        val p = plan(lib, prefs.now, db.important.value)
        var moved = 0
        if (p.all.isNotEmpty()) {
            val r = Ops.trash(app, p.all, allowDialog = false)
            moved = r.done
            Log.i(TAG, "Clean-up: ${p.shots.size} screenshots, ${p.whatsapp.size} WhatsApp, ${p.telegram.size} Telegram → $moved moved")
        }
        val purged = runCatching { Ops.purgeExpired(app) }.getOrDefault(0)
        if (purged > 0) Log.i(TAG, "Clean-up: $purged expired trash items deleted")
        prefs.lastCleanupAt = System.currentTimeMillis()
        prefs.lastCleanup = "${p.shots.size},${p.whatsapp.size},${p.telegram.size}"
        prefs.cleanedTotal = prefs.cleanedTotal + moved
        return moved
    }
}
