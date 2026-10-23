package com.freemytv.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.freemytv.home.android.AppArtwork
import com.freemytv.home.android.AppArtworkLoader
import com.freemytv.home.home.TileArt
import com.freemytv.home.ui.theme.Tokens

/** An app's tile picture: its App Banner, or its icon over a color taken from it. Fills its parent. */
@Composable
fun AppArt(packageName: String, label: String, art: TileArt, loader: AppArtworkLoader) {
    val artwork by produceState<AppArtwork?>(null, packageName) { value = loader.load(packageName, art) }
    when (val loaded = artwork) {
        is AppArtwork.Banner -> Image(
            bitmap = loaded.bitmap.asImageBitmap(),
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        is AppArtwork.IconOnColor -> Box(Modifier.fillMaxSize().background(Color(loaded.color)), contentAlignment = Alignment.Center) {
            Image(bitmap = loaded.icon.asImageBitmap(), contentDescription = label, modifier = Modifier.size(40.dp))
        }
        null -> if (art == TileArt.IconOnColor) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(label, color = Tokens.TextPrimary, fontSize = 12.sp)
            }
        }
    }
}
