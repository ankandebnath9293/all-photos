package com.ankandebnath.allphotos.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.ankandebnath.allphotos.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turns iPhone HEIC photos into JPGs that every app and website accepts. */
object Convert {
    const val FOLDER = "Pictures/All Photos/"

    /** Saves a JPG copy next to the original. The original is kept. */
    suspend fun toJpg(ctx: Context, m: Media): Uri? = withContext(Dispatchers.IO) {
        val bmp = Images.decodeForEdit(ctx, m.uri, maxPixels = 50_000_000L) ?: return@withContext null
        val tmp = Saver.tempFile(ctx, "jpg")
        try {
            if (!Saver.compress(bmp, tmp, Bitmap.CompressFormat.JPEG, 95)) return@withContext null
            bmp.recycle()
            Saver.copyExif(ctx, m.uri, tmp)
            Saver.insert(ctx, tmp, Saver.nameLike(m.name, "", "jpg"), "image/jpeg", Saver.folderOf(m, FOLDER), m.taken)
        } finally {
            tmp.delete()
        }
    }

    /** A temporary JPG for sharing, so apps that do not understand HEIC still get the photo. */
    suspend fun shareableJpg(ctx: Context, m: Media): Uri? = withContext(Dispatchers.IO) {
        val bmp = Images.decodeForEdit(ctx, m.uri, maxPixels = 50_000_000L) ?: return@withContext null
        val out = Saver.sharedFile(ctx, Saver.nameLike(m.name, "", "jpg"))
        if (!Saver.compress(bmp, out, Bitmap.CompressFormat.JPEG, 95)) return@withContext null
        bmp.recycle()
        Saver.copyExif(ctx, m.uri, out)
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
    }
}
