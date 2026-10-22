package com.ankandebnath.allphotos.media

import android.content.Context
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ankandebnath.allphotos.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * Grid thumbnails. They come from the phone's own thumbnail cache (fast, works for HEIC, RAW and
 * videos alike) and are kept in memory so scrolling back is instant.
 */
object Thumbs {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 5).toInt()

    private val cache = object : LruCache<String, ImageBitmap>(maxKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    /** Few decodes at once keeps the scroll smooth; extra requests queue up and cancel as cells leave. */
    private val io = Dispatchers.IO.limitedParallelism(4)

    /** One of a few fixed sizes, so cells of nearby zoom levels share cached thumbnails. */
    fun bucket(px: Int): Int = when {
        px <= 200 -> 192
        px <= 300 -> 288
        px <= 420 -> 400
        else -> 640
    }

    private fun key(m: Media, px: Int) = "${m.id}:${m.modified}:$px"

    /** The exact size if cached, otherwise the sharpest smaller one (so the viewer opens on a picture, not a blank). */
    fun cached(m: Media, px: Int): ImageBitmap? {
        cache.get(key(m, px))?.let { return it }
        for (b in listOf(640, 400, 288, 192)) if (b != px) cache.get(key(m, b))?.let { return it }
        return null
    }

    suspend fun load(ctx: Context, m: Media, px: Int): ImageBitmap? {
        cache.get(key(m, px))?.let { return it }
        return withContext(io) {
            cache.get(key(m, px))?.let { return@withContext it }
            val signal = CancellationSignal()
            val job = currentCoroutineContext()[Job]
            val handle = job?.invokeOnCompletion { if (it != null) signal.cancel() }
            try {
                val bmp = runCatching { ctx.contentResolver.loadThumbnail(m.uri, request(m, px), signal) }.getOrNull()
                    ?: (if (m.isVideo) videoFrame(ctx, m, px) else null)
                    ?: return@withContext null
                val img = shrink(bmp, px).asImageBitmap()
                cache.put(key(m, px), img)
                img
            } finally {
                handle?.dispose()
            }
        }
    }

    /**
     * Asks for a picture whose shorter side is at least [px], so square grid cells of tall
     * screenshots and wide panoramas stay sharp after cropping.
     */
    private fun request(m: Media, px: Int): Size {
        val a = m.aspect.coerceIn(1 / 3f, 3f)
        return if (a < 1f) Size(px, (px / a).toInt()) else Size((px * a).toInt(), px)
    }

    /** Some videos (often .MOV files from other phones) get no system thumbnail; read a frame ourselves. */
    private fun videoFrame(ctx: Context, m: Media, px: Int): Bitmap? = runCatching {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, m.uri)
            val at = (minOf(1000L, m.durationMs / 3)).coerceAtLeast(0L) * 1000L
            val sz = request(m, px)
            r.getScaledFrameAtTime(at, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, sz.width, sz.height)
                ?: r.frameAtTime
        } finally {
            r.release()
        }
    }.getOrNull()

    /** The system sometimes returns a larger picture than asked; keep memory use predictable. */
    private fun shrink(b: Bitmap, px: Int): Bitmap {
        val short = minOf(b.width, b.height)
        if (short <= px * 1.25f) return b
        val s = px.toFloat() / short
        val out = Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
        if (out !== b) b.recycle()
        return out
    }

    /** A plain bitmap for background work (fingerprints, notifications). Not cached. */
    fun bitmap(ctx: Context, m: Media, px: Int): Bitmap? =
        runCatching { ctx.contentResolver.loadThumbnail(m.uri, Size(px, px), null) }.getOrNull()

    fun forget(m: Media) {
        for (px in listOf(192, 288, 400, 640)) cache.remove(key(m, px))
    }

    fun clear() = cache.evictAll()
}
