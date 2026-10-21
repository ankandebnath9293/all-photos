package com.ankandebnath.allphotos.logic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A row of the photo grid: either a date heading or one photo or video. */
sealed interface Row {
    val key: Any

    /** [from] and [count] say which items (by position in the item list) sit under this heading. */
    data class Header(val day: Long, val title: String, val from: Int, val count: Int, val big: Boolean) : Row {
        override val key: Any get() = if (big) "m$day" else "d$day"
    }

    data class Cell(val index: Int, val id: Long) : Row {
        override val key: Any get() = id
    }
}

object Timeline {
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)
    private val dayYearFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM", Locale.US)
    private val monthYearFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

    fun dayOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun dayTitle(d: LocalDate, today: LocalDate): String = when {
        d == today -> "Today"
        d == today.minusDays(1) -> "Yesterday"
        d.year == today.year -> dayFmt.format(d)
        else -> dayYearFmt.format(d)
    }

    fun monthTitle(d: LocalDate, today: LocalDate): String =
        if (d.year == today.year) monthFmt.format(d) else monthYearFmt.format(d)

    /**
     * Builds grid rows from items sorted newest first. With [byMonth] the headings are months (for the
     * dense zoom levels); otherwise days.
     */
    fun rows(
        times: LongArray,
        ids: LongArray,
        byMonth: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): List<Row> {
        val out = ArrayList<Row>(times.size + times.size / 8 + 4)
        var i = 0
        while (i < times.size) {
            val d = dayOf(times[i], zone)
            val keyDate = if (byMonth) d.withDayOfMonth(1) else d
            var j = i + 1
            while (j < times.size) {
                val dj = dayOf(times[j], zone)
                val kj = if (byMonth) dj.withDayOfMonth(1) else dj
                if (kj != keyDate) break
                j++
            }
            val title = if (byMonth) monthTitle(keyDate, today) else dayTitle(keyDate, today)
            out += Row.Header(keyDate.toEpochDay(), title, i, j - i, byMonth)
            for (k in i until j) out += Row.Cell(k, ids[k])
            i = j
        }
        return out
    }

    /** Month and year for the fast-scroll bubble. */
    fun bubble(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = monthYearFmt.format(dayOf(ms, zone))
}
