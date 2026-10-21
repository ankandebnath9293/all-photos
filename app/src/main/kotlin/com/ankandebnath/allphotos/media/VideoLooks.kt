package com.ankandebnath.allphotos.media

import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter

/**
 * Filters for videos, built from the phone's own GPU video effects so playback and saving stay fast.
 * They follow the photo filters of the same name.
 */
object VideoLooks {
    val NAMES = listOf("Original", "Vivid", "Vivid Warm", "Vivid Cool", "Dramatic", "Mono", "Noir")

    private fun warm(r: Float, b: Float): Effect = RgbAdjustment.Builder().setRedScale(r).setGreenScale(1f).setBlueScale(b).build()

    fun effects(i: Int): List<Effect> = when (NAMES.getOrNull(i)) {
        "Vivid" -> listOf(Contrast(0.12f), HslAdjustment.Builder().adjustSaturation(22f).build())
        "Vivid Warm" -> listOf(Contrast(0.12f), HslAdjustment.Builder().adjustSaturation(20f).build(), warm(1.07f, 0.93f))
        "Vivid Cool" -> listOf(Contrast(0.12f), HslAdjustment.Builder().adjustSaturation(20f).build(), warm(0.94f, 1.07f))
        "Dramatic" -> listOf(Brightness(-0.06f), Contrast(0.3f), HslAdjustment.Builder().adjustSaturation(-12f).build())
        "Mono" -> listOf(RgbFilter.createGrayscaleFilter(), Contrast(0.06f))
        "Noir" -> listOf(RgbFilter.createGrayscaleFilter(), Brightness(-0.05f), Contrast(0.5f))
        else -> emptyList()
    }
}
