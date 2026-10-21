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
class DuePlan(val shots: List<Media>, val whatsapp: List<Media>, val telegram: List<Media>, val facebook: List<Media>) {
    val all: List<Media> get() = (shots + whatsapp + telegram + facebook).distinctBy { it.id }
    val bytes: Long get() = all.sumOf { it.size }
}

/**
 * The automatic clean-up: screenshots and WhatsApp, Telegram (and, if switched on, Facebook and
 * Messenger) media go to the trash after a while. Only files from after the app was installed count.
 */
object Cleaner {
    private const val TAG = "AllPhotos"

    fun ruleOn(s: Settings, c: Chat) = when (c) {
        Chat.WHATSAPP -> s.whatsappRule
        Chat.TELEGRAM -> s.telegramRule
        Chat.FACEBOOK -> s.facebookRule
    }

    fun plan(lib: Library, s: Settings, important: Set<Long>, start: Long, now: Long = System.currentTimeMillis()): DuePlan {
        val shots = if (s.shotAutoTrash) Rules.due(lib.screenshots, now, s.shotDays, important, start, { it.id }, { it.taken }) else emptyList()
        val chats = lib.items.filter { it.chat != null }
        // Chat files count from when they arrived on the phone; their camera date is often missing or old.
        fun chat(c: Chat) = if (ruleOn(s, c)) {
            Rules.due(chats.filter { it.chat == c }, now, s.chatDays, important, start, { it.id }, { it.added })
        } else emptyList()
        return DuePlan(shots, chat(Chat.WHATSAPP), chat(Chat.TELEGRAM), chat(Chat.FACEBOOK))
    }

    /** Screenshots that will go to the trash within [days] days. */
    fun upcoming(lib: Library, s: Settings, important: Set<Long>, start: Long, days: Int, now: Long = System.currentTimeMillis()): Int {
        if (!s.shotAutoTrash) return 0
        return lib.screenshots.count { it.id !in important && it.taken >= start && Rules.daysLeft(it.taken, now, s.shotDays) in 1..days }
    }

    /** Runs the rules. Returns how many files were moved to the trash. */
    suspend fun run(ctx: Context): Int {
        val app = ctx.applicationContext
        restoreEarly(app)
        if (MediaRepo.library.value == null) MediaRepo.refresh(app)
        val lib = MediaRepo.library.value ?: return 0
        val prefs = Prefs.get(app)
        val db = Db.get(app)
        db.warm()
        val p = plan(lib, prefs.now, db.important.value, prefs.rulesStart)
        var moved = 0
        if (p.all.isNotEmpty()) {
            val r = Ops.trash(app, p.all, allowDialog = false)
            moved = r.done
            Log.i(TAG, "Clean-up: ${p.shots.size} screenshots, ${p.whatsapp.size} WhatsApp, ${p.telegram.size} Telegram, ${p.facebook.size} Facebook → $moved moved")
        }
        val purged = runCatching { Ops.purgeExpired(app) }.getOrDefault(0)
        if (purged > 0) Log.i(TAG, "Clean-up: $purged expired trash items deleted")
        prefs.lastCleanupAt = System.currentTimeMillis()
        prefs.lastCleanup = "${p.shots.size},${p.whatsapp.size},${p.telegram.size},${p.facebook.size}"
        prefs.cleanedTotal = prefs.cleanedTotal + moved
        return moved
    }

    /**
     * Version 1.0 counted every old screenshot and chat file, so its first clean-up moved files from
     * before the app was installed to the trash. This puts them back, once. Returns how many.
     */
    suspend fun restoreEarly(ctx: Context): Int {
        val app = ctx.applicationContext
        val prefs = Prefs.get(app)
        if (prefs.restoredEarlyTrash) return 0
        val start = prefs.rulesStart
        val ours = Db.get(app).trashedAt()
        val early = MediaRepo.trashed(app).filter { m ->
            m.id in ours && ((m.isScreenshot && m.taken < start) || (m.chat != null && m.added < start))
        }
        var back = 0
        if (early.isNotEmpty()) {
            back = Ops.restore(app, early).done
            Log.i(TAG, "Put back $back of ${early.size} files from before the app was installed")
        }
        // Only mark done once everything that needed it is back (or there was nothing to do).
        if (back == early.size) prefs.restoredEarlyTrash = true
        if (back > 0) prefs.restoredCount = back
        return back
    }
}
