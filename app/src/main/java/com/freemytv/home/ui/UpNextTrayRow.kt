package com.freemytv.home.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
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
import com.freemytv.home.android.AppArtworkLoader
import com.freemytv.home.home.TileArt
import com.freemytv.home.ui.theme.Tokens
import com.freemytv.home.upnext.UpNextEntry
import com.freemytv.home.upnext.UpNextState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Continuar assistindo": what the owner started in other apps; hidden while empty. */
@Composable
fun UpNextTrayRow(
    state: UpNextState,
    artwork: AppArtworkLoader,
    onSelect: (UpNextEntry) -> Unit,
    onRemove: (UpNextEntry) -> Unit,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    if (state.entries.isEmpty()) return
    val trayFocus = rememberTrayFocus(state.entries.map { it.key })
    // Edit Mode on this Tray has a single action, so holding OK opens it straight away.
    var menuFor by remember { mutableStateOf<String?>(null) }

    Column(modifier.alpha(if (dimmed) DIMMED else 1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.up_next),
            color = Tokens.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = Tokens.SafeAreaHorizontal),
        )
        LazyRow(
            Modifier.fillMaxWidth().then(trayFocus.trayModifier()),
            contentPadding = PaddingValues(horizontal = Tokens.SafeAreaHorizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(state.entries, key = { _, entry -> entry.key }) { index, entry ->
                TvTile(
                    onClick = { onSelect(entry) },
                    onLongClick = { menuFor = entry.key },
                    width = TILE_WIDTH,
                    height = TILE_HEIGHT,
                    focusedScale = 1.08f,
                    modifier = trayFocus.itemModifier(entry.key, index),
                ) {
                    UpNextTile(entry, artwork)
                    if (menuFor == entry.key) {
                        ItemActionsMenu(
                            items = listOf(
                                MenuItem(stringResource(R.string.remove_from_list), R.drawable.ic_close, destructive = true) {
                                    menuFor = null
                                    onRemove(entry)
                                },
                            ),
                            onDismiss = { menuFor = null },
                            placement = MenuPlacement.Above,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpNextTile(entry: UpNextEntry, artwork: AppArtworkLoader) {
    val picture by produceState<Bitmap?>(null, entry.artwork) { value = entry.artwork?.let { decode(it) } }
    Box(Modifier.fillMaxSize()) {
        picture?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } ?: AppArt(entry.packageName, entry.appLabel, TileArt.Banner, artwork)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xCC000000))))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                entry.title ?: stringResource(R.string.up_next_app, entry.appLabel),
                color = Tokens.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail(entry)?.let { Text(it, color = Color(0xCCFFFFFF), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            entry.progress?.let { progress ->
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(2.dp).background(Color(0x40FFFFFF), RoundedCornerShape(1.dp))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Tokens.TextPrimary, RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}

/** "Episódio 4, faltam 18 min": the app's subtitle and the time left, whichever are known. */
@Composable
private fun detail(entry: UpNextEntry): String? {
    val left = entry.remainingMillis?.let { millis ->
        val minutes = ((millis + 59_999) / 60_000).toInt()
        if (minutes < 60) stringResource(R.string.left_minutes, minutes) else stringResource(R.string.left_hours, minutes / 60, minutes % 60)
    }
    return listOfNotNull(entry.subtitle, left).joinToString(", ").ifEmpty { null }
}

/** Decoded near tile size: a 1080p artwork would cost the Home ~8 MB per tile (ADR 0004). */
private suspend fun decode(path: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= DECODE_WIDTH) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

private val TILE_WIDTH = 200.dp
private val TILE_HEIGHT = 112.dp
private const val DECODE_WIDTH = 440
private const val DIMMED = 0.45f
