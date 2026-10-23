package com.freemytv.home.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.android.AppArtworkLoader
import com.freemytv.home.topshelf.TopShelfState
import com.freemytv.home.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Top Shelf's picture, behind the top of the Home; it dims when focus moves to the lower Trays. */
@Composable
fun TopShelfBackdrop(state: TopShelfState, artwork: AppArtworkLoader, lowered: Boolean, modifier: Modifier = Modifier) {
    val app = state.app ?: return
    val alpha by animateFloatAsState(if (lowered) LOWERED_ALPHA else 1f, label = "top shelf")
    val path = state.current?.artwork
    val picture by produceState<Bitmap?>(null, app.packageName, path) {
        value = path?.let { decodeBackdrop(it) } ?: artwork.backdrop(app.packageName)
    }
    Box(modifier.alpha(alpha)) {
        Crossfade(picture, label = "top shelf picture") { bitmap ->
            bitmap?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xB3000000), 0.6f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Tokens.Background)))
    }
}

/** The Top Shelf's words: the app, the title, how much is left, and dots when several take turns. */
@Composable
fun TopShelfText(state: TopShelfState, modifier: Modifier = Modifier) {
    val app = state.app ?: return
    val entry = state.current
    Column(modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (entry != null) Text(app.label, color = Tokens.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(
            entry?.title ?: if (entry != null) stringResource(R.string.up_next_app, app.label) else app.label,
            color = Tokens.TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.75).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        entry?.let { upNextDetail(it) }?.let { Text(it, color = Color(0xD9FFFFFF), fontSize = 12.sp, maxLines = 2) }
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

/** Decoded at 1080p at most (ADR 0004), and smaller when the stored artwork allows. */
private suspend fun decodeBackdrop(path: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= BACKDROP_WIDTH) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

private const val BACKDROP_WIDTH = 1280
private const val LOWERED_ALPHA = 0.2f
