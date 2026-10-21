package com.ankandebnath.allphotos.capture

import android.content.Context
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.logic.Foreground
import com.ankandebnath.allphotos.logic.Links
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A short memory of which app was on screen (and which address a browser showed). Kept for two
 * days, only on this phone, and used for nothing but labelling screenshots.
 */
object FgLog {
    private const val KEEP = 240
    private val recent = ArrayDeque<Foreground>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    @Volatile var lastPkg: String? = null
        private set

    private fun add(ctx: Context, e: Foreground) {
        synchronized(recent) {
            recent.addLast(e)
            while (recent.size > KEEP) recent.removeFirst()
        }
        val app = ctx.applicationContext
        scope.launch { runCatching { Db.get(app).logForeground(e) } }
    }

    /** An app came to the front. */
    fun app(ctx: Context, pkg: String, time: Long = System.currentTimeMillis()) {
        if (pkg == lastPkg) return
        lastPkg = pkg
        add(ctx, Foreground(time, pkg, lastUrl(pkg)))
    }

    private val urls = HashMap<String, String>()

    fun lastUrl(pkg: String): String? = synchronized(urls) { urls[pkg] }

    /** A browser's address bar shows [raw]. */
    fun url(ctx: Context, pkg: String, raw: String?, time: Long = System.currentTimeMillis()) {
        val clean = Links.clean(raw) ?: return
        val changed = synchronized(urls) {
            val prev = urls[pkg]
            urls[pkg] = clean
            prev != clean
        }
        if (changed && pkg == lastPkg) add(ctx, Foreground(time, pkg, clean))
    }

    fun snapshot(): List<Foreground> = synchronized(recent) { recent.toList() }

    fun prune(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch { runCatching { Db.get(app).pruneForeground(System.currentTimeMillis() - 2 * 86_400_000L) } }
    }
}
