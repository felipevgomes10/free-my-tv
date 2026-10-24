package com.freemytv.home.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.Modifier
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
import com.freemytv.home.ui.theme.Tokens

/**
 * The Top Shelf's picture, behind the top of the Home, shown only for something the owner
 * watched. It is baked once per picture, darkened and faded, so a frame draws one image
 * (ADR 0004); moving down to the lower Trays covers it with black instead of fading a layer.
 */
@Composable
fun TopShelfBackdrop(state: TopShelfState, lowered: Boolean, modifier: Modifier = Modifier) {
    val path = state.current?.artwork
    val picture by produceState<Bitmap?>(null, path) { value = path?.let { bakeBackdrop(it, BACKDROP_WIDTH, BACKDROP_HEIGHT) } }
    val cover by animateColorAsState(if (lowered) Color(0xD9000000) else Color.Transparent, label = "top shelf")
    val bitmap = picture ?: return
    Box(modifier) {
        Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(cover))
    }
}

/** The Top Shelf's words: the app, the title, how much is left, and dots when several take turns. */
@Composable
fun TopShelfText(state: TopShelfState, modifier: Modifier = Modifier) {
    val app = state.app ?: return
    // Nothing watched in this app: the top of the Home stays dark and quiet.
    val entry = state.current ?: return
    Column(modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(app.label, color = Tokens.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(
            entry.title ?: stringResource(R.string.up_next_app, app.label),
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

// The Top Shelf's shape (960 × 300 dp), at two thirds of the 1080p panel's pixels.
private const val BACKDROP_WIDTH = 1280
private const val BACKDROP_HEIGHT = 400
