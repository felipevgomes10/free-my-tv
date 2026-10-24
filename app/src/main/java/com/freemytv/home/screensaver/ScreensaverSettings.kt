package com.freemytv.home.screensaver

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreensaverPreferences(val enabled: Boolean = false, val afterMinutes: Int = 5) {
    companion object { val MINUTES = listOf(2, 5, 10) }
}

data class ScreensaverState(
    val preferences: ScreensaverPreferences = ScreensaverPreferences(),
    val ready: Boolean = false,
)

/** The owner's choice; Android owns inactivity and respects apps keeping the screen awake. */
class ScreensaverSettings(private val platform: TvPlatform) {
    private val mutable = MutableStateFlow(ScreensaverState())
    val state = mutable.asStateFlow()

    suspend fun load() {
        mutable.value = ScreensaverState(platform.loadScreensaverPreferences(), platform.isScreensaverReady())
    }

    suspend fun setEnabled(enabled: Boolean) = change(mutable.value.preferences.copy(enabled = enabled))

    suspend fun setMinutes(minutes: Int) {
        require(minutes in ScreensaverPreferences.MINUTES)
        change(mutable.value.preferences.copy(afterMinutes = minutes))
    }

    private suspend fun change(preferences: ScreensaverPreferences) {
        platform.saveScreensaverPreferences(preferences)
        mutable.value = ScreensaverState(preferences, platform.configureScreensaver(preferences))
    }
}
