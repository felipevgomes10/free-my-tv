package com.freemytv.home.home

import com.freemytv.home.apps.AppLibrary
import com.freemytv.home.apps.tileArt
import com.freemytv.home.platform.TvPlatform
import com.freemytv.home.trays.TrayEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeStateHolder(
    private val platform: TvPlatform,
    private val library: AppLibrary,
    scope: CoroutineScope,
) {

    private val clock = MutableStateFlow(statusClock(platform.now()))
    private val edit = MutableStateFlow<TrayEdit<String>?>(null)

    /** The App Tray stays empty until the library has loaded, so focus never lands on a half-loaded tray. */
    val state: StateFlow<HomeState> = combine(clock, library.library, edit) { clock, library, edit ->
        if (library == null) return@combine HomeState(clock, emptyList())
        val byPackage = library.installed.associateBy { it.packageName }
        val order = edit?.order ?: library.trayApps.map { it.packageName }
        val apps = order.mapNotNull { byPackage[it] }.map { AppTrayItem.App(it.packageName, it.label, it.tileArt()) }
        HomeState(clock, apps + AppTrayItem.AllApps, apps.firstOrNull { it.packageName == edit?.lifted })
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

    /** Enters Edit Mode with [item] lifted. "Todos os apps" can't be lifted. */
    fun startEdit(item: AppTrayItem) {
        if (item !is AppTrayItem.App || edit.value != null) return
        edit.value = TrayEdit.start(trayOrder(), item.packageName)
    }

    /** Moves the lifted app [steps] places along the App Tray (negative: left). */
    fun move(steps: Int) = edit.update { it?.move(steps) }

    /** Drops the lifted app where it is, keeps the new order and leaves Edit Mode. */
    suspend fun drop() {
        val dropped = edit.value ?: return
        library.reorderTray(dropped.order)
        edit.value = null
    }

    /** Leaves Edit Mode, putting the lifted app back where it was. */
    fun cancelEdit() {
        edit.value = null
    }

    suspend fun unpinLifted() {
        val lifted = edit.value?.lifted ?: return
        edit.value = null
        library.unpin(lifted)
    }

    fun uninstallLifted() {
        val lifted = edit.value?.lifted ?: return
        edit.value = null
        library.requestUninstall(lifted)
    }

    private fun trayOrder() = state.value.appTray.filterIsInstance<AppTrayItem.App>().map { it.packageName }

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
