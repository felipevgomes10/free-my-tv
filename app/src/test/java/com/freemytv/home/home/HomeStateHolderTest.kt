package com.freemytv.home.home

import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.app
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeStateHolderTest {

    private val netflix = app("com.netflix.ninja", "Netflix")
    private val youtube = app("com.google.android.youtube.tv", "YouTube")
    private val kodi = app("org.xbmc.kodi", "Kodi", hasBanner = false)
    private val apple = app("com.apple.atve.androidtv.appletv", "Apple TV")
    private val vlc = app("org.videolan.vlc", "VLC")
    private val twitch = app("tv.twitch.android.app", "Twitch")
    private val steam = app("com.valvesoftware.steamlink", "Steam Link")

    private fun FakeTvPlatform.home() = HomeStateHolder(this)

    private fun HomeState.trayPackages() = appTray.map { (it as? AppTrayItem.App)?.packageName ?: "AllApps" }

    @Test
    fun `the App Tray shows the pinned apps in the owner's order, with All Apps last`() = runTest {
        val home = FakeTvPlatform(apps = listOf(netflix, youtube, kodi), pinned = listOf(kodi.packageName, netflix.packageName)).home()

        home.refresh()

        assertEquals(listOf(kodi.packageName, netflix.packageName, "AllApps"), home.state.value.trayPackages())
    }

    @Test
    fun `on first run the App Tray pins known streaming apps first, then fills alphabetically, and remembers it`() = runTest {
        val platform = FakeTvPlatform(apps = listOf(vlc, twitch, youtube, steam, kodi, apple, netflix))
        val home = platform.home()

        home.refresh()

        val expected = listOf(netflix.packageName, youtube.packageName, apple.packageName, kodi.packageName, steam.packageName)
        assertEquals(expected + "AllApps", home.state.value.trayPackages())
        assertEquals(expected, platform.pinned)
    }

    @Test
    fun `an owner who unpinned everything keeps an empty App Tray instead of getting defaults again`() = runTest {
        val home = FakeTvPlatform(apps = listOf(netflix, youtube), pinned = emptyList()).home()

        home.refresh()

        assertEquals(listOf("AllApps"), home.state.value.trayPackages())
    }

    @Test
    fun `a pinned app that is no longer installed does not appear on the App Tray`() = runTest {
        val home = FakeTvPlatform(apps = listOf(netflix), pinned = listOf(kodi.packageName, netflix.packageName)).home()

        home.refresh()

        assertEquals(listOf(netflix.packageName, "AllApps"), home.state.value.trayPackages())
    }

    @Test
    fun `apps with an App Banner show it, apps without one show their icon over a color`() = runTest {
        val home = FakeTvPlatform(apps = listOf(netflix, kodi), pinned = listOf(netflix.packageName, kodi.packageName)).home()

        home.refresh()

        val arts = home.state.value.appTray.filterIsInstance<AppTrayItem.App>().map { it.art }
        assertEquals(listOf(TileArt.Banner, TileArt.IconOnColor), arts)
    }

    @Test
    fun `selecting an app on the App Tray opens it`() = runTest {
        val platform = FakeTvPlatform(apps = listOf(netflix), pinned = listOf(netflix.packageName))
        val home = platform.home()
        home.refresh()

        home.select(home.state.value.appTray.first())

        assertEquals(listOf(netflix.packageName), platform.launched)
    }

    @Test
    fun `selecting All Apps opens no app`() = runTest {
        val platform = FakeTvPlatform(apps = listOf(netflix), pinned = listOf(netflix.packageName))
        val home = platform.home()
        home.refresh()

        home.select(AppTrayItem.AllApps)

        assertEquals(emptyList<String>(), platform.launched)
    }

    @Test
    fun `the App Tray is empty until the Home has loaded the owner's apps`() = runTest {
        val home = FakeTvPlatform(apps = listOf(netflix), pinned = listOf(netflix.packageName)).home()

        assertEquals(emptyList<AppTrayItem>(), home.state.value.appTray)
        home.refresh()
        assertEquals(listOf(netflix.packageName, "AllApps"), home.state.value.trayPackages())
    }

    @Test
    fun `the Status Bar shows the time and the date in Portuguese`() {
        val home = FakeTvPlatform().home()

        assertEquals(StatusClock("21:40", "sexta, 23 de outubro"), home.state.value.statusClock)
    }

    @Test
    fun `the Status Bar clock follows the platform clock on each tick`() {
        val platform = FakeTvPlatform()
        val home = platform.home()

        platform.clock = ZonedDateTime.of(2026, 10, 25, 9, 5, 0, 0, ZoneId.of("America/Sao_Paulo"))
        home.tick()

        assertEquals(StatusClock("09:05", "domingo, 25 de outubro"), home.state.value.statusClock)
    }
}
