package com.ankandebnath.allphotos.media

import android.app.WallpaperManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ankandebnath.allphotos.data.Media

/** Handing photos and videos to other apps. */
object Share {
    fun send(ctx: Context, items: List<Media>) {
        if (items.isEmpty()) return
        sendUris(ctx, items.map { it.uri }, mimeFor(items))
    }

    fun sendUris(ctx: Context, uris: List<Uri>, mime: String) {
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = mime
        intent.clipData = ClipData.newUri(ctx.contentResolver, "media", uris[0]).apply {
            for (u in uris.drop(1)) addItem(ClipData.Item(u))
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun mimeFor(items: List<Media>): String {
        val types = items.map { it.mime }.distinct()
        return when {
            types.size == 1 -> types[0].ifEmpty { "*/*" }
            items.all { it.isVideo } -> "video/*"
            items.none { it.isVideo } -> "image/*"
            else -> "*/*"
        }
    }

    fun openWith(ctx: Context, m: Media) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, m.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens the phone's own "set as wallpaper" screen for a photo. */
    fun setAs(ctx: Context, m: Media) {
        val i = runCatching { WallpaperManager.getInstance(ctx).getCropAndSetWallpaperIntent(m.uri) }.getOrNull()
            ?: Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(m.uri, m.mime).putExtra("mimeType", m.mime)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    fun openLink(ctx: Context, url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
