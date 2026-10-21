package com.ankandebnath.allphotos.data

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The result of changing files. If the phone wants the user to confirm (only when the app's
 * storage access was taken away), [consent] holds the system dialog to show.
 */
class OpResult(val done: Int, val consent: PendingIntent? = null, val failed: Int = 0)

/** Moving files to and from the trash, and deleting them for good. */
object Ops {
    private const val TAG = "AllPhotos"
    const val TRASH_DAYS = 30

    private fun trashValues(on: Boolean, withExpiry: Boolean) = ContentValues().apply {
        put(MediaStore.MediaColumns.IS_TRASHED, if (on) 1 else 0)
        if (on && withExpiry) {
            put(MediaStore.MediaColumns.DATE_EXPIRES, (System.currentTimeMillis() / 1000) + TRASH_DAYS * 86_400L)
        }
    }

    /** Lets a change reach files that are already in the trash. */
    private val includeTrashed = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }

    private fun setTrashed(ctx: Context, uri: Uri, on: Boolean): Boolean {
        val cr = ctx.contentResolver
        // Ask for a 30-day expiry where the phone allows it; otherwise it applies its own default.
        val first = runCatching { cr.update(uri, trashValues(on, withExpiry = true), includeTrashed) > 0 }
        if (first.getOrNull() == true) return true
        return runCatching { cr.update(uri, trashValues(on, withExpiry = false), includeTrashed) > 0 }
            .onFailure { Log.w(TAG, "Trash change failed for $uri", it) }
            .getOrDefault(false)
    }

    /** Moves files to the trash. They stay there for 30 days and can be restored. */
    suspend fun trash(ctx: Context, items: List<Media>, allowDialog: Boolean = true): OpResult = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext OpResult(0)
        val failed = ArrayList<Media>()
        val done = ArrayList<Long>()
        for (m in items) if (setTrashed(ctx, m.uri, true)) done += m.id else failed += m
        if (done.isNotEmpty()) Db.get(ctx).setTrashedAt(done, System.currentTimeMillis())
        val consent = if (failed.isNotEmpty() && allowDialog) {
            runCatching { MediaStore.createTrashRequest(ctx.contentResolver, failed.map { it.uri }, true) }.getOrNull()
        } else null
        if (done.isNotEmpty()) MediaRepo.refresh(ctx)
        MediaRepo.trashTick.value++
        OpResult(done.size, consent, if (consent == null) failed.size else 0)
    }

    suspend fun restore(ctx: Context, items: List<Media>): OpResult = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext OpResult(0)
        val failed = ArrayList<Media>()
        val done = ArrayList<Long>()
        for (m in items) if (setTrashed(ctx, m.uri, false)) done += m.id else failed += m
        if (done.isNotEmpty()) Db.get(ctx).setTrashedAt(done, null)
        val consent = if (failed.isNotEmpty()) {
            runCatching { MediaStore.createTrashRequest(ctx.contentResolver, failed.map { it.uri }, false) }.getOrNull()
        } else null
        MediaRepo.refresh(ctx)
        MediaRepo.trashTick.value++
        OpResult(done.size, consent, if (consent == null) failed.size else 0)
    }

    /** Deletes files for good. Only used from the trash. */
    suspend fun deleteForever(ctx: Context, items: List<Media>): OpResult = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext OpResult(0)
        val cr = ctx.contentResolver
        val failed = ArrayList<Media>()
        val done = ArrayList<Long>()
        for (m in items) {
            val ok = runCatching { cr.delete(m.uri, includeTrashed) > 0 }
                .onFailure { Log.w(TAG, "Delete failed for ${m.uri}", it) }
                .getOrDefault(false)
            if (ok) done += m.id else failed += m
        }
        if (done.isNotEmpty()) Db.get(ctx).forget(done)
        val consent = if (failed.isNotEmpty()) {
            runCatching { MediaStore.createDeleteRequest(cr, failed.map { it.uri }) }.getOrNull()
        } else null
        MediaRepo.trashTick.value++
        OpResult(done.size, consent, if (consent == null) failed.size else 0)
    }

    /** Deletes trashed files whose 30 days are up, in case the phone has not got round to it. */
    suspend fun purgeExpired(ctx: Context): Int {
        val now = System.currentTimeMillis()
        val ours = Db.get(ctx).trashedAt()
        val due = MediaRepo.trashed(ctx).filter { m ->
            val exp = if (m.expires > 0) m.expires else (ours[m.id]?.plus(TRASH_DAYS * 86_400_000L) ?: 0L)
            exp in 1..now
        }
        return if (due.isEmpty()) 0 else deleteForever(ctx, due).done
    }
}
