package com.freemytv.home.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.topshelf.TopShelfState
import com.freemytv.home.upnext.UpNextEntry
import com.freemytv.home.ui.theme.Tokens

/**
 * The Top Shelf: what the owner last watched in the focused app, its words beside its picture in
 * a card. The picture keeps its own size: the apps' artwork is small (YouTube's is 640 × 360),
 * and stretched across the top of the Home it went soft.
 */
@Composable
fun TopShelf(state: TopShelfState, modifier: Modifier = Modifier) {
    val app = state.app ?: return
    // Nothing watched in this app: the top of the Home stays dark and quiet.
    val entry = state.current ?: return
    val path = entry.artwork
    val picture by produceState<Bitmap?>(null, path) { value = path?.let { decodeNear(it, CARD_DECODE_WIDTH) } }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Bottom) {
        picture?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(CARD_WIDTH).height(CARD_HEIGHT).clip(RoundedCornerShape(12.dp)),
            )
        }
        TopShelfText(state, app.label, entry)
    }
}

/** The Top Shelf's words: the app, the title, how much is left, and dots when several take turns. */
@Composable
private fun TopShelfText(state: TopShelfState, appLabel: String, entry: UpNextEntry) {
    Column(Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(appLabel, color = Tokens.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(
            entry.title ?: stringResource(R.string.up_next_app, appLabel),
            color = Tokens.TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.75).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        upNextDetail(entry)?.let { Text(it, color = Color(0xD9FFFFFF), fontSize = 12.sp, maxLines = 2) }
        if (state.entries.size > 1) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                state.entries.indices.forEach { i ->
                    Box(
                        Modifier
                            .height(4.dp)
                            .width(if (i == state.index) 14.dp else 4.dp)
                            .background(if (i == state.index) Tokens.TextPrimary else Color(0x80FFFFFF), if (i == state.index) RoundedCornerShape(2.dp) else CircleShape),
                    )
                }
            }
        }
    }
}

private val CARD_WIDTH = 240.dp
private val CARD_HEIGHT = 135.dp
// The card's pixels on the 1080p panel.
private const val CARD_DECODE_WIDTH = 480
