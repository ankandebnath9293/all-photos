package com.ankandebnath.allphotos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private fun hsl(h: Float, s: Float, l: Float): Color =
    Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

/** The fixed colour themes, for when the wallpaper colours are switched off. */
class Palette(val name: String, val hue: Float, val accent: Float)

object Palettes {
    val all = listOf(
        Palette("Violet", 268f, 320f),
        Palette("Ocean", 212f, 175f),
        Palette("Forest", 148f, 85f),
        Palette("Sunset", 18f, 340f),
        Palette("Rose", 340f, 285f),
    )

    fun get(i: Int) = all[i.coerceIn(0, all.lastIndex)]
}

val LocalDark = staticCompositionLocalOf { false }

private fun schemeFor(p: Palette, dark: Boolean): ColorScheme {
    val h = p.hue
    val a = p.accent
    return if (dark) {
        darkColorScheme(
            primary = hsl(h, 0.85f, 0.80f), onPrimary = hsl(h, 0.65f, 0.17f),
            primaryContainer = hsl(h, 0.45f, 0.30f), onPrimaryContainer = hsl(h, 0.90f, 0.91f),
            inversePrimary = hsl(h, 0.55f, 0.42f),
            secondary = hsl(h, 0.30f, 0.78f), onSecondary = hsl(h, 0.30f, 0.18f),
            secondaryContainer = hsl(h, 0.24f, 0.26f), onSecondaryContainer = hsl(h, 0.50f, 0.90f),
            tertiary = hsl(a, 0.70f, 0.80f), onTertiary = hsl(a, 0.60f, 0.18f),
            tertiaryContainer = hsl(a, 0.42f, 0.28f), onTertiaryContainer = hsl(a, 0.85f, 0.90f),
            background = hsl(h, 0.14f, 0.07f), onBackground = hsl(h, 0.12f, 0.92f),
            surface = hsl(h, 0.14f, 0.07f), onSurface = hsl(h, 0.12f, 0.92f),
            surfaceVariant = hsl(h, 0.10f, 0.28f), onSurfaceVariant = hsl(h, 0.10f, 0.78f),
            surfaceTint = hsl(h, 0.85f, 0.80f),
            inverseSurface = hsl(h, 0.10f, 0.90f), inverseOnSurface = hsl(h, 0.10f, 0.16f),
            outline = hsl(h, 0.07f, 0.57f), outlineVariant = hsl(h, 0.09f, 0.30f),
            surfaceBright = hsl(h, 0.10f, 0.24f), surfaceDim = hsl(h, 0.14f, 0.07f),
            surfaceContainerLowest = hsl(h, 0.14f, 0.045f), surfaceContainerLow = hsl(h, 0.13f, 0.095f),
            surfaceContainer = hsl(h, 0.13f, 0.12f), surfaceContainerHigh = hsl(h, 0.12f, 0.155f),
            surfaceContainerHighest = hsl(h, 0.12f, 0.195f),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = hsl(h, 0.55f, 0.42f), onPrimary = Color.White,
            primaryContainer = hsl(h, 0.85f, 0.90f), onPrimaryContainer = hsl(h, 0.60f, 0.18f),
            inversePrimary = hsl(h, 0.85f, 0.80f),
            secondary = hsl(h, 0.20f, 0.40f), onSecondary = Color.White,
            secondaryContainer = hsl(h, 0.42f, 0.89f), onSecondaryContainer = hsl(h, 0.42f, 0.14f),
            tertiary = hsl(a, 0.55f, 0.40f), onTertiary = Color.White,
            tertiaryContainer = hsl(a, 0.75f, 0.88f), onTertiaryContainer = hsl(a, 0.80f, 0.15f),
            background = hsl(h, 0.50f, 0.985f), onBackground = hsl(h, 0.15f, 0.11f),
            surface = hsl(h, 0.50f, 0.985f), onSurface = hsl(h, 0.15f, 0.11f),
            surfaceVariant = hsl(h, 0.20f, 0.90f), onSurfaceVariant = hsl(h, 0.09f, 0.30f),
            surfaceTint = hsl(h, 0.55f, 0.42f),
            inverseSurface = hsl(h, 0.12f, 0.18f), inverseOnSurface = hsl(h, 0.20f, 0.94f),
            outline = hsl(h, 0.06f, 0.47f), outlineVariant = hsl(h, 0.12f, 0.80f),
            surfaceBright = hsl(h, 0.50f, 0.985f), surfaceDim = hsl(h, 0.18f, 0.86f),
            surfaceContainerLowest = Color.White, surfaceContainerLow = hsl(h, 0.40f, 0.965f),
            surfaceContainer = hsl(h, 0.32f, 0.945f), surfaceContainerHigh = hsl(h, 0.26f, 0.92f),
            surfaceContainerHighest = hsl(h, 0.22f, 0.895f),
            error = Color(0xFFBA1A1A), onError = Color.White,
            errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        )
    }
}

@Composable
fun isDark(themeMode: Int): Boolean = when (themeMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun AppTheme(themeMode: Int, dynamic: Boolean, palette: Int, forceDark: Boolean = false, motion: Int = 1, content: @Composable () -> Unit) {
    val dark = forceDark || isDark(themeMode)
    val ctx = LocalContext.current
    val scheme = if (dynamic) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        schemeFor(Palettes.get(palette), dark)
    }
    val m = remember(motion) { Motion(motion) }
    CompositionLocalProvider(LocalDark provides dark, LocalMotion provides m) {
        MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
    }
}
