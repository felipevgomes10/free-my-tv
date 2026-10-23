package com.freemytv.home.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings
import android.util.Log
import com.freemytv.home.inputs.InputKind
import com.freemytv.home.inputs.InputPreferences
import com.freemytv.home.inputs.Signal
import com.freemytv.home.inputs.TvInputSource
import com.freemytv.home.platform.InstalledApp
import com.freemytv.home.platform.PackageEvent
import com.freemytv.home.platform.TvPlatform
import com.freemytv.home.weather.Place
import com.freemytv.home.widgets.WidgetPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

class AndroidTvPlatform(private val context: Context) : TvPlatform {

    private val packageManager = context.packageManager
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    override suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(packageManager).toString(),
                    hasBanner = info.banner != 0 || info.applicationInfo.banner != 0,
                )
            }
    }

    // LauncherApps reports an app update as a change, never as a removal, so updates don't unpin apps.
    override val packageEvents: Flow<PackageEvent> = callbackFlow {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                trySend(PackageEvent.Installed(packageName))
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                trySend(PackageEvent.Changed(packageName))
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                trySend(PackageEvent.Removed(packageName))
            }

            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                packageNames.forEach { trySend(PackageEvent.Changed(it)) }
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                packageNames.forEach { trySend(PackageEvent.Changed(it)) }
            }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    private val weather = WeatherService(prefs)
    private val system = SystemReader(context)

    override suspend fun storageUsage() = system.storageUsage()

    override suspend fun memoryUsage() = system.memoryUsage()

    override suspend fun appStorage() = system.appStorage()

    override suspend fun processMemory() = system.processMemory()

    override suspend fun appLabel(packageName: String) = withContext(Dispatchers.IO) { system.appLabel(packageName) }

    override fun closeApp(packageName: String) = system.closeApp(packageName)

    override val homeFrameRate = system.homeFrameRate

    override fun displayRefreshRate() = system.displayRefreshRate()

    override suspend fun loadWidgetPreferences(): WidgetPreferences = withContext(Dispatchers.IO) {
        WidgetPreferences(
            weatherVisible = prefs.getBoolean(WEATHER_VISIBLE, true),
            monitorVisible = prefs.getBoolean(MONITOR_VISIBLE, true),
            weatherCity = prefs.getString(WEATHER_CITY, null),
        )
    }

    override suspend fun saveWidgetPreferences(preferences: WidgetPreferences) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putBoolean(WEATHER_VISIBLE, preferences.weatherVisible)
            .putBoolean(MONITOR_VISIBLE, preferences.monitorVisible)
            .putString(WEATHER_CITY, preferences.weatherCity)
            .apply()
    }

    override suspend fun locateByIp() = weather.locateByIp()

    override suspend fun geocode(city: String) = weather.geocode(city)

    override suspend fun forecast(place: Place) = weather.forecast(place)

    override suspend fun lastForecast() = weather.lastForecast()

    private val tvInputManager: TvInputManager? = context.getSystemService(TvInputManager::class.java)

    override suspend fun tvInputs(): List<TvInputSource> = withContext(Dispatchers.IO) {
        DemoInputs.sources(context) ?: tvInputManager?.tvInputList.orEmpty().map { info ->
            TvInputSource(
                id = info.id,
                ownerPackage = info.serviceInfo.packageName,
                kind = when (info.type) {
                    TvInputInfo.TYPE_HDMI -> InputKind.Hdmi
                    TvInputInfo.TYPE_TUNER -> InputKind.Tuner
                    else -> InputKind.Other
                },
                isPassthrough = info.isPassthroughInput,
                parentId = info.parentId,
                label = info.loadLabel(context).toString(),
                signal = when (tvInputManager?.getInputState(info.id)) {
                    TvInputManager.INPUT_STATE_DISCONNECTED -> Signal.Disconnected
                    TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> Signal.Standby
                    else -> Signal.Connected
                },
            )
        }
    }

    override val inputChanges: Flow<Unit> = callbackFlow {
        val manager = tvInputManager
        if (manager == null) {
            awaitClose()
            return@callbackFlow
        }
        val callback = object : TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(inputId: String, state: Int) {
                trySend(Unit)
            }

            override fun onInputAdded(inputId: String) {
                trySend(Unit)
            }

            override fun onInputRemoved(inputId: String) {
                trySend(Unit)
            }

            override fun onInputUpdated(inputId: String) {
                trySend(Unit)
            }
        }
        manager.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { manager.unregisterCallback(callback) }
    }

    override fun switchToInput(inputId: String, isPassthrough: Boolean) {
        val uri = if (isPassthrough) TvContract.buildChannelUriForPassthroughInput(inputId) else TvContract.buildChannelsUriForInput(inputId)
        try {
            start(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No TV player can show input $inputId", e)
        }
    }

    override suspend fun loadInputPreferences(): InputPreferences = withContext(Dispatchers.IO) {
        val json = prefs.getString(INPUT_PREFERENCES, null)?.let(::JSONObject) ?: return@withContext InputPreferences()
        InputPreferences(
            order = json.getJSONArray("order").strings(),
            names = json.getJSONObject("names").let { names -> names.keys().asSequence().associateWith { names.getString(it) } },
            hidden = json.getJSONArray("hidden").strings().toSet(),
        )
    }

    override suspend fun saveInputPreferences(preferences: InputPreferences) = withContext(Dispatchers.IO) {
        val json = JSONObject()
            .put("order", JSONArray(preferences.order))
            .put("names", JSONObject(preferences.names))
            .put("hidden", JSONArray(preferences.hidden.toList()))
        prefs.edit().putString(INPUT_PREFERENCES, json.toString()).apply()
    }

    override fun launch(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        start(intent)
    }

    override fun requestUninstall(packageName: String) {
        start(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)))
    }

    override fun openAppInfo(packageName: String) {
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    override fun openPlayStore() = launch(PLAY_STORE)

    override suspend fun loadPinnedApps(): List<String>? = withContext(Dispatchers.IO) {
        prefs.getString(PINNED_APPS, null)?.let { saved -> saved.split(SEPARATOR).filter { it.isNotEmpty() } }
    }

    override suspend fun savePinnedApps(packageNames: List<String>) = withContext(Dispatchers.IO) {
        prefs.edit().putString(PINNED_APPS, packageNames.joinToString(SEPARATOR)).apply()
    }

    override fun now(): ZonedDateTime = ZonedDateTime.now()

    private fun JSONArray.strings() = List(length()) { getString(it) }

    private fun start(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val TAG = "AndroidTvPlatform"
        const val PINNED_APPS = "pinned_apps"
        const val INPUT_PREFERENCES = "input_preferences"
        const val WEATHER_VISIBLE = "weather_visible"
        const val MONITOR_VISIBLE = "monitor_visible"
        const val WEATHER_CITY = "weather_city"
        const val SEPARATOR = ","
        const val PLAY_STORE = "com.android.vending"
    }
}
