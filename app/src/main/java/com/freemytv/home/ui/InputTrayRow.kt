package com.freemytv.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import com.freemytv.home.inputs.InputTile
import com.freemytv.home.inputs.InputTrayState
import com.freemytv.home.ui.theme.Tokens

/** Everything the owner can do on the Input Tray. */
class InputActions(
    val onSelect: (InputTile) -> Unit,
    val onStartEdit: (InputTile) -> Unit,
    val onMove: (Int) -> Unit,
    val onDrop: () -> Unit,
    val onRename: (String) -> Unit,
    val onHide: () -> Unit,
    val onShowHidden: () -> Unit,
    val onCancel: () -> Unit,
)

/** The name the owner sees on an Input's tile. */
@Composable
fun InputTile.displayName(): String = name ?: if (isAntenna) stringResource(R.string.antenna) else portLabel

@Composable
fun InputTrayRow(
    state: InputTrayState,
    actions: InputActions,
    dimmed: Boolean,
    focusRequest: Int,
    modifier: Modifier = Modifier,
) {
    val lifted = state.lifted
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<InputTile?>(null) }
    LaunchedEffect(lifted) { if (lifted == null) menuOpen = false }
    val trayFocus = rememberTrayFocus(state.inputs.map { it.id })
    // The Input button asks for the tray: focus its first Input once the tray is there.
    LaunchedEffect(focusRequest, state.inputs.isNotEmpty()) {
        if (focusRequest > 0) state.inputs.firstOrNull()?.let { runCatching { trayFocus.requester(it.id).requestFocus() } }
    }

    Column(modifier.alpha(if (dimmed) DIMMED else 1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.inputs),
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
            itemsIndexed(state.inputs, key = { _, input -> input.id }) { index, input ->
                val isLifted = input.id == lifted?.id
                var focused by remember { mutableStateOf(false) }
                Box(Modifier.alpha(if (lifted == null || isLifted) 1f else DIMMED)) {
                    TvTile(
                        onClick = { if (isLifted) actions.onDrop() else if (lifted == null) actions.onSelect(input) },
                        onLongClick = { if (lifted == null) actions.onStartEdit(input) },
                        width = TILE_WIDTH,
                        height = TILE_HEIGHT,
                        focusedScale = 1.1f,
                        modifier = Modifier
                            .then(trayFocus.itemModifier(input.id, index))
                            .onFocusChanged { focused = it.isFocused }
                            .onPreviewKeyEvent { lifted != null && editKey(it.key, it.type, actions.onMove) { menuOpen = true } },
                    ) {
                        InputTileContent(input, highlighted = focused)
                        if (isLifted && menuOpen) {
                            ItemActionsMenu(
                                items = buildList {
                                    add(MenuItem(stringResource(R.string.move), R.drawable.ic_move) { menuOpen = false })
                                    add(MenuItem(stringResource(R.string.rename), R.drawable.ic_edit) { menuOpen = false; renaming = input })
                                    add(MenuItem(stringResource(R.string.hide), R.drawable.ic_visibility_off) { actions.onHide() })
                                    if (state.hiddenCount > 0) {
                                        add(MenuItem(stringResource(R.string.show_hidden, state.hiddenCount), R.drawable.ic_visibility) { actions.onShowHidden() })
                                    }
                                },
                                onDismiss = { menuOpen = false },
                                placement = MenuPlacement.Above,
                            )
                        }
                    }
                }
            }
        }
    }

    renaming?.let { input ->
        TextInputDialog(
            title = stringResource(R.string.rename_input),
            hint = stringResource(R.string.rename_input_hint),
            current = input.displayName(),
            onDone = { actions.onRename(it); renaming = null },
            onDismiss = { renaming = null; actions.onCancel() },
        )
    }
}

@Composable
private fun InputTileContent(input: InputTile, highlighted: Boolean) {
    val primary = when {
        highlighted -> Color.Black
        input.hasSignal -> Tokens.TextPrimary
        else -> Tokens.TextSecondary
    }
    val secondary = if (highlighted) Color(0x99000000) else Tokens.TextSecondary
    val detail = when {
        input.isAntenna -> stringResource(R.string.broadcast_tv)
        !input.hasSignal -> stringResource(R.string.nothing_connected)
        input.name != null -> input.portLabel
        else -> stringResource(R.string.connected)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(if (highlighted) Tokens.TextPrimary else Color.Transparent)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            painterResource(if (input.isAntenna) R.drawable.ic_antenna else R.drawable.ic_hdmi),
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(18.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(input.displayName(), color = primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, color = secondary, fontSize = 9.sp, maxLines = 1)
        }
    }
}


private val TILE_WIDTH = 138.dp
private val TILE_HEIGHT = 75.dp
private const val DIMMED = 0.45f
