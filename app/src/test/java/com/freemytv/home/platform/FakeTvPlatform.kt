package com.freemytv.home.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.ZoneId
import java.time.ZonedDateTime

class FakeTvPlatform(
    var apps: List<InstalledApp> = emptyList(),
    var pinned: List<String>? = null,
    var clock: ZonedDateTime = ZonedDateTime.of(2026, 10, 23, 21, 40, 0, 0, ZoneId.of("America/Sao_Paulo")),
) : TvPlatform {

    val launched = mutableListOf<String>()
    val uninstallRequests = mutableListOf<String>()
    val appInfoOpened = mutableListOf<String>()
    var playStoreOpened = 0

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
