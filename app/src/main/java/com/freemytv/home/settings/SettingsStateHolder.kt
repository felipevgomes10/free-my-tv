package com.freemytv.home.settings

import com.freemytv.home.platform.TvPlatform
import com.freemytv.home.upnext.WatchNextRow
import com.freemytv.home.widgets.WidgetPreferences
import com.freemytv.home.widgets.WidgetSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import com.freemytv.home.screensaver.ScreensaverSettings
import com.freemytv.home.screensaver.ScreensaverState

/** The packages behind each kind of Cast on the owner's TV (ADR 0002). */
enum class CastReceiver(val packages: List<String>) {
    Chromecast(listOf("com.google.android.apps.mediashell")),
    AirPlay(listOf("com.tcl.airplay2", "com.amlogic.airplay")),
    Miracast(listOf("com.tcl.miracast")),
}

enum class PackageState { NotInstalled, Disabled, Enabled }

data class CastReceiverStatus(val receiver: CastReceiver, val state: PackageState)

/** What the TV and the Home are. */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val securityPatch: LocalDate?,
    val build: String,
    val homeVersion: String,
)

/** One app's programs in Watch Next, counted: how many, and how many carry a link, progress and a picture. */
data class WatchNextPublisher(val label: String, val entries: Int, val withLink: Int, val withProgress: Int, val withPicture: Int)

data class SettingsState(
    val widgets: WidgetPreferences = WidgetPreferences(),
    /** The name other devices see when they Cast to this TV. */
    val tvName: String? = null,
    val cast: List<CastReceiverStatus> = emptyList(),
    val device: DeviceInfo? = null,
    val screensaver: ScreensaverState = ScreensaverState(),
    /**
     * The Watch Next probe: which apps list what the owner is watching, to decide whether
     * Continuar assistindo should read it. Null without the access Setup grants.
     */
    val watchNext: List<WatchNextPublisher>? = null,
)

class SettingsStateHolder(
    private val platform: TvPlatform,
    private val widgetSettings: WidgetSettings,
    scope: CoroutineScope,
) {

    private val system = MutableStateFlow(SettingsState())
    private val screensaver = ScreensaverSettings(platform)

    val state: StateFlow<SettingsState> = combine(system, widgetSettings.preferences, screensaver.state) { system, widgets, saver -> system.copy(widgets = widgets, screensaver = saver) }
        .stateIn(scope, SharingStarted.Eagerly, SettingsState())

    suspend fun load() {
        screensaver.load()
        system.value = SettingsState(
            tvName = platform.tvName(),
            cast = CastReceiver.entries.map { CastReceiverStatus(it, stateOf(it)) },
            device = platform.deviceInfo(),
            watchNext = platform.watchNextRows()?.let { summarize(it) },
        )
    }

    private suspend fun summarize(rows: List<WatchNextRow>): List<WatchNextPublisher> =
        rows.groupBy { it.packageName }
            .map { (pkg, own) ->
                WatchNextPublisher(
                    label = platform.appLabel(pkg),
                    entries = own.size,
                    withLink = own.count { it.hasLink },
                    withProgress = own.count { it.hasProgress },
                    withPicture = own.count { it.hasPicture },
                )
            }
            .sortedByDescending { it.entries }

    suspend fun setWeatherVisible(visible: Boolean) = widgetSettings.setWeatherVisible(visible)

    suspend fun setMonitorVisible(visible: Boolean) = widgetSettings.setMonitorVisible(visible)

    suspend fun setScreensaverEnabled(enabled: Boolean) = screensaver.setEnabled(enabled)
    suspend fun setScreensaverMinutes(minutes: Int) = screensaver.setMinutes(minutes)

    /** A blank city means "Automático". */
    suspend fun setWeatherCity(city: String?) = widgetSettings.setWeatherCity(city)

    fun openAndroidSettings() = platform.openAndroidSettings()

    /** A receiver works only when every one of its installed packages is enabled. */
    private suspend fun stateOf(receiver: CastReceiver): PackageState {
        val installed = receiver.packages.map { platform.packageState(it) }.filter { it != PackageState.NotInstalled }
        return when {
            installed.isEmpty() -> PackageState.NotInstalled
            installed.any { it == PackageState.Disabled } -> PackageState.Disabled
            else -> PackageState.Enabled
        }
    }
}
