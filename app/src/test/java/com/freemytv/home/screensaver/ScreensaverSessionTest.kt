package com.freemytv.home.screensaver

import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.upnext.PlayingMedia
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreensaverSessionTest {
    @Test fun `a disabled screensaver never shows`() = runTest {
        val session = ScreensaverSession(FakeTvPlatform())
        session.run()
        assertEquals(ScreensaverPhase.Finished, session.state.value)
    }

    @Test fun `playing media prevents the screensaver even when the app names no title`() = runTest {
        val platform = FakeTvPlatform().apply {
            screensaverPreferences = ScreensaverPreferences(enabled = true)
            nowPlaying.value = listOf(PlayingMedia("com.netflix.ninja", playing = true))
        }
        val session = ScreensaverSession(platform)
        backgroundScope.launch { session.run() }
        runCurrent()
        assertEquals(ScreensaverPhase.Finished, session.state.value)
    }

    @Test fun `paused apps allow the screensaver and beginning playback ends it without reopening later`() = runTest {
        val platform = FakeTvPlatform().apply {
            screensaverPreferences = ScreensaverPreferences(enabled = true)
            nowPlaying.value = listOf(PlayingMedia("com.netflix.ninja", playing = false))
        }
        val session = ScreensaverSession(platform)
        backgroundScope.launch { session.run() }
        runCurrent()
        assertEquals(ScreensaverPhase.Showing, session.state.value)
        platform.nowPlaying.value = listOf(PlayingMedia("com.netflix.ninja", playing = true))
        runCurrent()
        assertEquals(ScreensaverPhase.Finished, session.state.value)
        platform.nowPlaying.value = emptyList()
        runCurrent()
        assertEquals(ScreensaverPhase.Finished, session.state.value)
    }
}
