package com.freemytv.home.platform

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
import com.freemytv.home.inputs.InputPreferences
import com.freemytv.home.inputs.TvInputSource
import com.freemytv.home.monitor.AppStorage
import com.freemytv.home.monitor.MemoryUsage
import com.freemytv.home.monitor.ProcessMemory
import com.freemytv.home.monitor.StorageUsage
import com.freemytv.home.settings.DeviceInfo
import com.freemytv.home.setup.SetupCheck
import com.freemytv.home.settings.PackageState
import com.freemytv.home.updates.InstalledPackage
import com.freemytv.home.updates.ReleaseAsset
import com.freemytv.home.updates.ReleaseLookup
import com.freemytv.home.weather.CachedForecast
import com.freemytv.home.weather.Forecast
import com.freemytv.home.weather.Place
import com.freemytv.home.widgets.WidgetPreferences
import com.freemytv.home.notifications.TvNotification
import com.freemytv.home.upnext.PlayingMedia
import com.freemytv.home.upnext.UpNextEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate
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
    var storage: StorageUsage = StorageUsage(totalBytes = 8_200_000_000, freeBytes = 3_600_000_000),
    var memory: MemoryUsage = MemoryUsage(totalBytes = 2_000_000_000, availableBytes = 580_000_000),
    /** Null simulates missing usage access. */
    var storagePerApp: List<AppStorage>? = emptyList(),
    /** Null simulates missing dump access. */
    var processes: List<ProcessMemory>? = emptyList(),
    var labels: Map<String, String> = emptyMap(),
    var refreshRate: Int = 60,
    var packageStates: Map<String, PackageState> = emptyMap(),
    var name: String? = "TCL Smart TV Pro",
    var packages: List<InstalledPackage> = emptyList(),
    var lastUsed: Map<String, Instant> = emptyMap(),
    var openAppLimit: Int = 3,
    var buttonAssignments: Map<ShortcutButton, ButtonTarget> = emptyMap(),
    var keyFilterEnabled: Boolean = true,
    var canWriteSecureSettings: Boolean = true,
    var setup: Map<SetupCheck, Boolean> = SetupCheck.entries.associateWith { true },
    var setupHistory: Set<SetupCheck> = emptySet(),
    var announcedRegressions: Set<SetupCheck> = emptySet(),
    var device: DeviceInfo = DeviceInfo("TCL", "Smart TV Pro", "12", LocalDate.of(2025, 12, 5), "STT2.230203.001.AS24", "0.1.0"),
    var clock: ZonedDateTime = ZonedDateTime.of(2026, 10, 23, 21, 40, 0, 0, ZoneId.of("America/Sao_Paulo")),
) : TvPlatform {

    val launched = mutableListOf<String>()

    override val nowPlaying = MutableStateFlow<List<PlayingMedia>>(emptyList())
    val resumed = mutableListOf<String>()
    var upNext: List<UpNextEntry> = emptyList()

    /** Packages whose media session is still alive on what they played. */
    var liveSessions: Set<String> = emptySet()
    val youTubeSearches = mutableListOf<String>()

    override fun resumeSession(packageName: String, title: String?): Boolean {
        if (packageName !in liveSessions) return false
        resumed += packageName
        return true
    }

    override fun openYouTubeSearch(query: String) {
        youTubeSearches += query
    }

    override suspend fun loadUpNext() = upNext

    override suspend fun saveUpNext(entries: List<UpNextEntry>) {
        upNext = entries
    }

    var address: String? = "192.168.100.18"

    /** Uploads the APK Drop page receives: each is a code, a size and the file it becomes. */
    val uploads = MutableSharedFlow<Triple<String, Long, String>>()
    var serving = false
    val verdicts = mutableListOf<UploadVerdict>()
    var apkInfos: Map<String, ApkInfo> = emptyMap()
    var installResult: InstallResult = InstallResult.Installed
    val installed = mutableListOf<Pair<String, Boolean>>()
    val deletedFiles = mutableListOf<String>()

    override suspend fun localAddress() = address

    override fun receiveApks(port: Int, verdict: (code: String, sizeBytes: Long) -> UploadVerdict) =
        kotlinx.coroutines.flow.flow {
            serving = true
            try {
                uploads.collect { (code, size, path) ->
                    val answer = verdict(code, size)
                    verdicts += answer
                    if (answer == UploadVerdict.Accept) emit(path)
                }
            } finally {
                serving = false
            }
        }

    override suspend fun inspectApk(path: String) = apkInfos[path]

    override suspend fun installApk(path: String, silent: Boolean): InstallResult {
        installed += path to silent
        return installResult
    }

    override suspend fun deleteFile(path: String) {
        deletedFiles += path
    }

    /** Latest release per repository; a missing repository is not found. Null simulates being offline. */
    var releases: Map<String, ReleaseLookup>? = emptyMap()
    val releaseChecks = mutableListOf<String>()
    var downloadFails = false
    val downloaded = mutableListOf<ReleaseAsset>()
    var updateSources: Map<String, String> = emptyMap()

    override suspend fun latestRelease(repository: String): ReleaseLookup {
        releaseChecks += repository
        return releases?.let { it[repository] ?: ReleaseLookup.NotFound } ?: ReleaseLookup.Offline
    }

    override suspend fun download(asset: ReleaseAsset): String? {
        downloaded += asset
        return if (downloadFails) null else "/cache/updates/${asset.name}"
    }

    override suspend fun loadUpdateSources() = updateSources

    override suspend fun saveUpdateSources(sources: Map<String, String>) {
        updateSources = sources
    }

    /** Trending per window and filter; a missing pair is offline. */
    var trending: Map<Pair<TrendWindow, KindFilter>, TrendingLookup> = emptyMap()
    val trendingCalls = mutableListOf<Pair<TrendWindow, KindFilter>>()

    override suspend fun trending(window: TrendWindow, filter: KindFilter): TrendingLookup {
        trendingCalls += window to filter
        return trending[window to filter] ?: TrendingLookup.Offline
    }

    /** Details per Title id; a missing id is offline. */
    var details: Map<Int, DetailsLookup> = emptyMap()

    override suspend fun titleDetails(kind: TitleKind, id: Int) = details[id] ?: DetailsLookup.Offline

    val playStorePages = mutableListOf<String>()
    val youTubeVideos = mutableListOf<String>()

    val youTubeStarts = mutableListOf<Int>()

    override fun openYouTubeVideo(key: String, startSeconds: Int) {
        youTubeVideos += key
        youTubeStarts += startSeconds
    }

    override fun openPlayStorePage(packageName: String) {
        playStorePages += packageName
    }

    val deletedArtwork = mutableListOf<String>()

    override suspend fun deleteArtwork(path: String) {
        deletedArtwork += path
    }
    val uninstallRequests = mutableListOf<String>()
    val appInfoOpened = mutableListOf<String>()
    var playStoreOpened = 0
    val switchedTo = mutableListOf<String>()

    /** Every system measurement the Home took, in order: "storage", "memory", "apps-storage", "processes". */
    val measurements = mutableListOf<String>()
    val closed = mutableListOf<String>()

    override suspend fun storageUsage(): StorageUsage {
        measurements += "storage"
        return storage
    }

    override suspend fun memoryUsage(): MemoryUsage {
        measurements += "memory"
        return memory
    }

    /** How long ranking per-app storage takes; over a second on the owner's TV. */
    var appStorageTakes: kotlin.time.Duration = kotlin.time.Duration.ZERO

    override suspend fun appStorage(): List<AppStorage>? {
        kotlinx.coroutines.delay(appStorageTakes)
        measurements += "apps-storage"
        return storagePerApp
    }

    override suspend fun processMemory(): List<ProcessMemory>? {
        measurements += "processes"
        return processes
    }

    override suspend fun appLabel(packageName: String) =
        labels[packageName] ?: apps.firstOrNull { it.packageName == packageName }?.label ?: packageName

    override fun closeApp(packageName: String) {
        closed += packageName
        processes = processes?.filterNot { it.packageName == packageName }
    }

    /** Emit a reading per second; [MutableSharedFlow.subscriptionCount] tells whether the Home is measuring. */
    override val homeFrameRate = MutableSharedFlow<Int>()

    override fun displayRefreshRate() = refreshRate

    override val notificationAccess = MutableStateFlow(true)
    override val activeNotifications = MutableStateFlow<List<TvNotification>>(emptyList())
    val openedNotifications = mutableListOf<String>()

    override fun openNotification(key: String) {
        openedNotifications += key
    }

    override fun clearNotifications() {
        activeNotifications.value = activeNotifications.value.filterNot { it.clearable }
    }

    /** An app posts (or updates) a notification. */
    fun post(notification: TvNotification) {
        activeNotifications.value = activeNotifications.value.filterNot { it.key == notification.key } + notification
    }

    override suspend fun recentUsage(since: Instant) = lastUsed.filterValues { !it.isBefore(since) }

    override suspend fun loadOpenAppLimit() = openAppLimit

    override suspend fun saveOpenAppLimit(limit: Int) {
        openAppLimit = limit
    }

    val homeOpenedAt = mutableListOf<HomeDestination>()
    val setupNotifications = mutableListOf<SetupCheck>()

    override suspend fun setupStatus() = setup

    override suspend fun loadSetupHistory() = setupHistory

    override suspend fun saveSetupHistory(held: Set<SetupCheck>) {
        setupHistory = held
    }

    override suspend fun loadAnnouncedRegressions() = announcedRegressions

    override suspend fun saveAnnouncedRegressions(lost: Set<SetupCheck>) {
        announcedRegressions = lost
    }

    override fun postSetupNotification(check: SetupCheck) {
        setupNotifications += check
    }

    /** Setup notifications the Home withdrew. */
    val withdrawnSetupNotifications = mutableListOf<SetupCheck>()

    override fun cancelSetupNotification(check: SetupCheck) {
        withdrawnSetupNotifications += check
    }

    override suspend fun loadButtonAssignments() = buttonAssignments

    override suspend fun saveButtonAssignments(assignments: Map<ShortcutButton, ButtonTarget>) {
        buttonAssignments = assignments
    }

    override fun isLaunchable(packageName: String) = apps.any { it.packageName == packageName }

    override fun switchToInputById(inputId: String) {
        switchedTo += inputId
    }

    override fun openHome(destination: HomeDestination) {
        homeOpenedAt += destination
    }

    override fun isKeyFilterEnabled() = keyFilterEnabled

    override fun enableKeyFilter(): Boolean {
        if (canWriteSecureSettings) keyFilterEnabled = true
        return canWriteSecureSettings
    }

    var androidSettingsOpened = 0
    var systemUpdaterOpened = 0
    var playStoreAppsOpened = 0

    override suspend fun installedPackages() = packages

    override fun openSystemUpdater() {
        systemUpdaterOpened++
    }

    override fun openPlayStoreApps() {
        playStoreAppsOpened++
    }

    override suspend fun packageState(packageName: String) = packageStates[packageName] ?: PackageState.NotInstalled

    override suspend fun tvName() = name

    override suspend fun deviceInfo() = device

    override fun openAndroidSettings() {
        androidSettingsOpened++
    }

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
