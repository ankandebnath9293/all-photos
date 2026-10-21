package com.ankandebnath.allphotos.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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

    /** A page opening on top (settings, an album, a clean-up tool). */
    fun pageOpen(): ContentTransform {
        if (minimal) return fadeIn(tween(120)) togetherWith fadeOut(tween(90))
        val enter: EnterTransition = fadeIn(tween(fadeMs(210), delayMillis = 40)) +
            scaleIn(spring(dampingRatio = if (rich) 0.78f else 0.9f, stiffness = if (rich) 380f else 520f), initialScale = if (rich) 0.88f else 0.92f)
        val exit: ExitTransition = fadeOut(tween(fadeMs(150))) + scaleOut(tween(220), targetScale = 1.04f)
        return enter togetherWith exit
    }

    /** Going back from a page. */
    fun pageClose(): ContentTransform {
        if (minimal) return fadeIn(tween(120)) togetherWith fadeOut(tween(90))
        val enter: EnterTransition = fadeIn(tween(fadeMs(210))) + scaleIn(tween(260), initialScale = 1.04f)
        val exit: ExitTransition = fadeOut(tween(fadeMs(160))) +
            scaleOut(spring(dampingRatio = 0.9f, stiffness = 600f), targetScale = 0.9f)
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
