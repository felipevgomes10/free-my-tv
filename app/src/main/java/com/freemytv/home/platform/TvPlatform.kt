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
import com.freemytv.home.notifications.TvNotification
import com.freemytv.home.settings.DeviceInfo
import com.freemytv.home.setup.SetupCheck
import com.freemytv.home.settings.PackageState
import com.freemytv.home.updates.InstalledPackage
import com.freemytv.home.updates.ReleaseAsset
import com.freemytv.home.updates.ReleaseLookup
import com.freemytv.home.upnext.PlayingMedia
import com.freemytv.home.upnext.UpNextEntry
import com.freemytv.home.upnext.WatchNextProgram
import com.freemytv.home.weather.CachedForecast
import com.freemytv.home.weather.Forecast
import com.freemytv.home.weather.Place
import com.freemytv.home.widgets.WidgetPreferences
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZonedDateTime

/**
 * The single seam between the Home and Android. Every decision lives on the Home's
 * side of this interface; implementations only translate to and from the system.
 */
interface TvPlatform {

    suspend fun loadScreensaverPreferences(): com.freemytv.home.screensaver.ScreensaverPreferences
    suspend fun saveScreensaverPreferences(preferences: com.freemytv.home.screensaver.ScreensaverPreferences)
    suspend fun isScreensaverReady(): Boolean
    suspend fun configureScreensaver(preferences: com.freemytv.home.screensaver.ScreensaverPreferences): Boolean

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

    /** Plays a YouTube video in the YouTube app from [startSeconds], or opens its Play Store page when it isn't installed. */
    fun openYouTubeVideo(key: String, startSeconds: Int = 0)

    /** Opens an app's page in the Play Store, to install it. */
    fun openPlayStorePage(packageName: String)

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

    suspend fun storageUsage(): StorageUsage

    suspend fun memoryUsage(): MemoryUsage

    /** Storage per installed app; null without the usage access Setup grants. */
    suspend fun appStorage(): List<AppStorage>?

    /** Memory per running process with its importance; null without the dump access Setup grants. */
    suspend fun processMemory(): List<ProcessMemory>?

    /** An app's name, or its package name if it has none. */
    suspend fun appLabel(packageName: String): String

    /** Ends a background app's processes, quietly; Android won't end protected ones (playing media, foreground services) this way. */
    fun closeApp(packageName: String)

    /**
     * Ends all of an app's processes, protected or not, the way Settings' Force stop does,
     * and keeps them from restarting by themselves; Settings shows for a moment.
     */
    fun forceStop(packageName: String)

    /** The Home's frames per second, one reading each second, for as long as it is collected. */
    val homeFrameRate: Flow<Int>

    fun displayRefreshRate(): Int

    suspend fun packageState(packageName: String): PackageState

    /** The name other devices see on the network, or null if the system has none. */
    suspend fun tvName(): String?

    suspend fun deviceInfo(): DeviceInfo

    fun openAndroidSettings()

    /** Every installed package, launchable or not, with who installed it. */
    suspend fun installedPackages(): List<InstalledPackage>

    /** Opens the system updater (on the owner's TV, Google's system update screen). */
    fun openSystemUpdater()

    /** Opens the Play Store's list of the owner's apps, where updates are managed. */
    fun openPlayStoreApps()

    /** Whether the Home may read notifications (granted by Setup). */
    val notificationAccess: Flow<Boolean>

    /** Every current notification, re-sent whenever one is posted, updated or removed. Empty without access. */
    val activeNotifications: Flow<List<TvNotification>>

    /** Opens what the notification points to. */
    fun openNotification(key: String)

    /** Dismisses every clearable notification. */
    fun clearNotifications()

    /** When each app was last in front, for apps used since [since]; empty without usage access. */
    suspend fun recentUsage(since: Instant): Map<String, Instant>

    suspend fun loadOpenAppLimit(): Int

    suspend fun saveOpenAppLimit(limit: Int)

    suspend fun loadButtonAssignments(): Map<ShortcutButton, ButtonTarget>

    suspend fun saveButtonAssignments(assignments: Map<ShortcutButton, ButtonTarget>)

    /** Whether an app is installed and can be opened. */
    fun isLaunchable(packageName: String): Boolean

    /** Switches to an Input known only by its id, as a remapped Shortcut Button does. */
    fun switchToInputById(inputId: String)

    /** Brings the Home to the front at [destination]. */
    fun openHome(destination: HomeDestination)

    /** Whether the accessibility service that filters remote keys is on. */
    fun isKeyFilterEnabled(): Boolean

    /** Turns the key filter on; needs WRITE_SECURE_SETTINGS (granted by Setup). Returns whether it worked. */
    fun enableKeyFilter(): Boolean

    /** Whether the TV's network debugging answers on 127.0.0.1:5555, where the Web Remote's keys go. */
    suspend fun isNetworkDebuggingOn(): Boolean

    /** Whether the Home was linked to the TV's ADB, as far as it last knew. */
    suspend fun isAdbLinked(): Boolean

    /** Links the Home to the TV's ADB: Android shows "Allow debugging?" and this waits for the Owner. */
    suspend fun linkAdb(): com.freemytv.home.webremote.AdbLinkResult

    /** Whether each part of Setup holds right now. */
    suspend fun setupStatus(): Map<SetupCheck, Boolean>

    /** Checks that have held at some point on this TV. */
    suspend fun loadSetupHistory(): Set<SetupCheck>

    suspend fun saveSetupHistory(held: Set<SetupCheck>)

    /** Lost checks the owner was already told about. */
    suspend fun loadAnnouncedRegressions(): Set<SetupCheck>

    suspend fun saveAnnouncedRegressions(lost: Set<SetupCheck>)

    /** Posts an Android notification telling the owner a part of Setup was lost. */
    fun postSetupNotification(check: SetupCheck)

    /** Withdraws that notification once the part of Setup holds again. */
    fun cancelSetupNotification(check: SetupCheck)

    /**
     * The TV's media sessions, re-sent whenever one starts, stops or changes, and every few
     * seconds while something plays. Empty without notification access.
     */
    val nowPlaying: Flow<List<PlayingMedia>>

    /**
     * Resumes what [packageName] played through its live media session, when that session is
     * still on [title] (or any, for an app that names nothing); false when there is none.
     */
    fun resumeSession(packageName: String, title: String?): Boolean

    /** Opens YouTube's search for [query]. */
    fun openYouTubeSearch(query: String)

    /**
     * What streaming apps list in Android TV's Watch Next, from apps that can be opened, pictures
     * stored; null without the access Setup grants.
     */
    suspend fun watchNext(): List<WatchNextProgram>?

    /** Opens [packageName]'s own link, an intent URI or a web address, in that app only; false when it doesn't open. */
    fun openLink(packageName: String, link: String): Boolean

    /** Watch Next entries the owner removed from the Up Next Tray, with when they were last watched then. */
    suspend fun loadDismissedUpNext(): Map<String, Instant>

    suspend fun saveDismissedUpNext(dismissed: Map<String, Instant>)

    suspend fun loadUpNext(): List<UpNextEntry>

    suspend fun saveUpNext(entries: List<UpNextEntry>)

    /** Deletes artwork stored for an Up Next entry that is gone. */
    suspend fun deleteArtwork(path: String)

    /** The TV's IPv4 address on the home network, or null when it has none. */
    suspend fun localAddress(): String?

    /**
     * Serves the APK Drop page on [port] for as long as it is collected, emitting the path of
     * each APK received. [verdict] decides each upload, from its code and size, before the file
     * is accepted; the page shows the answer.
     */
    fun receiveApks(port: Int, verdict: (code: String, sizeBytes: Long) -> UploadVerdict): Flow<String>

    /** What an APK file is, compared with what is installed; null when it isn't a valid APK. */
    suspend fun inspectApk(path: String): ApkInfo?

    /**
     * Installs an APK through Android's package installer. Android asks the owner to confirm,
     * unless [silent] and the system allows it (the Home updating itself, once it installed itself).
     */
    suspend fun installApk(path: String, silent: Boolean = false, dexMetadata: String? = null): InstallResult

    suspend fun deleteFile(path: String)

    /** The latest release of a GitHub repository ("owner/name"). */
    suspend fun latestRelease(repository: String): ReleaseLookup

    /** Downloads a release's file; its path, or null when the download failed. */
    suspend fun download(asset: ReleaseAsset): String?

    /** Each sideloaded package's Update Source, "owner/name" on GitHub. */
    suspend fun loadUpdateSources(): Map<String, String>

    suspend fun saveUpdateSources(sources: Map<String, String>)

    /** TMDB's trending Titles, in Portuguese, with genre names. */
    suspend fun trending(window: TrendWindow, filter: KindFilter): TrendingLookup

    /** A Title's details for Brazil, in Portuguese, in one TMDB request. */
    suspend fun titleDetails(kind: TitleKind, id: Int): DetailsLookup

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
