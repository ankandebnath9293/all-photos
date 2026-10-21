package com.ankandebnath.allphotos.capture

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ankandebnath.allphotos.logic.Sources
import java.util.concurrent.ConcurrentHashMap

/** Names and icons of the apps screenshots came from. */
object Apps {
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, ImageBitmap>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    private val friendly = mapOf(
        "com.facebook.orca" to "Messenger",
        "com.facebook.katana" to "Facebook",
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.android.chrome" to "Chrome",
        "com.google.android.apps.photos" to "Google Photos",
        "com.google.android.gm" to "Gmail",
        "com.google.android.apps.maps" to "Maps",
    )

    fun label(ctx: Context, pkg: String?): String {
        if (pkg.isNullOrEmpty()) return "Unknown app"
        labels[pkg]?.let { return it }
        if (Sources.isLauncher(pkg)) return "Home screen".also { labels[pkg] = it }
        val l = friendly[pkg] ?: runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
        }.getOrNull() ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        labels[pkg] = l
        return l
    }

    fun icon(ctx: Context, pkg: String?, px: Int = 96): ImageBitmap? {
        if (pkg.isNullOrEmpty() || pkg in missing) return null
        icons[pkg]?.let { return it }
        val d = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull()
        if (d == null) {
            missing += pkg
            return null
        }
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, px, px)
        d.draw(c)
        val img = b.asImageBitmap()
        icons[pkg] = img
        return img
    }
}
