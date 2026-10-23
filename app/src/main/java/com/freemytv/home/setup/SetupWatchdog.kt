package com.freemytv.home.setup

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.minutes

/** What Setup puts in place on the TV; each can be lost, e.g. to a system update (ADR 0003). */
enum class SetupCheck {
    /** The Stock Launcher is disabled, so Home opens the Home. */
    StockLauncherDisabled,

    /** DUMP and PACKAGE_USAGE_STATS: the System Monitor rankings and the Open App Limit. */
    ProcessAccess,

    /** The usage-stats app-op: Open Apps' order and per-app storage. */
    UsageAccess,

    /** The notification listener: the Notification Panel and Banner. */
    NotificationAccess,

    /** WRITE_SECURE_SETTINGS and the key filter: Shortcut Buttons and long-press Home. */
    KeyFilter,
}

data class SetupState(
    /** Each check and whether it holds right now; empty until the first check. */
    val checks: Map<SetupCheck, Boolean> = emptyMap(),
    /** False until any check has ever held: Setup was never run on this TV. */
    val everSetUp: Boolean = false,
) {
    val active get() = checks.isNotEmpty() && checks.values.all { it }
    val missing get() = checks.filterValues { !it }.keys
}

/**
 * Watches that Setup is still in effect. When something that held before is lost, the owner
 * gets one notification for it, not one per check; Settings → Preparação shows the details.
 */
class SetupWatchdog(private val platform: TvPlatform) {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    suspend fun check() {
        val now = platform.setupStatus()
        val heldBefore = platform.loadSetupHistory()
        val announced = platform.loadAnnouncedRegressions()
        val lost = now.filter { (check, holds) -> !holds && check in heldBefore }.keys
        (lost - announced).forEach { platform.postSetupNotification(it) }
        // A check that holds again may be announced again if it's lost later.
        platform.saveAnnouncedRegressions(lost)
        val held = heldBefore + now.filterValues { it }.keys
        platform.saveSetupHistory(held)
        _state.value = SetupState(now, everSetUp = held.isNotEmpty())
    }

    suspend fun keepWatching() {
        while (true) {
            check()
            delay(CHECK_EVERY)
        }
    }

    companion object {
        val CHECK_EVERY = 15.minutes
    }
}
