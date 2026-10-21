package com.ankandebnath.allphotos.logic

/** A photo ready for comparison: its picture fingerprint and how sharp it is. */
data class Candidate(val id: Long, val time: Long, val hash: Long, val score: Double, val size: Long)

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
        // Bursts: compare each photo with the ones taken shortly after it.
        for (i in sorted.indices) {
            var j = i + 1
            while (j < sorted.size && sorted[j].time - sorted[i].time <= gapMs) {
                if (distance(sorted[i].hash, sorted[j].hash) <= maxDistance) union(i, j)
                j++
            }
        }
        // Exact copies: identical fingerprints, whenever they were taken.
        val byHash = HashMap<Long, Int>()
        for (i in sorted.indices) {
            val prev = byHash.putIfAbsent(sorted[i].hash, i)
            if (prev != null && sorted[i].hash != 0L && sorted[i].hash != -1L) union(prev, i)
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
