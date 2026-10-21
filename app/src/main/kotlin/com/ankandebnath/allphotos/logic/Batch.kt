package com.ankandebnath.allphotos.logic

/** Screenshots taken close together share one "Mark as important" notification. */
data class ShotBatch(val start: Long, val ids: List<Long>) {
    val size get() = ids.size
}

object Batches {
    const val WINDOW_MS = 30_000L

    /**
     * Adds a screenshot to the batch it belongs to. A screenshot joins the open batch when it was
     * taken within [windowMs] of the batch's first one; otherwise it starts a new batch.
     */
    fun add(current: ShotBatch?, id: Long, time: Long, windowMs: Long = WINDOW_MS): ShotBatch {
        if (current != null && id in current.ids) return current
        if (current != null && time >= current.start && time - current.start <= windowMs) {
            return current.copy(ids = current.ids + id)
        }
        return ShotBatch(time, listOf(id))
    }

    /** Splits screenshots (id to time) into batches, oldest first. */
    fun split(shots: List<Pair<Long, Long>>, windowMs: Long = WINDOW_MS): List<ShotBatch> {
        val out = ArrayList<ShotBatch>()
        var cur: ShotBatch? = null
        for ((id, t) in shots.sortedBy { it.second }) {
            val next = add(cur, id, t, windowMs)
            if (cur != null && next.start != cur.start) out += cur
            cur = next
        }
        cur?.let { out += it }
        return out
    }
}
