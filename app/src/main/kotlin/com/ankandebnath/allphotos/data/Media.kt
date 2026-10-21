package com.ankandebnath.allphotos.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import com.ankandebnath.allphotos.logic.Chat

/** One photo or video on the phone, as the system's media index knows it. */
@Immutable
data class Media(
    val id: Long,
    val isVideo: Boolean,
    val mime: String,
    val name: String,
    /** When it was taken, in milliseconds (falls back to when it was added). */
    val taken: Long,
    /** When the file arrived on the phone, in milliseconds. */
    val added: Long,
    val modified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val orientation: Int,
    val bucketId: Long,
    val bucket: String,
    val relPath: String,
    val path: String,
    val favorite: Boolean,
    val owner: String?,
    val isScreenshot: Boolean,
    val chat: Chat?,
    /** Set only for items in the trash: when the phone will delete it for good. */
    val expires: Long = 0,
    /** A file opened from another app that is not in the phone's media index. */
    val external: Uri? = null,
) {
    val uri: Uri
        get() = external ?: ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id,
        )

    val isHeic: Boolean
        get() = mime == "image/heic" || mime == "image/heif" ||
            name.endsWith(".heic", true) || name.endsWith(".heif", true)

    val isGif: Boolean get() = mime == "image/gif"

    /** Width and height as they appear on screen (rotated photos swap them). */
    val shownW: Int get() = if (orientation == 90 || orientation == 270) height else width
    val shownH: Int get() = if (orientation == 90 || orientation == 270) width else height

    val aspect: Float
        get() = if (shownW > 0 && shownH > 0) shownW.toFloat() / shownH else 1f
}

/** Everything in the library at one moment, newest first. */
class Library(val items: List<Media>) {
    val byId: Map<Long, Media> = items.associateBy { it.id }
    val times: LongArray = LongArray(items.size) { items[it].taken }
    val ids: LongArray = LongArray(items.size) { items[it].id }
    val screenshots: List<Media> by lazy { items.filter { it.isScreenshot } }
    val totalBytes: Long by lazy { items.sumOf { it.size } }

    companion object {
        val EMPTY = Library(emptyList())
    }
}

/** A folder of photos (what Android calls a bucket). */
@Immutable
data class Album(
    val id: Long,
    val name: String,
    val relPath: String,
    val count: Int,
    val bytes: Long,
    val cover: Media,
    val latest: Long,
)
