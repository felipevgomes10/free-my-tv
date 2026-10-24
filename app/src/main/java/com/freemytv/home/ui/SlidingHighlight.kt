package com.freemytv.home.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import com.freemytv.home.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The white pill of a group of pills or list rows, drawn once by the group and sliding from
 * item to item instead of jumping. It follows focus, or the chosen option for a segmented
 * control. Items keep their own look otherwise, and whatever of them the pill passes under is
 * drawn black, exactly where it is. Everything happens at draw time: sliding recomposes nothing.
 * Tiles don't use it: a focused tile grows in place. A highlight that [inks] nothing, such as a
 * glass focus pill under a segmented control's white choice, only slides.
 */
@Stable
class SlidingHighlight internal constructor(private val followsSelection: Boolean, private val inks: Boolean) {
    private var origin by mutableStateOf(Offset.Zero)
    private var shape: Shape = RectangleShape
    private val bounds = mutableStateMapOf<Any, Rect>()
    private var focused by mutableStateOf<Any?>(null)
    internal var selected by mutableStateOf<Any?>(null)
    internal val rect = Animatable(Rect.Zero, Rect.VectorConverter)
    internal val alpha = Animatable(0f)
    internal val kept = mutableStateListOf<Kept>()

    /** An element that keeps its own colors under the pill, recorded so it can be drawn again over the black. */
    internal class Kept(val layer: GraphicsLayer) {
        var bounds by mutableStateOf(Rect.Zero)
    }

    private val target: Any? get() = if (followsSelection) selected else focused

    /** The item the pill should be on and where it is now, in the group's own coordinates. */
    internal val targetItem: Pair<Any, Rect>? get() = target?.let { key -> bounds[key]?.let { key to it.translate(-origin) } }

    internal val shown: Boolean get() = target != null

    /** Put on the group: the row or column the items sit in. The pill is drawn behind them. */
    fun group(shape: Shape, color: Color = Tokens.TextPrimary): Modifier = Modifier
        .onGloballyPositioned {
            origin = it.positionInRoot()
            this.shape = shape
        }
        .drawBehind {
            val a = alpha.value
            if (a <= 0f) return@drawBehind
            val r = rect.value
            translate(r.left, r.top) { drawOutline(shape.createOutline(r.size, layoutDirection, this), color, alpha = a) }
        }

    /** Put on each item, outermost: it tracks the item and inks it black under the pill. */
    fun item(key: Any): Modifier = Modifier
        .onGloballyPositioned {
            // Written only when it moved: every write redraws the group's items.
            val now = Rect(it.positionInRoot(), it.size.toSize())
            if (bounds[key] != now) bounds[key] = now
        }
        .onFocusChanged {
            if (it.isFocused) focused = key else if (focused == key) focused = null
        }
        .then(if (inks) ink(key) else Modifier)

    private fun ink(key: Any): Modifier = Modifier
        // Its own layer, so the black below lands on its pixels only. (Switching to a layer only
        // while the pill is over it didn't take on the emulator.)
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val pill = pillOver(key) ?: return@drawWithContent
            val outline = Path().apply { addOutline(shape.createOutline(pill.size, layoutDirection, this@drawWithContent)) }
            outline.translate(pill.topLeft)
            val item = bounds[key] ?: return@drawWithContent
            clipPath(outline) {
                drawRect(Color.Black, alpha = alpha.value, blendMode = BlendMode.SrcAtop)
                kept.forEach { if (item.contains(it.bounds.center)) translate(it.bounds.left - item.left, it.bounds.top - item.top) { drawLayer(it.layer) } }
            }
        }

    /** The pill in the coordinates of the item at [key], when it is over that item at all. */
    private fun pillOver(key: Any): Rect? {
        val item = bounds[key] ?: return null
        if (alpha.value <= 0f) return null
        val pill = rect.value.translate(origin - item.topLeft)
        return pill.takeIf { it.overlaps(Rect(Offset.Zero, item.size)) }
    }

    /**
     * Surface colors for an item: [container] of its own, none of its own under the pill, and
     * solid words when focused, since the pill's black keeps their opacity.
     */
    @Composable
    fun colors(
        content: Color = Tokens.TextPrimary,
        container: Color = Color.Transparent,
        disabledContent: Color = content,
        disabledContainer: Color = container,
    ): ClickableSurfaceColors = ClickableSurfaceDefaults.colors(
        containerColor = container,
        contentColor = content,
        focusedContainerColor = Color.Transparent,
        focusedContentColor = Tokens.TextPrimary,
        pressedContainerColor = Color.Transparent,
        pressedContentColor = Tokens.TextPrimary,
        disabledContainerColor = disabledContainer,
        disabledContentColor = disabledContent,
    )
}

/** The group a pill or row sits in; null outside any, where focus fills the item itself. */
val LocalSlidingHighlight = staticCompositionLocalOf<SlidingHighlight?> { null }

/** Lays out [content] as a group whose items share one sliding pill of [shape]. */
@Composable
fun SlidingHighlightGroup(highlight: SlidingHighlight, shape: Shape, content: @Composable (Modifier) -> Unit) {
    CompositionLocalProvider(LocalSlidingHighlight provides highlight) { content(highlight.group(shape)) }
}

/** Joins the group around this item, if any; put it first on the item's Surface. */
@Composable
fun Modifier.slidingHighlightItem(key: Any? = null): Modifier {
    val highlight = LocalSlidingHighlight.current ?: return this
    val own = remember { Any() }
    return this.then(highlight.item(key ?: own))
}

/** Keeps this element's own colors under the pill, such as an app's icon or a toggle's green. */
@Composable
fun Modifier.keepColorsUnderHighlight(): Modifier {
    val highlight = LocalSlidingHighlight.current ?: return this
    val layer = rememberGraphicsLayer()
    val kept = remember(layer) { SlidingHighlight.Kept(layer) }
    DisposableEffect(highlight, kept) {
        highlight.kept += kept
        onDispose { highlight.kept -= kept }
    }
    return this
        .onGloballyPositioned { kept.bounds = Rect(it.positionInRoot(), it.size.toSize()) }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
}

/** A pill's or row's colors: the group's when in one, otherwise turning white when focused. */
@Composable
fun highlightColors(
    content: Color = Tokens.TextPrimary,
    container: Color = Color.Transparent,
    disabledContent: Color = content,
    disabledContainer: Color = container,
): ClickableSurfaceColors =
    LocalSlidingHighlight.current?.colors(content, container, disabledContent, disabledContainer) ?: ClickableSurfaceDefaults.colors(
        containerColor = container,
        contentColor = content,
        focusedContainerColor = Tokens.TextPrimary,
        focusedContentColor = Color.Black,
        disabledContainerColor = disabledContainer,
        disabledContentColor = disabledContent,
    )

/** A pill's or row's focused growth: none in a group, where the pill does the showing. */
@Composable
fun highlightScale(focusedScale: Float) =
    ClickableSurfaceDefaults.scale(focusedScale = if (LocalSlidingHighlight.current != null) 1f else focusedScale)

/** A [SlidingHighlight] following focus; with [selected], one following that option instead. */
@Composable
fun rememberSlidingHighlight(selected: Any? = null, followsSelection: Boolean = selected != null, inks: Boolean = true): SlidingHighlight {
    val highlight = remember { SlidingHighlight(followsSelection, inks) }
    highlight.selected = selected
    LaunchedEffect(highlight) {
        launch {
            // Moving between items is a moment of no focus here; only a real departure fades the pill.
            snapshotFlow { highlight.shown }.collectLatest { shown ->
                if (!shown) delay(LEAVE_GRACE_MILLIS)
                highlight.alpha.animateTo(if (shown) 1f else 0f, tween(FADE_MILLIS))
            }
        }
        var resting: Any? = null
        var sliding = false
        snapshotFlow { highlight.targetItem }.collectLatest { item ->
            val (key, target) = item ?: return@collectLatest
            when {
                // Arriving in the group: the pill starts on the item rather than flying in.
                highlight.alpha.value == 0f -> {
                    resting = key
                    highlight.rect.snapTo(target)
                }
                // The item moved under a resting pill, as when a list scrolls: follow it exactly.
                key == resting && !sliding -> highlight.rect.snapTo(target)
                // Another item, or this one moving while the pill is still on its way: slide.
                else -> {
                    resting = key
                    sliding = true
                    highlight.rect.animateTo(target, SLIDE)
                    sliding = false
                }
            }
        }
    }
    return highlight
}

private val SLIDE = spring<Rect>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f)
private const val FADE_MILLIS = 120
private const val LEAVE_GRACE_MILLIS = 40L
