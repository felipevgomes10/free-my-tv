package com.freemytv.home.updates

import com.freemytv.home.apkdrop.InstallResult
import com.freemytv.home.platform.TvPlatform
import com.freemytv.home.settings.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Duration.Companion.hours

/** An installed package as Android reports it, with who installed it. */
data class InstalledPackage(
    val packageName: String,
    val label: String,
    val version: String,
    /** The package that installed it, or null when it came over ADB or a file manager without saying. */
    val installer: String?,
    val isSystem: Boolean,
)

data class SideloadedApp(
    val packageName: String,
    val label: String,
    val version: String,
    /** Its Update Source, "owner/name" on GitHub, or null when the owner never set one. */
    val source: String? = null,
    /** A newer release found at the source. */
    val update: AvailableUpdate? = null,
    /** Downloading or installing an update right now. */
    val updating: Boolean = false,
    val problem: UpdateProblem? = null,
    /** The Home itself: its Update Source is this project's repository and can't be changed. */
    val fixedSource: Boolean = false,
)

data class AvailableUpdate(val version: String, val asset: ReleaseAsset)

enum class UpdateProblem {
    /** The repository doesn't exist or has no releases. */
    NotFound,
    /** The repository's latest release has no APK for this TV. */
    NoApk,
    Offline,
    DownloadFailed,
    InstallFailed,
}

data class UpdatesState(
    val device: DeviceInfo? = null,
    /** Whole months since the Security Patch, or null when the TV doesn't report one. */
    val patchAgeMonths: Long? = null,
    /** True when the Security Patch is more than six months old. */
    val patchOutdated: Boolean = false,
    /** Apps installed from an APK rather than the Play Store, alphabetically. */
    val sideloaded: List<SideloadedApp> = emptyList(),
) {
    /** The Updates icon's dot: an old Security Patch, or an app with an update waiting. */
    val needsAttention: Boolean get() = patchOutdated || sideloaded.any { it.update != null }
}

/**
 * Updates. The Home can report the Security Patch and open the manufacturer's updater,
 * never produce or install one; Play Store apps are updated by the Play Store. Apps
 * installed from an APK update from their Update Source, a GitHub repository's releases.
 */
class UpdatesStateHolder(private val platform: TvPlatform) {

    private val _state = MutableStateFlow(UpdatesState())
    val state: StateFlow<UpdatesState> = _state.asStateFlow()

    suspend fun load() {
        val device = platform.deviceInfo()
        val today = platform.now().toLocalDate()
        val patch = device.securityPatch
        _state.value = UpdatesState(
            device = device,
            patchAgeMonths = patch?.let { ChronoUnit.MONTHS.between(it, today) },
            patchOutdated = patch != null && patch.isBefore(today.minusMonths(OUTDATED_AFTER_MONTHS)),
            sideloaded = sideloadedApps(),
        )
    }

    private suspend fun sideloadedApps(): List<SideloadedApp> {
        val sources = platform.loadUpdateSources()
        val known = _state.value.sideloaded.associateBy { it.packageName }
        return platform.installedPackages()
            .filter { !it.isSystem && it.installer != PLAY_STORE }
            .sortedBy { it.label.lowercase(PT_BR) }
            .map { pkg ->
                val source = if (pkg.packageName == HOME) HOME_SOURCE else sources[pkg.packageName]
                // What the last check found still holds, unless the app got there meanwhile.
                val update = known[pkg.packageName]?.update?.takeIf { source != null && isNewer(it.version, pkg.version) }
                SideloadedApp(
                    pkg.packageName,
                    pkg.label,
                    pkg.version,
                    source,
                    update,
                    problem = known[pkg.packageName]?.problem?.takeIf { source != null },
                    fixedSource = pkg.packageName == HOME,
                )
            }
    }

    /** Looks at every Update Source's latest release. */
    suspend fun checkSources() {
        _state.value.sideloaded.filter { it.source != null }.forEach { app -> check(app.packageName) }
    }

    /** Checks the sources now and then about once a day, for as long as it runs. */
    suspend fun keepChecking() {
        while (true) {
            checkSources()
            delay(CHECK_EVERY)
        }
    }

    /**
     * Sets an app's Update Source from what the owner typed; accepted only when the
     * repository's latest release has an APK for this TV. A blank entry removes the source.
     */
    suspend fun setSource(packageName: String, input: String) {
        if (packageName == HOME) return
        if (input.isBlank()) return removeSource(packageName)
        val repository = parseRepository(input) ?: return setProblem(packageName, UpdateProblem.NotFound)
        val problem = when (val lookup = platform.latestRelease(repository)) {
            ReleaseLookup.NotFound -> UpdateProblem.NotFound
            ReleaseLookup.Offline -> UpdateProblem.Offline
            is ReleaseLookup.Found -> if (lookup.release.apkForTv() == null) UpdateProblem.NoApk else null
        }
        if (problem != null) return setProblem(packageName, problem)
        platform.saveUpdateSources(platform.loadUpdateSources() + (packageName to repository))
        _state.update { it.copy(sideloaded = sideloadedApps()) }
        check(packageName)
    }

    suspend fun removeSource(packageName: String) {
        if (packageName == HOME) return
        platform.saveUpdateSources(platform.loadUpdateSources() - packageName)
        _state.update { it.copy(sideloaded = sideloadedApps()) }
    }

    /**
     * Downloads the update and hands it to Android's installer, which asks the owner. The Home's
     * own update asks Android to skip that, which it does once the Home installed itself before.
     */
    suspend fun update(packageName: String) {
        val app = _state.value.sideloaded.firstOrNull { it.packageName == packageName } ?: return
        val update = app.update ?: return
        change(packageName) { it.copy(updating = true, problem = null) }
        val path = platform.download(update.asset)
        if (path == null) {
            change(packageName) { it.copy(updating = false, problem = UpdateProblem.DownloadFailed) }
            return
        }
        // A successful update of the Home ends this process; Android brings the new Home back.
        val result = platform.installApk(path, silent = packageName == HOME)
        platform.deleteFile(path)
        _state.update { it.copy(sideloaded = sideloadedApps()) }
        change(packageName) { it.copy(updating = false, problem = if (result is InstallResult.Failed) UpdateProblem.InstallFailed else null) }
    }

    private suspend fun check(packageName: String) {
        val app = _state.value.sideloaded.firstOrNull { it.packageName == packageName } ?: return
        val source = app.source ?: return
        when (val lookup = platform.latestRelease(source)) {
            ReleaseLookup.NotFound -> change(packageName) { it.copy(update = null, problem = UpdateProblem.NotFound) }
            ReleaseLookup.Offline -> change(packageName) { it.copy(problem = UpdateProblem.Offline) }
            is ReleaseLookup.Found -> {
                val release = lookup.release
                val asset = release.apkForTv()
                change(packageName) {
                    when {
                        asset == null -> it.copy(update = null, problem = UpdateProblem.NoApk)
                        isNewer(release.version, it.version) -> it.copy(update = AvailableUpdate(release.version, asset), problem = null)
                        else -> it.copy(update = null, problem = null)
                    }
                }
            }
        }
    }

    private fun setProblem(packageName: String, problem: UpdateProblem) = change(packageName) { it.copy(problem = problem) }

    private fun change(packageName: String, edit: (SideloadedApp) -> SideloadedApp) {
        _state.update { state -> state.copy(sideloaded = state.sideloaded.map { if (it.packageName == packageName) edit(it) else it }) }
    }

    fun checkSystemUpdate() = platform.openSystemUpdater()

    fun openPlayStoreApps() = platform.openPlayStoreApps()

    private companion object {
        const val OUTDATED_AFTER_MONTHS = 6L
        val CHECK_EVERY = 24.hours
        const val PLAY_STORE = "com.android.vending"
        const val HOME = "com.freemytv.home"
        const val HOME_SOURCE = "felipevgomes10/free-my-tv"
        val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
    }
}
