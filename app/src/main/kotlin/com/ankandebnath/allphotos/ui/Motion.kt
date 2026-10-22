package com.ankandebnath.allphotos.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset

/**
 * How much the app moves. Minimal keeps short fades only, Standard is quick and snappy, Rich adds
 * a little more travel and bounce. Every animation in the app reads its timing from here.
 */
class Motion(val level: Int) {
    val minimal get() = level == 0
    val rich get() = level == 2

    /** Photo opening from the grid and flying back to it. */
    fun <T> hero(): FiniteAnimationSpec<T> = when (level) {
        0 -> tween(150, easing = FastOutSlowInEasing)
        1 -> spring(dampingRatio = 0.88f, stiffness = 520f)
        else -> spring(dampingRatio = 0.76f, stiffness = 360f)
    }

    /** Small things: chips, badges, toggles. */
    fun <T> quick(): FiniteAnimationSpec<T> = when (level) {
        0 -> tween(90)
        1 -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        else -> spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
    }

    private fun fadeMs(base: Int) = if (level == 0) (base * 0.6f).toInt() else base

    // Material's emphasized curves, the ones Android uses when one screen slides over another.
    private val decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** How the new page slides in from the right edge. */
    fun <T> slideIn(): FiniteAnimationSpec<T> = when (level) {
        2 -> spring(dampingRatio = 0.86f, stiffness = 300f)
        else -> tween(420, easing = decelerate)
    }

    /** How the page you leave slides back out to the right. */
    fun <T> slideOut(): FiniteAnimationSpec<T> = when (level) {
        2 -> spring(dampingRatio = 0.9f, stiffness = 380f)
        else -> tween(320, easing = emphasized)
    }

    /** How far the page underneath drifts left while the new one covers it (a quarter of the width). */
    val parallax = 0.25f

    /**
     * A page opening on top (settings, an album, a clean-up tool): it slides in from the right edge
     * over the page you were on, which drifts a little to the left and dims, like Google Photos.
     */
    fun pageOpen(): ContentTransform {
        if (minimal) return fadeIn(tween(120)) togetherWith fadeOut(tween(90))
        val enter = slideInHorizontally(slideIn()) { it }
        val exit = slideOutHorizontally(slideIn()) { -(it * parallax).toInt() }
        return enter togetherWith exit
    }

    /** Going back: the page slides away to the right and the one underneath slides back into place. */
    fun pageClose(): ContentTransform {
        if (minimal) return fadeIn(tween(120)) togetherWith fadeOut(tween(90))
        val enter = slideInHorizontally(slideOut()) { -(it * parallax).toInt() }
        val exit = slideOutHorizontally(slideOut()) { it }
        return enter togetherWith exit
    }

    /** Switching tabs. */
    fun tab(): ContentTransform {
        if (minimal) return fadeIn(tween(100)) togetherWith fadeOut(tween(80))
        return (fadeIn(tween(fadeMs(200), delayMillis = 30)) + scaleIn(tween(260), initialScale = if (rich) 0.94f else 0.97f)) togetherWith
            fadeOut(tween(fadeMs(120)))
    }

    val placement: FiniteAnimationSpec<IntOffset>?
        get() = if (minimal) null else spring(dampingRatio = if (rich) 0.8f else 1f, stiffness = Spring.StiffnessMediumLow)
}

val LocalMotion = staticCompositionLocalOf { Motion(1) }
