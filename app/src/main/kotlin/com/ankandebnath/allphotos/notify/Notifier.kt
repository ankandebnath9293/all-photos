package com.ankandebnath.allphotos.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.provider.MediaStore
import android.content.ContentUris
import android.util.Size
import com.ankandebnath.allphotos.R
import com.ankandebnath.allphotos.capture.Apps
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.logic.Links
import com.ankandebnath.allphotos.logic.ShotBatch
import com.ankandebnath.allphotos.ui.MainActivity

/** The small notification after a screenshot, with "Mark as important" right there. */
object Notifier {
    const val CHANNEL_SHOTS = "shots"
    const val ID_SHOTS = 1001

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(CHANNEL_SHOTS, "New screenshots", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Lets you mark a screenshot as important (so it is never cleaned up) right after taking it"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    fun allowed(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            ctx.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true

    private fun thumb(ctx: Context, id: Long): Bitmap? = runCatching {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        ctx.contentResolver.loadThumbnail(uri, Size(512, 512), null)
    }.getOrNull()

    private fun action(ctx: Context, action: String, ids: List<Long>, req: Int): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(action).putExtra(ActionReceiver.EXTRA_IDS, ids.toLongArray())
        return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun shots(ctx: Context, b: ShotBatch) {
        if (!allowed(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val latest = b.ids.last()
        val src = Db.get(ctx).sources.value[latest]
        val from = src?.pkg?.let { Apps.label(ctx, it) }
        val site = Links.domain(src?.url)
        val n = b.size
        val title = if (n == 1) "Screenshot saved" else "$n screenshots saved"
        val text = listOfNotNull(from?.let { "From $it" }, site).joinToString(" · ").ifEmpty { "Tap to view" }
        val open = PendingIntent.getActivity(
            ctx, 7,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_ID, latest)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val mark = Notification.Action.Builder(
            Icon.createWithResource(ctx, R.drawable.ic_stat_photo),
            if (n == 1) "Mark as important" else "Mark all $n as important",
            action(ctx, ActionReceiver.ACTION_MARK, b.ids, 1),
        ).build()
        val trash = Notification.Action.Builder(
            Icon.createWithResource(ctx, R.drawable.ic_stat_photo),
            if (n == 1) "Delete" else "Delete all $n",
            action(ctx, ActionReceiver.ACTION_TRASH, b.ids, 2),
        ).build()
        val pic = thumb(ctx, latest)
        val builder = Notification.Builder(ctx, CHANNEL_SHOTS)
            .setSmallIcon(R.drawable.ic_stat_photo)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setTimeoutAfter(20 * 60_000L)
            .addAction(mark)
            .addAction(trash)
        if (pic != null) {
            builder.setLargeIcon(pic)
            builder.setStyle(Notification.BigPictureStyle().bigPicture(pic).showBigPictureWhenCollapsed(false))
        }
        nm.notify(ID_SHOTS, builder.build())
    }

    /** Replaces the notification with a short confirmation that disappears by itself. */
    fun confirm(ctx: Context, text: String) {
        if (!allowed(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val n = Notification.Builder(ctx, CHANNEL_SHOTS)
            .setSmallIcon(R.drawable.ic_stat_photo)
            .setContentTitle(text)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(3_000)
            .build()
        nm.notify(ID_SHOTS, n)
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(ID_SHOTS)
    }
}
