package com.freemytv.home.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.freemytv.home.R
import com.freemytv.home.android.AppArtworkLoader
import com.freemytv.home.multitasking.OpenApp
import com.freemytv.home.multitasking.SwitcherState
import com.freemytv.home.ui.theme.Tokens
import java.time.Instant

/** The App Switcher: Open Apps, most recent first; OK switches, up closes. */
@Composable
fun AppSwitcherScreen(
    state: SwitcherState,
    artwork: AppArtworkLoader,
    now: Instant,
    onSwitch: (OpenApp) -> Unit,
    onClose: (OpenApp) -> Unit,
    overlay: Boolean = false,
    underneath: String? = null,
    onFocused: (String) -> Unit = {},
) {
    val apps = state.apps.orEmpty()
    val focus = rememberTrayFocus(apps.map { it.packageName })
    val lastClose = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        apps.getOrNull(state.initialFocus)?.let { runCatching { focus.requester(it.packageName).requestFocus() } }
    }
    Box(Modifier.fillMaxSize()) {
        val panel = if (overlay) Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(310.dp)
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.18f to Color(0xDD14161C), 1f to Color(0xF2000000)))
            else Modifier.fillMaxSize().background(Tokens.Background)
                .background(Brush.verticalGradient(0f to Color(0xFF14161C), 0.7f to Tokens.Background))
        Box(panel) {
            Column(Modifier.padding(start = Tokens.SafeAreaHorizontal, top = if (overlay) 28.dp else 75.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.open_apps), color = Tokens.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Text(stringResource(R.string.open_apps_limit, state.counted, state.limit), color = Tokens.TextSecondary, fontSize = 11.sp)
            }
            if (apps.isEmpty()) {
                Text(stringResource(R.string.no_open_apps), color = Tokens.TextSecondary, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
            }
            LazyRow(
                Modifier.align(Alignment.CenterStart).fillMaxWidth().then(focus.trayModifier()),
                contentPadding = PaddingValues(horizontal = Tokens.SafeAreaHorizontal, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    var focused by remember { mutableStateOf(false) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (focused && !app.playing && (!overlay || app.canClose && underneath != null && app.packageName != underneath)) {
                                Icon(painterResource(R.drawable.ic_chevron_up), contentDescription = null, tint = Tokens.TextSecondary, modifier = Modifier.size(13.dp))
                                Text(stringResource(R.string.close_app), color = Tokens.TextSecondary, fontSize = 10.sp)
                            }
                        }
                        TvTile(
                            onClick = { onSwitch(app) },
                            width = CARD_WIDTH,
                            height = CARD_HEIGHT,
                            focusedScale = 1.09f,
                            modifier = Modifier
                                .then(focus.itemModifier(app.packageName, index))
                                .onFocusChanged { focused = it.isFocused; if (focused) onFocused(app.packageName) }
                                .onPreviewKeyEvent { event ->
                                    (event.key == Key.DirectionUp).also { up ->
                                        val native = event.nativeKeyEvent
                                        // One app per press: a held Up repeats, and a swipe on the FMTV Controller sends Ups
                                        // in quick succession, which would close the next app as soon as it took the focus.
                                        if (up && event.type == KeyEventType.KeyDown && native.repeatCount == 0 && native.eventTime - lastClose.longValue >= CLOSE_GAP_MILLIS) {
                                            lastClose.longValue = native.eventTime
                                            if (!overlay || app.canClose && underneath != null && app.packageName != underneath) onClose(app)
                                        }
                                    }
                                },
                        ) {
                            AppArt(app.packageName, app.label, app.art, artwork)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(app.label, color = Tokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (overlay && app.packageName == underneath) {
                            Text(stringResource(R.string.app_in_use), color = Tokens.TextSecondary, fontSize = 10.sp)
                        } else if (app.playing) {
                            Text(stringResource(R.string.playing_not_closed), color = Color(0xFF30D158), fontSize = 10.sp)
                        } else {
                            Text(stringResource(R.string.used_when, ago(app.lastUsed, now)), color = Tokens.TextSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = Tokens.SafeAreaHorizontal, bottom = if (overlay) 24.dp else 50.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(stringResource(R.string.ok_to_open), color = Tokens.TextSecondary, fontSize = 10.sp)
                Text(stringResource(if (overlay) R.string.back_to_app else R.string.home_to_go_back), color = Tokens.TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

/** After closing an app, Up closes nothing for this long. */
private const val CLOSE_GAP_MILLIS = 500L

private val CARD_WIDTH = 192.dp
private val CARD_HEIGHT = 108.dp
