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
        assertNull(Rules.chatOf("Pictures/Instagram/"))
    }

    @Test fun dueAfterThirtyDaysUnlessImportant() {
        val now = 1_800_000_000_000L
        val day = Rules.DAY_MS
        data class F(val id: Long, val t: Long)
        val files = listOf(F(1, now - 31 * day), F(2, now - 29 * day), F(3, now - 30 * day), F(4, now - 90 * day), F(5, 0))
        val due = Rules.due(files, now, 30, setOf(4L), 0L, { it.id }, { it.t }).map { it.id }
        assertEquals(listOf(1L, 3L), due)
    }

    @Test fun nothingFromBeforeInstallIsCounted() {
        val day = Rules.DAY_MS
        val installed = 1_800_000_000_000L
        val now = installed + 40 * day
        data class F(val id: Long, val t: Long)
        // 1 and 2 were taken before the app was installed; 3 after, and is old enough.
        val files = listOf(F(1, installed - 400 * day), F(2, installed - 1), F(3, installed + 2 * day), F(4, installed + 20 * day))
        assertEquals(listOf(3L), Rules.due(files, now, 30, emptySet(), installed, { it.id }, { it.t }).map { it.id })
    }

    @Test fun startOfDay() {
        val zone = java.time.ZoneId.of("Asia/Dhaka")
        val installedAt = java.time.ZonedDateTime.of(2026, 10, 22, 0, 30, 0, 0, zone).toInstant().toEpochMilli()
        val start = Rules.startOfDay(installedAt, zone)
        assertEquals(java.time.ZonedDateTime.of(2026, 10, 22, 0, 0, 0, 0, zone).toInstant().toEpochMilli(), start)
    }

    @Test fun facebookAndMessengerFolders() {
        assertEquals(Chat.FACEBOOK, Rules.chatOf("Pictures/Messenger/"))
        assertEquals(Chat.FACEBOOK, Rules.chatOf("Pictures/Facebook/"))
        assertEquals(Chat.FACEBOOK, Rules.chatOf("DCIM/Facebook/"))
        assertEquals(Chat.FACEBOOK, Rules.chatOf("Movies/Messenger/"))
        assertEquals(Chat.FACEBOOK, Rules.chatOf("Download/", owner = "com.facebook.orca"))
        assertNull(Rules.chatOf("Pictures/Screenshots/", owner = "com.facebook.orca"))
        assertNull(Rules.chatOf("DCIM/Camera/", owner = "com.google.android.GoogleCamera"))
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

class DocsTest {
    @Test fun receipts() {
        val t = "STAR KABAB\nItem Qty Price\nChicken Biryani 1 350.00\nBorhani 2 120.00\nSub Total 470.00\nVAT 5% 23.50\nGrand Total 493.50\nCash 500.00\nChange 6.50\nThank you for coming"
        assertEquals(com.ankandebnath.allphotos.logic.DocKind.RECEIPT, com.ankandebnath.allphotos.logic.Docs.classify(t, emptyList(), false))
        val bkash = "Send Money Successful\nAmount ৳1,000.00\nCharge ৳5.00\nTrxID BK7XY12AB\nReference rent\nBalance ৳2,340.00"
        assertEquals(com.ankandebnath.allphotos.logic.DocKind.RECEIPT, com.ankandebnath.allphotos.logic.Docs.classify(bkash, emptyList(), true))
    }

    @Test fun paymentCards() {
        val t = "BRAC BANK\nVISA Debit Platinum\n4567 1234 5678 9012\nVALID THRU 08/29\nCARDHOLDER NAME"
        assertEquals(com.ankandebnath.allphotos.logic.DocKind.PAYMENT, com.ankandebnath.allphotos.logic.Docs.classify(t, emptyList(), false))
    }

    @Test fun identityAndTickets() {
        val id = "People's Republic of Bangladesh\nGovernment of the People's Republic\nNational ID Card\nName\nDate of Birth 10 Nov 2002\nID NO 1234567890"
        assertEquals(com.ankandebnath.allphotos.logic.DocKind.IDENTITY, com.ankandebnath.allphotos.logic.Docs.classify(id, emptyList(), false))
        val ticket = "ENTRY PASS\nDhaka Music Festival\nVenue: ICCB Hall 4\nGate B  Row F  Seat 12\nAdmit one"
        assertEquals(com.ankandebnath.allphotos.logic.DocKind.EVENT, com.ankandebnath.allphotos.logic.Docs.classify(ticket, emptyList(), false))
    }

    @Test fun menusNotesAndBooks() {
        val menu = "KFC Tuesday Special\n20 pcs of chicken\n8pc hot & crispy\nCombo meal with fries\nOffer price"
        assertEquals(com.ankandebnath.allphotos.logic.DocKind.MENU, com.ankandebnath.allphotos.logic.Docs.classify(menu, emptyList(), false))
        val notes = "Marketing 201\nChapter 4 segmentation\nTargeting and positioning\nQuiz on Sunday"
        assertEquals(com.ankandebnath.allphotos.logic.DocKind.NOTES, com.ankandebnath.allphotos.logic.Docs.classify(notes, emptyList(), false))
        assertEquals(com.ankandebnath.allphotos.logic.DocKind.BOOKS, com.ankandebnath.allphotos.logic.Docs.classify("Chapter one", listOf("Book"), false))
    }

    @Test fun ordinaryPhotosAreNotDocuments() {
        assertNull(com.ankandebnath.allphotos.logic.Docs.classify("", listOf("Sky", "Tree"), false))
        assertNull(com.ankandebnath.allphotos.logic.Docs.classify("EXIT", listOf("Building"), false))
        // A chat screenshot is not a document.
        assertNull(com.ankandebnath.allphotos.logic.Docs.classify("Tahsan\nkal dekha hobe\nok bhai\n10:42 PM", emptyList(), true))
    }
}

class SmartTest {
    @Test fun phonesLinksAndEmails() {
        val found = com.ankandebnath.allphotos.logic.Smart.find("Call +880 1712-345678 or mail hi@motionblurmedia.agency, see motionblurmedia.agency/work")
        val kinds = found.map { it.kind }
        assertTrue(com.ankandebnath.allphotos.logic.SmartAction.Kind.PHONE in kinds)
        assertTrue(com.ankandebnath.allphotos.logic.SmartAction.Kind.EMAIL in kinds)
        assertTrue(com.ankandebnath.allphotos.logic.SmartAction.Kind.LINK in kinds)
        assertEquals("+8801712345678", com.ankandebnath.allphotos.logic.Smart.dialable(found.first { it.kind == com.ankandebnath.allphotos.logic.SmartAction.Kind.PHONE }.value))
        assertEquals("motionblurmedia.agency/work", found.first { it.kind == com.ankandebnath.allphotos.logic.SmartAction.Kind.LINK }.value)
    }

    @Test fun datesAndPricesAreNotPhones() {
        val found = com.ankandebnath.allphotos.logic.Smart.find("Paid on 21/10/2026 total 1,250.00")
        assertTrue(found.none { it.kind == com.ankandebnath.allphotos.logic.SmartAction.Kind.PHONE })
    }
}

class LookTest {
    private fun run(l: com.ankandebnath.allphotos.logic.Look, r: Float, g: Float, b: Float, u: Float = 0.5f, v: Float = 0.5f): FloatArray {
        val out = FloatArray(3)
        com.ankandebnath.allphotos.logic.Look.apply(l, r, g, b, u, v, out)
        return out
    }

    @Test fun neutralLeavesPixelsAlone() {
        val o = run(com.ankandebnath.allphotos.logic.Look(), 0.3f, 0.6f, 0.9f)
        assertEquals(0.3f, o[0], 1e-5f); assertEquals(0.6f, o[1], 1e-5f); assertEquals(0.9f, o[2], 1e-5f)
    }

    @Test fun vividIsMoreColourfulAndKeepsGreys() {
        val vivid = com.ankandebnath.allphotos.logic.Look.FILTERS.first { it.first == "Vivid" }.second
        val o = run(vivid, 0.6f, 0.4f, 0.3f)
        assertTrue(o[0] - o[2] > 0.6f - 0.3f)
        val grey = run(vivid, 0.5f, 0.5f, 0.5f)
        assertEquals(grey[0], grey[1], 1e-4f); assertEquals(grey[1], grey[2], 1e-4f)
        // Black and white stay black and white.
        val black = run(vivid, 0f, 0f, 0f)
        assertTrue(black.all { it < 0.02f })
    }

    @Test fun monoFiltersRemoveColour() {
        for (name in listOf("Mono", "Noir")) {
            val l = com.ankandebnath.allphotos.logic.Look.FILTERS.first { it.first == name }.second
            val o = run(l, 0.8f, 0.2f, 0.1f)
            assertEquals(o[0], o[1], 1e-4f); assertEquals(o[1], o[2], 1e-4f)
        }
        val silver = com.ankandebnath.allphotos.logic.Look.FILTERS.first { it.first == "Silvertone" }.second
        val s = run(silver, 0.5f, 0.5f, 0.5f)
        assertTrue(s[2] > s[0])
    }

    @Test fun slidersMoveTheRightWay() {
        assertTrue(run(com.ankandebnath.allphotos.logic.Look(brightness = 0.5f), 0.4f, 0.4f, 0.4f)[0] > 0.4f)
        assertTrue(run(com.ankandebnath.allphotos.logic.Look(brightness = -0.5f), 0.4f, 0.4f, 0.4f)[0] < 0.4f)
        assertTrue(run(com.ankandebnath.allphotos.logic.Look(contrast = 0.5f), 0.8f, 0.8f, 0.8f)[0] > 0.8f)
        assertTrue(run(com.ankandebnath.allphotos.logic.Look(contrast = 0.5f), 0.2f, 0.2f, 0.2f)[0] < 0.2f)
        assertTrue(run(com.ankandebnath.allphotos.logic.Look(highlights = -0.8f), 0.9f, 0.9f, 0.9f)[0] < 0.9f)
        assertTrue(run(com.ankandebnath.allphotos.logic.Look(shadows = 0.8f), 0.1f, 0.1f, 0.1f)[0] > 0.1f)
        val warm = run(com.ankandebnath.allphotos.logic.Look(warmth = 1f), 0.5f, 0.5f, 0.5f)
        assertTrue(warm[0] > warm[2])
        val centre = run(com.ankandebnath.allphotos.logic.Look(vignette = 1f), 0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
        val corner = run(com.ankandebnath.allphotos.logic.Look(vignette = 1f), 0.5f, 0.5f, 0.5f, 0f, 0f)
        assertEquals(0.5f, centre[0], 1e-4f)
        assertTrue(corner[0] < 0.3f)
    }

    @Test fun everyFilterStaysInRange() {
        for ((_, l) in com.ankandebnath.allphotos.logic.Look.FILTERS) {
            for (c in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val o = run(l, c, 1f - c, c * 0.5f, 0.1f, 0.9f)
                assertTrue(o.all { it in 0f..1f })
            }
        }
    }

    @Test fun shaderHasEveryUniform() {
        for (n in com.ankandebnath.allphotos.logic.Look.NAMES) assertTrue(n, com.ankandebnath.allphotos.logic.Look.SHADER.contains("uniform float $n;"))
        assertEquals(com.ankandebnath.allphotos.logic.Look.NAMES.size, com.ankandebnath.allphotos.logic.Look().uniforms().size)
    }
}
