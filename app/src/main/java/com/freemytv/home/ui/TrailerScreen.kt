package com.freemytv.home.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.explorar.TitleVideo
import com.freemytv.home.ui.theme.Tokens
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * A Trailer full screen in YouTube's embedded player (ADR 0009), with YouTube's controls off
 * and the design's on top (docs/design/explorar-trailer.png). The WebView lives only while
 * this screen does.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerScreen(
    title: String,
    video: TitleVideo,
    onBack: () -> Unit,
    /** The player can't play this video: the caller hands it to the YouTube app. */
    onFailed: () -> Unit,
) {
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var lastKey by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    val playPause = remember { FocusRequester() }
    val main = remember { Handler(Looper.getMainLooper()) }
    val webView = remember { mutableStateOf<WebView?>(null) }

    fun run(script: String) = webView.value?.evaluateJavascript(script, null)

    // Controls hide after a few seconds without a key, and come back on any.
    LaunchedEffect(lastKey, playing) {
        controlsVisible = true
        if (playing) {
            delay(CONTROLS_HIDE_MILLIS)
            controlsVisible = false
        }
    }
    LaunchedEffect(Unit) {
        runCatching { playPause.requestFocus() }
        while (true) {
            delay(POLL_MILLIS)
            webView.value?.evaluateJavascript("progress()") { json ->
                runCatching {
                    val o = JSONObject(json.trim('"').replace("\\\"", "\""))
                    position = o.optDouble("t").toFloat()
                    duration = o.optDouble("d").toFloat()
                }
            }
        }
    }
    // Home or sleep stops the trailer instead of letting it play behind another app.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) run("player && player.pauseVideo()") }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            webView.value?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webView.value = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent {
                lastKey++
                false
            },
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    isFocusable = false
                    setBackgroundColor(AndroidColor.BLACK)
                    settings.javaScriptEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                            main.post(onFailed)
                            return true
                        }
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onState(state: Int) = main.post {
                                playing = state == PLAYING || state == BUFFERING
                                if (state == ENDED) onBack()
                            }

                            @JavascriptInterface
                            fun onError(code: Int) = main.post(onFailed)
                        },
                        "Bridge",
                    )
                    // The page needs an origin of its own, which it also tells the player: without one
                    // YouTube refuses to play (error 152).
                    loadDataWithBaseURL(PAGE_ORIGIN, playerPage(video.key), "text/html", "utf-8", null)
                    webView.value = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Faded rather than removed: the focused button must stay, or keys would stop reaching the screen.
        val controlsAlpha by animateFloatAsState(if (controlsVisible) 1f else 0f, label = "controls")
        Box(Modifier.fillMaxSize().alpha(controlsAlpha)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.3f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0xCC000000)))) {
                PillButton(stringResource(R.string.back_to_details), R.drawable.ic_chevron_left, onBack, Modifier.padding(start = Tokens.SafeAreaHorizontal, top = 32.dp))
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = Tokens.SafeAreaHorizontal, vertical = 28.dp)) {
                    Text(title, color = Tokens.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(if (video.official && video.type.equals("Trailer", true)) R.string.official_trailer else R.string.trailer), color = Color(0xD9FFFFFF), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    ProgressBar(if (duration > 0) position / duration else 0f, Modifier.padding(top = 20.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(clock(position), color = Tokens.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            SeekButton(R.drawable.ic_replay_10, "10 s") { run("player.seekTo(Math.max(0, player.getCurrentTime() - 10), true)") }
                            Surface(
                                onClick = { run(if (playing) "player.pauseVideo()" else "player.playVideo()") },
                                modifier = Modifier.size(44.dp).focusRequester(playPause),
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(containerColor = Tokens.TextPrimary, contentColor = Color.Black, focusedContainerColor = Tokens.TextPrimary, focusedContentColor = Color.Black),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                            SeekButton(R.drawable.ic_forward_10, "10 s") { run("player.seekTo(player.getCurrentTime() + 10, true)") }
                        }
                        Text(clock(duration), color = Tokens.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeekButton(icon: Int, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF), contentColor = Tokens.TextPrimary, focusedContainerColor = Tokens.TextPrimary, focusedContentColor = Color.Black),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp))
            Text(label, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Color(0x40FFFFFF), CircleShape)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Tokens.TextPrimary, CircleShape))
    }
}

private fun clock(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** The page the WebView loads: YouTube's IFrame Player API, its controls off, reporting to the Home. */
private fun playerPage(key: String) = """
    <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
      html,body{margin:0;background:#000;overflow:hidden}
      #player{position:fixed;top:0;left:0;border:0}
    </style></head>
    <body><div id="player"></div>
    <script src="https://www.youtube.com/iframe_api"></script>
    <script>
      var player;
      function onYouTubeIframeAPIReady() {
        player = new YT.Player('player', {
          width: '100%', height: '100%', videoId: '$key',
          playerVars: { autoplay: 1, controls: 0, disablekb: 1, fs: 0, rel: 0, iv_load_policy: 3, playsinline: 1, modestbranding: 1, origin: '$PAGE_ORIGIN', widget_referrer: '$PAGE_ORIGIN' },
          events: {
            onReady: function (e) { fit(); e.target.playVideo(); },
            onStateChange: function (e) { Bridge.onState(e.data); },
            onError: function (e) { Bridge.onError(e.data); }
          }
        });
      }
      // The WebView starts at zero size, and "100%" or "100vh" keep that zero: the player is sized
      // in pixels once the page has its real size, and again whenever it changes.
      function fit() {
        if (player && player.setSize) player.setSize(window.innerWidth, window.innerHeight);
      }
      window.addEventListener('resize', fit);
      function progress() {
        if (!player || !player.getCurrentTime) return JSON.stringify({ t: 0, d: 0 });
        return JSON.stringify({ t: player.getCurrentTime(), d: player.getDuration() });
      }
    </script></body></html>
""".trimIndent()

private const val PAGE_ORIGIN = "https://freemytv.home"
private const val CONTROLS_HIDE_MILLIS = 4_000L
private const val POLL_MILLIS = 500L
// YouTube's player states.
private const val ENDED = 0
private const val PLAYING = 1
private const val BUFFERING = 3
