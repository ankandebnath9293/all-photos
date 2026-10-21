package com.ankandebnath.allphotos.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.util.Log
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.logic.Similar
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * On-device understanding of photos: a fingerprint for finding similar shots, the text in a
 * picture, and a few labels ("Dog", "Food"). Everything runs on the phone's own chip; the models
 * are inside the app and nothing is sent anywhere.
 */
object Analyze {
    private const val TAG = "AllPhotos"

    private val recognizer: TextRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val labeler: ImageLabeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.72f).build())
    }

    private fun gray(b: Bitmap): IntArray {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        for (i in px.indices) {
            val c = px[i]
            px[i] = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        }
        return px
    }

    private fun soft(b: Bitmap): Bitmap = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b

    /** Fingerprint and sharpness of a photo, from a small thumbnail. A few milliseconds each. */
    fun fingerprint(ctx: Context, m: Media): Pair<Long, Double>? {
        val t = Thumbs.bitmap(ctx, m, 160)?.let { soft(it) } ?: return null
        return try {
            val tiny = Bitmap.createScaledBitmap(t, 9, 8, true)
            val hash = Similar.dHash(gray(tiny))
            val long = maxOf(t.width, t.height)
            val s = 128f / long
            val mid = Bitmap.createScaledBitmap(t, (t.width * s).toInt().coerceAtLeast(3), (t.height * s).toInt().coerceAtLeast(3), true)
            val sharp = Similar.sharpness(gray(mid), mid.width, mid.height)
            tiny.recycle()
            if (mid !== t) mid.recycle()
            hash to sharp
        } catch (e: Throwable) {
            Log.w(TAG, "Fingerprint failed for ${m.id}", e)
            null
        } finally {
            t.recycle()
        }
    }

    /** A copy big enough for reading small text, but no bigger. */
    fun forReading(ctx: Context, m: Media): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, m.uri)) { d, info, _ ->
            val long = maxOf(info.size.width, info.size.height)
            val max = if (m.isScreenshot) 2600 else 1600
            if (long > max) {
                val s = max.toFloat() / long
                d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()

    fun text(b: Bitmap): String? = runCatching {
        val r = Tasks.await(recognizer.process(InputImage.fromBitmap(b, 0)), 30, TimeUnit.SECONDS)
        r.textBlocks.joinToString("\n") { it.text }
    }.onFailure { Log.w(TAG, "Text reading failed", it) }.getOrNull()

    fun labels(b: Bitmap): List<String>? = runCatching {
        Tasks.await(labeler.process(InputImage.fromBitmap(b, 0)), 30, TimeUnit.SECONDS).map { it.text }
    }.onFailure { Log.w(TAG, "Labelling failed", it) }.getOrNull()
}
