package com.ankandebnath.allphotos.logic

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * How a photo is toned: every slider and filter is one of these. The same maths runs on the GPU
 * for the live preview (see [Look.SHADER]) and here on the CPU when the photo is saved, so what you
 * see is exactly what you get. Values run from -1 to 1 (0 means unchanged), except [mono] (0 or 1).
 */
data class Look(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val mono: Float = 0f,
    /** Tone of black and white: negative is cool silver, positive warm. */
    val tone: Float = 0f,
) {
    val isNeutral: Boolean get() = this == Look()

    /** A filter with the user's own slider changes added on top. */
    operator fun plus(o: Look) = Look(
        (brightness + o.brightness).c(), (contrast + o.contrast).c(), (highlights + o.highlights).c(), (shadows + o.shadows).c(),
        (saturation + o.saturation).c(), (vibrance + o.vibrance).c(), (warmth + o.warmth).c(), (tint + o.tint).c(),
        (fade + o.fade).c(0f, 1f), (vignette + o.vignette).c(0f, 1f), max(mono, o.mono), tone + o.tone,
    )

    private fun Float.c(lo: Float = -1f, hi: Float = 1f) = coerceIn(lo, hi)

    /** The uniforms the preview shader expects, in order. */
    fun uniforms(): FloatArray = floatArrayOf(brightness, contrast, highlights, shadows, saturation, vibrance, warmth, tint, fade, vignette, mono, tone)

    companion object {
        val NAMES = listOf("brightness", "contrast", "highlights", "shadows", "saturation", "vibrance", "warmth", "tint", "fade", "vignette", "mono", "tone")

        private fun smooth(e0: Float, e1: Float, x: Float): Float {
            val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        private fun curve(x: Float, k: Float): Float = x + k * (x - 0.5f) * (1f - abs(2f * x - 1f))

        /**
         * Tones one pixel. [r], [g], [b] are 0..1; [u], [v] are its position (0..1) in the picture.
         * Writes the result into [out].
         */
        fun apply(l: Look, r0: Float, g0: Float, b0: Float, u: Float, v: Float, out: FloatArray) {
            var r = r0
            var g = g0
            var b = b0
            val bt = l.brightness
            if (bt > 0f) {
                val e = 1f + bt * 1.2f
                r = 1f - (1f - r).pow(e); g = 1f - (1f - g).pow(e); b = 1f - (1f - b).pow(e)
            } else if (bt < 0f) {
                val e = 1f - bt * 1.2f
                r = r.pow(e); g = g.pow(e); b = b.pow(e)
            }
            var lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val hi = l.highlights
            if (hi != 0f) {
                val w = hi * 0.35f * smooth(0.45f, 1f, lum)
                if (hi < 0f) { r += w * r; g += w * g; b += w * b } else { r += w * (1 - r); g += w * (1 - g); b += w * (1 - b) }
            }
            val sh = l.shadows
            if (sh != 0f) {
                // Shadows lift the dark tones but leave true black alone.
                val w = sh * 0.4f * (1f - smooth(0f, 0.55f, lum)) * smooth(0f, 0.08f, lum)
                if (sh > 0f) { r += w * (1 - r); g += w * (1 - g); b += w * (1 - b) } else { r += w * r; g += w * g; b += w * b }
            }
            r = r.coerceIn(0f, 1f); g = g.coerceIn(0f, 1f); b = b.coerceIn(0f, 1f)
            val k = l.contrast
            if (k != 0f) { r = curve(r, k); g = curve(g, k); b = curve(b, k) }
            if (l.warmth != 0f) { r *= 1f + 0.10f * l.warmth; b *= 1f - 0.10f * l.warmth }
            if (l.tint != 0f) g *= 1f - 0.06f * l.tint
            lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            if (l.saturation != 0f || l.vibrance != 0f) {
                val sat = max(r, max(g, b)) - min(r, min(g, b))
                var vb = l.vibrance * (1f - sat.coerceIn(0f, 1f)) * 1.4f
                // Skin tones (red over green over blue) get half the vibrance, so faces stay natural.
                if (r > g && g > b) vb *= 0.5f
                val f = (1f + l.saturation) * (1f + vb)
                r = lum + (r - lum) * f; g = lum + (g - lum) * f; b = lum + (b - lum) * f
            }
            if (l.mono > 0f) {
                val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val tr = y + l.tone * 0.03f
                val tb = y - l.tone * 0.03f
                r += (tr - r) * l.mono; g += (y - g) * l.mono; b += (tb - b) * l.mono
            }
            if (l.fade > 0f) {
                r += (0.08f + r * 0.86f - r) * l.fade; g += (0.08f + g * 0.86f - g) * l.fade; b += (0.08f + b * 0.86f - b) * l.fade
            }
            if (l.vignette > 0f) {
                val du = u - 0.5f
                val dv = v - 0.5f
                val d = sqrt(du * du + dv * dv) * 1.4142f
                val m = 1f - l.vignette * 0.55f * smooth(0.35f, 1f, d)
                r *= m; g *= m; b *= m
            }
            out[0] = r.coerceIn(0f, 1f)
            out[1] = g.coerceIn(0f, 1f)
            out[2] = b.coerceIn(0f, 1f)
        }

        private val vivid = Look(brightness = 0.02f, contrast = 0.22f, highlights = -0.12f, shadows = 0.08f, saturation = 0.14f, vibrance = 0.32f)
        private val dramatic = Look(brightness = -0.1f, contrast = 0.38f, highlights = -0.3f, shadows = -0.05f, saturation = -0.12f, vignette = 0.25f)

        /** The filter set, modelled on the iPhone's (Vivid, Dramatic and the black-and-white trio) plus Film. */
        val FILTERS: List<Pair<String, Look>> = listOf(
            "Original" to Look(),
            "Vivid" to vivid,
            "Vivid Warm" to vivid.copy(warmth = 0.32f),
            "Vivid Cool" to vivid.copy(warmth = -0.32f, tint = 0.04f),
            "Dramatic" to dramatic,
            "Dramatic Warm" to dramatic.copy(warmth = 0.3f, saturation = -0.06f),
            "Dramatic Cool" to dramatic.copy(warmth = -0.32f),
            "Mono" to Look(contrast = 0.08f, mono = 1f),
            "Silvertone" to Look(contrast = 0.25f, highlights = -0.15f, shadows = 0.12f, fade = 0.1f, mono = 1f, tone = -1f),
            "Noir" to Look(brightness = -0.06f, contrast = 0.65f, shadows = -0.2f, vignette = 0.2f, mono = 1f),
            "Film" to Look(contrast = 0.1f, saturation = -0.12f, warmth = 0.18f, fade = 0.35f),
        )

        /** The GPU version of [apply], for the live preview. Kept line for line with the Kotlin above. */
        const val SHADER = """
uniform shader image;
uniform float2 size;
uniform float brightness;
uniform float contrast;
uniform float highlights;
uniform float shadows;
uniform float saturation;
uniform float vibrance;
uniform float warmth;
uniform float tint;
uniform float fade;
uniform float vignette;
uniform float mono;
uniform float tone;

float smooth01(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float3 curve(float3 x, float k) {
    return x + k * (x - 0.5) * (1.0 - abs(2.0 * x - 1.0));
}

half4 main(float2 p) {
    half4 src = image.eval(p);
    float a = float(src.a);
    if (a <= 0.0) return src;
    float3 c = float3(src.rgb) / a;
    if (brightness > 0.0) {
        float e = 1.0 + brightness * 1.2;
        c = 1.0 - pow(1.0 - c, float3(e));
    } else if (brightness < 0.0) {
        float e = 1.0 - brightness * 1.2;
        c = pow(max(c, float3(0.0)), float3(e));
    }
    float lum = dot(c, float3(0.2126, 0.7152, 0.0722));
    if (highlights != 0.0) {
        float w = highlights * 0.35 * smooth01(0.45, 1.0, lum);
        if (highlights < 0.0) { c += w * c; } else { c += w * (1.0 - c); }
    }
    if (shadows != 0.0) {
        float w = shadows * 0.4 * (1.0 - smooth01(0.0, 0.55, lum)) * smooth01(0.0, 0.08, lum);
        if (shadows > 0.0) { c += w * (1.0 - c); } else { c += w * c; }
    }
    c = clamp(c, 0.0, 1.0);
    if (contrast != 0.0) { c = curve(c, contrast); }
    c.r *= 1.0 + 0.10 * warmth;
    c.b *= 1.0 - 0.10 * warmth;
    c.g *= 1.0 - 0.06 * tint;
    lum = dot(c, float3(0.2126, 0.7152, 0.0722));
    if (saturation != 0.0 || vibrance != 0.0) {
        float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
        float vb = vibrance * (1.0 - clamp(sat, 0.0, 1.0)) * 1.4;
        if (c.r > c.g && c.g > c.b) { vb *= 0.5; }
        float f = (1.0 + saturation) * (1.0 + vb);
        c = lum + (c - lum) * f;
    }
    if (mono > 0.0) {
        float y = dot(c, float3(0.2126, 0.7152, 0.0722));
        float3 t = float3(y + tone * 0.03, y, y - tone * 0.03);
        c = c + (t - c) * mono;
    }
    if (fade > 0.0) { c = c + (0.08 + c * 0.86 - c) * fade; }
    if (vignette > 0.0) {
        float2 uv = p / size - 0.5;
        float d = length(uv) * 1.4142;
        c *= 1.0 - vignette * 0.55 * smooth01(0.35, 1.0, d);
    }
    c = clamp(c, 0.0, 1.0);
    return half4(half3(c * a), half(a));
}
"""
    }
}
