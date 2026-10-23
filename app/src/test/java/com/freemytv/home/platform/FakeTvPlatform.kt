package com.freemytv.home.platform

import com.freemytv.home.inputs.InputPreferences
import com.freemytv.home.inputs.TvInputSource
import com.freemytv.home.weather.CachedForecast
import com.freemytv.home.weather.Forecast
import com.freemytv.home.weather.Place
import com.freemytv.home.widgets.WidgetPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.ZoneId
import java.time.ZonedDateTime

class FakeTvPlatform(
    var apps: List<InstalledApp> = emptyList(),
    var pinned: List<String>? = null,
    var inputs: List<TvInputSource> = emptyList(),
    var inputPreferences: InputPreferences = InputPreferences(),
    var widgetPreferences: WidgetPreferences = WidgetPreferences(),
    /** Where the TV's IP says it is; null simulates being offline. */
    var ipPlace: Place? = null,
    /** City name to place; a missing city is not found. */
    var cities: Map<String, Place> = emptyMap(),
    /** Forecast per place name; a missing place simulates being offline. */
    var forecasts: Map<String, Forecast> = emptyMap(),
    var cached: CachedForecast? = null,
    var clock: ZonedDateTime = ZonedDateTime.of(2026, 10, 23, 21, 40, 0, 0, ZoneId.of("America/Sao_Paulo")),
) : TvPlatform {

    val launched = mutableListOf<String>()
    val uninstallRequests = mutableListOf<String>()
    val appInfoOpened = mutableListOf<String>()
    var playStoreOpened = 0
    val switchedTo = mutableListOf<String>()

    /** Every network request the Home made, in order: "ip", "geocode:<city>", "forecast:<place>". */
    val networkRequests = mutableListOf<String>()

    override suspend fun loadWidgetPreferences() = widgetPreferences

    override suspend fun saveWidgetPreferences(preferences: WidgetPreferences) {
        widgetPreferences = preferences
    }

    override suspend fun locateByIp(): Place? {
        networkRequests += "ip"
        return ipPlace
    }

    override suspend fun geocode(city: String): Place? {
        networkRequests += "geocode:$city"
        return cities[city]
    }

    override suspend fun forecast(place: Place): Forecast? {
        networkRequests += "forecast:${place.name}"
        return forecasts[place.name]?.also { cached = CachedForecast(place, it) }
    }

    override suspend fun lastForecast() = cached

    override val inputChanges = MutableSharedFlow<Unit>()

    override suspend fun tvInputs() = inputs

    override fun switchToInput(inputId: String, isPassthrough: Boolean) {
        switchedTo += inputId
    }

    override suspend fun loadInputPreferences() = inputPreferences

    override suspend fun saveInputPreferences(preferences: InputPreferences) {
        inputPreferences = preferences
    }

    /** Something was plugged in, unplugged or switched on. */
    suspend fun changeInputs(newInputs: List<TvInputSource>) {
        inputs = newInputs
        inputChanges.emit(Unit)
    }

    override val packageEvents = MutableSharedFlow<PackageEvent>()

    override suspend fun installedApps() = apps

    override fun launch(packageName: String) {
        launched += packageName
    }

    override fun requestUninstall(packageName: String) {
        uninstallRequests += packageName
    }

    override fun openAppInfo(packageName: String) {
        appInfoOpened += packageName
    }

    override fun openPlayStore() {
        playStoreOpened++
    }

    override suspend fun loadPinnedApps() = pinned

    override suspend fun savePinnedApps(packageNames: List<String>) {
        pinned = packageNames
    }

    override fun now() = clock

    /** The owner confirmed Android's uninstall dialog. */
    suspend fun completeUninstall(packageName: String) {
        apps = apps.filterNot { it.packageName == packageName }
        packageEvents.emit(PackageEvent.Removed(packageName))
    }

    /** An app arrived from the Play Store or an APK. */
    suspend fun install(app: InstalledApp) {
        apps = apps + app
        packageEvents.emit(PackageEvent.Installed(app.packageName))
    }
}

fun app(packageName: String, label: String = packageName, hasBanner: Boolean = true) =
    InstalledApp(packageName, label, hasBanner)
