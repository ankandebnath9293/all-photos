package com.ankandebnath.allphotos.logic

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Turns raw numbers into short labels. */
object Fmt {
    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, if (mb < 10) "%.1f MB" else "%.0f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, if (gb < 10) "%.2f GB" else "%.1f GB", gb)
    }

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    /** Position in a video, with tenths of a second when it is short. */
    fun position(ms: Long, total: Long): String {
        val base = duration(ms)
        return if (total in 1..59_999) "$base.${(ms % 1000) / 100}" else base
    }

    fun megapixels(w: Int, h: Int): String {
        val mp = w.toLong() * h / 1_000_000.0
        return if (mp >= 10) String.format(Locale.US, "%.0f MP", mp) else String.format(Locale.US, "%.1f MP", mp)
    }

    /** "4K", "1080p" and so on, from the shorter side of a video. */
    fun videoClass(w: Int, h: Int): String? {
        val short = minOf(w, h)
        val long = maxOf(w, h)
        return when {
            long >= 7680 -> "8K"
            long >= 5000 -> "6K"
            long >= 3800 || short >= 2100 -> "4K"
            short >= 1400 -> "1440p"
            short >= 1000 -> "1080p"
            short >= 700 -> "720p"
            short >= 460 -> "480p"
            short > 0 -> "${short}p"
            else -> null
        }
    }

    fun fps(f: Float): String {
        if (f <= 0f) return "—"
        val r = f.roundToInt()
        return if (abs(f - r) < 0.025f) "$r fps" else String.format(Locale.US, "%.2f fps", f)
    }

    fun bitrate(bps: Long): String = when {
        bps <= 0 -> "—"
        bps >= 1_000_000 -> String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
        else -> String.format(Locale.US, "%.0f kbps", bps / 1000.0)
    }

    fun codec(mime: String?): String = when (mime?.lowercase()) {
        null, "" -> "Unknown"
        "video/hevc" -> "HEVC (H.265)"
        "video/avc" -> "AVC (H.264)"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/dolby-vision" -> "Dolby Vision"
        "video/mp4v-es" -> "MPEG-4"
        "video/3gpp" -> "H.263"
        "video/mpeg2" -> "MPEG-2"
        "video/apv" -> "APV"
        "audio/mp4a-latm" -> "AAC"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/mpeg" -> "MP3"
        "audio/flac" -> "FLAC"
        "audio/ac3" -> "Dolby Digital (AC-3)"
        "audio/eac3" -> "Dolby Digital Plus"
        "audio/eac3-joc" -> "Dolby Atmos"
        "audio/raw" -> "PCM"
        "audio/3gpp" -> "AMR"
        "audio/amr-wb" -> "AMR-WB"
        "audio/alac" -> "ALAC"
        else -> mime.substringAfter('/').uppercase()
    }

    /** Short file type from a mime type or name: JPG, HEIC, MOV… */
    fun kind(mime: String?, name: String?): String {
        val ext = name?.substringAfterLast('.', "")?.uppercase().orEmpty()
        if (ext.isNotEmpty() && ext.length <= 5) return if (ext == "JPEG") "JPG" else ext
        return when (mime?.lowercase()) {
            "image/jpeg" -> "JPG"
            "image/heic", "image/heif" -> "HEIC"
            "video/quicktime" -> "MOV"
            null -> "File"
            else -> mime.substringAfter('/').uppercase()
        }
    }

    fun channels(n: Int): String = when (n) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1 surround"
        8 -> "7.1 surround"
        else -> "$n channels"
    }

    fun shutter(seconds: Double): String = when {
        seconds <= 0 -> "—"
        seconds >= 1 -> String.format(Locale.US, "%.1f s", seconds)
        else -> "1/${(1 / seconds).roundToInt()} s"
    }

    fun coord(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lon)
}
