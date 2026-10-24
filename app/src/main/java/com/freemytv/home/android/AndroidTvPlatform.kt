package com.freemytv.home.android

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings
import android.util.Log
import com.freemytv.home.MainActivity
import com.freemytv.home.apkdrop.ApkInfo
import com.freemytv.home.apkdrop.InstallResult
import com.freemytv.home.apkdrop.UploadVerdict
import com.freemytv.home.buttons.ButtonTarget
import com.freemytv.home.explorar.DetailsLookup
import com.freemytv.home.explorar.KindFilter
import com.freemytv.home.explorar.TitleKind
import com.freemytv.home.explorar.TrendWindow
import com.freemytv.home.explorar.TrendingLookup
import com.freemytv.home.buttons.HomeDestination
import com.freemytv.home.buttons.ShortcutButton
import com.freemytv.home.inputs.InputKind
import com.freemytv.home.inputs.InputPreferences
import com.freemytv.home.inputs.Signal
import com.freemytv.home.inputs.TvInputSource
import com.freemytv.home.multitasking.OpenAppLimit
import com.freemytv.home.notifications.TvNotification
import com.freemytv.home.platform.InstalledApp
import com.freemytv.home.platform.PackageEvent
import com.freemytv.home.platform.TvPlatform
import com.freemytv.home.settings.DeviceInfo
import com.freemytv.home.settings.PackageState
import com.freemytv.home.setup.SetupCheck
import com.freemytv.home.updates.InstalledPackage
import com.freemytv.home.updates.ReleaseAsset
import com.freemytv.home.updates.ReleaseLookup
import com.freemytv.home.upnext.UpNextEntry
import com.freemytv.home.weather.Place
import com.freemytv.home.widgets.WidgetPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

class AndroidTvPlatform(private val context: Context) : TvPlatform {

    private val packageManager = context.packageManager
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val mediaSessions = MediaSessions(context)
    private val apkDropServer = ApkDropServer(java.io.File(context.cacheDir, "apkdrop"))
    private val apkInstaller = ApkInstaller(context)
    private val tmdb = Tmdb(context)
    private val gitHub = GitHubReleases({ GitHubEndpoint.api(context) }, java.io.File(context.cacheDir, "updates"))

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

    override suspend fun packageState(packageName: String): PackageState = withContext(Dispatchers.IO) {
        val info = try {
            packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (e: PackageManager.NameNotFoundException) {
            return@withContext PackageState.NotInstalled
        }
        if (info.enabled) PackageState.Enabled else PackageState.Disabled
    }

    override suspend fun tvName(): String? = withContext(Dispatchers.IO) {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
    }

    override suspend fun deviceInfo(): DeviceInfo = withContext(Dispatchers.IO) {
        DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            securityPatch = runCatching { LocalDate.parse(Build.VERSION.SECURITY_PATCH) }.getOrNull(),
            build = Build.DISPLAY,
            homeVersion = packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
        )
    }

    override fun openAndroidSettings() = start(Intent(Settings.ACTION_SETTINGS))

    /** The last time each app came to the front, from usage events (needs the usage access Setup grants). */
    override suspend fun recentUsage(since: Instant): Map<String, Instant> = withContext(Dispatchers.IO) {
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val events = runCatching { usage.queryEvents(since.toEpochMilli(), System.currentTimeMillis()) }.getOrNull() ?: return@withContext emptyMap()
        val last = HashMap<String, Instant>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last[event.packageName] = Instant.ofEpochMilli(event.timeStamp)
        }
        last
    }

    override suspend fun loadOpenAppLimit(): Int = withContext(Dispatchers.IO) { prefs.getInt(OPEN_APP_LIMIT, OpenAppLimit.DEFAULT) }

    override suspend fun saveOpenAppLimit(limit: Int) = withContext(Dispatchers.IO) { prefs.edit().putInt(OPEN_APP_LIMIT, limit).apply() }

    override suspend fun loadButtonAssignments(): Map<ShortcutButton, ButtonTarget> = withContext(Dispatchers.IO) {
        val json = prefs.getString(BUTTON_ASSIGNMENTS, null)?.let(::JSONObject) ?: return@withContext emptyMap()
        json.keys().asSequence().mapNotNull { name ->
            val button = runCatching { ShortcutButton.valueOf(name) }.getOrNull() ?: return@mapNotNull null
            val value = json.getString(name)
            val target = when {
                value == "switcher" -> ButtonTarget.AppSwitcher
                value == "inputs" -> ButtonTarget.InputTray
                value == "settings" -> ButtonTarget.HomeSettings
                value.startsWith("app:") -> ButtonTarget.App(value.removePrefix("app:"))
                value.startsWith("input:") -> ButtonTarget.TvInput(value.removePrefix("input:"))
                else -> return@mapNotNull null
            }
            button to target
        }.toMap()
    }

    override suspend fun saveButtonAssignments(assignments: Map<ShortcutButton, ButtonTarget>) = withContext(Dispatchers.IO) {
        val json = JSONObject()
        assignments.forEach { (button, target) ->
            json.put(
                button.name,
                when (target) {
                    ButtonTarget.AppSwitcher -> "switcher"
                    ButtonTarget.InputTray -> "inputs"
                    ButtonTarget.HomeSettings -> "settings"
                    is ButtonTarget.App -> "app:${target.packageName}"
                    is ButtonTarget.TvInput -> "input:${target.inputId}"
                },
            )
        }
        prefs.edit().putString(BUTTON_ASSIGNMENTS, json.toString()).apply()
    }

    override fun isLaunchable(packageName: String): Boolean =
        packageManager.getLeanbackLaunchIntentForPackage(packageName) != null || packageManager.getLaunchIntentForPackage(packageName) != null

    override fun switchToInputById(inputId: String) {
        val passthrough = runCatching { tvInputManager?.getTvInputInfo(inputId)?.isPassthroughInput }.getOrNull() ?: true
        switchToInput(inputId, passthrough)
    }

    override fun openHome(destination: HomeDestination) {
        start(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DESTINATION, destination.name))
    }

    override fun isKeyFilterEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == keyFilter }
    }

    override fun enableKeyFilter(): Boolean {
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return false
        val resolver = context.contentResolver
        val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val services = (enabled.split(':').filter { it.isNotBlank() } + keyFilter.flattenToString()).distinct()
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services.joinToString(":"))
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        return true
    }

    private val keyFilter = ComponentName(context, KeyFilterService::class.java)

    override suspend fun setupStatus(): Map<SetupCheck, Boolean> = withContext(Dispatchers.IO) {
        fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val notifications = context.getSystemService(NotificationManager::class.java)
        mapOf(
            SetupCheck.StockLauncherDisabled to STOCK_HOME.all { packageState(it) != PackageState.Enabled },
            SetupCheck.ProcessAccess to (granted(android.Manifest.permission.DUMP) && granted(android.Manifest.permission.PACKAGE_USAGE_STATS)),
            SetupCheck.UsageAccess to system.hasUsageAccess(),
            SetupCheck.NotificationAccess to notifications.isNotificationListenerAccessGranted(ComponentName(context, HomeNotificationListener::class.java)),
            SetupCheck.KeyFilter to (granted(android.Manifest.permission.WRITE_SECURE_SETTINGS) && isKeyFilterEnabled()),
            SetupCheck.InstallApps to context.packageManager.canRequestPackageInstalls(),
            SetupCheck.Explorar to (TmdbToken.read(context) != null),
        )
    }

    override suspend fun loadSetupHistory() = loadChecks(SETUP_HISTORY)

    override suspend fun saveSetupHistory(held: Set<SetupCheck>) = saveChecks(SETUP_HISTORY, held)

    override suspend fun loadAnnouncedRegressions() = loadChecks(SETUP_ANNOUNCED)

    override suspend fun saveAnnouncedRegressions(lost: Set<SetupCheck>) = saveChecks(SETUP_ANNOUNCED, lost)

    override fun postSetupNotification(check: SetupCheck) = SetupNotifications.post(context, check)

    override fun cancelSetupNotification(check: SetupCheck) = SetupNotifications.cancel(context, check)

    private suspend fun loadChecks(key: String): Set<SetupCheck> = withContext(Dispatchers.IO) {
        prefs.getString(key, null).orEmpty().split(SEPARATOR).mapNotNull { name -> SetupCheck.entries.firstOrNull { it.name == name } }.toSet()
    }

    private suspend fun saveChecks(key: String, checks: Set<SetupCheck>) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, checks.joinToString(SEPARATOR) { it.name }).apply()
    }

    override val notificationAccess: Flow<Boolean> = HomeNotificationListener.isConnected

    override val activeNotifications: Flow<List<TvNotification>> = HomeNotificationListener.notifications

    override fun openNotification(key: String) = HomeNotificationListener.open(key)

    override fun clearNotifications() = HomeNotificationListener.clearAll()

    override suspend fun installedPackages(): List<InstalledPackage> = withContext(Dispatchers.IO) {
        packageManager.getInstalledPackages(0).map { info ->
            val app = info.applicationInfo
            InstalledPackage(
                packageName = info.packageName,
                label = app?.loadLabel(packageManager)?.toString() ?: info.packageName,
                version = info.versionName.orEmpty(),
                installer = runCatching { packageManager.getInstallSourceInfo(info.packageName).installingPackageName }.getOrNull(),
                isSystem = app != null && app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            )
        }
    }

    override fun openSystemUpdater() {
        if (!tryStart(Intent(SYSTEM_UPDATE_SETTINGS))) start(Intent(Settings.ACTION_SETTINGS))
    }

    override fun openPlayStoreApps() {
        if (!tryStart(Intent(PLAY_STORE_MY_APPS).setPackage(PLAY_STORE))) launch(PLAY_STORE)
    }

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

    override fun openYouTubeVideo(key: String, startSeconds: Int) {
        val start = if (startSeconds > 0) "&t=$startSeconds" else ""
        val video = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key$start")).setPackage(YOUTUBE)
        if (!tryStart(video)) openPlayStorePage(YOUTUBE)
    }

    override fun openPlayStorePage(packageName: String) {
        if (!tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).setPackage(PLAY_STORE))) openPlayStore()
    }

    override val nowPlaying = mediaSessions.nowPlaying

    override suspend fun localAddress(): String? = withContext(Dispatchers.IO) {
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        runCatching { connectivity.getLinkProperties(connectivity.activeNetwork) }.getOrNull()?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    override fun receiveApks(port: Int, verdict: (code: String, sizeBytes: Long) -> UploadVerdict): Flow<String> = apkDropServer.serve(port, verdict)

    override suspend fun inspectApk(path: String): ApkInfo? = apkInstaller.inspect(path)

    override suspend fun installApk(path: String, silent: Boolean): InstallResult = apkInstaller.install(path, silent)

    override suspend fun deleteFile(path: String) {
        withContext(Dispatchers.IO) { java.io.File(path).delete() }
    }

    override suspend fun trending(window: TrendWindow, filter: KindFilter): TrendingLookup = tmdb.trending(window, filter)

    override suspend fun titleDetails(kind: TitleKind, id: Int): DetailsLookup = tmdb.details(kind, id)

    override suspend fun latestRelease(repository: String): ReleaseLookup = gitHub.latest(repository)

    override suspend fun download(asset: ReleaseAsset): String? = gitHub.download(asset)

    override suspend fun loadUpdateSources(): Map<String, String> = withContext(Dispatchers.IO) {
        val json = prefs.getString(UPDATE_SOURCES, null)?.let(::JSONObject) ?: return@withContext emptyMap()
        json.keys().asSequence().associateWith { json.getString(it) }
    }

    override suspend fun saveUpdateSources(sources: Map<String, String>) = withContext(Dispatchers.IO) {
        prefs.edit().putString(UPDATE_SOURCES, JSONObject(sources).toString()).apply()
    }

    override suspend fun deleteArtwork(path: String) {
        withContext(Dispatchers.IO) { mediaSessions.deleteArtwork(path) }
    }

    override fun resumeSession(packageName: String, title: String?): Boolean = mediaSessions.resume(packageName, title)

    override fun openYouTubeSearch(query: String) {
        // Spaces as %20: YouTube's TV app shows a "+" literally.
        val encoded = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val search = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).setPackage(YOUTUBE)
        if (!tryStart(search)) openPlayStorePage(YOUTUBE)
    }

    override suspend fun loadUpNext(): List<UpNextEntry> = withContext(Dispatchers.IO) {
        val json = prefs.getString(UP_NEXT, null)?.let(::JSONArray) ?: return@withContext emptyList()
        List(json.length()) { json.getJSONObject(it) }.map { o ->
            UpNextEntry(
                key = o.getString("key"),
                packageName = o.getString("package"),
                appLabel = o.getString("label"),
                title = o.optStringOrNull("title"),
                subtitle = o.optStringOrNull("subtitle"),
                positionMillis = o.optLongOrNull("position"),
                durationMillis = o.optLongOrNull("duration"),
                artwork = o.optStringOrNull("artwork"),
                lastWatched = Instant.ofEpochMilli(o.getLong("watched")),
            )
        }
    }

    override suspend fun saveUpNext(entries: List<UpNextEntry>) = withContext(Dispatchers.IO) {
        val json = JSONArray(entries.map { e ->
            JSONObject()
                .put("key", e.key)
                .put("package", e.packageName)
                .put("label", e.appLabel)
                .put("title", e.title)
                .put("subtitle", e.subtitle)
                .put("position", e.positionMillis)
                .put("duration", e.durationMillis)
                .put("artwork", e.artwork)
                .put("watched", e.lastWatched.toEpochMilli())
        })
        prefs.edit().putString(UP_NEXT, json.toString()).apply()
    }

    private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name)

    private fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name)) null else optLong(name)

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

    private fun tryStart(intent: Intent): Boolean = try {
        start(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private companion object {
        const val TAG = "AndroidTvPlatform"
        const val PINNED_APPS = "pinned_apps"
        const val INPUT_PREFERENCES = "input_preferences"
        const val WEATHER_VISIBLE = "weather_visible"
        const val MONITOR_VISIBLE = "monitor_visible"
        const val WEATHER_CITY = "weather_city"
        const val OPEN_APP_LIMIT = "open_app_limit"
        const val BUTTON_ASSIGNMENTS = "button_assignments"
        const val SETUP_HISTORY = "setup_history"
        const val SETUP_ANNOUNCED = "setup_announced"
        const val UP_NEXT = "up_next"
        const val UPDATE_SOURCES = "update_sources"
        /** The Stock Launcher and Google TV's setup app, whose recovery screen takes Home over without it. */
        val STOCK_HOME = listOf("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith")
        const val SEPARATOR = ","
        const val PLAY_STORE = "com.android.vending"
        const val YOUTUBE = "com.google.android.youtube.tv"
        const val PLAY_STORE_MY_APPS = "com.google.android.finsky.VIEW_MY_DOWNLOADS"
        const val SYSTEM_UPDATE_SETTINGS = "android.settings.SYSTEM_UPDATE_SETTINGS"
    }
}
