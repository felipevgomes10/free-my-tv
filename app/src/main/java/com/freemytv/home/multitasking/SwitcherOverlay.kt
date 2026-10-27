package com.freemytv.home.multitasking

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OverlayResult { Shown, Unavailable, Busy, Error }

data class OverlayState(val switcher: SwitcherState, val underneath: String?) {
    // Without a known underlying surface, closing is withheld rather than risking that app.
    fun canClose(app: OpenApp) = underneath != null && app.packageName != underneath && !app.playing && app.canClose
}

/** The overlay has its own presentation state; the full-screen switcher is left alone. */
class SwitcherOverlay(private val platform: TvPlatform, private val multitasking: Multitasking) {
    private val _state = MutableStateFlow<OverlayState?>(null)
    val state = _state.asStateFlow()

    suspend fun open(underneath: String?) {
        if (_state.value != null) return
        _state.value = OverlayState(multitasking.switcherSnapshot(), underneath)
    }

    fun dismiss() { _state.value = null }

    suspend fun refresh() {
        val current = _state.value ?: return
        val snapshot = multitasking.switcherSnapshot()
        if (_state.value === current) _state.value = current.copy(switcher = snapshot)
    }

    suspend fun select(packageName: String) {
        val current = _state.value ?: return
        val apps = multitasking.openApps()
        if (_state.value !== current) return
        if (apps.none { it.packageName == packageName }) {
            refresh()
            return
        }
        dismiss()
        if (packageName != current.underneath) platform.launch(packageName)
    }

    suspend fun close(packageName: String) {
        val current = _state.value ?: return
        val app = multitasking.openApps().firstOrNull { it.packageName == packageName } ?: return refresh()
        if (_state.value !== current) return
        if (!current.canClose(app)) return
        val remaining = current.switcher.apps.orEmpty().filterNot { it.packageName == packageName }
        _state.value = current.copy(switcher = current.switcher.copy(apps = remaining, counted = remaining.count { !it.playing }))
        // The Android adapter coordinates the Settings return path while the overlay is covered.
        platform.forceStop(packageName)
    }
}
