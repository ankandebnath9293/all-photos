package com.ankandebnath.allphotos.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ankandebnath.allphotos.logic.Foreground
import com.ankandebnath.allphotos.logic.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the photo index has learned about a file. */
data class Scan(val id: Long, val hash: Long?, val sharp: Double?, val mod: Long)

/**
 * The app's own notes about each file: important marks, where screenshots came from, and the
 * search index. Photos themselves stay where they are; only these notes live here.
 */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "allphotos.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE meta(
                id INTEGER PRIMARY KEY,
                important INTEGER NOT NULL DEFAULT 0,
                src_pkg TEXT, src_url TEXT, src_how INTEGER NOT NULL DEFAULT 0,
                hash INTEGER, sharp REAL, hash_mod INTEGER NOT NULL DEFAULT 0,
                text_mod INTEGER NOT NULL DEFAULT 0, labels TEXT,
                trashed_at INTEGER)""",
        )
        db.execSQL("CREATE INDEX meta_important ON meta(important)")
        db.execSQL("CREATE VIRTUAL TABLE fts USING fts4(body)")
        db.execSQL("CREATE TABLE fg(t INTEGER NOT NULL, pkg TEXT NOT NULL, url TEXT)")
        db.execSQL("CREATE INDEX fg_t ON fg(t)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    private val importantState = MutableStateFlow<Set<Long>>(emptySet())
    private val sourceState = MutableStateFlow<Map<Long, Source>>(emptyMap())

    /** Files marked important. They are never cleaned up automatically. */
    val important: StateFlow<Set<Long>> = importantState.asStateFlow()

    /** Where each screenshot came from. */
    val sources: StateFlow<Map<Long, Source>> = sourceState.asStateFlow()

    @Volatile private var loaded = false

    /** Reads the small tables into memory once, so screens never wait on the disk for them. */
    fun warm() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val imp = HashSet<Long>()
            readableDatabase.rawQuery("SELECT id FROM meta WHERE important=1", null).use { c ->
                while (c.moveToNext()) imp += c.getLong(0)
            }
            val src = HashMap<Long, Source>()
            readableDatabase.rawQuery("SELECT id, src_pkg, src_url, src_how FROM meta WHERE src_how != 0", null).use { c ->
                while (c.moveToNext()) src[c.getLong(0)] = Source(c.getString(1), c.getString(2), c.getInt(3))
            }
            importantState.value = imp
            sourceState.value = src
            loaded = true
        }
    }

    private fun ensure(db: SQLiteDatabase, id: Long) {
        db.execSQL("INSERT OR IGNORE INTO meta(id) VALUES(?)", arrayOf<Any?>(id))
    }

    fun setImportant(ids: Collection<Long>, on: Boolean) {
        warm()
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                ensure(db, id)
                db.execSQL("UPDATE meta SET important=? WHERE id=?", arrayOf<Any?>(if (on) 1 else 0, id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        importantState.value = if (on) importantState.value + ids else importantState.value - ids.toSet()
    }

    fun setSource(id: Long, src: Source) {
        warm()
        val db = writableDatabase
        ensure(db, id)
        db.update("meta", ContentValues().apply {
            put("src_pkg", src.pkg)
            put("src_url", src.url)
            put("src_how", src.how)
        }, "id=?", arrayOf(id.toString()))
        sourceState.value = sourceState.value + (id to src)
    }

    fun setScan(id: Long, hash: Long, sharp: Double, mod: Long) {
        val db = writableDatabase
        ensure(db, id)
        db.update("meta", ContentValues().apply {
            put("hash", hash)
            put("sharp", sharp)
            put("hash_mod", mod)
        }, "id=?", arrayOf(id.toString()))
    }

    /** Saves the words found in a photo (its text and labels) for search. */
    fun setText(id: Long, text: String, labels: String, mod: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ensure(db, id)
            db.update("meta", ContentValues().apply {
                put("text_mod", mod)
                put("labels", labels)
            }, "id=?", arrayOf(id.toString()))
            db.execSQL("DELETE FROM fts WHERE docid=?", arrayOf<Any?>(id))
            val body = (text + "\n" + labels).trim()
            if (body.isNotEmpty()) db.execSQL("INSERT INTO fts(docid, body) VALUES(?, ?)", arrayOf<Any?>(id, body))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun textOf(id: Long): String? =
        readableDatabase.rawQuery("SELECT body FROM fts WHERE docid=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun labelsOf(id: Long): String? =
        readableDatabase.rawQuery("SELECT labels FROM meta WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** Fingerprints of every photo scanned so far, with the file version they were taken from. */
    fun scans(): Map<Long, Scan> {
        val out = HashMap<Long, Scan>()
        readableDatabase.rawQuery("SELECT id, hash, sharp, hash_mod FROM meta WHERE hash_mod != 0", null).use { c ->
            while (c.moveToNext()) {
                out[c.getLong(0)] = Scan(
                    c.getLong(0),
                    if (c.isNull(1)) null else c.getLong(1),
                    if (c.isNull(2)) null else c.getDouble(2),
                    c.getLong(3),
                )
            }
        }
        return out
    }

    /** File versions whose text has been read, by id. */
    fun textVersions(): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        readableDatabase.rawQuery("SELECT id, text_mod FROM meta WHERE text_mod != 0", null).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getLong(1)
        }
        return out
    }

    /** Ids whose text or labels match every word typed (each word may be the start of a longer one). */
    fun search(query: String): Set<Long> {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.take(8)
        if (words.isEmpty()) return emptySet()
        val match = words.joinToString(" ") { "$it*" }
        val out = HashSet<Long>()
        runCatching {
            readableDatabase.rawQuery("SELECT docid FROM fts WHERE body MATCH ?", arrayOf(match)).use { c ->
                while (c.moveToNext()) out += c.getLong(0)
            }
        }
        return out
    }

    fun setTrashedAt(ids: Collection<Long>, at: Long?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                ensure(db, id)
                db.execSQL("UPDATE meta SET trashed_at=? WHERE id=?", arrayOf<Any?>(at, id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun trashedAt(): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        readableDatabase.rawQuery("SELECT id, trashed_at FROM meta WHERE trashed_at IS NOT NULL", null).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getLong(1)
        }
        return out
    }

    /** Forgets files that were deleted for good. */
    fun forget(ids: Collection<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                db.execSQL("DELETE FROM meta WHERE id=?", arrayOf<Any?>(id))
                db.execSQL("DELETE FROM fts WHERE docid=?", arrayOf<Any?>(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        importantState.value = importantState.value - ids.toSet()
        sourceState.value = sourceState.value - ids.toSet()
    }

    /** Keeps a short history of which app was on screen, for screenshots found later. */
    fun logForeground(e: Foreground) {
        val db = writableDatabase
        db.execSQL("INSERT INTO fg(t, pkg, url) VALUES(?, ?, ?)", arrayOf<Any?>(e.time, e.pkg, e.url))
    }

    fun foregroundBetween(from: Long, to: Long): List<Foreground> {
        val out = ArrayList<Foreground>()
        readableDatabase.rawQuery(
            "SELECT t, pkg, url FROM fg WHERE t BETWEEN ? AND ? ORDER BY t",
            arrayOf(from.toString(), to.toString()),
        ).use { c ->
            while (c.moveToNext()) out += Foreground(c.getLong(0), c.getString(1), c.getString(2))
        }
        return out
    }

    fun pruneForeground(before: Long) {
        writableDatabase.execSQL("DELETE FROM fg WHERE t < ?", arrayOf<Any?>(before))
    }

    fun counts(): Pair<Int, Int> {
        val hashed = readableDatabase.rawQuery("SELECT COUNT(*) FROM meta WHERE hash_mod != 0", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        val read = readableDatabase.rawQuery("SELECT COUNT(*) FROM meta WHERE text_mod != 0", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        return hashed to read
    }

    companion object {
        @Volatile private var instance: Db? = null
        fun get(ctx: Context): Db = instance ?: synchronized(this) {
            instance ?: Db(ctx.applicationContext).also { instance = it }
        }
    }
}
