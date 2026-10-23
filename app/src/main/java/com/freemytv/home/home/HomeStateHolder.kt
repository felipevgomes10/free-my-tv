package com.freemytv.home.home

import com.freemytv.home.platform.InstalledApp
import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeStateHolder(private val platform: TvPlatform) {

    /** The App Tray stays empty until the first [refresh], so focus never lands on a half-loaded tray. */
    private val _state = MutableStateFlow(HomeState(statusClock(platform.now()), emptyList()))
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /** Re-reads installed apps and the owner's tray; called whenever the Home comes to the front. */
    suspend fun refresh() {
        val installed = platform.installedApps().associateBy { it.packageName }
        val pinned = platform.loadPinnedApps() ?: defaultPins(installed.values).also { platform.savePinnedApps(it) }
        val tray = pinned.mapNotNull { installed[it]?.toTrayItem() } + AppTrayItem.AllApps
        _state.value = HomeState(statusClock(platform.now()), tray)
    }

    fun tick() {
        _state.value = _state.value.copy(statusClock = statusClock(platform.now()))
    }

    fun select(item: AppTrayItem) {
        when (item) {
            is AppTrayItem.App -> platform.launch(item.packageName)
            AppTrayItem.AllApps -> Unit
        }
    }

    private fun defaultPins(installed: Collection<InstalledApp>): List<String> {
        val present = installed.map { it.packageName }.toSet()
        val preferred = PREFERRED_DEFAULTS.filter { it in present }
        val rest = installed.sortedBy { it.label.lowercase(PT_BR) }.map { it.packageName }.filterNot { it in preferred }
        return (preferred + rest).take(DEFAULT_TRAY_SIZE)
    }

    private fun InstalledApp.toTrayItem() =
        AppTrayItem.App(packageName, label, if (hasBanner) TileArt.Banner else TileArt.IconOnColor)

    private companion object {
        val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", PT_BR)
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR)

        const val DEFAULT_TRAY_SIZE = 5

        /** Pinned on first run when installed, before filling the tray alphabetically. */
        val PREFERRED_DEFAULTS = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.globo.globotv",
            "com.disney.disneyplus",
        )

        fun statusClock(now: ZonedDateTime) = StatusClock(
            time = now.format(TIME),
            date = now.format(DATE).replace("-feira", ""),
        )
    }
}
