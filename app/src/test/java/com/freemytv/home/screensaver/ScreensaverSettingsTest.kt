package com.freemytv.home.screensaver

import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ScreensaverSettingsTest {
    @Test fun `screensaver is off by default and its optional delay starts at five minutes`() = runTest {
        val settings = ScreensaverSettings(FakeTvPlatform()).also { it.load() }
        assertFalse(settings.state.value.preferences.enabled)
        assertEquals(5, settings.state.value.preferences.afterMinutes)
    }

    @Test fun `enabled state and the three allowed delays survive a restart and configure Android`() = runTest {
        val platform = FakeTvPlatform()
        val settings = ScreensaverSettings(platform).also { it.load() }
        settings.setEnabled(true)
        for (minutes in listOf(2, 5, 10)) {
            settings.setMinutes(minutes)
            val restarted = ScreensaverSettings(platform).also { it.load() }
            assertEquals(ScreensaverPreferences(true, minutes), restarted.state.value.preferences)
            assertEquals(ScreensaverPreferences(true, minutes), platform.screensaverConfigurations.last())
        }
        settings.setEnabled(false)
        assertEquals(ScreensaverPreferences(false, 10), platform.screensaverConfigurations.last())
    }

    @Test fun `without Setup the owner keeps their choice and is told preparation is needed`() = runTest {
        val platform = FakeTvPlatform().apply { screensaverReady = false }
        val settings = ScreensaverSettings(platform).also { it.load() }
        settings.setEnabled(true)
        settings.setMinutes(2)
        assertEquals(ScreensaverPreferences(true, 2), settings.state.value.preferences)
        assertFalse(settings.state.value.ready)
        assertTrue(platform.screensaverConfigurations.isEmpty())
    }

    @Test fun `a delay outside two five or ten minutes is refused`() = runTest {
        val settings = ScreensaverSettings(FakeTvPlatform()).also { it.load() }
        try { settings.setMinutes(3); fail("invalid delay accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(5, settings.state.value.preferences.afterMinutes)
    }
}
