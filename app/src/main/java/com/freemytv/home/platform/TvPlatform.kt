package com.freemytv.home.platform

import com.freemytv.home.inputs.InputPreferences
import com.freemytv.home.inputs.TvInputSource
import com.freemytv.home.weather.CachedForecast
import com.freemytv.home.weather.Forecast
import com.freemytv.home.weather.Place
import com.freemytv.home.widgets.WidgetPreferences
import kotlinx.coroutines.flow.Flow
import java.time.ZonedDateTime

/**
 * The single seam between the Home and Android. Every decision lives on the Home's
 * side of this interface; implementations only translate to and from the system.
 */
interface TvPlatform {

    /** Launchable TV apps currently installed, excluding the Home itself. */
    suspend fun installedApps(): List<InstalledApp>

    /**
     * Apps being installed or uninstalled. An app update is neither: it never
     * reports [PackageEvent.Removed].
     */
    val packageEvents: Flow<PackageEvent>

    fun launch(packageName: String)

    /** Asks Android to uninstall, which shows its own confirmation; completion arrives as [PackageEvent.Removed]. */
    fun requestUninstall(packageName: String)

    fun openAppInfo(packageName: String)

    fun openPlayStore()

    /** Every TV input Android publishes, physical or not; the Input Tray decides which are Inputs. */
    suspend fun tvInputs(): List<TvInputSource>

    /** Fires whenever an input appears, disappears or changes signal. */
    val inputChanges: Flow<Unit>

    fun switchToInput(inputId: String, isPassthrough: Boolean)

    suspend fun loadInputPreferences(): InputPreferences

    suspend fun saveInputPreferences(preferences: InputPreferences)

    suspend fun loadWidgetPreferences(): WidgetPreferences

    suspend fun saveWidgetPreferences(preferences: WidgetPreferences)

    /** Where the TV is, from its public IP; null when offline or unknown. */
    suspend fun locateByIp(): Place?

    /** The place for a city name; null when offline or not found. */
    suspend fun geocode(city: String): Place?

    /** Current conditions plus hourly and daily forecasts; null when offline. Each success is kept as [lastForecast]. */
    suspend fun forecast(place: Place): Forecast?

    suspend fun lastForecast(): CachedForecast?

    /** The owner's pinned apps in tray order, or null if the owner has never had a tray. */
    suspend fun loadPinnedApps(): List<String>?

    suspend fun savePinnedApps(packageNames: List<String>)

    fun now(): ZonedDateTime
}

data class InstalledApp(
    val packageName: String,
    val label: String,
    /** Whether the app supplies a 16:9 App Banner for TV home screens. */
    val hasBanner: Boolean,
)

sealed interface PackageEvent {
    val packageName: String

    data class Installed(override val packageName: String) : PackageEvent

    /** Updated, enabled or disabled. */
    data class Changed(override val packageName: String) : PackageEvent
    data class Removed(override val packageName: String) : PackageEvent
}
