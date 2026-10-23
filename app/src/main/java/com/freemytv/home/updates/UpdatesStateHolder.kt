package com.freemytv.home.updates

import com.freemytv.home.platform.TvPlatform
import com.freemytv.home.settings.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.temporal.ChronoUnit
import java.util.Locale

/** An installed package as Android reports it, with who installed it. */
data class InstalledPackage(
    val packageName: String,
    val label: String,
    val version: String,
    /** The package that installed it, or null when it came over ADB or a file manager without saying. */
    val installer: String?,
    val isSystem: Boolean,
)

data class SideloadedApp(val packageName: String, val label: String, val version: String)

data class UpdatesState(
    val device: DeviceInfo? = null,
    /** Whole months since the Security Patch, or null when the TV doesn't report one. */
    val patchAgeMonths: Long? = null,
    /** True when the Security Patch is more than six months old. */
    val patchOutdated: Boolean = false,
    /** Apps installed from an APK rather than the Play Store, alphabetically. */
    val sideloaded: List<SideloadedApp> = emptyList(),
)

/**
 * Updates. The Home can report the Security Patch and open the manufacturer's updater,
 * never produce or install one; Play Store apps are updated by the Play Store.
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
            sideloaded = platform.installedPackages()
                .filter { !it.isSystem && it.installer != PLAY_STORE && it.packageName != HOME }
                .sortedBy { it.label.lowercase(PT_BR) }
                .map { SideloadedApp(it.packageName, it.label, it.version) },
        )
    }

    fun checkSystemUpdate() = platform.openSystemUpdater()

    fun openPlayStoreApps() = platform.openPlayStoreApps()

    private companion object {
        const val OUTDATED_AFTER_MONTHS = 6L
        const val PLAY_STORE = "com.android.vending"
        const val HOME = "com.freemytv.home"
        val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
    }
}
