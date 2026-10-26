package com.freemytv.home.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.topshelf.TopShelfState
import com.freemytv.home.ui.theme.Tokens
import com.freemytv.home.upnext.UpNextEntry

/** What the Top Shelf shows at one moment: an entry of an app, with its picture made ready. */
internal data class ShelfPage(val packageName: String, val appLabel: String, val entry: UpNextEntry, val picture: ShelfPicture?)

/**
 * The page the Top Shelf shows, once its picture is ready; the previous one stays meanwhile, so
 * nothing pops in half made. Null while there is nothing to show.
 */
@Composable
internal fun rememberShelfPage(state: TopShelfState): ShelfPage? {
    val app = state.app
    val entry = state.current
    val cardWidth = with(LocalDensity.current) { CARD_WIDTH.roundToPx() }
    val cardHeight = with(LocalDensity.current) { CARD_HEIGHT.roundToPx() }
    val page by produceState<ShelfPage?>(null, app, entry, cardWidth) {
        value = if (app == null || entry == null) null
        else ShelfPage(app.packageName, app.label, entry, entry.artwork?.let { shelfPicture(it, cardWidth, cardHeight) })
    }
    return page
}

/**
 * The light of what was watched across the top of the Home: the shown picture's colours,
 * blurred, fading to black before the App Tray. It cross-fades as the pictures change, and
 * fades away while the top of the Home is put aside ([visible] false).
 */
@Composable
internal fun TopShelfGlow(page: ShelfPage?, visible: Boolean, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, tween(GLOW_MILLIS), label = "glow shown")
    if (shown == 0f) return
    Crossfade(page?.picture?.glow, modifier.graphicsLayer { alpha = shown }, tween(GLOW_MILLIS), label = "glow") { glow ->
        if (glow != null) {
            Image(glow.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * The Top Shelf: what the owner last watched in the focused app, its picture as a card beside
 * its name, episode and how far along it is. The App Tray right below already names the app.
 *
 * Taking turns, the next entry slides in from the right, picture and words together, while the
 * dots stay put; another app's entries cross-fade in.
 */
@Composable
internal fun TopShelf(state: TopShelfState, page: ShelfPage?, modifier: Modifier = Modifier) {
    val shown = page ?: return
    val turn = updateTransition(shown, label = "topShelf")
    // Picture and words travel the same distance, the picture inside its card's frame.
    val distance = with(LocalDensity.current) { SLIDE_DISTANCE.roundToPx() }
    fun spec(clip: Boolean): AnimatedContentTransitionScope<ShelfPage>.() -> ContentTransform = {
        val change = if (initialState.packageName == targetState.packageName) {
            (slideInHorizontally(SLIDE) { distance } + fadeIn(tween(SLIDE_MILLIS))) togetherWith
                (slideOutHorizontally(SLIDE) { -distance } + fadeOut(tween(SLIDE_MILLIS / 2)))
        } else {
            fadeIn(tween(SLIDE_MILLIS)) togetherWith fadeOut(tween(SLIDE_MILLIS / 2))
        }
        change using SizeTransform(clip = clip)
    }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        turn.AnimatedContent(transitionSpec = spec(clip = true), contentKey = { it.packageName to it.entry.key }) { page ->
            page.picture?.let {
                // Prepared at the card's exact size: drawn pixel for pixel.
                Image(
                    it.card.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.None,
                    modifier = Modifier.padding(end = 24.dp).width(CARD_WIDTH).height(CARD_HEIGHT).clip(RoundedCornerShape(10.dp)),
                )
            }
        }
        Column(Modifier.width(WORDS_WIDTH)) {
            turn.AnimatedContent(transitionSpec = spec(clip = false), contentKey = { it.packageName to it.entry.key }) { page -> TopShelfWords(page.entry, page.appLabel) }
            if (state.entries.size > 1) Dots(state.entries.size, state.index)
        }
    }
}

/** The Top Shelf's words: the title, its episode or channel, and how much is left. */
@Composable
private fun TopShelfWords(entry: UpNextEntry, appLabel: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            entry.title ?: stringResource(R.string.up_next_app, appLabel),
            color = Tokens.TextPrimary,
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.9).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        entry.subtitle?.let { Text(it, color = DETAIL, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        val left = timeLeft(entry)
        val progress = entry.progress
        if (left != null || progress != null) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                progress?.let {
                    Box(Modifier.width(TRACK_WIDTH).height(3.dp).background(Color(0x40FFFFFF), RoundedCornerShape(1.5.dp))) {
                        Box(Modifier.fillMaxWidth(it).fillMaxHeight().background(Tokens.TextPrimary, RoundedCornerShape(1.5.dp)))
                    }
                }
                left?.let { Text(it, color = DETAIL, fontSize = 12.sp, maxLines = 1) }
            }
        }
    }
}

/** One dot per entry taking turns, the current one drawn out into a dash. */
@Composable
private fun Dots(count: Int, current: Int) {
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { i ->
            val on = i == current
            val width by animateDpAsState(if (on) 14.dp else 4.dp, tween(SLIDE_MILLIS), label = "dot")
            Box(Modifier.height(4.dp).width(width).background(if (on) Tokens.TextPrimary else Color(0x80FFFFFF), RoundedCornerShape(2.dp)))
        }
    }
}

/** How far down the glow reaches: past the Top Shelf, gone to black behind the App Tray. */
internal val GLOW_HEIGHT = 320.dp
// The apps' pictures are 640 pixels wide or less: on the 1080p panel this card shrinks them.
private val CARD_WIDTH = 256.dp
private val CARD_HEIGHT = 144.dp
private val WORDS_WIDTH = 560.dp
private val TRACK_WIDTH = 160.dp
private val DETAIL = Color(0xCCFFFFFF)
private const val SLIDE_MILLIS = 450
private const val GLOW_MILLIS = 600
private val SLIDE = tween<IntOffset>(SLIDE_MILLIS, easing = FastOutSlowInEasing)
private val SLIDE_DISTANCE = 96.dp
