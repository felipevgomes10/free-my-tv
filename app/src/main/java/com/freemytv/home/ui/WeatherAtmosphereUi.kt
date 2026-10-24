package com.freemytv.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.freemytv.home.android.WeatherBackdrop
import com.freemytv.home.weather.WeatherAtmosphere

@Composable
fun WeatherAtmosphereBackground(atmosphere: WeatherAtmosphere?, active: Boolean, muted: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (atmosphere != null) key(atmosphere) {
        val context = LocalContext.current
        val view = remember { WeatherBackdrop(context, atmosphere) }
        DisposableEffect(view) { onDispose { view.releasePlayback() } }
        AndroidView(
            factory = { view },
            modifier = Modifier.fillMaxSize(),
            update = { it.playback(active && resumed, muted) },
        )
    }
    // Even a pure white video frame is limited to 38% white. Secondary text here is 85% white:
    // the worst-case contrast is > 5:1, before the additional top/bottom shading.
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.55f),
        0.12f to Color.Black.copy(alpha = 0.45f),
        0.35f to Color.Transparent,
        0.65f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.6f),
    )))
}
