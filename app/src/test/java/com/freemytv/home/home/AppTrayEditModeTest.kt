package com.freemytv.home.home

import com.freemytv.home.apps.AppLibrary
import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.app
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppTrayEditModeTest {

    private val netflix = app("com.netflix.ninja", "Netflix")
    private val youtube = app("com.google.android.youtube.tv", "YouTube")
    private val prime = app("com.amazon.amazonvideo.livingroom", "Prime Video")
    private val plex = app("com.plexapp.android", "Plex")

    private val pins = listOf(netflix, youtube, prime, plex).map { it.packageName }

    private fun platform() = FakeTvPlatform(apps = listOf(netflix, youtube, prime, plex), pinned = pins)

    private suspend fun TestScope.home(platform: FakeTvPlatform) =
        HomeStateHolder(platform, AppLibrary(platform), backgroundScope).also { it.refresh() }

    private fun HomeStateHolder.tray() = state.value.appTray.map { (it as? AppTrayItem.App)?.packageName ?: "AllApps" }

    private fun HomeStateHolder.item(packageName: String) =
        state.value.appTray.filterIsInstance<AppTrayItem.App>().first { it.packageName == packageName }

    @Test
    fun `holding OK on an app lifts it into Edit Mode`() = runTest(UnconfinedTestDispatcher()) {
        val home = home(platform())

        home.startEdit(home.item(prime.packageName))

        assertEquals(prime.packageName, home.state.value.lifted?.packageName)
    }

    @Test
    fun `the lifted app moves left and right along the App Tray`() = runTest(UnconfinedTestDispatcher()) {
        val home = home(platform())
        home.startEdit(home.item(prime.packageName))

        home.move(-1)
        assertEquals(listOf(netflix, prime, youtube, plex).map { it.packageName } + "AllApps", home.tray())

        home.move(+2)
        assertEquals(listOf(netflix, youtube, plex, prime).map { it.packageName } + "AllApps", home.tray())
    }

    @Test
    fun `the lifted app stops at either end and never passes Todos os apps`() = runTest(UnconfinedTestDispatcher()) {
        val home = home(platform())
        home.startEdit(home.item(youtube.packageName))

        home.move(-5)
        assertEquals(listOf(youtube, netflix, prime, plex).map { it.packageName } + "AllApps", home.tray())

        home.move(+9)
        assertEquals(listOf(netflix, prime, plex, youtube).map { it.packageName } + "AllApps", home.tray())
    }

    @Test
    fun `dropping keeps the new order, leaves Edit Mode and survives a restart`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform()
        val home = home(platform)
        home.startEdit(home.item(plex.packageName))
        home.move(-3)

        home.drop()

        val expected = listOf(plex, netflix, youtube, prime).map { it.packageName }
        assertNull(home.state.value.lifted)
        assertEquals(expected + "AllApps", home.tray())
        assertEquals(expected + "AllApps", home(platform).tray())
    }

    @Test
    fun `cancelling puts the app back where it was and saves nothing`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform()
        val home = home(platform)
        home.startEdit(home.item(netflix.packageName))
        home.move(+2)

        home.cancelEdit()

        assertNull(home.state.value.lifted)
        assertEquals(pins + "AllApps", home.tray())
        assertEquals(pins, platform.pinned)
    }

    @Test
    fun `Todos os apps cannot be lifted`() = runTest(UnconfinedTestDispatcher()) {
        val home = home(platform())

        home.startEdit(AppTrayItem.AllApps)

        assertNull(home.state.value.lifted)
    }

    @Test
    fun `Unpin from Edit Mode takes the app off the App Tray and leaves Edit Mode`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform()
        val home = home(platform)
        home.startEdit(home.item(youtube.packageName))

        home.unpinLifted()

        assertNull(home.state.value.lifted)
        assertEquals(listOf(netflix, prime, plex).map { it.packageName } + "AllApps", home.tray())
        assertEquals(listOf(netflix, prime, plex).map { it.packageName }, platform.pinned)
    }

    @Test
    fun `Uninstall from Edit Mode asks Android and leaves Edit Mode`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform()
        val home = home(platform)
        home.startEdit(home.item(plex.packageName))

        home.uninstallLifted()

        assertNull(home.state.value.lifted)
        assertEquals(listOf(plex.packageName), platform.uninstallRequests)
        assertEquals(pins + "AllApps", home.tray())
    }

    @Test
    fun `reordering keeps pins of apps that are not installed right now`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(apps = listOf(netflix, youtube), pinned = listOf(netflix.packageName, plex.packageName, youtube.packageName))
        val home = home(platform)
        home.startEdit(home.item(youtube.packageName))
        home.move(-1)

        home.drop()

        assertEquals(listOf(youtube.packageName, netflix.packageName, plex.packageName), platform.pinned)
    }
}
