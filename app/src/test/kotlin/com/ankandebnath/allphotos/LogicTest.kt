package com.ankandebnath.allphotos

import com.ankandebnath.allphotos.logic.Adjust
import com.ankandebnath.allphotos.logic.Batches
import com.ankandebnath.allphotos.logic.Browsers
import com.ankandebnath.allphotos.logic.Candidate
import com.ankandebnath.allphotos.logic.Chat
import com.ankandebnath.allphotos.logic.CropRect
import com.ankandebnath.allphotos.logic.EditMath
import com.ankandebnath.allphotos.logic.Fmt
import com.ankandebnath.allphotos.logic.Foreground
import com.ankandebnath.allphotos.logic.Links
import com.ankandebnath.allphotos.logic.Row
import com.ankandebnath.allphotos.logic.Rules
import com.ankandebnath.allphotos.logic.Similar
import com.ankandebnath.allphotos.logic.Sources
import com.ankandebnath.allphotos.logic.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class RulesTest {
    @Test fun screenshotsAreRecognised() {
        assertTrue(Rules.isScreenshot("Pictures/Screenshots/", "Screenshots", "Screenshot_20261021-200700.png"))
        assertTrue(Rules.isScreenshot("DCIM/Screenshots/", null, "x.jpg"))
        assertTrue(Rules.isScreenshot("Pictures/", null, "Screenshot_20261021.png"))
        assertFalse(Rules.isScreenshot("DCIM/Camera/", "Camera", "PXL_20261021_120000.jpg"))
        assertFalse(Rules.isScreenshot("Download/", "Download", "Screenshot_x.png"))
    }

    @Test fun chatFoldersAreRecognised() {
        assertEquals(Chat.WHATSAPP, Rules.chatOf("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"))
        assertEquals(Chat.WHATSAPP, Rules.chatOf("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Sent/"))
        assertEquals(Chat.WHATSAPP, Rules.chatOf("Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images/"))
        assertNull(Rules.chatOf("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers/"))
        assertNull(Rules.chatOf("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Profile Photos/"))
        assertEquals(Chat.TELEGRAM, Rules.chatOf("Android/media/org.telegram.messenger/Telegram/Telegram Images/"))
        assertEquals(Chat.TELEGRAM, Rules.chatOf("Pictures/Telegram/"))
        assertEquals(Chat.TELEGRAM, Rules.chatOf("Movies/Telegram/"))
        assertNull(Rules.chatOf("DCIM/Camera/"))
        assertNull(Rules.chatOf("Pictures/Messenger/"))
    }

    @Test fun dueAfterThirtyDaysUnlessImportant() {
        val now = 1_800_000_000_000L
        val day = Rules.DAY_MS
        data class F(val id: Long, val t: Long)
        val files = listOf(F(1, now - 31 * day), F(2, now - 29 * day), F(3, now - 30 * day), F(4, now - 90 * day), F(5, 0))
        val due = Rules.due(files, now, 30, setOf(4L), { it.id }, { it.t }).map { it.id }
        assertEquals(listOf(1L, 3L), due)
    }

    @Test fun daysLeftRoundsUp() {
        val now = 1_800_000_000_000L
        val day = Rules.DAY_MS
        assertEquals(1, Rules.daysLeft(now - 29 * day - 1000, now, 30))
        assertEquals(0, Rules.daysLeft(now - 30 * day, now, 30))
        assertEquals(30, Rules.daysLeft(now, now, 30))
        assertEquals(3, Rules.daysLeft(now - 27 * day, now, 30))
    }
}

class BatchTest {
    @Test fun screenshotsWithinThirtySecondsShareABatch() {
        var b = Batches.add(null, 1, 1_000)
        b = Batches.add(b, 2, 10_000)
        b = Batches.add(b, 3, 31_000)
        assertEquals(listOf(1L, 2L, 3L), b.ids)
        val next = Batches.add(b, 4, 31_001)
        assertEquals(listOf(4L), next.ids)
        assertEquals(31_001L, next.start)
    }

    @Test fun sameScreenshotIsNotAddedTwice() {
        val b = Batches.add(Batches.add(null, 1, 0), 1, 100)
        assertEquals(1, b.size)
    }

    @Test fun splitGroupsByWindow() {
        val batches = Batches.split(listOf(1L to 0L, 2L to 5_000L, 3L to 60_000L, 4L to 70_000L, 5L to 200_000L))
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L), listOf(5L)), batches.map { it.ids })
    }
}

class SimilarTest {
    private fun gray(seed: Int, noise: Int = 0): IntArray = IntArray(72) { i -> ((i * 37 + seed * 11) % 200) + (if (i % 7 == 0) noise else 0) }

    @Test fun dHashIsStableAndSensitive() {
        val a = Similar.dHash(gray(1))
        val b = Similar.dHash(gray(1, noise = 3))
        val c = Similar.dHash(gray(9))
        assertTrue(Similar.distance(a, b) <= 6)
        assertTrue(Similar.distance(a, c) > 10)
    }

    @Test fun burstsAndCopiesAreGrouped() {
        val h1 = Similar.dHash(gray(1))
        val h2 = Similar.dHash(gray(5))
        val items = listOf(
            Candidate(1, 1_000, h1, 10.0, 3_000_000),
            Candidate(2, 2_000, h1 xor 0b101, 50.0, 3_100_000),
            Candidate(3, 3_000, h1 xor 0b1, 20.0, 2_900_000),
            Candidate(4, 500_000, h2, 5.0, 1_000_000),
            // An exact copy of #4 saved much later (a re-download).
            Candidate(5, 90_000_000, h2, 4.0, 1_000_000),
            // Similar to the burst but taken an hour later: a different moment.
            Candidate(6, 3_600_000, h1 xor 0b11, 30.0, 3_000_000),
        )
        val g = Similar.groups(items)
        val sets = g.map { it.ids.toSet() }
        assertTrue(setOf(1L, 2L, 3L) in sets)
        assertTrue(setOf(4L, 5L) in sets)
        assertTrue(sets.none { 6L in it })
        val burst = g.first { 1L in it.ids }
        assertEquals(2L, burst.ids.first())
        assertEquals(3_000_000L + 2_900_000L, burst.extraBytes)
        assertTrue(g.first { 4L in it.ids }.exact)
    }

    @Test fun sharpnessPrefersDetail() {
        val flat = IntArray(100) { 128 }
        val sharp = IntArray(100) { if ((it / 10 + it % 10) % 2 == 0) 0 else 255 }
        assertTrue(Similar.sharpness(sharp, 10, 10) > Similar.sharpness(flat, 10, 10))
        assertEquals(0.0, Similar.sharpness(flat, 10, 10), 0.0001)
    }
}

class FmtTest {
    @Test fun sizes() {
        assertEquals("512 B", Fmt.size(512))
        assertEquals("2 KB", Fmt.size(2048))
        assertEquals("1.5 MB", Fmt.size(1_572_864))
        assertEquals("15 MB", Fmt.size(15L * 1024 * 1024))
        assertEquals("1.50 GB", Fmt.size(1536L * 1024 * 1024))
    }

    @Test fun durations() {
        assertEquals("0:05", Fmt.duration(5_400))
        assertEquals("12:03", Fmt.duration(723_000))
        assertEquals("1:02:03", Fmt.duration(3_723_000))
        assertEquals("0:05.4", Fmt.position(5_400, 30_000))
    }

    @Test fun videoFacts() {
        assertEquals("4K", Fmt.videoClass(3840, 2160))
        assertEquals("4K", Fmt.videoClass(2160, 3840))
        assertEquals("1080p", Fmt.videoClass(1920, 1080))
        assertEquals("30 fps", Fmt.fps(30.0f))
        assertEquals("29.97 fps", Fmt.fps(29.97f))
        assertEquals("60 fps", Fmt.fps(59.98f))
        assertEquals("HEVC (H.265)", Fmt.codec("video/hevc"))
        assertEquals("AAC", Fmt.codec("audio/mp4a-latm"))
        assertEquals("45.2 Mbps", Fmt.bitrate(45_200_000))
        assertEquals("HEIC", Fmt.kind("image/heic", "IMG_0001.HEIC"))
        assertEquals("MOV", Fmt.kind("video/quicktime", "IMG_0002.MOV"))
        assertEquals("1/120 s", Fmt.shutter(1 / 120.0))
        assertEquals("12 MP", Fmt.megapixels(4000, 3000))
    }
}

class LinksTest {
    @Test fun cleansAddressBars() {
        assertEquals("example.com/a/b", Links.clean("https://www.example.com/a/b/"))
        assertEquals("en.wikipedia.org/wiki/Dhaka", Links.clean("en.wikipedia.org/wiki/Dhaka"))
        assertNull(Links.clean("Search or type web address"))
        assertNull(Links.clean(""))
    }

    @Test fun domains() {
        assertEquals("example.com", Links.domain("https://www.example.com/a?b=1"))
        assertEquals("wikipedia.org", Links.domain("m.wikipedia.org/wiki/x"))
        assertEquals("https://example.com/a", Links.openable("example.com/a"))
    }

    @Test fun findsAddressInScreenshotText() {
        val text = "10:42\n⌂ motionblurmedia.agency/work\nOur work\nContact us at hi@motionblurmedia.agency"
        assertEquals("motionblurmedia.agency/work", Links.findInText(text))
        assertNull(Links.findInText("Hello there\nNo links here at all"))
        assertNull(Links.findInText("file.jpg and photo.png"))
    }
}

class SourcesTest {
    private val own = "com.ankandebnath.allphotos"

    @Test fun picksTheAppInFrontAtTheMoment() {
        val log = listOf(
            Foreground(1_000, "com.whatsapp"),
            Foreground(5_000, "com.android.chrome", "example.com"),
            Foreground(9_000, "com.android.systemui"),
            Foreground(9_500, "com.facebook.orca"),
        )
        assertEquals("com.android.chrome", Sources.attribute(log, 8_000, own)?.pkg)
        assertEquals("example.com", Sources.attribute(log, 8_000, own)?.url)
        // The screenshot overlay (System UI) never counts as the source.
        assertEquals("com.android.chrome", Sources.attribute(log, 9_100, own, slackMs = 0)?.pkg)
        assertEquals("com.facebook.orca", Sources.attribute(log, 9_000, own)?.pkg)
        assertNull(Sources.attribute(log, 500, own, slackMs = 0))
    }

    @Test fun keyboardsAndOwnAppAreIgnored() {
        val log = listOf(Foreground(1_000, "com.instagram.android"), Foreground(2_000, "com.google.android.inputmethod.latin"), Foreground(3_000, own))
        assertEquals("com.instagram.android", Sources.attribute(log, 4_000, own)?.pkg)
    }

    @Test fun browserAddressBars() {
        assertTrue(Browsers.isBrowser("com.android.chrome"))
        assertEquals(listOf("com.android.chrome:id/url_bar"), Browsers.urlViewIds("com.android.chrome"))
        assertFalse(Browsers.isBrowser("com.whatsapp"))
    }
}

class TimelineTest {
    @Test fun daysGetHeadingsAndCellsKeepTheirPositions() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 10, 21)
        fun ms(d: LocalDate, h: Int) = d.atStartOfDay(zone).plusHours(h.toLong()).toInstant().toEpochMilli()
        val times = longArrayOf(ms(today, 10), ms(today, 9), ms(today.minusDays(1), 20), ms(LocalDate.of(2025, 3, 2), 8))
        val ids = longArrayOf(10, 11, 12, 13)
        val rows = Timeline.rows(times, ids, byMonth = false, zone = zone, today = today)
        val heads = rows.filterIsInstance<Row.Header>()
        assertEquals(listOf("Today", "Yesterday", "Sun, 2 Mar 2025"), heads.map { it.title })
        assertEquals(listOf(2, 1, 1), heads.map { it.count })
        assertEquals(listOf(0, 1, 2, 3), rows.filterIsInstance<Row.Cell>().map { it.index })
        val months = Timeline.rows(times, ids, byMonth = true, zone = zone, today = today).filterIsInstance<Row.Header>()
        assertEquals(listOf("October", "March 2025"), months.map { it.title })
        // Keys stay unique, so the grid can animate between zoom levels.
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }
}

class EditMathTest {
    @Test fun neutralAdjustLeavesColoursAlone() {
        val m = EditMath.matrix(Adjust(), 0)
        val c = EditMath.apply(m, 120f, 60f, 200f)
        assertEquals(120f, c[0], 0.01f)
        assertEquals(60f, c[1], 0.01f)
        assertEquals(200f, c[2], 0.01f)
    }

    @Test fun adjustmentsMoveInTheRightDirection() {
        val bright = EditMath.apply(EditMath.matrix(Adjust(brightness = 0.5f), 0), 100f, 100f, 100f)
        assertTrue(bright[0] > 100f)
        val mono = EditMath.apply(EditMath.matrix(Adjust(saturation = -1f), 0), 200f, 50f, 50f)
        assertEquals(mono[0], mono[1], 0.5f)
        assertEquals(mono[1], mono[2], 0.5f)
        val warm = EditMath.apply(EditMath.matrix(Adjust(warmth = 1f), 0), 100f, 100f, 100f)
        assertTrue(warm[0] > warm[2])
        val contrast = EditMath.matrix(Adjust(contrast = 0.5f), 0)
        assertTrue(EditMath.apply(contrast, 200f, 200f, 200f)[0] > 200f)
        assertTrue(EditMath.apply(contrast, 50f, 50f, 50f)[0] < 50f)
        assertEquals(127.5f, EditMath.apply(contrast, 127.5f, 127.5f, 127.5f)[0], 0.01f)
    }

    @Test fun concatMatchesApplyingInOrder() {
        val a = EditMath.contrast(0.3f)
        val b = EditMath.brightness(0.2f)
        val both = EditMath.apply(EditMath.concat(a, b), 90f, 140f, 30f)
        val step = EditMath.apply(b, 90f, 140f, 30f).let { EditMath.apply(a, it[0], it[1], it[2]) }
        for (i in 0..2) assertEquals(step[i], both[i], 0.01f)
    }

    @Test fun cropKeepsTheChosenShape() {
        val c = EditMath.fitAspect(CropRect(), 1f, 4f / 3f)
        // A square crop of a 4:3 photo: full height, three quarters of the width, centred.
        assertEquals(1f, c.h, 0.001f)
        assertEquals(0.75f, c.w, 0.001f)
        assertEquals(0.125f, c.l, 0.001f)
        val wide = EditMath.fitAspect(CropRect(), 16f / 9f, 3f / 4f)
        assertEquals(1f, wide.w, 0.001f)
        assertEquals((3f / 4f) / (16f / 9f), wide.h, 0.001f)
    }

    @Test fun everyFilterIsAValidMatrix() {
        for (f in EditMath.filters) assertEquals(20, f.matrix.size)
        assertEquals("Original", EditMath.filters[0].name)
    }
}

class ZoneSanity {
    @Test fun systemZoneWorks() {
        assertTrue(Timeline.dayOf(0, ZoneId.of("Asia/Dhaka")).year == 1970)
    }
}
