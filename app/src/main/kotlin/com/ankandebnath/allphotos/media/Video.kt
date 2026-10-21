package com.ankandebnath.allphotos.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.ankandebnath.allphotos.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/** Saving a single frame of a video, and trimming videos. */
object Video {
    private const val TAG = "AllPhotos"
    const val FRAMES_FOLDER = "Pictures/All Photos/Frames/"
    const val CLIPS_FOLDER = "Movies/All Photos/"

    /** The exact frame at [positionMs], at the video's full resolution. */
    suspend fun frameAt(ctx: Context, uri: Uri, positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            r.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (t: Throwable) {
            Log.w(TAG, "Frame grab failed", t)
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Saves the frame as a JPG in Pictures/All Photos/Frames and returns it. */
    suspend fun saveFrame(ctx: Context, m: Media, positionMs: Long): Uri? = withContext(Dispatchers.IO) {
        val bmp = frameAt(ctx, m.uri, positionMs) ?: return@withContext null
        val tmp = Saver.tempFile(ctx, "jpg")
        try {
            if (!Saver.compress(bmp, tmp, Bitmap.CompressFormat.JPEG, 96)) return@withContext null
            bmp.recycle()
            val s = positionMs / 1000
            val stamp = String.format(Locale.US, "%02dm%02ds%03d", s / 60, s % 60, positionMs % 1000)
            Saver.insert(ctx, tmp, Saver.nameLike(m.name, "_frame_$stamp", "jpg"), "image/jpeg", FRAMES_FOLDER, System.currentTimeMillis())
        } finally {
            tmp.delete()
        }
    }

    /** A few evenly spaced frames for the trim timeline. */
    suspend fun strip(ctx: Context, uri: Uri, durationMs: Long, count: Int, height: Int): List<Bitmap> = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        val out = ArrayList<Bitmap>()
        try {
            r.setDataSource(ctx, uri)
            for (i in 0 until count) {
                val t = durationMs * (i + 0.5) / count
                val us = (t * 1000).toLong()
                val f = runCatching { r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, height * 2, height) }.getOrNull()
                    ?: runCatching { r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()?.let { full ->
                        val s = height.toFloat() / full.height.coerceAtLeast(1)
                        Bitmap.createScaledBitmap(full, (full.width * s).toInt().coerceAtLeast(1), height, true).also { if (it !== full) full.recycle() }
                    }
                if (f != null) out += f
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Strip failed", t)
        } finally {
            runCatching { r.release() }
        }
        out
    }

    sealed interface TrimResult {
        class Done(val file: File) : TrimResult
        class Failed(val reason: String) : TrimResult
    }

    /**
     * Cuts [startMs]..[endMs] out of a video into a new MP4 file. Where the cut points allow, the
     * video is copied without re-encoding so quality is untouched.
     */
    suspend fun trim(
        ctx: Context, uri: Uri, startMs: Long, endMs: Long,
        effects: List<androidx.media3.common.Effect> = emptyList(),
        onProgress: (Float) -> Unit = {},
    ): TrimResult {
        val out = Saver.tempFile(ctx, "mp4")
        val main = Handler(Looper.getMainLooper())
        return suspendCancellableCoroutine { cont ->
            main.post {
                val item = MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                            .setEndPositionMs(endMs)
                            .build(),
                    )
                    .build()
                val transformer = Transformer.Builder(ctx)
                    // Copying without re-encoding is only possible when no filter is applied.
                    .experimentalSetTrimOptimizationEnabled(effects.isEmpty())
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(TrimResult.Done(out))
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            Log.w(TAG, "Trim failed", exportException)
                            out.delete()
                            if (cont.isActive) cont.resume(TrimResult.Failed(exportException.errorCodeName))
                        }
                    })
                    .build()
                try {
                    val edited = EditedMediaItem.Builder(item)
                        .apply { if (effects.isNotEmpty()) setEffects(androidx.media3.transformer.Effects(emptyList(), effects)) }
                        .build()
                    transformer.start(edited, out.absolutePath)
                } catch (t: Throwable) {
                    Log.w(TAG, "Trim could not start", t)
                    if (cont.isActive) cont.resume(TrimResult.Failed(t.message ?: "error"))
                    return@post
                }
                val holder = androidx.media3.transformer.ProgressHolder()
                val poll = object : Runnable {
                    override fun run() {
                        if (!cont.isActive) return
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                        main.postDelayed(this, 250)
                    }
                }
                main.post(poll)
                cont.invokeOnCancellation { main.post { runCatching { transformer.cancel() }; out.delete() } }
            }
        }
    }
}
