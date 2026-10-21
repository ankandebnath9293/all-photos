package com.ankandebnath.allphotos.data

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import com.ankandebnath.allphotos.logic.Rules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Reads every photo and video straight from the phone's own media index (no cloud, no sync), and
 * refreshes when files are added, changed or removed.
 */
object MediaRepo {
    private const val TAG = "AllPhotos"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow<Library?>(null)
    private val loading = Mutex()
    private var observer: ContentObserver? = null
    private var pending: Job? = null

    /** The library, or null until the first read finishes. */
    val library: StateFlow<Library?> = state.asStateFlow()

    /** Bumped whenever the trash may have changed. */
    val trashTick = MutableStateFlow(0)

    val now: Library get() = state.value ?: Library.EMPTY

    private val filesUri: Uri get() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.DATE_TAKEN,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.WIDTH,
        MediaStore.MediaColumns.HEIGHT,
        MediaStore.MediaColumns.DURATION,
        MediaStore.MediaColumns.ORIENTATION,
        MediaStore.MediaColumns.BUCKET_ID,
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        MediaStore.MediaColumns.RELATIVE_PATH,
        @Suppress("DEPRECATION") MediaStore.MediaColumns.DATA,
        MediaStore.MediaColumns.IS_FAVORITE,
        MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
        MediaStore.MediaColumns.DATE_EXPIRES,
    )

    private fun read(c: Cursor): Media {
        val type = c.getInt(1)
        val mime = c.getString(2).orEmpty()
        val name = c.getString(3).orEmpty()
        val added = c.getLong(5) * 1000
        val taken = if (c.isNull(4) || c.getLong(4) <= 0) added else c.getLong(4)
        val rel = c.getString(14).orEmpty()
        val bucket = c.getString(13).orEmpty()
        val owner = c.getString(17)
        return Media(
            id = c.getLong(0),
            isVideo = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
            mime = mime,
            name = name,
            taken = taken,
            added = added,
            modified = c.getLong(6) * 1000,
            size = c.getLong(7),
            width = c.getInt(8),
            height = c.getInt(9),
            durationMs = c.getLong(10),
            orientation = c.getInt(11),
            bucketId = c.getLong(12),
            bucket = bucket.ifEmpty { rel.trimEnd('/').substringAfterLast('/').ifEmpty { "Phone" } },
            relPath = rel,
            path = c.getString(15).orEmpty(),
            favorite = c.getInt(16) == 1,
            owner = owner,
            isScreenshot = type == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE && Rules.isScreenshot(rel, bucket, name),
            chat = Rules.chatOf(rel, owner),
            expires = if (c.isNull(18)) 0 else c.getLong(18) * 1000,
        )
    }

    private fun query(ctx: Context, trashed: Boolean): List<Media> {
        val args = Bundle().apply {
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
            )
            if (trashed) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        val out = ArrayList<Media>()
        ctx.contentResolver.query(filesUri, projection, args, null)?.use { c ->
            out.ensureCapacity(c.count)
            while (c.moveToNext()) {
                runCatching { read(c) }.onSuccess { out += it }
            }
        }
        out.sortWith(compareByDescending<Media> { it.taken }.thenByDescending { it.id })
        return out
    }

    /** Reads the library now. Safe to call often; overlapping calls share one read. */
    suspend fun refresh(ctx: Context) = withContext(Dispatchers.IO) {
        loading.withLock {
            val t0 = System.currentTimeMillis()
            val items = runCatching { query(ctx.applicationContext, trashed = false) }
                .onFailure { Log.w(TAG, "Library read failed", it) }
                .getOrNull() ?: return@withLock
            state.value = Library(items)
            Log.i(TAG, "Library: ${items.size} items in ${System.currentTimeMillis() - t0} ms")
        }
    }

    /** Starts following changes to the phone's photos. */
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        if (observer == null) {
            val thread = HandlerThread("media-watch").apply { start() }
            val obs = object : ContentObserver(Handler(thread.looper)) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    // Many changes arrive in a burst (a camera save, a big delete); read once after it settles.
                    pending?.cancel()
                    pending = scope.launch {
                        delay(700)
                        refresh(app)
                        trashTick.value++
                    }
                }
            }
            runCatching { app.contentResolver.registerContentObserver(filesUri, true, obs) }
            observer = obs
        }
        scope.launch { refresh(app) }
    }

    suspend fun trashed(ctx: Context): List<Media> = withContext(Dispatchers.IO) {
        runCatching { query(ctx.applicationContext, trashed = true) }.getOrDefault(emptyList())
    }

    /** Folders, with the camera first, then pinned ones, then the most recently added to. */
    fun albums(lib: Library, pinned: Set<Long>): List<Album> {
        val groups = LinkedHashMap<Long, MutableList<Media>>()
        for (m in lib.items) groups.getOrPut(m.bucketId) { ArrayList() } += m
        val list = groups.map { (id, items) ->
            val first = items.first()
            Album(id, first.bucket, first.relPath, items.size, items.sumOf { it.size }, first, first.taken)
        }
        return list.sortedWith(
            compareByDescending<Album> { it.relPath.equals("DCIM/Camera/", true) }
                .thenByDescending { it.id in pinned }
                .thenByDescending { it.latest },
        )
    }
}
