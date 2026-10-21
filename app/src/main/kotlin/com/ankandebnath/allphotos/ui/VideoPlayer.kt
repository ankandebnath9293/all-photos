package com.ankandebnath.allphotos.ui

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.logic.Fmt
import com.ankandebnath.allphotos.media.Share
import com.ankandebnath.allphotos.media.Video
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One video player for the whole viewer, moved to whichever page is showing. Decoding uses the
 * phone's hardware, so 4K and HDR stay smooth.
 */
@Stable
class PlayerHolder(ctx: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(ctx, DefaultRenderersFactory(ctx).setEnableDecoderFallback(true))
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()

    var uri by mutableStateOf<Uri?>(null)
        private set
    var playing by mutableStateOf(false)
    /** True while the video is meant to play (even if it is briefly buffering). */
    var wantsPlay by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var firstFrame by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var aspect by mutableFloatStateOf(0f)
    var error by mutableStateOf<String?>(null)

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                wantsPlay = playWhenReady
            }

            override fun onPlaybackStateChanged(state: Int) {
                ready = state == Player.STATE_READY || state == Player.STATE_ENDED
                ended = state == Player.STATE_ENDED
                if (player.duration > 0) duration = player.duration
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrame = true
            }

            override fun onPlayerError(e: PlaybackException) {
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                    -> "This video uses a format the phone can't decode (for example Apple ProRes)."
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The file is missing."
                    else -> "This video can't be played (${e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')})."
                }
            }
        })
    }

    /** Loads [m] if it is not already loaded, and starts it when [autoplay]. */
    fun load(m: Media?, target: Uri?, autoplay: Boolean = true) {
        val u = target ?: m?.uri ?: return
        if (u == uri) return
        uri = u
        error = null
        ended = false
        ready = false
        firstFrame = false
        position = 0
        duration = m?.durationMs ?: 0
        aspect = if (m != null && m.shownH > 0) m.shownW.toFloat() / m.shownH else 0f
        player.setSeekParameters(SeekParameters.DEFAULT)
        player.setMediaItem(MediaItem.fromUri(u))
        player.repeatMode = if ((m?.durationMs ?: 60_000) in 1..15_000) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.prepare()
        player.playWhenReady = autoplay
    }

    fun stop() {
        player.pause()
        player.clearMediaItems()
        uri = null
        firstFrame = false
    }

    fun toggle() {
        if (ended) {
            player.seekTo(0)
            player.play()
        } else if (player.playWhenReady) player.pause() else player.play()
    }

    private var scrubResume = false
    private var lastScrub = 0L

    /** Dragging the seek bar: the video holds still and shows the nearest frame quickly. */
    fun beginScrub() {
        scrubResume = player.playWhenReady
        player.pause()
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
    }

    fun scrubTo(ms: Long) {
        position = ms
        val now = android.os.SystemClock.uptimeMillis()
        // Asking for a new frame on every finger movement makes the decoder fall behind; a few a second is smoother.
        if (now - lastScrub >= 45) {
            lastScrub = now
            player.seekTo(ms.coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE))
        }
    }

    fun endScrub(ms: Long) {
        seek(ms, exact = true)
        if (scrubResume) player.play()
    }

    fun seek(ms: Long, exact: Boolean) {
        player.setSeekParameters(if (exact) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
        player.seekTo(ms.coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE))
        position = ms
    }

    /** One frame forwards or backwards, paused, landing exactly on the frame. */
    fun step(frames: Int, fps: Float) {
        player.pause()
        val f = if (fps > 1f) 1000f / fps else 33.4f
        seek(player.currentPosition + (frames * f).toLong(), exact = true)
    }

    fun release() = player.release()
}

/**
 * The one picture surface for the whole viewer. It stays alive while the viewer is open and is
 * shown over whichever page is a video, so switching videos never loses the picture.
 */
@Composable
fun SharedVideoSurface(holder: PlayerHolder, visible: Boolean, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { c -> SurfaceView(c).also { holder.player.setVideoSurfaceView(it) } },
        update = { v -> v.visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE },
        onRelease = { v -> holder.player.clearVideoSurfaceView(v) },
        modifier = modifier,
    )
}

@Composable
fun VideoError(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.VideocamOff, null, tint = Color.White)
        Gap(8.dp)
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The big play button in the middle. */
@Composable
fun CenterPlay(holder: PlayerHolder, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible && holder.error == null, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f), modifier = modifier) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)).clickable { holder.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when {
                    holder.ended -> Icons.Rounded.Replay
                    holder.wantsPlay -> Icons.Rounded.Pause
                    else -> Icons.Rounded.PlayArrow
                },
                if (holder.wantsPlay) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

/** Seek bar and a few tools: 10 seconds back or forward, frame by frame, save this frame, rotate. */
@Composable
fun VideoControls(holder: PlayerHolder, m: Media?, fps: Float, onRotate: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var lastFrame by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    val view = LocalView.current

    LaunchedEffect(holder.uri) {
        while (isActive) {
            if (!dragging) {
                holder.position = holder.player.currentPosition
                if (holder.player.duration > 0) holder.duration = holder.player.duration
            }
            delay(if (holder.playing) 50 else 250)
        }
    }
    DisposableEffect(holder.playing) {
        view.keepScreenOn = holder.playing
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(lastFrame) {
        if (lastFrame != null) {
            delay(5000)
            lastFrame = null
        }
    }

    val dur = holder.duration.coerceAtLeast(1)
    val frac = if (dragging) dragValue else (holder.position.toFloat() / dur).coerceIn(0f, 1f)
    val shown = (frac * dur).toLong()

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        AnimatedVisibility(
            lastFrame != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Frame saved", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    TextButton(onClick = { lastFrame?.let { Share.sendUris(ctx, listOf(it), "image/jpeg") } }) {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                        GapW(6.dp)
                        Text("Share")
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.position(shown, dur), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(52.dp))
            Slider(
                value = frac,
                onValueChange = { v ->
                    if (!dragging) {
                        dragging = true
                        holder.beginScrub()
                    }
                    dragValue = v
                    holder.scrubTo((v * dur).toLong())
                },
                onValueChangeFinished = {
                    holder.endScrub((dragValue * dur).toLong())
                    dragging = false
                },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                modifier = Modifier.weight(1f),
            )
            Text(Fmt.duration(dur), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(Icons.Rounded.Replay10, "Back 10 seconds") { holder.seek(holder.player.currentPosition - 10_000, false) }
            ToolIcon(Icons.Rounded.SkipPrevious, "Previous frame") { holder.step(-1, fps) }
            ToolIcon(Icons.Rounded.CameraAlt, "Save this frame", highlight = true) {
                val item = m ?: return@ToolIcon
                if (saving) return@ToolIcon
                saving = true
                scope.launch {
                    val u = Video.saveFrame(ctx, item, holder.player.currentPosition)
                    saving = false
                    if (u != null) lastFrame = u else Toasts.show("Couldn't save the frame")
                }
            }
            ToolIcon(Icons.Rounded.SkipNext, "Next frame") { holder.step(1, fps) }
            ToolIcon(Icons.Rounded.Forward10, "Forward 10 seconds") { holder.seek(holder.player.currentPosition + 10_000, false) }
            ToolIcon(Icons.Rounded.ScreenRotation, "Rotate") { onRotate() }
        }
    }
}

@Composable
private fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, highlight: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Box(
            if (highlight) Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary) else Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, desc, tint = if (highlight) MaterialTheme.colorScheme.onPrimary else Color.White, modifier = Modifier.size(if (highlight) 24.dp else 24.dp))
        }
    }
}
