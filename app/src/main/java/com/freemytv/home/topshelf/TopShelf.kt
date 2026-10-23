package com.freemytv.home.topshelf

import com.freemytv.home.upnext.UpNextEntry
import com.freemytv.home.upnext.UpNextState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The app focused in the App Tray. */
data class ShelfApp(val packageName: String, val label: String)

data class TopShelfState(
    /** The app the Top Shelf is about; null while nothing in the App Tray has been focused, or on "Todos os apps". */
    val app: ShelfApp? = null,
    /** That app's Up Next entries, most recent first. */
    val entries: List<UpNextEntry> = emptyList(),
    val index: Int = 0,
) {
    /** What is shown; null means the app's banner, enlarged and blurred. */
    val current: UpNextEntry? get() = entries.getOrNull(index)
}

/**
 * The Top Shelf: what the owner last watched in the app focused in the App Tray, cycling
 * through that app's entries while focus rests. Focus has to settle for [settle] first, so
 * running along the App Tray doesn't load artwork for every app passed.
 */
class TopShelf(
    private val upNext: Flow<UpNextState>,
    private val settle: Duration = 300.milliseconds,
    private val cycle: Duration = 8.seconds,
) {

    private val focused = MutableStateFlow<ShelfApp?>(null)
    private val _state = MutableStateFlow(TopShelfState())
    val state: StateFlow<TopShelfState> = _state.asStateFlow()

    /** The owner moved focus in the App Tray; null for "Todos os apps". */
    fun focus(app: ShelfApp?) {
        focused.value = app
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    suspend fun run() {
        combine(focused.debounce(settle), upNext) { app, upNext ->
            app to upNext.entries.filter { it.packageName == app?.packageName }
        }
            .distinctUntilChanged()
            .collectLatest { (app, entries) ->
                // Positions update while something plays; the entry on screen stays on screen.
                val shown = _state.value.current?.key
                var index = entries.indexOfFirst { it.key == shown }.takeIf { it >= 0 && _state.value.app == app } ?: 0
                _state.value = TopShelfState(app, entries, index)
                while (entries.size > 1) {
                    delay(cycle)
                    index = (index + 1) % entries.size
                    _state.value = TopShelfState(app, entries, index)
                }
            }
    }
}
