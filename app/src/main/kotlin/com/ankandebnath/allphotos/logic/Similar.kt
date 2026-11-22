package com.ankandebnath.allphotos.logic

/** A photo ready for comparison: its picture fingerprint and how sharp it is. */
/** [width] and [height] are as shown on screen; 0 when unknown. */
data class Candidate(val id: Long, val time: Long, val hash: Long, val score: Double, val size: Long, val width: Int = 0, val height: Int = 0)

/** A set of near-identical photos. [ids] are best first; the first one is kept by default. */
data class SimilarGroup(val ids: List<Long>, val extraBytes: Long, val exact: Boolean)

object Similar {
    /** Photos further apart than this are not treated as one burst. */
    const val BURST_GAP_MS = 120_000L
    /** Fingerprint bits that may differ for two photos to count as the same shot. */
    const val MAX_DISTANCE = 10

    /**
     * Difference hash of a 9×8 grey picture (72 values, row by row): one bit per neighbour pair,
     * set when the left pixel is brighter. Robust to size, compression and small exposure changes.
     */
    fun dHash(gray9x8: IntArray): Long {
        require(gray9x8.size == 72)
        var h = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (gray9x8[y * 9 + x] > gray9x8[y * 9 + x + 1]) h = h or (1L shl bit)
                bit++
            }
        }
        return h
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Same width-to-height shape (within 1%), or unknown. */
    private fun sameShape(a: Candidate, b: Candidate): Boolean {
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return true
        val ra = a.width.toDouble() / a.height
        val rb = b.width.toDouble() / b.height
        return kotlin.math.abs(ra - rb) <= 0.01 * maxOf(ra, rb)
    }

    /** How sharp a grey picture is: the variance of its Laplacian. Blurry shots score low. */
    fun sharpness(gray: IntArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return 0.0
        var n = 0
        var sum = 0.0
        var sq = 0.0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val v = (4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - w] - gray[i + w]).toDouble()
                sum += v
                sq += v * v
                n++
            }
        }
        val mean = sum / n
        return sq / n - mean * mean
    }

    /**
     * Finds bursts of near-identical photos (taken within [gapMs] of each other) and exact copies
     * anywhere in the library (the same picture saved twice).
     */
    fun groups(items: List<Candidate>, gapMs: Long = BURST_GAP_MS, maxDistance: Int = MAX_DISTANCE): List<SimilarGroup> {
        if (items.size < 2) return emptyList()
        val sorted = items.sortedBy { it.time }
        val parent = IntArray(sorted.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }
        // Bursts: a photo joins a set only if it is close to EVERY photo already in it, and was taken
        // within [gapMs] of the set's first photo. No chaining (A like B, B like C does not put A with C),
        // so a run of slides or pages that each look a bit like the last never becomes one big set.
        val active = ArrayList<MutableList<Int>>()
        for (i in sorted.indices) {
            active.removeAll { sorted[i].time - sorted[it[0]].time > gapMs }
            var best: MutableList<Int>? = null
            var bestWorst = Int.MAX_VALUE
            for (set in active) {
                var worst = 0
                for (k in set) {
                    worst = maxOf(worst, distance(sorted[i].hash, sorted[k].hash))
                    if (worst > maxDistance) break
                }
                if (worst <= maxDistance && worst < bestWorst) {
                    best = set
                    bestWorst = worst
                }
            }
            if (best != null) {
                union(best[0], i)
                best += i
            } else {
                active += mutableListOf(i)
            }
        }
        // Exact copies: identical fingerprints, whenever they were taken, but only for pictures with
        // enough detail to trust the match (plain pages, walls and sky can share a fingerprint by chance)
        // and the same shape.
        val byHash = HashMap<Long, Int>()
        for (i in sorted.indices) {
            val c = sorted[i]
            if (java.lang.Long.bitCount(c.hash) !in 8..56) continue
            val prev = byHash.putIfAbsent(c.hash, i)
            if (prev != null && sameShape(sorted[prev], c)) union(prev, i)
        }
        val buckets = LinkedHashMap<Int, MutableList<Candidate>>()
        for (i in sorted.indices) buckets.getOrPut(find(i)) { ArrayList() } += sorted[i]
        val timeOf = sorted.associate { it.id to it.time }
        return buckets.values
            .filter { it.size >= 2 }
            .map { members ->
                val best = members.sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.size })
                SimilarGroup(
                    ids = best.map { it.id },
                    extraBytes = best.drop(1).sumOf { it.size },
                    exact = best.all { it.hash == best[0].hash },
                )
            }
            .sortedByDescending { g -> g.ids.maxOf { timeOf[it] ?: 0L } }
    }
}
