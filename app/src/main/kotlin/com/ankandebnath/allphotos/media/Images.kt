package com.ankandebnath.allphotos.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** A photo ready to show. */
sealed interface Picture {
    /** [fullW] and [fullH] are the original size, so zooming knows when a sharper copy is worth loading. */
    class Still(val bitmap: Bitmap, val fullW: Int, val fullH: Int, val hdr: Boolean) : Picture
    class Animated(val drawable: AnimatedImageDrawable, val w: Int, val h: Int) : Picture
    data object Failed : Picture
}

object Images {
    private const val TAG = "AllPhotos"

    /** Pictures that can light up the screen: Ultra HDR photos and HDR colour spaces. */
    fun isHdr(b: Bitmap): Boolean {
        if (runCatching { b.hasGainmap() }.getOrDefault(false)) return true
        val cs = b.colorSpace ?: return false
        return cs == ColorSpace.get(ColorSpace.Named.BT2020_HLG) || cs == ColorSpace.get(ColorSpace.Named.BT2020_PQ)
    }

    /**
     * Decodes a photo so its longer side is at most [maxSide]. Works for JPEG, HEIC/HEIF (iPhone),
     * AVIF, PNG, WebP, GIF and DNG. Hardware memory is used where possible so big photos do not
     * crowd the app's own memory.
     */
    suspend fun decode(ctx: Context, uri: Uri, maxSide: Int, animate: Boolean = true, software: Boolean = false): Picture =
        withContext(Dispatchers.IO) {
            var fw = 0
            var fh = 0
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            val listener = ImageDecoder.OnHeaderDecodedListener { decoder, info, _ ->
                fw = info.size.width
                fh = info.size.height
                val long = max(fw, fh)
                if (long > maxSide) {
                    val s = maxSide.toFloat() / long
                    decoder.setTargetSize((fw * s).roundToInt().coerceAtLeast(1), (fh * s).roundToInt().coerceAtLeast(1))
                }
                decoder.allocator = if (software) ImageDecoder.ALLOCATOR_SOFTWARE else ImageDecoder.ALLOCATOR_DEFAULT
                decoder.setOnPartialImageListener { true }
            }
            try {
                if (animate) {
                    val d = ImageDecoder.decodeDrawable(src, listener)
                    if (d is AnimatedImageDrawable) return@withContext Picture.Animated(d, fw, fh)
                    if (d is android.graphics.drawable.BitmapDrawable) d.bitmap?.let { return@withContext Picture.Still(it, fw, fh, isHdr(it)) }
                }
                val b = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri), listener)
                Picture.Still(b, fw, fh, isHdr(b))
            } catch (t: Throwable) {
                Log.w(TAG, "ImageDecoder failed for $uri, trying the older decoder", t)
                fallback(ctx, uri, maxSide) ?: Picture.Failed
            }
        }

    private fun fallback(ctx: Context, uri: Uri, maxSide: Int): Picture? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val long = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (long / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        Picture.Still(b, bounds.outWidth, bounds.outHeight, isHdr(b))
    }.getOrNull()

    /** A full-quality, editable copy for saving edits and conversions. */
    suspend fun decodeForEdit(ctx: Context, uri: Uri, maxPixels: Long = 24_000_000L): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val px = w.toLong() * h
                if (px > maxPixels) {
                    val s = kotlin.math.sqrt(maxPixels.toDouble() / px)
                    decoder.setTargetSize((w * s).toInt(), (h * s).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }.onFailure { Log.w(TAG, "Edit decode failed", it) }.getOrNull()
    }
}
