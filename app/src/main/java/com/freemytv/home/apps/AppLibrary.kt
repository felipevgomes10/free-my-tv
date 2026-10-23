package com.freemytv.home.apps

import com.freemytv.home.platform.InstalledApp
import com.freemytv.home.platform.PackageEvent
import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** The installed apps and which of them are pinned: the one source of truth the App Tray and All Apps share. */
data class Library(
    /** Every installed app, alphabetically. */
    val installed: List<InstalledApp>,
    /** Pinned apps in tray order; may name apps that are not installed right now. */
    val pinned: List<String>,
) {
    val trayApps: List<InstalledApp> by lazy {
        val byPackage = installed.associateBy { it.packageName }
        pinned.mapNotNull { byPackage[it] }
    }

    fun isPinned(packageName: String) = packageName in pinned
}

class AppLibrary(private val platform: TvPlatform) {

    private val _library = MutableStateFlow<Library?>(null)

    /** Null until the first [load]. */
    val library: StateFlow<Library?> = _library.asStateFlow()

    suspend fun load() {
        val installed = platform.installedApps().sortedBy { it.label.lowercase(PT_BR) }
        val pinned = platform.loadPinnedApps() ?: defaultPins(installed).also { platform.savePinnedApps(it) }
        _library.value = Library(installed, pinned)
    }

    /** Keeps the library current while apps are installed and uninstalled. */
    suspend fun followPackageEvents() {
        platform.packageEvents.collect { event ->
            if (event is PackageEvent.Removed) unpin(event.packageName)
            load()
        }
    }

    /** Adds the app to the end of the App Tray. */
    suspend fun pin(packageName: String) = editPins { if (packageName in it) it else it + packageName }

    suspend fun unpin(packageName: String) = editPins { it - packageName }

    fun requestUninstall(packageName: String) = platform.requestUninstall(packageName)

    private suspend fun editPins(edit: (List<String>) -> List<String>) {
        val current = _library.value ?: return
        val pinned = edit(current.pinned)
        if (pinned == current.pinned) return
        platform.savePinnedApps(pinned)
        _library.update { it?.copy(pinned = pinned) }
    }

    private fun defaultPins(alphabetical: List<InstalledApp>): List<String> {
        val present = alphabetical.map { it.packageName }.toSet()
        val preferred = PREFERRED_DEFAULTS.filter { it in present }
        val rest = alphabetical.map { it.packageName }.filterNot { it in preferred }
        return (preferred + rest).take(DEFAULT_TRAY_SIZE)
    }

    private companion object {
        val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

        const val DEFAULT_TRAY_SIZE = 5

        /** Pinned on first run when installed, before filling the tray alphabetically. */
        val PREFERRED_DEFAULTS = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.globo.globotv",
            "com.disney.disneyplus",
        )
    }
}
