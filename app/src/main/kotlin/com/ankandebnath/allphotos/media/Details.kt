package com.ankandebnath.allphotos.media

import android.content.Context
import android.graphics.ImageDecoder
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.logic.Fmt
import com.ankandebnath.allphotos.logic.Links
import com.ankandebnath.allphotos.logic.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class Fact(val label: String, val value: String)
class FactGroup(val title: String, val facts: List<Fact>)

/** What the details sheet shows: a few key facts up front, the rest behind "Show more". */
class Details(val primary: List<Fact>, val more: List<FactGroup>, val fps: Float, val hdr: String?)

/** The technical facts of a video, read from the file itself. */
class VideoFacts(
    val codec: String?, val profile: Int, val width: Int, val height: Int, val fps: Float, val bitrate: Long,
    val transfer: Int, val standard: Int, val range: Int, val rotation: Int, val frames: Int,
    val audioCodec: String?, val sampleRate: Int, val channels: Int, val audioBitrate: Int,
    val dolbyVision: Boolean, val location: String?, val durationMs: Long, val captureFps: Float,
)

object DetailsReader {
    private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a", Locale.US)

    private fun date(ms: Long): String = dateFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    fun readVideo(ctx: Context, uri: Uri): VideoFacts? {
        var codec: String? = null
        var profile = -1
        var w = 0
        var h = 0
        var fps = 0f
        var transfer = 0
        var standard = 0
        var range = 0
        var audio: String? = null
        var rate = 0
        var ch = 0
        var abr = 0
        var dv = false
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) dv = true
                if (mime.startsWith("video/") && (codec == null || codec == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION)) {
                    if (codec == null || mime != MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) codec = mime
                    w = f.int(MediaFormat.KEY_WIDTH)
                    h = f.int(MediaFormat.KEY_HEIGHT)
                    profile = if (f.containsKey(MediaFormat.KEY_PROFILE)) f.int(MediaFormat.KEY_PROFILE) else -1
                    fps = f.num(MediaFormat.KEY_FRAME_RATE)
                    transfer = f.int(MediaFormat.KEY_COLOR_TRANSFER)
                    standard = f.int(MediaFormat.KEY_COLOR_STANDARD)
                    range = f.int(MediaFormat.KEY_COLOR_RANGE)
                } else if (mime.startsWith("audio/") && audio == null) {
                    audio = mime
                    rate = f.int(MediaFormat.KEY_SAMPLE_RATE)
                    ch = f.int(MediaFormat.KEY_CHANNEL_COUNT)
                    abr = f.int(MediaFormat.KEY_BIT_RATE)
                }
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { ex.release() }
        }
        val r = MediaMetadataRetriever()
        var bitrate = 0L
        var rotation = 0
        var frames = 0
        var loc: String? = null
        var dur = 0L
        var capture = 0f
        try {
            r.setDataSource(ctx, uri)
            bitrate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0
            rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            frames = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
            loc = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            capture = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
            if (w == 0) {
                w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { r.release() }
        }
        if (codec == null && w == 0) return null
        if (fps <= 0f && frames > 0 && dur > 0) fps = frames * 1000f / dur
        return VideoFacts(codec, profile, w, h, fps, bitrate, transfer, standard, range, rotation, frames, audio, rate, ch, abr, dv, loc, dur, capture)
    }

    private fun MediaFormat.int(key: String): Int = runCatching { if (containsKey(key)) getInteger(key) else 0 }.getOrDefault(0)

    private fun MediaFormat.num(key: String): Float = runCatching {
        if (!containsKey(key)) 0f else getNumber(key)?.toFloat() ?: 0f
    }.getOrDefault(0f)

    /** HDR10, HLG or Dolby Vision, from the colour information in the file. */
    fun hdrLabel(v: VideoFacts): String? = when {
        v.dolbyVision -> "Dolby Vision"
        v.transfer == MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR10"
        v.transfer == MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
        else -> null
    }

    private fun bitDepth(v: VideoFacts): String? {
        val tenBit = when (v.codec) {
            MediaFormat.MIMETYPE_VIDEO_HEVC -> v.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                v.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                v.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
            MediaFormat.MIMETYPE_VIDEO_AV1 -> v.profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10 ||
                v.profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10 ||
                v.profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus
            MediaFormat.MIMETYPE_VIDEO_VP9 -> v.profile == MediaCodecInfo.CodecProfileLevel.VP9Profile2 ||
                v.profile == MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR
            else -> false
        }
        return when {
            v.profile < 0 && v.codec != null -> if (hdrLabel(v) != null) "10-bit" else null
            tenBit || hdrLabel(v) != null -> "10-bit"
            else -> "8-bit"
        }
    }

    private fun standardName(s: Int): String? = when (s) {
        MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020"
        MediaFormat.COLOR_STANDARD_BT709 -> "BT.709"
        MediaFormat.COLOR_STANDARD_BT601_NTSC, MediaFormat.COLOR_STANDARD_BT601_PAL -> "BT.601"
        else -> null
    }

    private fun parseIso6709(s: String?): String? {
        if (s.isNullOrBlank()) return null
        val m = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(s) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        return Fmt.coord(lat, lon)
    }

    suspend fun read(ctx: Context, m: Media, source: Source?, appLabel: String?): Details = withContext(Dispatchers.IO) {
        val file = ArrayList<Fact>()
        file += Fact("Name", m.name)
        file += Fact("Where", m.path.ifEmpty { m.relPath + m.name })
        file += Fact("Type", m.mime.ifEmpty { Fmt.kind(m.mime, m.name) })
        file += Fact("Size", "${Fmt.size(m.size)} (${String.format(Locale.US, "%,d", m.size)} bytes)")
        file += Fact("Taken", date(m.taken))
        file += Fact("Added", date(m.added))
        if (m.modified > 0) file += Fact("Changed", date(m.modified))
        m.owner?.let { file += Fact("Saved by", it) }

        val groups = ArrayList<FactGroup>()
        val primary = ArrayList<Fact>()
        var fps = 0f
        var hdr: String? = null

        if (m.isVideo) {
            val v = readVideo(ctx, m.uri)
            val w = v?.width?.takeIf { it > 0 } ?: m.width
            val h = v?.height?.takeIf { it > 0 } ?: m.height
            val rot = v?.rotation ?: m.orientation
            val (sw, sh) = if (rot == 90 || rot == 270) h to w else w to h
            fps = v?.fps ?: 0f
            hdr = v?.let { hdrLabel(it) }
            primary += Fact("Resolution", "$sw × $sh" + (Fmt.videoClass(w, h)?.let { " · $it" } ?: ""))
            primary += Fact("Frame rate", Fmt.fps(fps))
            primary += Fact("Size", Fmt.size(m.size))
            primary += Fact("Length", Fmt.duration(if (m.durationMs > 0) m.durationMs else v?.durationMs ?: 0))
            hdr?.let { primary += Fact("HDR", it) }
            primary += Fact("Taken", date(m.taken))
            if (v != null) {
                val vf = ArrayList<Fact>()
                vf += Fact("Codec", Fmt.codec(v.codec))
                bitDepth(v)?.let { vf += Fact("Colour depth", it) }
                vf += Fact("Dynamic range", hdr ?: "SDR")
                standardName(v.standard)?.let { vf += Fact("Colour space", it) }
                if (v.range == MediaFormat.COLOR_RANGE_FULL) vf += Fact("Colour range", "Full")
                vf += Fact("Bitrate", Fmt.bitrate(v.bitrate))
                if (v.captureFps > 0 && v.captureFps > fps + 1) vf += Fact("Recorded at", Fmt.fps(v.captureFps) + " (slow motion)")
                if (v.frames > 0) vf += Fact("Frames", String.format(Locale.US, "%,d", v.frames))
                if (v.rotation != 0) vf += Fact("Rotation", "${v.rotation}°")
                vf += Fact("Stored size", "${v.width} × ${v.height}")
                groups += FactGroup("Video", vf)
                if (v.audioCodec != null) {
                    val af = ArrayList<Fact>()
                    af += Fact("Codec", Fmt.codec(v.audioCodec))
                    if (v.sampleRate > 0) af += Fact("Sample rate", String.format(Locale.US, "%.1f kHz", v.sampleRate / 1000.0))
                    if (v.channels > 0) af += Fact("Channels", Fmt.channels(v.channels))
                    if (v.audioBitrate > 0) af += Fact("Bitrate", Fmt.bitrate(v.audioBitrate.toLong()))
                    groups += FactGroup("Audio", af)
                } else {
                    groups += FactGroup("Audio", listOf(Fact("Sound", "No audio track")))
                }
                parseIso6709(v.location)?.let { groups += FactGroup("Location", listOf(Fact("Coordinates", it))) }
            }
        } else {
            val w = m.shownW
            val h = m.shownH
            hdr = runCatching {
                var g = false
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, m.uri)) { d, info, _ ->
                    val s = 96f / maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                    if (s < 1f) d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                }.also { g = Images.isHdr(it); it.recycle() }
                if (g) "Ultra HDR" else null
            }.getOrNull()
            primary += Fact("Resolution", if (w > 0) "$w × $h · ${Fmt.megapixels(w, h)}" else "—")
            primary += Fact("Size", Fmt.size(m.size))
            primary += Fact("Type", Fmt.kind(m.mime, m.name))
            hdr?.let { primary += Fact("HDR", it) }
            primary += Fact("Taken", date(m.taken))

            val exif = runCatching { ctx.contentResolver.openInputStream(m.uri)?.use { ExifInterface(it) } }.getOrNull()
            if (exif != null) {
                val cam = ArrayList<Fact>()
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                val device = listOfNotNull(make, model).joinToString(" ").let { s ->
                    // "Google Pixel 7" not "Google Google Pixel 7".
                    if (make != null && model != null && model.startsWith(make, true)) model else s
                }
                if (device.isNotBlank()) cam += Fact("Camera", device)
                exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.let { cam += Fact("Lens", it) }
                exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let {
                    cam += Fact("Aperture", String.format(Locale.US, "ƒ/%.1f", it))
                }
                exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let {
                    cam += Fact("Shutter", Fmt.shutter(it))
                }
                exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }?.let {
                    cam += Fact("ISO", it.toString())
                }
                exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { f ->
                    val eq = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
                    cam += Fact("Focal length", String.format(Locale.US, "%.1f mm", f) + if (eq > 0) " ($eq mm equiv.)" else "")
                }
                exif.getAttributeInt(ExifInterface.TAG_FLASH, -1).takeIf { it >= 0 }?.let {
                    cam += Fact("Flash", if (it and 1 == 1) "Fired" else "Off")
                }
                exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.let { cam += Fact("Software", it) }
                if (cam.isNotEmpty()) groups += FactGroup("Camera", cam)
                exif.latLong?.let { ll -> groups += FactGroup("Location", listOf(Fact("Coordinates", Fmt.coord(ll[0], ll[1])))) }
            }
            val img = ArrayList<Fact>()
            if (w > 0) img += Fact("Pixels", String.format(Locale.US, "%,d", w.toLong() * h))
            img += Fact("Dynamic range", hdr ?: "Standard")
            if (m.orientation != 0) img += Fact("Rotation", "${m.orientation}°")
            groups.add(0, FactGroup("Image", img))
        }

        if (m.isScreenshot || source != null) {
            val s = ArrayList<Fact>()
            if (appLabel != null) s += Fact("App", appLabel)
            source?.url?.let { s += Fact("Website", Links.clean(it) ?: it) }
            if (source != null && source.how != Source.NONE && source.how != Source.UNKNOWN) {
                s += Fact(
                    "Found by", when (source.how) {
                        Source.LIVE -> "Seen when the screenshot was taken"
                        Source.USAGE -> "App usage history"
                        Source.TEXT -> "Text in the screenshot"
                        else -> "—"
                    },
                )
            }
            if (s.isNotEmpty()) groups.add(0, FactGroup("Screenshot", s))
        }
        groups += FactGroup("File", file)
        Details(primary, groups, fps, hdr)
    }
}
