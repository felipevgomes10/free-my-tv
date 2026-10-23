package com.freemytv.home.apps

import com.freemytv.home.home.AppTrayItem
import com.freemytv.home.home.HomeStateHolder
import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.app
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AllAppsStateHolderTest {

    private val netflix = app("com.netflix.ninja", "Netflix")
    private val youtube = app("com.google.android.youtube.tv", "YouTube")
    private val kodi = app("org.xbmc.kodi", "Kodi")
    private val vlc = app("org.videolan.vlc", "VLC")

    /** The Home and All Apps as the owner sees them, sharing one library that follows package events. */
    private class Screens(val home: HomeStateHolder, val allApps: AllAppsStateHolder)

    private suspend fun TestScope.screens(platform: FakeTvPlatform): Screens {
        val library = AppLibrary(platform)
        backgroundScope.launch { library.followPackageEvents() }
        val screens = Screens(HomeStateHolder(platform, library, backgroundScope), AllAppsStateHolder(platform, library, backgroundScope))
        library.load()
        return screens
    }

    private fun Screens.tray() = home.state.value.appTray.filterIsInstance<AppTrayItem.App>().map { it.packageName }
    private fun Screens.allAppsLabels() = allApps.state.value.apps.map { it.label }
    private fun Screens.pinnedInAllApps() = allApps.state.value.apps.filter { it.isPinned }.map { it.packageName }

    @Test
    fun `All Apps lists every installed app alphabetically and marks the pinned ones`() = runTest(UnconfinedTestDispatcher()) {
        val screens = screens(FakeTvPlatform(apps = listOf(vlc, youtube, kodi, netflix), pinned = listOf(youtube.packageName, kodi.packageName)))

        assertEquals(listOf("Kodi", "Netflix", "VLC", "YouTube"), screens.allAppsLabels())
        assertEquals(listOf(kodi.packageName, youtube.packageName), screens.pinnedInAllApps())
        assertEquals(2, screens.allApps.state.value.pinnedCount)
    }

    @Test
    fun `pinning adds the app to the end of the App Tray and the owner keeps it`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(apps = listOf(netflix, youtube, kodi), pinned = listOf(netflix.packageName))
        val screens = screens(platform)

        screens.allApps.togglePin(kodi.packageName)

        assertEquals(listOf(netflix.packageName, kodi.packageName), screens.tray())
        assertEquals(listOf(netflix.packageName, kodi.packageName), platform.pinned)
        assertEquals(listOf(kodi.packageName, netflix.packageName), screens.pinnedInAllApps())
    }

    @Test
    fun `unpinning takes the app off the App Tray but it stays in All Apps`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(apps = listOf(netflix, youtube), pinned = listOf(netflix.packageName, youtube.packageName))
        val screens = screens(platform)

        screens.allApps.togglePin(netflix.packageName)

        assertEquals(listOf(youtube.packageName), screens.tray())
        assertEquals(listOf(youtube.packageName), platform.pinned)
        assertEquals(listOf("Netflix", "YouTube"), screens.allAppsLabels())
    }

    @Test
    fun `uninstalling asks Android for confirmation and changes nothing until it completes`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(apps = listOf(netflix, kodi), pinned = listOf(kodi.packageName))
        val screens = screens(platform)

        screens.allApps.uninstall(kodi.packageName)

        assertEquals(listOf(kodi.packageName), platform.uninstallRequests)
        assertEquals(listOf(kodi.packageName), screens.tray())
        assertEquals(listOf("Kodi", "Netflix"), screens.allAppsLabels())
    }

    @Test
    fun `a completed uninstall removes the app from All Apps, the App Tray and the owner's pins`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(apps = listOf(netflix, kodi), pinned = listOf(kodi.packageName, netflix.packageName))
        val screens = screens(platform)

        screens.allApps.uninstall(kodi.packageName)
        platform.completeUninstall(kodi.packageName)

        assertEquals(listOf("Netflix"), screens.allAppsLabels())
        assertEquals(listOf(netflix.packageName), screens.tray())
        assertEquals(listOf(netflix.packageName), platform.pinned)
    }

    @Test
    fun `reinstalling a previously pinned app does not pin it again`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(apps = listOf(netflix, kodi), pinned = listOf(kodi.packageName))
        val screens = screens(platform)

        platform.completeUninstall(kodi.packageName)
        platform.install(kodi)

        assertEquals(emptyList<String>(), screens.tray())
        assertEquals(listOf("Kodi", "Netflix"), screens.allAppsLabels())
    }

    @Test
    fun `a newly installed app appears in All Apps without being pinned`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(apps = listOf(netflix), pinned = listOf(netflix.packageName))
        val screens = screens(platform)

        platform.install(vlc)

        assertEquals(listOf("Netflix", "VLC"), screens.allAppsLabels())
        assertEquals(listOf(netflix.packageName), screens.tray())
    }

    @Test
    fun `Install app opens the Play Store`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(apps = listOf(netflix))
        val screens = screens(platform)

        screens.allApps.installApp()

        assertEquals(1, platform.playStoreOpened)
    }

    @Test
    fun `App info opens Android's page for that app, and selecting an app opens it`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(apps = listOf(netflix, kodi))
        val screens = screens(platform)

        screens.allApps.openAppInfo(kodi.packageName)
        screens.allApps.open(netflix.packageName)

        assertEquals(listOf(kodi.packageName), platform.appInfoOpened)
        assertEquals(listOf(netflix.packageName), platform.launched)
    }
}
