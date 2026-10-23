package com.freemytv.home.platform

import com.freemytv.home.buttons.ButtonTarget
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
import com.freemytv.home.upnext.PlayingMedia
import com.freemytv.home.upnext.UpNextEntry
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

    suspend fun storageUsage(): StorageUsage

    suspend fun memoryUsage(): MemoryUsage

    /** Storage per installed app; null without the usage access Setup grants. */
    suspend fun appStorage(): List<AppStorage>?

    /** Memory per running process with its importance; null without the dump access Setup grants. */
    suspend fun processMemory(): List<ProcessMemory>?

    /** An app's name, or its package name if it has none. */
    suspend fun appLabel(packageName: String): String

    /** Ends a background app's processes. */
    fun closeApp(packageName: String)

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

    /** Reopens what [packageName] is playing through its live media session, or else opens the app. */
    fun resume(packageName: String)

    suspend fun loadUpNext(): List<UpNextEntry>

    suspend fun saveUpNext(entries: List<UpNextEntry>)

    /** Deletes artwork stored for an Up Next entry that is gone. */
    suspend fun deleteArtwork(path: String)

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
