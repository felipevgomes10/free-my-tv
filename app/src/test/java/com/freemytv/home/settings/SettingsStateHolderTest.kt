package com.freemytv.home.settings

import com.freemytv.home.monitor.SystemMonitor
import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.upnext.WatchNextRow
import com.freemytv.home.weather.WeatherWidget
import com.freemytv.home.widgets.WidgetSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStateHolderTest {

    private class Screens(val settings: SettingsStateHolder, val weather: WeatherWidget, val monitor: SystemMonitor)

    private suspend fun TestScope.screens(platform: FakeTvPlatform): Screens {
        val widgetSettings = WidgetSettings(platform).also { it.load() }
        val weather = WeatherWidget(platform, widgetSettings)
        val monitor = SystemMonitor(platform, widgetSettings)
        backgroundScope.launch { weather.follow() }
        backgroundScope.launch { monitor.follow() }
        val settings = SettingsStateHolder(platform, widgetSettings, backgroundScope).also { it.load() }
        runCurrent()
        return Screens(settings, weather, monitor)
    }

    @Test
    fun `turning a Widget off hides it from the Status Bar right away and the choice persists`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform()
        val screens = screens(platform)

        screens.settings.setWeatherVisible(false)
        screens.settings.setMonitorVisible(false)
        runCurrent()

        assertFalse(screens.weather.state.value.visible)
        assertFalse(screens.monitor.state.value.visible)
        assertFalse(screens.settings.state.value.widgets.weatherVisible)
        assertFalse(platform.widgetPreferences.monitorVisible)

        screens.settings.setWeatherVisible(true)
        runCurrent()
        assertTrue(screens.weather.state.value.visible)
    }

    @Test
    fun `a blank weather city means Automático`() = runTest(UnconfinedTestDispatcher()) {
        val screens = screens(FakeTvPlatform())

        screens.settings.setWeatherCity("  Campinas ")
        assertEquals("Campinas", screens.settings.state.value.widgets.weatherCity)

        screens.settings.setWeatherCity("   ")
        assertNull(screens.settings.state.value.widgets.weatherCity)
    }

    @Test
    fun `Cast status reflects each receiver's packages on the owner's TV`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(
            packageStates = mapOf(
                "com.google.android.apps.mediashell" to PackageState.Enabled,
                "com.tcl.airplay2" to PackageState.Enabled,
                "com.amlogic.airplay" to PackageState.Disabled,
            ),
        )

        val cast = screens(platform).settings.state.value.cast

        assertEquals(
            listOf(
                CastReceiverStatus(CastReceiver.Chromecast, PackageState.Enabled),
                CastReceiverStatus(CastReceiver.AirPlay, PackageState.Disabled),
                CastReceiverStatus(CastReceiver.Miracast, PackageState.NotInstalled),
            ),
            cast,
        )
    }

    @Test
    fun `Settings shows the TV's name on the network and what the TV is`() = runTest(UnconfinedTestDispatcher()) {
        val state = screens(FakeTvPlatform()).settings.state.value

        assertEquals("TCL Smart TV Pro", state.tvName)
        assertEquals("Smart TV Pro", state.device!!.model)
        assertEquals("0.1.0", state.device!!.homeVersion)
    }

    @Test
    fun `Configurações do Android opens Android Settings`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform()

        screens(platform).settings.openAndroidSettings()

        assertEquals(1, platform.androidSettingsOpened)
    }

    @Test
    fun `the Watch Next probe counts each app's entries and what they carry, most entries first`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(labels = mapOf("com.netflix.ninja" to "Netflix", "com.google.android.youtube.tv" to "YouTube")).apply {
            watchNext = listOf(
                WatchNextRow("com.google.android.youtube.tv", hasLink = true, hasProgress = false, hasPicture = true),
                WatchNextRow("com.netflix.ninja", hasLink = true, hasProgress = true, hasPicture = true),
                WatchNextRow("com.netflix.ninja", hasLink = true, hasProgress = false, hasPicture = false),
            )
        }

        val probe = screens(platform).settings.state.value.watchNext

        assertEquals(
            listOf(
                WatchNextPublisher("Netflix", entries = 2, withLink = 2, withProgress = 1, withPicture = 1),
                WatchNextPublisher("YouTube", entries = 1, withLink = 1, withProgress = 0, withPicture = 1),
            ),
            probe,
        )
    }

    @Test
    fun `without the access Setup grants, the Watch Next probe has nothing to say`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform().apply { watchNext = null }

        assertNull(screens(platform).settings.state.value.watchNext)
    }
}
