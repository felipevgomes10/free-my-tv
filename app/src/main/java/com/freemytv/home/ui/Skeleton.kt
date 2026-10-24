package com.freemytv.home.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether the screen has finished arriving. Until then a screen that loads from the internet
 * shows its skeleton: the move between screens then animates light shapes only, and the real
 * content, with its pictures to decode, comes in once the screen stands still.
 */
val LocalScreenSettled = compositionLocalOf { true }

/** Stand-ins for content still loading, shaped like it, pulsing gently together. */
@Composable
fun Skeleton(content: @Composable () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
        .animateFloat(PULSE_LOW, 1f, infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse), label = "pulse")
    CompositionLocalProvider(LocalPulse provides pulse) { content() }
}

/**
 * A block standing for a line of words, a picture or a button, [width] wide or as wide as it
 * may be. It pulses with its [Skeleton] without recomposing: the pulse is read when drawing.
 */
@Composable
fun Bone(width: Dp?, height: Dp, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(height / 3)) {
    val pulse = LocalPulse.current
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), BONE, alpha = pulse?.value ?: 1f) },
    )
}

/**
 * A line of words of [fontSize] in sp: a bar as tall as their letters, in a box as tall as their
 * line, so the skeleton's lines sit where the words will.
 */
@Composable
fun TextBone(width: Dp?, fontSize: Int, modifier: Modifier = Modifier, align: Alignment = Alignment.CenterStart) {
    Box(modifier.height((fontSize * LINE).dp), contentAlignment = align) {
        Bone(width, (fontSize * LETTERS).dp, shape = RoundedCornerShape(3.dp))
    }
}

private val LocalPulse = staticCompositionLocalOf<State<Float>?> { null }

private val BONE = Color(0x1FFFFFFF)
private const val PULSE_LOW = 0.45f
private const val PULSE_MILLIS = 900
private const val LINE = 1.2f
private const val LETTERS = 0.7f
