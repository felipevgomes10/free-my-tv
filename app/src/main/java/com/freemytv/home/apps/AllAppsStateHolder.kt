package com.freemytv.home.apps

import com.freemytv.home.home.TileArt
import com.freemytv.home.platform.InstalledApp
import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AllAppsState(
    /** Every installed app, alphabetically. */
    val apps: List<AllAppsItem>,
    /** How many installed apps are on the App Tray. */
    val pinnedCount: Int,
)

data class AllAppsItem(
    val packageName: String,
    val label: String,
    val art: TileArt,
    val isPinned: Boolean,
)

class AllAppsStateHolder(
    private val platform: TvPlatform,
    private val library: AppLibrary,
    scope: CoroutineScope,
) {

    val state: StateFlow<AllAppsState> = library.library.map { library ->
        if (library == null) {
            AllAppsState(emptyList(), 0)
        } else {
            AllAppsState(
                apps = library.installed.map { AllAppsItem(it.packageName, it.label, it.tileArt(), library.isPinned(it.packageName)) },
                pinnedCount = library.trayApps.size,
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, AllAppsState(emptyList(), 0))

    fun open(packageName: String) = platform.launch(packageName)

    suspend fun togglePin(packageName: String) {
        if (library.library.value?.isPinned(packageName) == true) library.unpin(packageName) else library.pin(packageName)
    }

    fun uninstall(packageName: String) = library.requestUninstall(packageName)

    fun openAppInfo(packageName: String) = platform.openAppInfo(packageName)

    fun installApp() = platform.openPlayStore()
}

fun InstalledApp.tileArt() = if (hasBanner) TileArt.Banner else TileArt.IconOnColor
