package com.ankandebnath.allphotos.media

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.ankandebnath.allphotos.logic.Look
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Applies a [Look]: on the GPU for the preview, on the CPU (all cores) when saving. */
object LookRender {
    /** A live-preview effect for a layer of [w] × [h] pixels. */
    fun effect(look: Look, w: Float, h: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (look.isNeutral || w <= 0f || h <= 0f) return null
        val s = RuntimeShader(Look.SHADER)
        s.setFloatUniform("size", w, h)
        val u = look.uniforms()
        Look.NAMES.forEachIndexed { i, n -> s.setFloatUniform(n, u[i]) }
        return RenderEffect.createRuntimeShaderEffect(s, "image").asComposeRenderEffect()
    }

    /** Returns a new bitmap with the look baked in. */
    suspend fun bake(src: Bitmap, look: Look): Bitmap = withContext(Dispatchers.Default) {
        val w = src.width
        val h = src.height
        // Keep the photo's colour space (Pixel photos are Display P3) so colours do not shift.
        val cs = src.colorSpace ?: android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, true, cs)
        val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val band = (h + cores - 1) / cores
        coroutineScope {
            (0 until cores).map { part ->
                async {
                    val y0 = part * band
                    val y1 = minOf(h, y0 + band)
                    val row = IntArray(w)
                    val px = FloatArray(3)
                    for (y in y0 until y1) {
                        src.getPixels(row, 0, w, 0, y, w, 1)
                        val v = (y + 0.5f) / h
                        for (x in 0 until w) {
                            val c = row[x]
                            val a = c ushr 24
                            Look.apply(look, ((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f, (x + 0.5f) / w, v, px)
                            row[x] = (a shl 24) or ((px[0] * 255f + 0.5f).toInt() shl 16) or ((px[1] * 255f + 0.5f).toInt() shl 8) or (px[2] * 255f + 0.5f).toInt()
                        }
                        synchronized(out) { out.setPixels(row, 0, w, 0, y, w, 1) }
                    }
                }
            }.awaitAll()
        }
        out
    }
}
