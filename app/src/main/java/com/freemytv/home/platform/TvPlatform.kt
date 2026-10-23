package com.freemytv.home.platform

import java.time.ZonedDateTime

/**
 * The single seam between the Home and Android. Every decision lives on the Home's
 * side of this interface; implementations only translate to and from the system.
 */
interface TvPlatform {

    /** Launchable TV apps currently installed, excluding the Home itself. */
    suspend fun installedApps(): List<InstalledApp>

    fun launch(packageName: String)

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
