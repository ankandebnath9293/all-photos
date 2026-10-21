package com.ankandebnath.allphotos.ui

import android.graphics.Bitmap
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import com.ankandebnath.allphotos.media.VideoLooks
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.logic.Fmt
import com.ankandebnath.allphotos.media.Saver
import com.ankandebnath.allphotos.media.Video
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Cut the start and end off a video. Saved as a copy, or over the original MP4. */
@Composable
fun TrimScreen(m: Media, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val holder = remember { PlayerHolder(ctx.applicationContext) }
    var range by remember { mutableStateOf(0f..1f) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var menu by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var look by remember { mutableIntStateOf(0) }
    val dur = (if (m.durationMs > 0) m.durationMs else holder.duration).coerceAtLeast(1)
    val startMs = (range.start * dur).toLong()
    val endMs = (range.endInclusive * dur).toLong()
    val canReplace = m.mime == "video/mp4"

    DisposableEffect(Unit) {
        holder.load(m, null, autoplay = true)
        holder.player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
        onDispose { holder.release() }
    }
    // Loop the chosen part while you adjust it.
    LaunchedEffect(Unit) {
        while (isActive) {
            val p = holder.player.currentPosition
            holder.position = p
            if (holder.player.isPlaying && (p >= endMs || p < startMs - 200)) holder.seek(startMs, exact = false)
            delay(60)
        }
    }
    val strip by produceState(emptyList<Bitmap>(), m.id) { value = Video.strip(ctx, m.uri, dur, 10, 120) }

    fun save(replace: Boolean) {
        holder.player.pause()
        progress = 0f
        job = scope.launch {
            when (val r = Video.trim(ctx.applicationContext, m.uri, startMs, endMs, VideoLooks.effects(look)) { progress = it }) {
                is Video.TrimResult.Done -> {
                    val ok = if (replace && canReplace) {
                        Saver.overwrite(ctx.applicationContext, m.uri, r.file)
                    } else {
                        Saver.insert(
                            ctx.applicationContext, r.file, Saver.nameLike(m.name, if (look == 0) "_trim" else "_edit", "mp4"), "video/mp4",
                            Saver.folderOf(m, Video.CLIPS_FOLDER), m.taken,
                        ) != null
                    }
                    r.file.delete()
                    progress = -1f
                    Toasts.show(if (!ok) "Couldn't save" else if (replace) "Saved" else "Saved as a copy")
                    if (ok) onClose()
                }
                is Video.TrimResult.Failed -> {
                    progress = -1f
                    Toasts.show("Couldn't trim this video")
                }
            }
        }
    }

    BackHandler { job?.cancel(); onClose() }

    AppDarkSurface {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { job?.cancel(); onClose() }) { Icon(Icons.Rounded.Close, "Cancel", tint = Color.White) }
                Text("Edit video", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box {
                    Button(onClick = { menu = true }, enabled = progress < 0f && (look != 0 || range.start > 0.001f || range.endInclusive < 0.999f)) { Text("Save") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Save as copy") }, onClick = { menu = false; save(false) })
                        DropdownMenuItem(
                            text = { Column { Text("Replace original"); if (!canReplace) Text("Only for MP4 videos", style = MaterialTheme.typography.bodySmall) } },
                            enabled = canReplace,
                            onClick = { menu = false; save(true) },
                        )
                    }
                }
                GapW(8.dp)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val ar = holder.aspect
                Box(if (ar > 0f) Modifier.fillMaxWidth().aspectRatio(ar, matchHeightConstraintsFirst = ar < 1f) else Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { c -> SurfaceView(c).also { holder.player.setVideoSurfaceView(it) } },
                        onRelease = { v -> holder.player.clearVideoSurfaceView(v) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (progress >= 0f) {
                    Text("Saving… ${(progress * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Gap(8.dp)
                    LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Gap(12.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(onClick = { holder.toggle() }) {
                        Icon(if (holder.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play")
                    }
                    GapW(12.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${Fmt.position(startMs, dur)} – ${Fmt.position(endMs, dur)}",
                            color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        )
                        Text("Length ${Fmt.position(endMs - startMs, dur)}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Gap(12.dp)
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    Row(Modifier.fillMaxSize()) {
                        strip.forEach { b ->
                            Image(b.asImageBitmap(), null, Modifier.weight(1f).fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
                RangeSlider(
                    value = range,
                    onValueChange = { r ->
                        val moved = if (r.start != range.start) r.start else r.endInclusive
                        if (r.endInclusive - r.start >= 0.01f) range = r
                        holder.player.pause()
                        holder.seek((moved * dur).toLong(), exact = false)
                    },
                    onValueChangeFinished = { holder.seek(startMs, exact = true) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    itemsIndexed(VideoLooks.NAMES) { i, n ->
                        FilterChip(selected = look == i, onClick = {
                            look = i
                            // Preview the filter on the playing video.
                            runCatching {
                                val pos = holder.player.currentPosition
                                holder.player.stop()
                                holder.player.setVideoEffects(VideoLooks.effects(i))
                                holder.player.prepare()
                                holder.player.seekTo(pos)
                                holder.player.play()
                            }.onFailure { android.util.Log.w("AllPhotos", "Video filter preview failed", it) }
                        }, label = { Text(n) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { range = 0f..1f }) { Text("Reset") }
                    Text(Fmt.duration(dur), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}
