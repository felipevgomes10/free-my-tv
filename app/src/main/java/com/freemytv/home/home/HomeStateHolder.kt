package com.freemytv.home.home

import com.freemytv.home.apps.AppLibrary
import com.freemytv.home.apps.tileArt
import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeStateHolder(
    private val platform: TvPlatform,
    private val library: AppLibrary,
    scope: CoroutineScope,
) {

    private val clock = MutableStateFlow(statusClock(platform.now()))

    /** The App Tray stays empty until the library has loaded, so focus never lands on a half-loaded tray. */
    val state: StateFlow<HomeState> = combine(clock, library.library) { clock, library ->
        val tray = library?.let { it.trayApps.map { app -> AppTrayItem.App(app.packageName, app.label, app.tileArt()) } + AppTrayItem.AllApps }
        HomeState(clock, tray.orEmpty())
    }.stateIn(scope, SharingStarted.Eagerly, HomeState(clock.value, emptyList()))

    /** Re-reads installed apps and the owner's tray; called whenever the Home comes to the front. */
    suspend fun refresh() = library.load()

    fun tick() {
        clock.value = statusClock(platform.now())
    }

    /** Opens an app; All Apps is a screen change the UI handles. */
    fun select(item: AppTrayItem) {
        if (item is AppTrayItem.App) platform.launch(item.packageName)
    }

    private companion object {
        val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", PT_BR)
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR)

        fun statusClock(now: ZonedDateTime) = StatusClock(
            time = now.format(TIME),
            date = now.format(DATE).replace("-feira", ""),
        )
    }
}
