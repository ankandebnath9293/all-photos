package com.ankandebnath.allphotos.ui

import android.app.PendingIntent
import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.OpResult
import com.ankandebnath.allphotos.data.Ops
import com.ankandebnath.allphotos.media.Convert
import com.ankandebnath.allphotos.media.Share
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pages that open on top of the tabs. */
sealed interface Dest {
    data class Album(val bucketId: Long, val name: String) : Dest
    data class Group(val kind: SetKind, val title: String, val key: String = "") : Dest
    data object Similar : Dest
    data object Large : Dest
    data object Trash : Dest
    data object Settings : Dest
    data object Search : Dest
}

/** Ready-made collections. */
enum class SetKind { VIDEOS, FAVORITES, IMPORTANT, SHOT_APP, SHOT_SITE, WHATSAPP, TELEGRAM, FACEBOOK, SCREENSHOTS, RECENT, DOCS }

/** What the full-screen viewer should show: a list of items and where to start. */
class ViewerReq(val ids: List<Long>, val start: Int, val fromTrash: Boolean = false, val explicit: List<Media> = emptyList())

class EditReq(val media: Media, val trim: Boolean)

/** Navigation and the overlays (viewer, editor) for the main window. */
@Stable
class AppState {
    var tab by mutableIntStateOf(0)
    val stack = mutableStateListOf<Dest>()
    var viewer by mutableStateOf<ViewerReq?>(null)
    var editor by mutableStateOf<EditReq?>(null)
    var details by mutableStateOf<Media?>(null)

    /** Scrolls the grid the viewer was opened from so a photo is on screen (set by that grid). */
    var reveal: (suspend (Long) -> Unit)? = null

    /** Set by the activity: shows the system's confirm dialog when the phone insists on one. */
    var askConsent: (PendingIntent) -> Unit = {}

    fun open(d: Dest) {
        stack += d
    }

    fun back(): Boolean {
        when {
            details != null -> details = null
            editor != null -> editor = null
            viewer != null -> viewer = null
            stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
            tab != 0 -> tab = 0
            else -> return false
        }
        return true
    }

    fun view(items: List<Media>, index: Int) {
        if (items.isEmpty()) return
        viewer = ViewerReq(items.map { it.id }, index.coerceIn(0, items.lastIndex))
    }
}

/** The things you can do to photos, with the short messages that follow. */
class Actions(private val ctx: Context, private val scope: CoroutineScope, private val app: AppState) {
    private fun handle(r: OpResult, done: (Int) -> String) {
        r.consent?.let { app.askConsent(it) }
        if (r.done > 0) Toasts.show(done(r.done))
        else if (r.failed > 0) Toasts.show("Couldn't change ${r.failed} file${if (r.failed == 1) "" else "s"}")
    }

    fun trash(items: List<Media>, after: () -> Unit = {}) {
        if (items.isEmpty()) return
        scope.launch {
            val r = Ops.trash(ctx, items)
            handle(r) { n -> if (n == 1) "Moved to trash" else "Moved $n to trash" }
            after()
        }
    }

    fun restore(items: List<Media>, after: () -> Unit = {}) {
        scope.launch {
            val r = Ops.restore(ctx, items)
            handle(r) { n -> if (n == 1) "Restored" else "Restored $n" }
            after()
        }
    }

    fun deleteForever(items: List<Media>, after: () -> Unit = {}) {
        scope.launch {
            val r = Ops.deleteForever(ctx, items)
            handle(r) { n -> if (n == 1) "Deleted for good" else "Deleted $n for good" }
            after()
        }
    }

    fun important(items: List<Media>, on: Boolean) {
        if (items.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            Db.get(ctx).setImportant(items.map { it.id }, on)
            withContext(Dispatchers.Main) {
                Toasts.show(
                    when {
                        !on -> "Removed from important"
                        items.size == 1 -> "Marked as important"
                        else -> "${items.size} marked as important"
                    },
                )
            }
        }
    }

    fun share(items: List<Media>) = runCatching { Share.send(ctx, items) }

    /** Shares HEIC photos as JPGs, so apps that cannot open HEIC still get them. */
    fun shareAsJpg(items: List<Media>) {
        scope.launch {
            Toasts.show("Preparing JPG…")
            val uris = items.mapNotNull { if (it.isHeic) Convert.shareableJpg(ctx, it) else it.uri }
            if (uris.isNotEmpty()) runCatching { Share.sendUris(ctx, uris, "image/jpeg") }
        }
    }

    fun convert(items: List<Media>) {
        val heic = items.filter { it.isHeic }
        if (heic.isEmpty()) {
            Toasts.show("Only HEIC photos need converting")
            return
        }
        scope.launch {
            Toasts.show(if (heic.size == 1) "Converting…" else "Converting ${heic.size} photos…")
            var ok = 0
            for (m in heic) if (Convert.toJpg(ctx, m) != null) ok++
            Toasts.show(if (ok == heic.size) "Saved as JPG" else "Saved $ok of ${heic.size} as JPG")
            MediaRepo.refresh(ctx)
        }
    }
}
