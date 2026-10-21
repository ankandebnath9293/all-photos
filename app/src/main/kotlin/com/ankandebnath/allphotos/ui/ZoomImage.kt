package com.ankandebnath.allphotos.ui

import android.net.Uri
import android.widget.ImageView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ankandebnath.allphotos.media.Images
import com.ankandebnath.allphotos.media.Picture
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A photo you can pinch, pan and double-tap to zoom. A screen-sized copy loads first; a sharper
 * one loads once you zoom in. Swipe down (when not zoomed) to close.
 */
@Composable
fun ZoomImage(
    uri: Uri,
    animate: Boolean,
    placeholder: @Composable () -> Unit,
    onTap: () -> Unit,
    onZoomed: (Boolean) -> Unit,
    onHdr: (Boolean) -> Unit,
    onDrag: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var picture by remember(uri) { mutableStateOf<Picture?>(null) }
    var sharp by remember(uri) { mutableStateOf(false) }
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var ox by remember(uri) { mutableFloatStateOf(0f) }
    var oy by remember(uri) { mutableFloatStateOf(0f) }
    var dragY by remember(uri) { mutableFloatStateOf(0f) }
    val tap by rememberUpdatedState(onTap)
    val zoomed by rememberUpdatedState(onZoomed)
    val drag by rememberUpdatedState(onDrag)
    val dismiss by rememberUpdatedState(onDismiss)

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val screenMax = max(constraints.maxWidth, constraints.maxHeight)

        LaunchedEffect(uri) {
            val p = Images.decode(ctx, uri, maxSide = (screenMax * 1.25f).toInt().coerceAtLeast(1600), animate = animate)
            picture = p
            onHdr(p is Picture.Still && p.hdr)
        }
        // Once zoomed in, swap in a sharper copy.
        val still = picture as? Picture.Still
        LaunchedEffect(scale > 1.6f, still) {
            val s = still ?: return@LaunchedEffect
            if (scale <= 1.6f || sharp) return@LaunchedEffect
            val full = max(s.fullW, s.fullH)
            if (full <= max(s.bitmap.width, s.bitmap.height) * 1.1f) {
                sharp = true
                return@LaunchedEffect
            }
            val better = Images.decode(ctx, uri, maxSide = min(full, 8192), animate = false)
            if (better is Picture.Still) {
                sharp = true
                picture = better
            }
        }

        // The photo's on-screen size at 1× (fitted inside the page).
        val aspect = when (val p = picture) {
            is Picture.Still -> p.bitmap.width.toFloat() / p.bitmap.height
            is Picture.Animated -> if (p.h > 0) p.w.toFloat() / p.h else 1f
            else -> 1f
        }
        val fitW = if (w / h > aspect) h * aspect else w
        val fitH = if (w / h > aspect) h else w / aspect

        fun clampX(x: Float, s: Float): Float {
            val m = ((fitW * s - w) / 2f).coerceAtLeast(0f)
            return x.coerceIn(-m, m)
        }

        fun clampY(y: Float, s: Float): Float {
            val m = ((fitH * s - h) / 2f).coerceAtLeast(0f)
            return y.coerceIn(-m, m)
        }

        fun animateTo(ts: Float, tx: Float, ty: Float) {
            val s0 = scale
            val x0 = ox
            val y0 = oy
            scope.launch {
                animate(0f, 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { t, _ ->
                    scale = s0 + (ts - s0) * t
                    ox = x0 + (tx - x0) * t
                    oy = y0 + (ty - y0) * t
                }
            }
            zoomed(ts > 1.01f)
        }

        val gestures = Modifier
            .pointerInput(uri, fitW, fitH) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var total = Offset.Zero
                    var dismissing = false
                    var pinched = false
                    do {
                        val ev = awaitPointerEvent()
                        val fingers = ev.changes.count { it.pressed }
                        if (fingers >= 2) {
                            pinched = true
                            val z = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            val c = ev.calculateCentroid(useCurrent = true)
                            val ns = (scale * z).coerceIn(0.85f, 10f)
                            val cx = c.x - w / 2f
                            val cy = c.y - h / 2f
                            ox = cx - (cx - ox) * ns / scale + pan.x
                            oy = cy - (cy - oy) * ns / scale + pan.y
                            scale = ns
                            ev.changes.forEach { it.consume() }
                            zoomed(true)
                        } else if (scale > 1.01f) {
                            val pan = ev.calculatePan()
                            ox = clampX(ox + pan.x, scale)
                            oy = clampY(oy + pan.y, scale)
                            ev.changes.forEach { it.consume() }
                        } else if (!pinched) {
                            val ch = ev.changes.firstOrNull() ?: break
                            val d = ch.positionChange()
                            total += d
                            if (!dismissing && abs(total.y) > slop * 1.5f && abs(total.y) > abs(total.x) * 1.6f && !ch.isConsumed) {
                                dismissing = true
                            }
                            if (dismissing) {
                                dragY += d.y
                                drag((abs(dragY) / (h * 0.4f)).coerceIn(0f, 1f))
                                ch.consume()
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (dismissing) {
                        if (abs(dragY) > with(density) { 140.dp.toPx() }) {
                            dismiss()
                        } else {
                            val start = dragY
                            scope.launch {
                                animate(start, 0f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) { v, _ ->
                                    dragY = v
                                    drag((abs(v) / (h * 0.4f)).coerceIn(0f, 1f))
                                }
                            }
                        }
                    } else if (scale < 1f) {
                        animateTo(1f, 0f, 0f)
                    } else if (pinched) {
                        animateTo(scale, clampX(ox, scale), clampY(oy, scale))
                    }
                }
            }
            .pointerInput(uri, fitW, fitH) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { p ->
                        if (scale > 1.05f) {
                            animateTo(1f, 0f, 0f)
                        } else {
                            val ts = if (fitW < w * 0.98f && fitH >= h * 0.98f) max(2.5f, w / fitW) else 2.5f
                            val cx = p.x - w / 2f
                            val cy = p.y - h / 2f
                            animateTo(ts, clampX(cx * (1 - ts), ts), clampY(cy * (1 - ts), ts))
                        }
                    },
                )
            }

        val layer = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = ox
            translationY = oy + dragY
            val shrink = 1f - (abs(dragY) / (h * 4f)).coerceIn(0f, 0.15f)
            if (dragY != 0f) {
                scaleX = scale * shrink
                scaleY = scale * shrink
            }
        }

        Box(Modifier.fillMaxSize().then(gestures), contentAlignment = Alignment.Center) {
            when (val p = picture) {
                null -> Box(layer) { placeholder() }
                is Picture.Still -> {
                    val img = remember(p) { p.bitmap.asImageBitmap() }
                    Image(img, null, Modifier.fillMaxSize().then(layer), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
                }
                is Picture.Animated -> AndroidView(
                    factory = { c -> ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                    update = { v ->
                        v.setImageDrawable(p.drawable)
                        p.drawable.start()
                    },
                    modifier = Modifier.fillMaxSize().then(layer),
                )
                Picture.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.BrokenImage, null, tint = Color.White.copy(alpha = 0.7f))
                    Text("This file can't be shown", color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}
