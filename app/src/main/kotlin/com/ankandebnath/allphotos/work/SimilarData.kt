package com.ankandebnath.allphotos.work

import android.content.Context
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.Library
import com.ankandebnath.allphotos.logic.Candidate
import com.ankandebnath.allphotos.logic.Similar
import com.ankandebnath.allphotos.logic.SimilarGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Similar-photo groups from the fingerprints collected so far. */
object SimilarData {
    class Summary(val groups: List<SimilarGroup>, val scanned: Int, val total: Int) {
        val extraBytes: Long get() = groups.sumOf { it.extraBytes }
        val extraCount: Int get() = groups.sumOf { it.ids.size - 1 }
    }

    suspend fun compute(ctx: Context, lib: Library): Summary = withContext(Dispatchers.Default) {
        val scans = Db.get(ctx).scans()
        val photos = lib.items.filter { Indexer.wantsFingerprint(it) }
        val cands = ArrayList<Candidate>(photos.size)
        var scanned = 0
        for (m in photos) {
            val s = scans[m.id] ?: continue
            if (s.mod != m.modified) continue
            scanned++
            val h = s.hash ?: continue
            if (h == 0L) continue
            cands += Candidate(m.id, m.taken, h, (s.sharp ?: 0.0) * (1 + m.width.toDouble() * m.height / 50_000_000.0), m.size)
        }
        Summary(Similar.groups(cands), scanned, photos.size)
    }
}
