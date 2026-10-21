package com.ankandebnath.allphotos.logic

/** Slider values for photo adjustments, each from -1 to 1 (0 means unchanged). */
data class Adjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val fade: Float = 0f,
) {
    val isNeutral: Boolean get() = this == Adjust()
}

/** A normalised crop rectangle (0..1 on each side) inside the rotated photo. */
data class CropRect(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f) {
    val w get() = r - l
    val h get() = b - t
    val isFull get() = l <= 0.001f && t <= 0.001f && r >= 0.999f && b >= 0.999f
}

/**
 * Colour maths for the editor. Every adjustment and filter is a 4×5 colour matrix (the same
 * layout Android uses), so the preview runs on the GPU and the saved photo matches it exactly.
 */
object EditMath {
    val identity: FloatArray
        get() = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

    /** a × b: applying b first, then a. */
    fun concat(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var v = 0f
                for (k in 0 until 4) v += a[row * 5 + k] * b[k * 5 + col]
                if (col == 4) v += a[row * 5 + 4]
                out[row * 5 + col] = v
            }
        }
        return out
    }

    fun saturation(s: Float): FloatArray {
        val inv = 1 - s
        val r = 0.213f * inv
        val g = 0.715f * inv
        val b = 0.072f * inv
        return floatArrayOf(
            r + s, g, b, 0f, 0f,
            r, g + s, b, 0f, 0f,
            r, g, b + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun scaleOffset(rs: Float, gs: Float, bs: Float, off: Float) = floatArrayOf(
        rs, 0f, 0f, 0f, off,
        0f, gs, 0f, 0f, off,
        0f, 0f, bs, 0f, off,
        0f, 0f, 0f, 1f, 0f,
    )

    fun contrast(c: Float): FloatArray {
        val s = 1f + c
        return scaleOffset(s, s, s, (1f - s) * 127.5f)
    }

    fun brightness(b: Float): FloatArray = scaleOffset(1f, 1f, 1f, b * 70f)

    fun warmth(w: Float): FloatArray = scaleOffset(1f + 0.14f * w, 1f + 0.03f * w, 1f - 0.14f * w, 0f)

    /** Lifts the blacks, for a soft matte look. */
    fun fade(f: Float): FloatArray {
        val s = 1f - 0.25f * f
        return scaleOffset(s, s, s, 0.25f * f * 255f * 0.6f)
    }

    class Filter(val name: String, val matrix: FloatArray)

    val filters: List<Filter> by lazy {
        listOf(
            Filter("Original", identity),
            Filter("Vivid", concat(contrast(0.12f), saturation(1.35f))),
            Filter("Warm", concat(warmth(0.7f), saturation(1.08f))),
            Filter("Cool", concat(warmth(-0.7f), contrast(0.05f))),
            Filter("Mono", concat(contrast(0.15f), saturation(0f))),
            Filter("Noir", concat(contrast(0.45f), concat(brightness(-0.08f), saturation(0f)))),
            Filter("Fade", concat(fade(0.8f), saturation(0.85f))),
            Filter("Film", concat(fade(0.45f), concat(warmth(0.35f), saturation(0.9f)))),
            Filter("Pop", concat(contrast(0.25f), concat(brightness(0.04f), saturation(1.25f)))),
        )
    }

    /** The full colour change: the filter first, then the sliders. */
    fun matrix(adj: Adjust, filter: Int): FloatArray {
        var m = filters.getOrNull(filter)?.matrix ?: identity
        if (adj.saturation != 0f) m = concat(saturation(1f + adj.saturation), m)
        if (adj.warmth != 0f) m = concat(warmth(adj.warmth), m)
        if (adj.contrast != 0f) m = concat(contrast(adj.contrast * 0.8f), m)
        if (adj.brightness != 0f) m = concat(brightness(adj.brightness), m)
        if (adj.fade != 0f) m = concat(fade(adj.fade), m)
        return m
    }

    /** Applies a matrix to one colour (0..255 each), for tests. */
    fun apply(m: FloatArray, r: Float, g: Float, b: Float): FloatArray = floatArrayOf(
        (m[0] * r + m[1] * g + m[2] * b + m[4]).coerceIn(0f, 255f),
        (m[5] * r + m[6] * g + m[7] * b + m[9]).coerceIn(0f, 255f),
        (m[10] * r + m[11] * g + m[12] * b + m[14]).coerceIn(0f, 255f),
    )

    /**
     * Fits a crop to an aspect ratio (width / height in pixels of the rotated photo), keeping its
     * centre. [imageAspect] is the photo's own width / height.
     */
    fun fitAspect(c: CropRect, ratio: Float, imageAspect: Float): CropRect {
        if (ratio <= 0f) return c
        // Work in pixel-proportional units: x scaled by imageAspect.
        val cx = (c.l + c.r) / 2f
        val cy = (c.t + c.b) / 2f
        var w = c.w * imageAspect
        var h = c.h
        if (w / h > ratio) w = h * ratio else h = w / ratio
        var nw = w / imageAspect
        var nh = h
        if (nw > 1f) { nh *= 1f / nw; nw = 1f }
        if (nh > 1f) { nw *= 1f / nh; nh = 1f }
        val l = (cx - nw / 2f).coerceIn(0f, 1f - nw)
        val t = (cy - nh / 2f).coerceIn(0f, 1f - nh)
        return CropRect(l, t, l + nw, t + nh)
    }
}
