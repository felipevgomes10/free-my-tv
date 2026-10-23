package com.freemytv.home.monitor

import com.freemytv.home.platform.TvPlatform
import com.freemytv.home.widgets.WidgetSettings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class MonitorState(
    /** False for a Hidden Widget. */
    val visible: Boolean = true,
    val storage: StorageUsage? = null,
    val memory: MemoryUsage? = null,
    /** How many frames per second the Home itself is drawing. */
    val homeFps: Int? = null,
    val refreshRate: Int? = null,
    /** Null unless the detail screen is open. */
    val detail: MonitorDetail? = null,
)

data class MonitorDetail(
    /** The five apps using the most storage; null when the Home lacks the access Setup grants. */
    val topStorage: List<AppUsage>?,
    /** The five apps using the most memory; null when the Home lacks the access Setup grants. */
    val topMemory: List<AppUsage>?,
    /** The Home's frame rate, one reading per second, oldest first, at most 60. */
    val fpsHistory: List<Int>,
)

data class AppUsage(
    val packageName: String,
    val label: String,
    val bytes: Long,
    /** Uninstall is offered (storage list). */
    val canUninstall: Boolean = false,
    /** Close is offered (memory list): a background app the owner launched, never the foreground or one playing media. */
    val canClose: Boolean = false,
)

/**
 * The System Monitor Widget. It measures only while visible and the Home is in front;
 * a Hidden Widget takes no measurements at all. The detail screen adds per-app
 * rankings and the last minute of frame rates, measured only while it is open.
 */
class SystemMonitor(private val platform: TvPlatform, private val settings: WidgetSettings) {

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val detailOpen = MutableStateFlow(false)

    suspend fun follow() {
        combine(settings.preferences.map { it.monitorVisible }.distinctUntilChanged(), detailOpen) { visible, open -> visible to open }
            .collectLatest { (visible, open) ->
                if (!visible) {
                    _state.value = MonitorState(visible = false)
                    return@collectLatest
                }
                _state.update { it.copy(visible = true, refreshRate = platform.displayRefreshRate(), detail = if (open) it.detail ?: MonitorDetail(null, null, emptyList()) else null) }
                coroutineScope {
                    launch {
                        platform.homeFrameRate.collect { fps ->
                            _state.update { s -> s.copy(homeFps = fps, detail = s.detail?.let { d -> d.copy(fpsHistory = (d.fpsHistory + fps).takeLast(HISTORY)) }) }
                        }
                    }
                    while (true) {
                        // Measure first, then write: the frame-rate readings update the state every second,
                        // and a write that measures inside update() would keep being retried (the owner's TV
                        // takes over a second to rank ~200 packages).
                        val storage = platform.storageUsage()
                        val memory = platform.memoryUsage()
                        _state.update { it.copy(storage = storage, memory = memory) }
                        if (open) {
                            val topStorage = topStorage()
                            val topMemory = topMemory()
                            _state.update { it.copy(detail = it.detail?.copy(topStorage = topStorage, topMemory = topMemory)) }
                        }
                        delay(if (open) DETAIL_EVERY else COMPACT_EVERY)
                    }
                }
            }
    }

    fun openDetail() {
        detailOpen.value = true
    }

    fun closeDetail() {
        detailOpen.value = false
    }

    /** Ends a background app's processes; Android won't touch the foreground app this way. */
    suspend fun close(app: AppUsage) {
        if (!app.canClose) return
        platform.closeApp(app.packageName)
        val topMemory = topMemory()
        _state.update { it.copy(detail = it.detail?.copy(topMemory = topMemory)) }
    }

    private suspend fun topStorage(): List<AppUsage>? = platform.appStorage()
        ?.sortedByDescending { it.bytes }
        ?.take(TOP)
        ?.map { AppUsage(it.packageName, platform.appLabel(it.packageName), it.bytes, canUninstall = it.removable) }

    private suspend fun topMemory(): List<AppUsage>? {
        val processes = platform.processMemory() ?: return null
        val launchable = platform.installedApps().map { it.packageName }.toSet()
        return processes
            .filter { it.importance != Importance.System }
            .groupBy { it.packageName }
            .map { (pkg, procs) ->
                // An app is as important as its most important process: music playing in a service keeps the whole app.
                val importance = procs.minOf { it.importance }
                AppUsage(
                    packageName = pkg,
                    label = platform.appLabel(pkg),
                    bytes = procs.sumOf { it.bytes },
                    canClose = importance == Importance.Background && pkg in launchable && pkg !in NEVER_CLOSE,
                )
            }
            .sortedByDescending { it.bytes }
            .take(TOP)
    }

    companion object {
        const val TOP = 5
        const val HISTORY = 60
        val COMPACT_EVERY = 5.seconds
        val DETAIL_EVERY = 3.seconds

        /** Cast Receivers and the Home itself are never closed. */
        val NEVER_CLOSE = setOf(
            "com.freemytv.home",
            "com.google.android.apps.mediashell",
            "com.tcl.airplay2",
            "com.amlogic.airplay",
            "com.tcl.miracast",
        )
    }
}
