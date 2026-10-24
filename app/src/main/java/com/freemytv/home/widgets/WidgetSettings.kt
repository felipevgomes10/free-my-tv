package com.freemytv.home.widgets

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The owner's choices about the Widgets. A Widget that is not visible is a Hidden Widget. */
data class WidgetPreferences(
    val weatherVisible: Boolean = true,
    val monitorVisible: Boolean = true,
    /** The city the Weather Widget reports on, or null for "Automático" (located by the TV's public IP). */
    val weatherCity: String? = null,
    /** Only the Weather Widget's detail makes sound; remember the owner's mute choice. */
    val weatherSoundMuted: Boolean = false,
)

class WidgetSettings(private val platform: TvPlatform) {

    private val _preferences = MutableStateFlow(WidgetPreferences())
    val preferences: StateFlow<WidgetPreferences> = _preferences.asStateFlow()

    suspend fun load() {
        _preferences.value = platform.loadWidgetPreferences()
    }

    suspend fun setWeatherVisible(visible: Boolean) = change { it.copy(weatherVisible = visible) }

    suspend fun setMonitorVisible(visible: Boolean) = change { it.copy(monitorVisible = visible) }

    suspend fun setWeatherSoundMuted(muted: Boolean) = change { it.copy(weatherSoundMuted = muted) }

    /** A blank city means "Automático". */
    suspend fun setWeatherCity(city: String?) = change { it.copy(weatherCity = city?.trim()?.takeIf { name -> name.isNotEmpty() }) }

    private suspend fun change(edit: (WidgetPreferences) -> WidgetPreferences) {
        val changed = edit(_preferences.value)
        platform.saveWidgetPreferences(changed)
        _preferences.value = changed
    }
}
