package com.ankandebnath.allphotos.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.logic.CropRect
import com.ankandebnath.allphotos.logic.EditMath
import com.ankandebnath.allphotos.logic.Look
import com.ankandebnath.allphotos.media.LookRender
import com.ankandebnath.allphotos.media.Edit
import com.ankandebnath.allphotos.media.Export
import com.ankandebnath.allphotos.media.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private class Ratio(val label: String, val value: Float)

private val ratios = listOf(
    Ratio("Free", 0f), Ratio("Original", -1f), Ratio("1:1", 1f), Ratio("4:5", 4f / 5f),
    Ratio("3:4", 3f / 4f), Ratio("4:3", 4f / 3f), Ratio("9:16", 9f / 16f), Ratio("16:9", 16f / 9f),
)

private enum class Slide(val label: String, val min: Float = -1f) {
    BRIGHTNESS("Brightness"), CONTRAST("Contrast"), HIGHLIGHTS("Highlights"), SHADOWS("Shadows"),
    SATURATION("Saturation"), VIBRANCE("Vibrance"), WARMTH("Warmth"), TINT("Tint"), FADE("Fade", 0f), VIGNETTE("Vignette", 0f),
}

private fun Look.get(s: Slide) = when (s) {
    Slide.BRIGHTNESS -> brightness
    Slide.CONTRAST -> contrast
    Slide.HIGHLIGHTS -> highlights
    Slide.SHADOWS -> shadows
    Slide.SATURATION -> saturation
    Slide.VIBRANCE -> vibrance
    Slide.WARMTH -> warmth
    Slide.TINT -> tint
    Slide.FADE -> fade
    Slide.VIGNETTE -> vignette
}

private fun Look.with(s: Slide, v: Float) = when (s) {
    Slide.BRIGHTNESS -> copy(brightness = v)
    Slide.CONTRAST -> copy(contrast = v)
    Slide.HIGHLIGHTS -> copy(highlights = v)
    Slide.SHADOWS -> copy(shadows = v)
    Slide.SATURATION -> copy(saturation = v)
    Slide.VIBRANCE -> copy(vibrance = v)
    Slide.WARMTH -> copy(warmth = v)
    Slide.TINT -> copy(tint = v)
    Slide.FADE -> copy(fade = v)
    Slide.VIGNETTE -> copy(vignette = v)
}

/** A light photo editor: crop and rotate, a few adjustments, a few filters. */
@Composable
fun Editor(m: Media, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf<Bitmap?>(null) }
    var turns by remember { mutableIntStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf(CropRect()) }
    var ratio by remember { mutableIntStateOf(0) }
    var adjust by remember { mutableStateOf(Look()) }
    var filter by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var slide by remember { mutableStateOf(Slide.BRIGHTNESS) }
    var saving by remember { mutableStateOf(false) }
    var saveMenu by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(m.id) {
        val b = Images.decodeForEdit(ctx, m.uri, maxPixels = 3_000_000L)
        if (b == null) failed = true else base = b
    }
    // The preview after rotation and mirroring; crop and colour are drawn on top of it.
    val shown: ImageBitmap? = remember(base, turns, flip) {
        base?.let { b ->
            if (turns % 4 == 0 && !flip) b.asImageBitmap()
            else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Export.orient(turns, flip), true).asImageBitmap()
        }
    }
    val small = remember(base) {
        base?.let { b ->
            val s = 160f / maxOf(b.width, b.height)
            Bitmap.createScaledBitmap(b, (b.width * s).roundToInt().coerceAtLeast(1), (b.height * s).roundToInt().coerceAtLeast(1), true).asImageBitmap()
        }
    }
    val look = remember(adjust, filter) { Look.FILTERS[filter].second + adjust }
    val edit = Edit(turns, flip, crop, look)
    val thumbPx = with(LocalDensity.current) { 72.dp.toPx() }

    fun applyRatio(i: Int, c: CropRect = crop): CropRect {
        val img = shown ?: return c
        val ia = img.width.toFloat() / img.height
        val r = ratios[i].value
        val target = if (r < 0f) (base?.let { if (turns % 2 == 0) it.width.toFloat() / it.height else it.height.toFloat() / it.width } ?: 0f) else r
        return if (target <= 0f) c else EditMath.fitAspect(CropRect(), target, ia)
    }

    fun save(replace: Boolean) {
        saving = true
        scope.launch {
            val ok = Export.save(ctx.applicationContext, m, edit, replace) != null
            saving = false
            Toasts.show(if (!ok) "Couldn't save" else if (replace) "Saved" else "Saved as a copy")
            if (ok) onClose()
        }
    }

    BackHandler { onClose() }

    AppDarkSurface {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cancel", tint = Color.White) }
                Text("Edit", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!edit.isEmpty) {
                    TextButton(onClick = {
                        turns = 0; flip = false; crop = CropRect(); ratio = 0; adjust = Look(); filter = 0
                    }) { Text("Reset") }
                }
                Box {
                    Button(onClick = { saveMenu = true }, enabled = !saving && !edit.isEmpty && base != null) { Text(if (saving) "Saving…" else "Save") }
                    DropdownMenu(expanded = saveMenu, onDismissRequest = { saveMenu = false }) {
                        DropdownMenuItem(
                            text = { Column { Text("Save as copy"); Text("Keeps the original too", style = MaterialTheme.typography.bodySmall) } },
                            onClick = { saveMenu = false; save(replace = false) },
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Replace original")
                                    Text(
                                        if (Export.canReplace(m)) "Only the edited photo is kept" else "Not for ${m.mime.substringAfter('/').uppercase()} — saved as a copy",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                            enabled = Export.canReplace(m),
                            onClick = { saveMenu = false; save(replace = true) },
                        )
                    }
                }
                GapW(8.dp)
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                val img = shown
                when {
                    failed -> Text("This photo can't be edited", color = Color.White)
                    img == null -> LoadingIndicator()
                    else -> CropArea(img, look, crop, cropping = tab == 0, ratio = ratios[ratio].value.let { r ->
                        if (r < 0f) img.width.toFloat() / img.height else r
                    }) { crop = it }
                }
            }

            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
                when (tab) {
                    0 -> {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(ratios) { i, r ->
                                FilterChip(selected = ratio == i, onClick = { ratio = i; crop = applyRatio(i) }, label = { Text(r.label) })
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                            IconButton(onClick = { turns = (turns + 3) % 4; crop = CropRect(); ratio = 0 }) { Icon(Icons.Rounded.RotateLeft, "Rotate left", tint = Color.White) }
                            IconButton(onClick = { turns = (turns + 1) % 4; crop = CropRect(); ratio = 0 }) { Icon(Icons.Rounded.RotateRight, "Rotate right", tint = Color.White) }
                            IconButton(onClick = { flip = !flip; crop = CropRect(1f - crop.r, crop.t, 1f - crop.l, crop.b) }) { Icon(Icons.Rounded.Flip, "Mirror", tint = Color.White) }
                        }
                    }
                    1 -> {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(Slide.entries) { _, s ->
                                val v = adjust.get(s)
                                FilterChip(
                                    selected = slide == s, onClick = { slide = s },
                                    label = { Text(if (v == 0f) s.label else "${s.label} ${(v * 100).roundToInt()}") },
                                )
                            }
                        }
                        Slider(
                            value = adjust.get(slide),
                            onValueChange = { v -> adjust = adjust.with(slide, if (abs(v) < 0.03f) 0f else v) },
                            valueRange = slide.min..1f,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                    else -> {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(Look.FILTERS) { i, f ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { filter = i }.padding(4.dp)) {
                                    val sm = small
                                    Box(
                                        Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))
                                            .then(if (filter == i) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier),
                                    ) {
                                        if (sm != null) {
                                            val fx = remember(f) { LookRender.effect(f.second, thumbPx, thumbPx) }
                                            Image(sm, null, Modifier.fillMaxSize().graphicsLayer { renderEffect = fx }, contentScale = ContentScale.Crop)
                                        }
                                    }
                                    Gap(4.dp)
                                    Text(f.first, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                Gap(8.dp)
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Segmented(listOf("Crop", "Adjust", "Filters"), tab) { tab = it }
                }
            }
        }
    }
}

/** Dark colours for the editor and trimmer, whatever the app theme. */
@Composable
fun AppDarkSurface(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { com.ankandebnath.allphotos.data.Prefs.get(ctx) }.now
    AppTheme(s.themeMode, s.dynamicColor, s.palette, forceDark = true, motion = s.motion, content = content)
}

/** The photo with its crop frame. Drag the corners or edges to crop, or the middle to move it. */
@Composable
private fun CropArea(img: ImageBitmap, look: Look, crop: CropRect, cropping: Boolean, ratio: Float, onCrop: (CropRect) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bw = constraints.maxWidth.toFloat()
        val bh = constraints.maxHeight.toFloat()
        val a = img.width.toFloat() / img.height
        val fw = if (bw / bh > a) bh * a else bw
        val fh = if (bw / bh > a) bh else bw / a
        val left = (bw - fw) / 2f
        val top = (bh - fh) / 2f
        val cur = androidx.compose.runtime.rememberUpdatedState(crop)

        if (!cropping) {
            // Outside the crop tool, show just the cropped part, toned, as it will be saved.
            val part = remember(img, crop) {
                if (crop.isFull) img else {
                    val src = img.asAndroidBitmap()
                    val x = (crop.l * src.width).roundToInt().coerceIn(0, src.width - 1)
                    val y = (crop.t * src.height).roundToInt().coerceIn(0, src.height - 1)
                    val w = (crop.w * src.width).roundToInt().coerceIn(1, src.width - x)
                    val h = (crop.h * src.height).roundToInt().coerceIn(1, src.height - y)
                    Bitmap.createBitmap(src, x, y, w, h).asImageBitmap()
                }
            }
            val ca = part.width.toFloat() / part.height
            val dw = if (bw / bh > ca) bh * ca else bw
            val dh = if (bw / bh > ca) bh else bw / ca
            val fx = remember(look, dw, dh) { LookRender.effect(look, dw, dh) }
            Image(
                part, null,
                Modifier.size(with(density) { dw.toDp() }, with(density) { dh.toDp() }).graphicsLayer { renderEffect = fx },
                contentScale = ContentScale.FillBounds,
            )
            return@BoxWithConstraints
        }
        Box(Modifier.fillMaxSize()) {
            val fxFull = remember(look, fw, fh) { LookRender.effect(look, fw, fh) }
            Image(
                img, null,
                Modifier.size(with(density) { fw.toDp() }, with(density) { fh.toDp() }).offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .graphicsLayer { renderEffect = fxFull },
                contentScale = ContentScale.FillBounds,
            )
            run {
                val handle = with(density) { 28.dp.toPx() }
                Canvas(
                    Modifier.fillMaxSize().pointerInput(fw, fh, ratio) {
                        var mode = 0 // 1 move, 2 left, 4 top, 8 right, 16 bottom
                        detectDragGestures(
                            onDragStart = { p ->
                                val c = cur.value
                                val x0 = left + c.l * fw
                                val x1 = left + c.r * fw
                                val y0 = top + c.t * fh
                                val y1 = top + c.b * fh
                                mode = 0
                                if (abs(p.x - x0) < handle) mode = mode or 2
                                if (abs(p.x - x1) < handle) mode = mode or 8
                                if (abs(p.y - y0) < handle) mode = mode or 4
                                if (abs(p.y - y1) < handle) mode = mode or 16
                                if (mode == 0 && p.x in x0..x1 && p.y in y0..y1) mode = 1
                            },
                            onDrag = { change, d ->
                                change.consume()
                                val c = cur.value
                                val dx = d.x / fw
                                val dy = d.y / fh
                                val min = 0.08f
                                var n = c
                                if (mode == 1) {
                                    val l = (c.l + dx).coerceIn(0f, 1f - c.w)
                                    val t = (c.t + dy).coerceIn(0f, 1f - c.h)
                                    n = CropRect(l, t, l + c.w, t + c.h)
                                } else if (mode != 0) {
                                    var l = c.l
                                    var t = c.t
                                    var r = c.r
                                    var b = c.b
                                    if (mode and 2 != 0) l = (l + dx).coerceIn(0f, r - min)
                                    if (mode and 8 != 0) r = (r + dx).coerceIn(l + min, 1f)
                                    if (mode and 4 != 0) t = (t + dy).coerceIn(0f, b - min)
                                    if (mode and 16 != 0) b = (b + dy).coerceIn(t + min, 1f)
                                    n = CropRect(l, t, r, b)
                                    if (ratio > 0f) {
                                        // Keep the chosen shape: height follows width.
                                        val wPx = n.w * fw
                                        val hNorm = (wPx / ratio) / fh
                                        n = if (mode and 4 != 0) CropRect(n.l, (n.b - hNorm).coerceAtLeast(0f), n.r, n.b)
                                        else CropRect(n.l, n.t, n.r, (n.t + hNorm).coerceAtMost(1f))
                                        if (n.h < hNorm - 0.001f) n = EditMath.fitAspect(n, ratio, fw / fh)
                                    }
                                }
                                onCrop(n)
                            },
                        )
                    },
                ) {
                    val c = crop
                    val x0 = left + c.l * fw
                    val y0 = top + c.t * fh
                    val w = c.w * fw
                    val h = c.h * fh
                    clipRect(x0, y0, x0 + w, y0 + h, clipOp = ClipOp.Difference) {
                        drawRect(Color.Black.copy(alpha = 0.6f), Offset(left, top), Size(fw, fh))
                    }
                    drawRect(Color.White, Offset(x0, y0), Size(w, h), style = Stroke(2.dp.toPx()))
                    for (i in 1..2) {
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(x0 + w * i / 3f, y0), Offset(x0 + w * i / 3f, y0 + h), 1.dp.toPx())
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(x0, y0 + h * i / 3f), Offset(x0 + w, y0 + h * i / 3f), 1.dp.toPx())
                    }
                    val k = 18.dp.toPx()
                    val s = 4.dp.toPx()
                    for ((cx, cy) in listOf(x0 to y0, x0 + w to y0, x0 to y0 + h, x0 + w to y0 + h)) {
                        drawRoundRect(Color.White, Offset(cx - s / 2 - (if (cx > x0) k - s else 0f), cy - s / 2), Size(k, s), CornerRadius(s))
                        drawRoundRect(Color.White, Offset(cx - s / 2, cy - s / 2 - (if (cy > y0) k - s else 0f)), Size(s, k), CornerRadius(s))
                    }
                }
            }
        }
    }
}
