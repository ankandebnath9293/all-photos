package com.ankandebnath.allphotos.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.data.MediaRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes new photos and videos into the gallery, or over an existing file. */
object Saver {
    private const val TAG = "AllPhotos"

    fun tempFile(ctx: Context, ext: String): File {
        val dir = File(ctx.cacheDir, "work").apply { mkdirs() }
        return File.createTempFile("ap_", ".$ext", dir)
    }

    fun sharedFile(ctx: Context, name: String): File {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        return File(dir, name)
    }

    /** "Pictures/Screenshots/" style folder for an item, defaulting to a folder of our own. */
    fun folderOf(m: Media?, fallback: String): String {
        val rp = m?.relPath.orEmpty()
        // Files inside other apps' private media folders cannot get new neighbours; use ours instead.
        if (rp.isEmpty() || rp.startsWith("Android/", ignoreCase = true)) return fallback
        return rp
    }

    /**
     * Adds [file] to the gallery as [name] in [folder]. The file is hidden from other apps until it
     * is fully written, so nothing ever sees half a photo.
     */
    suspend fun insert(ctx: Context, file: File, name: String, mime: String, folder: String, takenMs: Long? = null): Uri? =
        withContext(Dispatchers.IO) {
            val video = mime.startsWith("video/")
            val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val cr = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (takenMs != null) put(MediaStore.MediaColumns.DATE_TAKEN, takenMs)
            }
            val uri = runCatching { cr.insert(collection, values) }
                .onFailure { Log.w(TAG, "Insert failed", it) }
                .getOrNull() ?: return@withContext null
            try {
                cr.openOutputStream(uri, "w")?.use { out -> file.inputStream().use { it.copyTo(out, 1 shl 16) } }
                    ?: error("No output stream")
                cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                MediaRepo.refresh(ctx)
                uri
            } catch (t: Throwable) {
                Log.w(TAG, "Write failed", t)
                runCatching { cr.delete(uri, null, null) }
                null
            }
        }

    /** Replaces an existing file's contents, keeping its place in the gallery. */
    suspend fun overwrite(ctx: Context, target: Uri, file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            ctx.contentResolver.openOutputStream(target, "wt")?.use { out -> file.inputStream().use { it.copyTo(out, 1 shl 16) } }
                ?: error("No output stream")
            true
        }.onFailure { Log.w(TAG, "Overwrite failed", it) }.getOrDefault(false).also {
            if (it) MediaRepo.refresh(ctx)
        }
    }

    fun compress(bitmap: Bitmap, file: File, format: Bitmap.CompressFormat, quality: Int = 95): Boolean =
        runCatching { file.outputStream().use { bitmap.compress(format, quality, it) } }.getOrDefault(false)

    /** Camera details worth keeping when a photo is re-saved. Orientation is reset because edits are baked in. */
    private val keepTags = listOf(
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
    )

    /** Copies camera details from the original photo to a new JPEG file. */
    fun copyExif(ctx: Context, from: Uri, toJpeg: File) {
        runCatching {
            val src = ctx.contentResolver.openInputStream(from)?.use { ExifInterface(it) } ?: return
            val dst = ExifInterface(toJpeg.absolutePath)
            for (tag in keepTags) src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
            dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            dst.setAttribute(ExifInterface.TAG_SOFTWARE, "All Photos")
            dst.saveAttributes()
        }.onFailure { Log.w(TAG, "Could not copy photo details", it) }
    }

    /** A file name that keeps the original's name with a new extension or suffix. */
    fun nameLike(original: String, suffix: String, ext: String): String {
        val base = original.substringBeforeLast('.', original)
        return "$base$suffix.$ext"
    }
}
