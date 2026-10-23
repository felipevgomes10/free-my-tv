package com.freemytv.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Keeps focus inside a Tray when the focused item disappears from it (unpinned, hidden,
 * uninstalled): focus moves to the item that takes its place, instead of jumping to
 * another Tray.
 */
class TrayFocus internal constructor() {
    private val requesters = HashMap<Any, FocusRequester>()
    internal var focusedKey: Any? = null
    internal var focusedIndex = 0
    internal var currentKeys: () -> List<Any> = { emptyList() }

    fun requester(key: Any): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /** Put on the Tray's row. */
    fun trayModifier(): Modifier = Modifier.onFocusChanged { state ->
        // Losing focus because the owner moved elsewhere forgets the item; losing it because the item vanished doesn't.
        if (!state.hasFocus && focusedKey in currentKeys()) focusedKey = null
    }

    /** Put on each item. */
    fun itemModifier(key: Any, index: Int): Modifier = Modifier
        .focusRequester(requester(key))
        .onFocusChanged {
            if (it.isFocused) {
                focusedKey = key
                focusedIndex = index
            }
        }
}

@Composable
fun rememberTrayFocus(keys: List<Any>): TrayFocus {
    val focus = remember { TrayFocus() }
    val latest by rememberUpdatedState(keys)
    focus.currentKeys = { latest }
    LaunchedEffect(keys) {
        val lost = focus.focusedKey ?: return@LaunchedEffect
        if (lost in keys || keys.isEmpty()) return@LaunchedEffect
        val replacement = keys[focus.focusedIndex.coerceAtMost(keys.lastIndex)]
        runCatching { focus.requester(replacement).requestFocus() }
    }
    return focus
}
