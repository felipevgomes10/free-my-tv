package com.freemytv.home.screensaver

import com.freemytv.home.platform.TvPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScreensaverPhase { Checking, Showing, Finished }

/** Playback wins both before the screensaver starts and if it begins while the weather is shown. */
class ScreensaverSession(private val platform: TvPlatform) {
    private val mutable = MutableStateFlow(ScreensaverPhase.Checking)
    val state = mutable.asStateFlow()

    suspend fun run() {
        if (!platform.loadScreensaverPreferences().enabled) {
            mutable.value = ScreensaverPhase.Finished
            return
        }
        platform.nowPlaying.collect { sessions ->
            if (mutable.value != ScreensaverPhase.Finished) {
                mutable.value = if (sessions.any { it.playing }) ScreensaverPhase.Finished else ScreensaverPhase.Showing
            }
        }
    }
}
