package com.freemytv.home.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import com.freemytv.home.inputs.InputTrayState
import com.freemytv.home.ui.theme.Tokens
import com.freemytv.home.upnext.UpNextEntry
import com.freemytv.home.upnext.UpNextState

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
    inputs: InputTrayState,
    upNext: UpNextState,
    onUpNext: (UpNextEntry) -> Unit,
    inputTrayFocusRequest: Int,
    artwork: AppArtworkLoader,
    actions: HomeActions,
    inputActions: InputActions,
    statusWidgets: @Composable () -> Unit,
) {
    var inputsFocused by remember { mutableStateOf(false) }
    var upNextFocused by remember { mutableStateOf(false) }
    // The top of the Home recedes when focus moves down to the lower Trays, so every tray fits.
    val topArea by animateDpAsState(if (inputsFocused || upNextFocused) 24.dp else 236.dp, label = "top area")

    Box(
        Modifier
            .fillMaxSize()
            .background(Tokens.Background)
            .background(Brush.verticalGradient(0f to Color(0xFF14161C), 0.6f to Tokens.Background)),
    ) {
        // Laid out at full height and clipped by the screen: trays below the fold peek in, nothing scrolls.
        Column(Modifier.fillMaxSize().wrapContentHeight(Alignment.Top, unbounded = true)) {
            StatusBar(state.statusClock, Modifier.padding(start = Tokens.SafeAreaHorizontal, end = Tokens.SafeAreaHorizontal, top = Tokens.SafeAreaTop), statusWidgets)
            Box(Modifier.height(topArea)) {
                val hint = state.lifted?.let { stringResource(R.string.app_tray) to it.label }
                    ?: inputs.lifted?.let { stringResource(R.string.inputs) to it.displayName() }
                hint?.let { (tray, item) -> EditModeHint(tray, item, Modifier.padding(start = Tokens.SafeAreaHorizontal, top = 40.dp)) }
            }
            AppTray(state, artwork, actions, dimmed = inputs.lifted != null)
            UpNextTrayRow(
                upNext,
                artwork,
                onSelect = onUpNext,
                dimmed = state.lifted != null || inputs.lifted != null,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .onFocusChanged { upNextFocused = it.hasFocus },
            )
            InputTrayRow(
                inputs,
                inputActions,
                dimmed = state.lifted != null,
                focusRequest = inputTrayFocusRequest,
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 48.dp)
                    .onFocusChanged { inputsFocused = it.hasFocus },
            )
        }
    }
}

/** The clock on the left and the compact Widgets and system icons on the right. */
@Composable
fun StatusBar(clock: StatusClock, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(clock.time, color = Tokens.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp)
            Text(clock.date, color = Tokens.TextSecondary, fontSize = 9.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

@Composable
private fun EditModeHint(tray: String, item: String, modifier: Modifier = Modifier) {
    Column(modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(stringResource(R.string.edit_mode), color = Tokens.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(tray, color = Tokens.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.75).sp)
        Text(stringResource(R.string.edit_mode_hint, item), color = Color(0xCCFFFFFF), fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun AppTray(
    state: HomeState,
    artwork: AppArtworkLoader,
    actions: HomeActions,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val items = state.appTray
    val lifted = state.lifted
    val keys = items.map { it.key() }
    val trayFocus = rememberTrayFocus(keys)
    var focused by remember { mutableStateOf<AppTrayItem?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty() && focused == null) runCatching { trayFocus.requester(keys.first()).requestFocus() }
    }
    LaunchedEffect(lifted) {
        if (lifted == null) menuOpen = false
    }

    LazyRow(
        modifier.fillMaxWidth().height(TILE_HEIGHT * FOCUSED_SCALE + 32.dp).alpha(if (dimmed) DIMMED else 1f).then(trayFocus.trayModifier()),
        contentPadding = PaddingValues(horizontal = Tokens.SafeAreaHorizontal, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.key() }) { index, item ->
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
                        .then(trayFocus.itemModifier(item.key(), index))
                        .onFocusChanged { if (it.isFocused) focused = item else if (focused == item) focused = null }
                        .onPreviewKeyEvent { event -> lifted != null && editKey(event.key, event.type, actions.onMove) { menuOpen = true } },
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
 * While an item is lifted, arrows move it instead of moving focus, and Menu opens its actions.
 * Returns whether the key was used.
 */
internal fun editKey(key: Key, type: KeyEventType, onMove: (Int) -> Unit, openMenu: () -> Unit): Boolean = when (key) {
    Key.DirectionLeft -> true.also { if (type == KeyEventType.KeyDown) onMove(-1) }
    Key.DirectionRight -> true.also { if (type == KeyEventType.KeyDown) onMove(+1) }
    Key.DirectionUp, Key.DirectionDown -> true
    Key.Menu -> true.also { if (type == KeyEventType.KeyUp) openMenu() }
    else -> false
}

private fun AppTrayItem.key(): String = (this as? AppTrayItem.App)?.packageName ?: "all-apps"

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
