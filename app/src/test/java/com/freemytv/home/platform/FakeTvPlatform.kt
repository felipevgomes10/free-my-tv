package com.freemytv.home.platform

import java.time.ZoneId
import java.time.ZonedDateTime

class FakeTvPlatform(
    var apps: List<InstalledApp> = emptyList(),
    var pinned: List<String>? = null,
    var clock: ZonedDateTime = ZonedDateTime.of(2026, 10, 23, 21, 40, 0, 0, ZoneId.of("America/Sao_Paulo")),
) : TvPlatform {

    val launched = mutableListOf<String>()

    override suspend fun installedApps() = apps

    override fun launch(packageName: String) {
        launched += packageName
    }

    override suspend fun loadPinnedApps() = pinned

    override suspend fun savePinnedApps(packageNames: List<String>) {
        pinned = packageNames
    }

    override fun now() = clock
}

fun app(packageName: String, label: String = packageName, hasBanner: Boolean = true) =
    InstalledApp(packageName, label, hasBanner)
