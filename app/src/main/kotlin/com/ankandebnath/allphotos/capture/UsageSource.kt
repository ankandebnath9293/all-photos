package com.ankandebnath.allphotos.capture

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.ankandebnath.allphotos.logic.Foreground
import com.ankandebnath.allphotos.logic.Sources

/** Works out which app was in front at a past moment, from the phone's own usage history. */
object UsageSource {
    fun allowed(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    /** App-to-front moments between [from] and [to]. */
    fun events(ctx: Context, from: Long, to: Long): List<Foreground> {
        if (!allowed(ctx)) return emptyList()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val out = ArrayList<Foreground>()
        val ev = runCatching { usm.queryEvents(from, to) }.getOrNull() ?: return emptyList()
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) out += Foreground(e.timeStamp, e.packageName)
        }
        return out
    }

    fun appAt(ctx: Context, time: Long): String? {
        val log = events(ctx, time - 6 * 3_600_000L, time + 2_000)
        return Sources.attribute(log, time, ctx.packageName, slackMs = 500)?.pkg
    }
}
