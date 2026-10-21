package com.ankandebnath.allphotos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.max

/**
 * Where each grid cell is on screen, so a photo can fly from its cell into the viewer and back.
 * Only cells that are currently laid out are kept.
 */
object HeroBounds {
    private val rects = HashMap<Long, Rect>()
    /** Hidden stack members fly back to the photo on top of their stack. */
    var alias: Map<Long, Long> = emptyMap()

    /** The cell whose photo is flying, hidden so it is not shown twice. */
    var hidden by mutableStateOf<Long?>(null)

    fun put(id: Long, r: Rect) {
        rects[id] = r
    }

    fun remove(id: Long) {
        rects.remove(id)
    }

    fun get(id: Long): Rect? = rects[id] ?: alias[id]?.let { rects[it] }
}

/** Reports a grid cell's position to [HeroBounds]. */
@Composable
fun Modifier.heroSource(id: Long): Modifier {
    DisposableEffect(id) { onDispose { HeroBounds.remove(id) } }
    return this.onGloballyPositioned { c ->
        val r = c.boundsInWindow()
        if (r.width > 0f && r.height > 0f) HeroBounds.put(id, r)
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * One moment of the photo's journey: where its centre is, how big it is, the visible window
 * around it (clip), the corner rounding and how dark the backdrop is.
 */
data class Frame(val center: Offset, val scale: Float, val clip: Rect, val corner: Float, val backdrop: Float, val alpha: Float = 1f) {
    companion object {
        fun lerpFrame(a: Frame, b: Frame, t: Float) = Frame(
            Offset(lerp(a.center.x, b.center.x, t), lerp(a.center.y, b.center.y, t)),
            lerp(a.scale, b.scale, t),
            Rect(lerp(a.clip.left, b.clip.left, t), lerp(a.clip.top, b.clip.top, t), lerp(a.clip.right, b.clip.right, t), lerp(a.clip.bottom, b.clip.bottom, t)),
            lerp(a.corner, b.corner, t),
            lerp(a.backdrop, b.backdrop, t),
            lerp(a.alpha, b.alpha, t),
        )

        /** The photo fitted to the screen of size [screen], with aspect [aspect] (width / height). */
        fun fitted(screen: Size, aspect: Float): Rect {
            val a = if (aspect > 0f) aspect else 1f
            val w: Float
            val h: Float
            if (screen.width / screen.height > a) {
                h = screen.height
                w = h * a
            } else {
                w = screen.width
                h = w / a
            }
            return Rect(Offset((screen.width - w) / 2f, (screen.height - h) / 2f), Size(w, h))
        }

        /** Full screen, moved by a drag of [drag] and shrunk by [shrink]. */
        fun full(screen: Size, aspect: Float, drag: Offset = Offset.Zero, shrink: Float = 1f, backdrop: Float = 1f): Frame {
            val fit = fitted(screen, aspect)
            val c = Offset(screen.width / 2f + drag.x, screen.height / 2f + drag.y)
            val w = fit.width * shrink
            val h = fit.height * shrink
            // While the photo is being dragged it is clipped to its own bounds; at rest, nothing is clipped.
            val clip = if (drag == Offset.Zero && shrink == 1f) Rect(Offset.Zero, screen) else Rect(c.x - w / 2f, c.y - h / 2f, c.x + w / 2f, c.y + h / 2f)
            return Frame(c, shrink, clip, if (shrink < 1f) 24f * (1f - shrink) * 4f else 0f, backdrop)
        }

        /** The photo covering a grid cell, cropped to it, like the thumbnail it came from. */
        fun cell(screen: Size, aspect: Float, cell: Rect, corner: Float): Frame {
            val fit = fitted(screen, aspect)
            val s = max(cell.width / fit.width, cell.height / fit.height)
            return Frame(cell.center, s, cell, corner, 0f)
        }
    }
}
