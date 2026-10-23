package com.freemytv.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.android.AppArtwork
import com.freemytv.home.android.AppArtworkLoader
import com.freemytv.home.home.AppTrayItem
import com.freemytv.home.home.HomeState
import com.freemytv.home.home.StatusClock
import com.freemytv.home.home.TileArt
import com.freemytv.home.ui.theme.Tokens

@Composable
fun HomeScreen(
    state: HomeState,
    artwork: AppArtworkLoader,
    onSelect: (AppTrayItem) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Tokens.Background)
            .background(Brush.verticalGradient(0f to Color(0xFF14161C), 0.6f to Tokens.Background)),
    ) {
        Column(Modifier.fillMaxSize()) {
            StatusBar(state.statusClock, Modifier.padding(start = Tokens.SafeAreaHorizontal, top = Tokens.SafeAreaTop))
            Spacer(Modifier.weight(1f))
            AppTray(state.appTray, artwork, onSelect, Modifier.padding(bottom = 80.dp))
        }
    }
}

@Composable
private fun StatusBar(clock: StatusClock, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(clock.time, color = Tokens.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp)
        Text(clock.date, color = Tokens.TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun AppTray(
    items: List<AppTrayItem>,
    artwork: AppArtworkLoader,
    onSelect: (AppTrayItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstTile = remember { FocusRequester() }
    var focused by remember { mutableStateOf<AppTrayItem?>(null) }

    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty() && focused == null) runCatching { firstTile.requestFocus() }
    }

    LazyRow(
        modifier.fillMaxWidth().height(TILE_HEIGHT * FOCUSED_SCALE + 32.dp),
        contentPadding = PaddingValues(horizontal = Tokens.SafeAreaHorizontal, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(items, key = { _, item -> (item as? AppTrayItem.App)?.packageName ?: "all-apps" }) { index, item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TrayTile(
                    onClick = { onSelect(item) },
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier)
                        .onFocusChanged { if (it.isFocused) focused = item },
                ) {
                    when (item) {
                        is AppTrayItem.App -> AppTileArt(item, artwork)
                        AppTrayItem.AllApps -> AllAppsTileArt()
                    }
                }
                val label = (item as? AppTrayItem.App)?.label
                Text(
                    text = if (focused == item && label != null) label else "",
                    color = Tokens.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TrayTile(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.size(TILE_WIDTH, TILE_HEIGHT),
        shape = CardDefaults.shape(RoundedCornerShape(11.dp)),
        scale = CardDefaults.scale(focusedScale = FOCUSED_SCALE),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(1.dp, Tokens.FocusRing))),
        colors = CardDefaults.colors(containerColor = Tokens.Glass, focusedContainerColor = Tokens.Glass),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun AppTileArt(app: AppTrayItem.App, loader: AppArtworkLoader) {
    val art by produceState<AppArtwork?>(null, app.packageName) { value = loader.load(app.packageName, app.art) }
    when (val loaded = art) {
        is AppArtwork.Banner -> Image(
            bitmap = loaded.bitmap.asImageBitmap(),
            contentDescription = app.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        is AppArtwork.IconOnColor -> Box(Modifier.fillMaxSize().background(Color(loaded.color)), contentAlignment = Alignment.Center) {
            Image(bitmap = loaded.icon.asImageBitmap(), contentDescription = app.label, modifier = Modifier.size(40.dp))
        }
        null -> if (app.art == TileArt.Banner) Unit else Text(app.label, color = Tokens.TextPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun AllAppsTileArt() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        GridGlyph()
        Text(stringResource(R.string.all_apps), color = Tokens.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GridGlyph() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) { Box(Modifier.size(7.dp).background(Tokens.TextPrimary, RoundedCornerShape(2.dp))) }
            }
        }
    }
}

private val TILE_WIDTH = 124.dp
private val TILE_HEIGHT = 75.dp
private const val FOCUSED_SCALE = 1.11f
