package com.ankandebnath.allphotos

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.Ops
import com.ankandebnath.allphotos.data.Prefs
import com.ankandebnath.allphotos.logic.Look
import com.ankandebnath.allphotos.media.LookRender
import com.ankandebnath.allphotos.logic.Chat
import com.ankandebnath.allphotos.logic.CropRect
import com.ankandebnath.allphotos.logic.ShotBatch
import com.ankandebnath.allphotos.logic.Source
import com.ankandebnath.allphotos.media.Analyze
import com.ankandebnath.allphotos.media.Convert
import com.ankandebnath.allphotos.media.DetailsReader
import com.ankandebnath.allphotos.media.Edit
import com.ankandebnath.allphotos.media.Export
import com.ankandebnath.allphotos.media.Images
import com.ankandebnath.allphotos.media.Picture
import com.ankandebnath.allphotos.media.Video
import com.ankandebnath.allphotos.notify.Notifier
import com.ankandebnath.allphotos.work.Cleaner
import com.ankandebnath.allphotos.work.Indexer
import com.ankandebnath.allphotos.work.SimilarData
import kotlinx.coroutines.delay

/**
 * Exercises every feature on a real (emulated) phone with the test files the smoke script copies
 * in, and reports one line: "db=OK lib=OK ocr=OK …". The emulator test fails if any part is not OK.
 */
object SelfTest {
    private const val TAG = "ALLPHOTOS_SELFTEST"

    private suspend fun step(name: String, out: MutableList<String>, block: suspend () -> Boolean?) {
        val r = runCatching { block() }
        val v = when {
            r.isFailure -> {
                Log.w(TAG, "$name failed", r.exceptionOrNull())
                "FAIL"
            }
            r.getOrNull() == null -> "SKIP"
            r.getOrNull() == true -> "OK"
            else -> "FAIL"
        }
        Log.i(TAG, "$name=$v")
        out += "$name=$v"
    }

    suspend fun run(ctx: Context): String {
        val out = ArrayList<String>()
        MediaRepo.refresh(ctx)
        val lib = MediaRepo.now
        val db = Db.get(ctx)
        db.warm()
        val photos = lib.items.filter { !it.isVideo && !it.isScreenshot && it.chat == null }
        val video = lib.items.firstOrNull { it.isVideo && it.chat == null }

        step("lib", out) {
            Log.i(TAG, "items=${lib.items.size} shots=${lib.screenshots.size} wa=${lib.items.count { it.chat == Chat.WHATSAPP }} tg=${lib.items.count { it.chat == Chat.TELEGRAM }} fb=${lib.items.count { it.chat == Chat.FACEBOOK }} videos=${lib.items.count { it.isVideo }}")
            lib.items.size >= 5 && lib.screenshots.isNotEmpty() && lib.items.any { it.chat == Chat.WHATSAPP } && lib.items.any { it.chat == Chat.TELEGRAM } &&
                lib.items.any { it.chat == Chat.FACEBOOK }
        }
        step("db", out) {
            val id = photos.first().id
            db.setImportant(listOf(id), true)
            val a = id in db.important.value
            db.setImportant(listOf(id), false)
            db.setSource(lib.screenshots.first().id, Source("com.android.chrome", "example.com/page", Source.LIVE))
            db.setText(id, "zebra crossing", "Road", photos.first().modified)
            a && id !in db.important.value && id in db.search("zebr") && id in db.search("road") &&
                db.sources.value[lib.screenshots.first().id]?.pkg == "com.android.chrome"
        }
        step("ocr", out) {
            val b = Bitmap.createBitmap(1080, 600, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.drawColor(Color.WHITE)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 96f }
            c.drawText("ALLPHOTOS 2468", 60f, 300f, p)
            val t = Analyze.text(b).orEmpty()
            Log.i(TAG, "ocr text: $t")
            t.contains("ALLPHOTOS", ignoreCase = true)
        }
        step("textlines", out) {
            val b = Bitmap.createBitmap(1080, 700, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.drawColor(Color.WHITE)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 80f }
            c.drawText("Call 01712345678", 60f, 250f, p)
            c.drawText("motionblurmedia.agency", 60f, 450f, p)
            val lines = Analyze.lines(b).orEmpty()
            Log.i(TAG, "text lines: ${lines.map { it.text + " @" + it.box }}")
            val all = lines.joinToString("\n") { it.text }
            val smart = com.ankandebnath.allphotos.logic.Smart.find(all)
            Log.i(TAG, "smart: $smart")
            lines.size >= 2 && lines.all { it.box.width() > 0 } && smart.isNotEmpty()
        }
        step("shader", out) {
            val sh = android.graphics.RuntimeShader(Look.SHADER)
            sh.setFloatUniform("size", 100f, 100f)
            val u = Look.FILTERS[1].second.uniforms()
            Look.NAMES.forEachIndexed { i, n -> sh.setFloatUniform(n, u[i]) }
            LookRender.effect(Look.FILTERS[4].second, 100f, 100f) != null
        }
        step("bake", out) {
            val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            b.eraseColor(Color.rgb(150, 100, 80))
            val o = LookRender.bake(b, Look.FILTERS.first { it.first == "Mono" }.second)
            val px = o.getPixel(32, 32)
            Log.i(TAG, "bake mono: ${Color.red(px)},${Color.green(px)},${Color.blue(px)}")
            Color.red(px) == Color.green(px) && Color.green(px) == Color.blue(px)
        }
        step("docs", out) {
            val id = lib.items.first { !it.isVideo && !it.isScreenshot }.id
            db.setText(id, "Sub Total 470.00\nVAT 23.50\nGrand Total 493.50\nCash 500.00\nChange 6.50", "", lib.byId[id]!!.modified)
            com.ankandebnath.allphotos.work.Indexer.sortDocs(ctx)
            val k = db.docs()[id]
            Log.i(TAG, "docs: $k")
            k == com.ankandebnath.allphotos.logic.DocKind.RECEIPT.ordinal
        }
        step("labels", out) {
            val b = Analyze.forReading(ctx, photos.first()) ?: return@step false
            Analyze.labels(b) != null
        }
        step("hash", out) { Analyze.fingerprint(ctx, photos.first()) != null }
        step("similar", out) {
            Indexer.fingerprints(ctx)
            val s = SimilarData.compute(ctx, MediaRepo.now)
            Log.i(TAG, "similar groups=${s.groups.size} scanned=${s.scanned}/${s.total}")
            s.groups.isNotEmpty()
        }
        step("decode", out) {
            photos.take(6).all { m ->
                val p = Images.decode(ctx, m.uri, 1600)
                Log.i(TAG, "decode ${m.name} ${m.mime} -> ${p.javaClass.simpleName}")
                p !is Picture.Failed
            }
        }
        step("details", out) {
            val v = video ?: return@step null
            val d = DetailsReader.read(ctx, v, null, null)
            val f = DetailsReader.readVideo(ctx, v.uri)
            Log.i(TAG, "video ${v.name}: fps=${f?.fps} codec=${f?.codec} more=${d.more.map { it.title }}")
            val pd = DetailsReader.read(ctx, photos.first(), null, null)
            d.primary.isNotEmpty() && (f?.fps ?: 0f) > 1f && pd.primary.isNotEmpty() && pd.more.isNotEmpty()
        }
        val made = ArrayList<android.net.Uri>()
        step("frame", out) {
            val v = video ?: return@step null
            val u = Video.saveFrame(ctx, v, 500) ?: return@step false
            made += u
            true
        }
        step("trim", out) {
            val v = video ?: return@step null
            when (val r = Video.trim(ctx, v.uri, 0, 1500)) {
                is Video.TrimResult.Done -> (r.file.length() > 1000).also { r.file.delete() }
                is Video.TrimResult.Failed -> {
                    Log.w(TAG, "trim: ${r.reason}")
                    false
                }
            }
        }
        step("videofilter", out) {
            val v = video ?: return@step null
            when (val r = Video.trim(ctx, v.uri, 0, 1000, com.ankandebnath.allphotos.media.VideoLooks.effects(1))) {
                is Video.TrimResult.Done -> (r.file.length() > 1000).also { r.file.delete() }
                is Video.TrimResult.Failed -> {
                    Log.w(TAG, "video filter: ${r.reason}")
                    false
                }
            }
        }
        step("convert", out) {
            val h = lib.items.firstOrNull { it.isHeic } ?: return@step null
            val u = Convert.toJpg(ctx, h) ?: return@step false
            made += u
            true
        }
        step("edit", out) {
            val src = photos.first()
            val e = Edit(1, false, CropRect(0.1f, 0.1f, 0.9f, 0.8f), Look.FILTERS[1].second + Look(brightness = 0.1f, vignette = 0.3f))
            val u = Export.save(ctx, src, e, replace = false) ?: return@step false
            made += u
            true
        }
        step("trash", out) {
            MediaRepo.refresh(ctx)
            val ids = made.mapNotNull { runCatching { android.content.ContentUris.parseId(it) }.getOrNull() }
            val items = MediaRepo.now.items.filter { it.id in ids }
            if (items.isEmpty()) return@step false
            val r = Ops.trash(ctx, items, allowDialog = false)
            val inTrash = MediaRepo.trashed(ctx).count { it.id in ids }
            val back = Ops.restore(ctx, items.take(1))
            delay(300)
            MediaRepo.refresh(ctx)
            val restored = MediaRepo.now.items.any { it.id == items[0].id }
            val gone = Ops.deleteForever(ctx, MediaRepo.trashed(ctx).filter { it.id in ids } + MediaRepo.now.items.filter { it.id in ids })
            Log.i(TAG, "trash: moved=${r.done} inTrash=$inTrash restored=$restored deleted=${gone.done}")
            r.done == items.size && inTrash == items.size && back.done == 1 && restored
        }
        step("restore", out) {
            // Pretend version 1.0 trashed two screenshots from before the install day, then check 1.1 puts them back.
            MediaRepo.refresh(ctx)
            val prefs = Prefs.get(ctx)
            val realStart = prefs.rulesStart
            val shots = MediaRepo.now.screenshots.take(2)
            if (shots.size < 2) return@step false
            prefs.rulesStart = System.currentTimeMillis() + 3_600_000L
            prefs.restoredEarlyTrash = false
            val moved = Ops.trash(ctx, shots, allowDialog = false).done
            val back = Cleaner.restoreEarly(ctx)
            MediaRepo.refresh(ctx)
            val present = shots.all { s -> MediaRepo.now.byId.containsKey(s.id) }
            prefs.rulesStart = realStart
            Log.i(TAG, "restore: moved=$moved back=$back present=$present")
            moved == 2 && back == 2 && present && prefs.restoredEarlyTrash
        }
        step("rules", out) {
            // A year from now, counting from long ago: everything is due.
            val plan = Cleaner.plan(MediaRepo.now, Prefs.get(ctx).now, emptySet(), start = 0L, now = System.currentTimeMillis() + 400L * 86_400_000L)
            // Counting from today (the default), the pushed test files are older than the start: nothing is due.
            val fromInstall = Cleaner.plan(MediaRepo.now, Prefs.get(ctx).now, emptySet(), start = System.currentTimeMillis() + 60_000L, now = System.currentTimeMillis() + 400L * 86_400_000L)
            Log.i(TAG, "rules from install: ${fromInstall.all.size} due")
            if (fromInstall.all.isNotEmpty()) return@step false
            Log.i(TAG, "rules (a year ahead): shots=${plan.shots.size} wa=${plan.whatsapp.size} tg=${plan.telegram.size}")
            plan.shots.size == MediaRepo.now.screenshots.size && plan.whatsapp.isNotEmpty() && plan.telegram.isNotEmpty() && plan.facebook.isEmpty()
        }
        step("notify", out) {
            if (!Notifier.allowed(ctx)) return@step null
            val shots = MediaRepo.now.screenshots.take(3).map { it.id }
            Notifier.shots(ctx, ShotBatch(System.currentTimeMillis(), shots))
            delay(500)
            ctx.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == Notifier.ID_SHOTS }
        }
        step("index", out) {
            Indexer.run(ctx, heavy = false) { false }
            val (hashed, read) = db.counts()
            Log.i(TAG, "index: hashed=$hashed read=$read")
            hashed > 0 && read >= MediaRepo.now.screenshots.size
        }
        MediaRepo.refresh(ctx)
        return out.joinToString(" ")
    }
}
