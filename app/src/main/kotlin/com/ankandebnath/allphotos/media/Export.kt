package com.ankandebnath.allphotos.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.logic.Adjust
import com.ankandebnath.allphotos.logic.CropRect
import com.ankandebnath.allphotos.logic.EditMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** An edit to apply: quarter turns, mirror, crop, colour. */
data class Edit(val turns: Int, val flip: Boolean, val crop: CropRect, val adjust: Adjust, val filter: Int) {
    val isEmpty get() = turns % 4 == 0 && !flip && crop.isFull && adjust.isNeutral && filter == 0
}

/** Bakes edits into a full-quality photo and saves it. */
object Export {
    /** Rotation and mirroring as a matrix, for a bitmap of [w] × [h]. */
    fun orient(turns: Int, flip: Boolean): Matrix = Matrix().apply {
        if (flip) postScale(-1f, 1f)
        postRotate(90f * (turns % 4))
    }

    fun apply(src: Bitmap, e: Edit): Bitmap {
        var b = src
        if (e.turns % 4 != 0 || e.flip) {
            val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, orient(e.turns, e.flip), true)
            if (r !== b && b !== src) b.recycle()
            b = r
        }
        if (!e.crop.isFull) {
            val x = (e.crop.l * b.width).roundToInt().coerceIn(0, b.width - 1)
            val y = (e.crop.t * b.height).roundToInt().coerceIn(0, b.height - 1)
            val w = (e.crop.w * b.width).roundToInt().coerceIn(1, b.width - x)
            val h = (e.crop.h * b.height).roundToInt().coerceIn(1, b.height - y)
            val c = Bitmap.createBitmap(b, x, y, w, h)
            if (c !== b && b !== src) b.recycle()
            b = c
        }
        if (!e.adjust.isNeutral || e.filter != 0) {
            val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
            val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(EditMath.matrix(e.adjust, e.filter))) }
            Canvas(out).drawBitmap(b, 0f, 0f, p)
            if (b !== src) b.recycle()
            b = out
        }
        return b
    }

    private fun formatFor(m: Media): Pair<Bitmap.CompressFormat, String> = when (m.mime) {
        "image/png" -> Bitmap.CompressFormat.PNG to "png"
        "image/webp" -> Bitmap.CompressFormat.WEBP_LOSSY to "webp"
        else -> Bitmap.CompressFormat.JPEG to "jpg"
    }

    /** Only these can be written back over the original in the same format. */
    fun canReplace(m: Media): Boolean = m.mime == "image/jpeg" || m.mime == "image/png" || m.mime == "image/webp"

    suspend fun save(ctx: Context, m: Media, e: Edit, replace: Boolean): Uri? = withContext(Dispatchers.IO) {
        val src = Images.decodeForEdit(ctx, m.uri) ?: return@withContext null
        val out = apply(src, e)
        if (out !== src) src.recycle()
        val (fmt, ext) = formatFor(m)
        val tmp = Saver.tempFile(ctx, ext)
        try {
            if (!Saver.compress(out, tmp, fmt, 95)) return@withContext null
            out.recycle()
            if (fmt == Bitmap.CompressFormat.JPEG) Saver.copyExif(ctx, m.uri, tmp)
            if (replace && canReplace(m)) {
                if (Saver.overwrite(ctx, m.uri, tmp)) {
                    Thumbs.forget(m)
                    m.uri
                } else null
            } else {
                Saver.insert(ctx, tmp, Saver.nameLike(m.name, "_edited", ext), if (ext == "jpg") "image/jpeg" else "image/$ext", Saver.folderOf(m, Convert.FOLDER), m.taken)
            }
        } finally {
            tmp.delete()
        }
    }
}
