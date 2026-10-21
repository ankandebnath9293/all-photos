package com.ankandebnath.allphotos.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ankandebnath.allphotos.capture.ShotTracker
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.Ops
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles the buttons on the screenshot notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ids = intent.getLongArrayExtra(EXTRA_IDS)?.toList().orEmpty()
        if (ids.isEmpty()) return
        val app = context.applicationContext
        val done = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_MARK -> {
                        Db.get(app).setImportant(ids, true)
                        Notifier.confirm(app, if (ids.size == 1) "Marked as important" else "${ids.size} screenshots marked as important")
                    }
                    ACTION_TRASH -> {
                        if (MediaRepo.library.value == null) MediaRepo.refresh(app)
                        val items = ids.mapNotNull { MediaRepo.now.byId[it] }
                        val r = Ops.trash(app, items, allowDialog = false)
                        if (r.done > 0) Notifier.confirm(app, "Moved to trash") else Notifier.cancel(app)
                    }
                }
                ShotTracker.forget(ids)
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK = "com.ankandebnath.allphotos.MARK"
        const val ACTION_TRASH = "com.ankandebnath.allphotos.TRASH"
        const val EXTRA_IDS = "ids"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
