package com.freemytv.home.multitasking

import com.freemytv.home.apps.tileArt
import com.freemytv.home.home.TileArt
import com.freemytv.home.monitor.Importance
import com.freemytv.home.monitor.SystemMonitor
import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** An app the owner has used that is still alive in the background. */
data class OpenApp(
    val packageName: String,
    val label: String,
    val art: TileArt,
    val lastUsed: Instant,
    /** Playing media or otherwise noticeable: never closed by the Open App Limit, never counted. */
    val playing: Boolean,
)

data class SwitcherState(
    /** Null while the App Switcher is closed. */
    val apps: List<OpenApp>? = null,
    /** Where focus starts: the previous app, so Home twice then OK flips between the last two. */
    val initialFocus: Int = 0,
    /** Open Apps that count towards the limit. */
    val counted: Int = 0,
    val limit: Int = OpenAppLimit.DEFAULT,
)

object OpenAppLimit {
    const val DEFAULT = 3
    val RANGE = 2..5

    /** Below this much free memory the least recently used Open App is closed, whatever the count. */
    const val MEMORY_FLOOR_BYTES = 300L * 1024 * 1024
}

/**
 * Open Apps, the App Switcher and the Open App Limit. An Open App is an app with a
 * launcher entry whose process is alive; when the process list isn't available (Setup
 * not run), recently used apps stand in and the limit is not enforced.
 */
class Multitasking(private val platform: TvPlatform) {

    private val _switcher = MutableStateFlow(SwitcherState())
    val switcher: StateFlow<SwitcherState> = _switcher.asStateFlow()

    private val _limit = MutableStateFlow(OpenAppLimit.DEFAULT)
    val limit: StateFlow<Int> = _limit.asStateFlow()

    suspend fun load() {
        _limit.value = platform.loadOpenAppLimit().coerceIn(OpenAppLimit.RANGE)
    }

    suspend fun setLimit(limit: Int) {
        val clamped = limit.coerceIn(OpenAppLimit.RANGE)
        platform.saveOpenAppLimit(clamped)
        _limit.value = clamped
    }

    /** The Open Apps, most recently used first. */
    suspend fun openApps(): List<OpenApp> = snapshot().map { it.app }

    suspend fun openSwitcher() {
        val open = snapshot()
        _switcher.value = SwitcherState(
            apps = open.map { it.app },
            initialFocus = if (open.size > 1) 1 else 0,
            counted = open.count { it.counts },
            limit = _limit.value,
        )
    }

    fun closeSwitcher() {
        _switcher.value = SwitcherState()
    }

    fun switchTo(app: OpenApp) {
        closeSwitcher()
        platform.launch(app.packageName)
    }

    /** Closes an Open App from the App Switcher: it leaves the row and is force stopped. Media playing stays open. */
    fun close(app: OpenApp) {
        if (app.playing) return
        platform.forceStop(app.packageName)
        _switcher.update { state ->
            val apps = state.apps?.filterNot { it.packageName == app.packageName }
            state.copy(apps = apps, counted = apps?.count { !it.playing } ?: 0)
        }
    }

    /**
     * Closes least recently used Open Apps while there are more than the limit or free
     * memory is under the floor. Never the foreground app, media, the Home or Cast Receivers.
     * Returns the apps it closed.
     */
    suspend fun enforceLimit(): List<String> {
        if (platform.processMemory() == null) return emptyList()
        val closed = mutableListOf<String>()
        var open = snapshot()
        while (true) {
            val counted = open.filter { it.counts }
            val lowMemory = platform.memoryUsage().availableBytes < OpenAppLimit.MEMORY_FLOOR_BYTES
            if (counted.size <= _limit.value && !lowMemory) break
            val victim = counted.filter { it.closable }.minByOrNull { it.app.lastUsed } ?: break
            platform.closeApp(victim.app.packageName)
            closed += victim.app.packageName
            open = open - victim
        }
        return closed
    }

    /** Keeps the Open App Limit while the Home is alive, in front or not. */
    suspend fun keepLimit() {
        while (true) {
            enforceLimit()
            delay(CHECK_EVERY)
        }
    }

    private class Entry(val app: OpenApp, val importance: Importance?) {
        val counts get() = !app.playing
        val closable get() = importance == Importance.Background && app.packageName !in SystemMonitor.NEVER_CLOSE
    }

    private suspend fun snapshot(): List<Entry> {
        val launchable = platform.installedApps().associateBy { it.packageName }
        val now = platform.now().toInstant()
        val used = platform.recentUsage(now.minus(USAGE_WINDOW))
        val processes = platform.processMemory()
        val alive: Map<String, Importance?> = if (processes != null) {
            processes.filter { it.importance != Importance.System }.groupBy { it.packageName }.mapValues { (_, procs) -> procs.minOf { it.importance } }
        } else {
            // Without the process list, recently used apps stand in for Open Apps.
            used.keys.associateWith { null }
        }
        return alive
            .filterKeys { it in launchable && it !in SystemMonitor.NEVER_CLOSE }
            .map { (pkg, importance) ->
                val app = launchable.getValue(pkg)
                Entry(
                    OpenApp(pkg, app.label, app.tileArt(), used[pkg] ?: Instant.EPOCH, playing = importance == Importance.Perceptible),
                    importance,
                )
            }
            .sortedByDescending { it.app.lastUsed }
    }

    companion object {
        val CHECK_EVERY = 30.seconds
        val USAGE_WINDOW: Duration = Duration.ofDays(1)
    }
}

/** Two Home presses within [WINDOW_MILLIS] open the App Switcher. */
class HomePresses {
    private var last: Instant? = null

    /** Returns true when this press completes a double press. */
    fun press(at: Instant): Boolean {
        val previous = last
        val double = previous != null && Duration.between(previous, at).toMillis() in 0..WINDOW_MILLIS
        last = if (double) null else at
        return double
    }

    companion object {
        const val WINDOW_MILLIS = 400L
    }
}
