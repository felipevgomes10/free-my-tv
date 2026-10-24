package com.freemytv.home.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * The Top Shelf: what the owner last watched in the focused app, its words beside its picture in
 * a card. The picture keeps its own size: the apps' artwork is small (YouTube's is 640 × 360),
 * and stretched across the top of the Home it went soft.
 *
 * Taking turns, the next entry slides in from the right, picture and words together, while the
 * dots stay put; another app's entries cross-fade in. A page moves in only once its picture is
 * decoded, so nothing pops in mid-slide.
 */
@Composable
fun TopShelf(state: TopShelfState, modifier: Modifier = Modifier) {
    val app = state.app ?: return
    // Nothing watched in this app: the top of the Home stays dark and quiet.
    val entry = state.current ?: return
    val page by produceState<ShelfPage?>(null, app, entry) {
        value = ShelfPage(app.packageName, app.label, entry, entry.artwork?.let { decodeNear(it, CARD_DECODE_WIDTH) })
    }
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
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(end = 28.dp).width(CARD_WIDTH).height(CARD_HEIGHT).clip(RoundedCornerShape(12.dp)),
                )
            }
        }
        Column(Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            turn.AnimatedContent(transitionSpec = spec(clip = false), contentKey = { it.packageName to it.entry.key }) { page -> TopShelfText(page) }
            if (state.entries.size > 1) Dots(state.entries.size, state.index)
        }
    }
}

/** What the Top Shelf shows at one moment: an entry of an app, with its picture decoded. */
private data class ShelfPage(val packageName: String, val appLabel: String, val entry: UpNextEntry, val picture: Bitmap?)

/** The Top Shelf's words: the app, the title and how much is left. */
@Composable
private fun TopShelfText(page: ShelfPage) {
    val entry = page.entry
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(page.appLabel, color = Tokens.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(
            entry.title ?: stringResource(R.string.up_next_app, page.appLabel),
            color = Tokens.TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.75).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        upNextDetail(entry)?.let { Text(it, color = Color(0xD9FFFFFF), fontSize = 12.sp, maxLines = 2) }
    }
}

/** One dot per entry taking turns, the current one drawn out into a dash. */
@Composable
private fun Dots(count: Int, current: Int) {
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { i ->
            val on = i == current
            val width by animateDpAsState(if (on) 14.dp else 4.dp, tween(SLIDE_MILLIS), label = "dot")
            Box(Modifier.height(4.dp).width(width).background(if (on) Tokens.TextPrimary else Color(0x80FFFFFF), RoundedCornerShape(2.dp)))
        }
    }
}

private val CARD_WIDTH = 240.dp
private val CARD_HEIGHT = 135.dp
// The card's pixels on the 1080p panel.
private const val CARD_DECODE_WIDTH = 480
private const val SLIDE_MILLIS = 450
private val SLIDE = tween<IntOffset>(SLIDE_MILLIS, easing = FastOutSlowInEasing)
private val SLIDE_DISTANCE = 96.dp
