package com.ankandebnath.allphotos.work

import android.content.Context
import android.util.Log
import com.ankandebnath.allphotos.capture.ShotTracker
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.logic.Browsers
import com.ankandebnath.allphotos.logic.Links
import com.ankandebnath.allphotos.logic.Source
import com.ankandebnath.allphotos.media.Analyze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How far the photo index has got. */
data class IndexState(
    val running: Boolean = false,
    val what: String = "",
    val done: Int = 0,
    val total: Int = 0,
)

/**
 * Builds the on-device index in small steps: fingerprints for finding similar photos, the text in
 * screenshots, and (while charging) text and labels for every photo, so search can find them.
 */
object Indexer {
    private const val TAG = "AllPhotos"
    private val lock = Mutex()
    private val state = MutableStateFlow(IndexState())
    val progress: StateFlow<IndexState> = state.asStateFlow()

    /** Kinds of photo worth fingerprinting: real photos, not screenshots, GIFs or videos. */
    fun wantsFingerprint(m: Media) = !m.isVideo && !m.isScreenshot && !m.isGif

    /** Fingerprints every photo that has none yet. Fast; also run from the Similar photos screen. */
    suspend fun fingerprints(ctx: Context, limit: Int = Int.MAX_VALUE, stop: () -> Boolean = { false }): Int = withContext(Dispatchers.IO) {
        val db = Db.get(ctx)
        val have = db.scans()
        val todo = MediaRepo.now.items.filter { wantsFingerprint(it) && have[it.id]?.mod != it.modified }.take(limit)
        var n = 0
        for (m in todo) {
            if (stop()) break
            val fp = Analyze.fingerprint(ctx, m)
            // Files that cannot be read still get a row, so they are not retried every time.
            db.setScan(m.id, fp?.first ?: 0L, fp?.second ?: 0.0, m.modified)
            n++
            if (n % 25 == 0) state.value = state.value.copy(what = "Finding similar photos", done = n, total = todo.size)
        }
        n
    }

    /** Labels screenshots taken before the watcher was on, using the phone's usage history. */
    private fun attributeOld(ctx: Context) {
        val db = Db.get(ctx)
        val known = db.sources.value
        val cutoff = System.currentTimeMillis() - 7 * 86_400_000L
        for (m in MediaRepo.now.screenshots) {
            if (m.taken < cutoff) break
            if (known[m.id] != null) continue
            val src = ShotTracker.sourceFor(ctx, m.taken)
            if (src.how != Source.NONE) db.setSource(m.id, src)
        }
    }

    /** Reads the text (and, for photos, labels) of one file into the search index. */
    private fun readOne(ctx: Context, m: Media, labels: Boolean) {
        val db = Db.get(ctx)
        val b = Analyze.forReading(ctx, m)
        if (b == null) {
            db.setText(m.id, "", "", m.modified)
            return
        }
        try {
            val text = Analyze.text(b).orEmpty()
            val tags = if (labels) Analyze.labels(b).orEmpty() else emptyList()
            db.setText(m.id, text, tags.joinToString(", "), m.modified)
            if (m.isScreenshot) {
                val src = db.sources.value[m.id]
                if (src?.url == null) {
                    val found = Links.findInText(text)
                    val pkg = src?.pkg
                    if (found != null && (pkg == null || Browsers.isBrowser(pkg))) {
                        val how = src?.how?.takeIf { it == Source.LIVE || it == Source.USAGE } ?: Source.TEXT
                        db.setSource(m.id, Source(pkg, found, how))
                    }
                }
            }
        } finally {
            b.recycle()
        }
    }

    /**
     * One pass of indexing. The light pass (any time) handles screenshots and fingerprints; the
     * heavy pass (while charging) reads text and labels for every photo.
     */
    suspend fun run(ctx: Context, heavy: Boolean, stop: () -> Boolean): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            val app = ctx.applicationContext
            if (MediaRepo.library.value == null) MediaRepo.refresh(app)
            val db = Db.get(app)
            db.warm()
            state.value = IndexState(running = true, what = "Starting")
            try {
                runCatching { attributeOld(app) }.onFailure { Log.w(TAG, "Source lookup failed", it) }
                val read = db.textVersions()
                val shots = MediaRepo.now.screenshots.filter { read[it.id] != it.modified }
                var n = 0
                for (m in shots.take(if (heavy) Int.MAX_VALUE else 300)) {
                    if (stop()) return@withContext false
                    readOne(app, m, labels = false)
                    n++
                    if (n % 5 == 0) state.value = IndexState(true, "Reading screenshots", n, shots.size)
                }
                fingerprints(app, if (heavy) Int.MAX_VALUE else 3000, stop)
                if (stop()) return@withContext false
                if (heavy) {
                    val photos = MediaRepo.now.items.filter { !it.isVideo && !it.isScreenshot && read[it.id] != it.modified }
                    var k = 0
                    for (m in photos) {
                        if (stop()) return@withContext false
                        readOne(app, m, labels = true)
                        k++
                        if (k % 5 == 0) state.value = IndexState(true, "Reading photos", k, photos.size)
                    }
                }
                true
            } finally {
                state.value = IndexState(running = false)
            }
        }
    }
}
