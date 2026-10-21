package com.ankandebnath.allphotos.capture

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.Prefs
import com.ankandebnath.allphotos.logic.Batches
import com.ankandebnath.allphotos.logic.Rules
import com.ankandebnath.allphotos.logic.ShotBatch
import com.ankandebnath.allphotos.logic.Source
import com.ankandebnath.allphotos.logic.Sources
import com.ankandebnath.allphotos.notify.Notifier
import com.ankandebnath.allphotos.work.Work
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A screenshot just saved by the phone. */
data class NewShot(val id: Long, val taken: Long, val name: String)

/** Notices new screenshots while the screenshot watcher is running. */
object ShotWatch {
    private var observer: ContentObserver? = null
    private var thread: HandlerThread? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    fun start(ctx: Context) {
        if (observer != null) return
        val app = ctx.applicationContext
        val t = HandlerThread("shot-watch").apply { start() }
        val obs = object : ContentObserver(Handler(t.looper)) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                pending?.cancel()
                pending = scope.launch {
                    delay(350)
                    ShotTracker.process(app)
                }
            }
        }
        runCatching { app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, obs) }
        observer = obs
        thread = t
    }

    fun stop(ctx: Context) {
        observer?.let { runCatching { ctx.applicationContext.contentResolver.unregisterContentObserver(it) } }
        observer = null
        thread?.quitSafely()
        thread = null
    }
}

/**
 * Handles each new screenshot once: notes where it came from, groups screenshots taken close
 * together, and posts the "Mark as important" notification.
 */
object ShotTracker {
    private const val TAG = "AllPhotos"
    private val lock = Mutex()
    private var batch: ShotBatch? = null

    /** Screenshots older than this when first noticed (copied in, restored) get no notification. */
    private const val FRESH_MS = 5 * 60_000L

    private fun newShots(ctx: Context, afterId: Long): List<NewShot> {
        val out = ArrayList<NewShot>()
        val proj = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DISPLAY_NAME,
        )
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Images.Media._ID} > ?", arrayOf(afterId.toString()),
            "${MediaStore.Images.Media._ID} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val rel = c.getString(3)
                val name = c.getString(5)
                if (!Rules.isScreenshot(rel, c.getString(4), name)) continue
                val added = c.getLong(2) * 1000
                val taken = if (c.isNull(1) || c.getLong(1) <= 0) added else c.getLong(1)
                out += NewShot(c.getLong(0), taken, name.orEmpty())
            }
        }
        return out
    }

    private fun maxImageId(ctx: Context): Long =
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            null, null, "${MediaStore.Images.Media._ID} DESC",
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L

    /** Where a screenshot taken at [taken] came from, using whatever the phone allows. */
    fun sourceFor(ctx: Context, taken: Long): Source {
        if (WatchService.running) {
            val live = Sources.attribute(FgLog.snapshot(), taken, ctx.packageName)
            if (live != null) return Source(live.pkg, live.url, Source.LIVE)
        }
        val logged = runCatching {
            Sources.attribute(Db.get(ctx).foregroundBetween(taken - 6 * 3_600_000L, taken + 2_000), taken, ctx.packageName)
        }.getOrNull()
        if (logged != null) return Source(logged.pkg, logged.url, Source.LIVE)
        val pkg = runCatching { UsageSource.appAt(ctx, taken) }.getOrNull()
        if (pkg != null) return Source(pkg, null, Source.USAGE)
        return Source(null, null, Source.NONE)
    }

    suspend fun process(ctx: Context) = lock.withLock {
        val app = ctx.applicationContext
        val prefs = Prefs.get(app)
        val last = prefs.lastShotId
        if (last < 0) {
            // First run: older screenshots are labelled later, in the background, without notifications.
            prefs.lastShotId = runCatching { maxImageId(app) }.getOrDefault(0L)
            return@withLock
        }
        val shots = runCatching { newShots(app, last) }.onFailure { Log.w(TAG, "Screenshot check failed", it) }.getOrNull()
            ?: return@withLock
        if (shots.isEmpty()) {
            val maxId = runCatching { maxImageId(app) }.getOrDefault(last)
            if (maxId > last) prefs.lastShotId = maxId
            return@withLock
        }
        val db = Db.get(app)
        val now = System.currentTimeMillis()
        var fresh = false
        for (s in shots) {
            val src = sourceFor(app, s.taken)
            if (src.how != Source.NONE) db.setSource(s.id, src)
            if (now - s.taken <= FRESH_MS) {
                batch = Batches.add(batch, s.id, s.taken)
                fresh = true
            }
        }
        prefs.lastShotId = maxOf(shots.maxOf { it.id }, runCatching { maxImageId(app) }.getOrDefault(0L))
        Work.indexSoon(app)
        val b = batch
        if (fresh && b != null && prefs.now.shotNotify) {
            runCatching { Notifier.shots(app, b) }.onFailure { Log.w(TAG, "Notification failed", it) }
        }
    }

    fun forget(ids: Collection<Long>) {
        val b = batch ?: return
        if (b.ids.any { it in ids }) batch = null
    }
}
