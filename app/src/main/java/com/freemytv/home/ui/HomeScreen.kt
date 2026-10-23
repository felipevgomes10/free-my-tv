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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.freemytv.home.home.AppTrayItem
import com.freemytv.home.home.HomeState
import com.freemytv.home.home.StatusClock
import com.freemytv.home.ui.theme.Tokens

/** Everything the owner can do from the Home. */
class HomeActions(
    val onSelect: (AppTrayItem) -> Unit,
    val onStartEdit: (AppTrayItem) -> Unit,
    val onMove: (Int) -> Unit,
    val onDrop: () -> Unit,
    val onUnpin: () -> Unit,
    val onUninstall: () -> Unit,
)

@Composable
fun HomeScreen(
    state: HomeState,
    artwork: AppArtworkLoader,
    actions: HomeActions,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Tokens.Background)
            .background(Brush.verticalGradient(0f to Color(0xFF14161C), 0.6f to Tokens.Background)),
    ) {
        Column(Modifier.fillMaxSize()) {
            StatusBar(state.statusClock, Modifier.padding(start = Tokens.SafeAreaHorizontal, top = Tokens.SafeAreaTop))
            state.lifted?.let { EditModeHint(it.label, Modifier.padding(start = Tokens.SafeAreaHorizontal, top = 64.dp)) }
            Spacer(Modifier.weight(1f))
            AppTray(state, artwork, actions, Modifier.padding(bottom = 80.dp))
        }
    }
}

@Composable
fun StatusBar(clock: StatusClock, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(clock.time, color = Tokens.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp)
        Text(clock.date, color = Tokens.TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun EditModeHint(appLabel: String, modifier: Modifier = Modifier) {
    Column(modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(stringResource(R.string.edit_mode), color = Tokens.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.app_tray), color = Tokens.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.75).sp)
        Text(stringResource(R.string.edit_mode_hint, appLabel), color = Color(0xCCFFFFFF), fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun AppTray(
    state: HomeState,
    artwork: AppArtworkLoader,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val items = state.appTray
    val lifted = state.lifted
    val firstTile = remember { FocusRequester() }
    var focused by remember { mutableStateOf<AppTrayItem?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty() && focused == null) runCatching { firstTile.requestFocus() }
    }
    LaunchedEffect(lifted) {
        if (lifted == null) menuOpen = false
    }

    LazyRow(
        modifier.fillMaxWidth().height(TILE_HEIGHT * FOCUSED_SCALE + 32.dp),
        contentPadding = PaddingValues(horizontal = Tokens.SafeAreaHorizontal, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(items, key = { _, item -> (item as? AppTrayItem.App)?.packageName ?: "all-apps" }) { index, item ->
            val isLifted = lifted != null && item == lifted
            Column(
                Modifier.alpha(if (lifted == null || isLifted) 1f else DIMMED),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvTile(
                    onClick = { if (isLifted) actions.onDrop() else if (lifted == null) actions.onSelect(item) },
                    onLongClick = { if (lifted == null) actions.onStartEdit(item) },
                    width = TILE_WIDTH,
                    height = TILE_HEIGHT,
                    focusedScale = FOCUSED_SCALE,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier)
                        .onFocusChanged { if (it.isFocused) focused = item }
                        .onPreviewKeyEvent { event -> lifted != null && editKey(event.key, event.type, actions) { menuOpen = true } },
                ) {
                    when (item) {
                        is AppTrayItem.App -> AppArt(item.packageName, item.label, item.art, artwork)
                        AppTrayItem.AllApps -> AllAppsTileArt()
                    }
                    if (isLifted && menuOpen) {
                        ItemActionsMenu(
                            items = listOf(
                                MenuItem(stringResource(R.string.move), R.drawable.ic_move) { menuOpen = false },
                                MenuItem(stringResource(R.string.unpin), R.drawable.ic_close) { actions.onUnpin() },
                                MenuItem(stringResource(R.string.uninstall), R.drawable.ic_delete, destructive = true) { actions.onUninstall() },
                            ),
                            onDismiss = { menuOpen = false },
                            placement = MenuPlacement.Above,
                        )
                    }
                }
                when {
                    isLifted -> MoveHint(lifted!!.label)
                    else -> Text(
                        text = if (focused == item && lifted == null && item is AppTrayItem.App) item.label else "",
                        color = Tokens.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * While an app is lifted, arrows move it instead of moving focus, and Menu opens its actions.
 * Returns whether the key was used.
 */
private fun editKey(key: Key, type: KeyEventType, actions: HomeActions, openMenu: () -> Unit): Boolean = when (key) {
    Key.DirectionLeft -> true.also { if (type == KeyEventType.KeyDown) actions.onMove(-1) }
    Key.DirectionRight -> true.also { if (type == KeyEventType.KeyDown) actions.onMove(+1) }
    Key.DirectionUp, Key.DirectionDown -> true
    Key.Menu -> true.also { if (type == KeyEventType.KeyUp) openMenu() }
    else -> false
}

@Composable
private fun MoveHint(label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null, tint = Tokens.TextPrimary, modifier = Modifier.size(14.dp))
        Text(label, color = Tokens.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Tokens.TextPrimary, modifier = Modifier.size(14.dp))
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
private const val DIMMED = 0.45f
