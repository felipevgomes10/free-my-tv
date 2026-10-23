package com.freemytv.home.buttons

import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.app
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutButtonsTest {

    private val home = 3
    private val dpadCenter = 23
    private val tvInput = 178

    private val factoryApps = listOf(
        app("com.netflix.ninja", "Netflix"),
        app("com.globo.globotv", "Globoplay"),
        app("com.amazon.amazonvideo.livingroom", "Prime Video"),
        app("com.disney.disneyplus", "Disney+"),
        app("com.google.android.youtube.tv", "YouTube"),
        app("com.spotify.tv.android", "Spotify"),
    )

    private suspend fun buttons(platform: FakeTvPlatform) = ShortcutButtons(platform).also { it.load() }

    /** Presses and releases a button the way the key filter sees it, carrying out what it asks. */
    private fun ShortcutButtons.press(scanCode: Int, keyCode: Int = 0): List<KeyDecision> {
        val down = onKey(scanCode, keyCode, down = true, repeatCount = 0)
        val up = onKey(scanCode, keyCode, down = false, repeatCount = 0)
        listOf(down, up).forEach { if (it is KeyDecision.Consume) it.action?.let(::perform) }
        return listOf(down, up)
    }

    @Test
    fun `by default each Shortcut Button opens its own app, even with the Stock Launcher gone`() = runTest {
        val platform = FakeTvPlatform(apps = factoryApps)
        val buttons = buttons(platform)

        ShortcutButton.entries.filter { it.factoryApp != null }.forEach { buttons.press(it.scanCode) }

        assertEquals(
            listOf("com.netflix.ninja", "com.globo.globotv", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.google.android.youtube.tv"),
            platform.launched,
        )
    }

    @Test
    fun `a button is swallowed on press and release and acts once`() = runTest {
        val platform = FakeTvPlatform(apps = factoryApps)

        val decisions = buttons(platform).press(ShortcutButton.Netflix.scanCode)

        assertEquals(listOf(KeyDecision.Consume(ButtonTarget.App("com.netflix.ninja")), KeyDecision.Consume(null)), decisions)
        assertEquals(1, platform.launched.size)
    }

    @Test
    fun `Input opens the Home focused on the Input Tray`() = runTest {
        val platform = FakeTvPlatform(apps = factoryApps)

        buttons(platform).press(ShortcutButton.Input.scanCode, tvInput)

        assertEquals(listOf(HomeDestination.InputTray), platform.homeOpenedAt)
    }

    @Test
    fun `a button can be reassigned to another app, an Input or the App Switcher, and it persists`() = runTest {
        val platform = FakeTvPlatform(apps = factoryApps)
        val buttons = buttons(platform)

        buttons.assign(ShortcutButton.PrimeVideo, ButtonTarget.AppSwitcher)
        buttons.assign(ShortcutButton.DisneyPlus, ButtonTarget.TvInput("com.tcl.tvinput/.TvPassThroughService/HW15"))
        buttons.assign(ShortcutButton.Netflix, ButtonTarget.App("com.spotify.tv.android"))

        val reloaded = buttons(platform)
        reloaded.press(ShortcutButton.PrimeVideo.scanCode)
        reloaded.press(ShortcutButton.DisneyPlus.scanCode)
        reloaded.press(ShortcutButton.Netflix.scanCode)

        assertEquals(listOf(HomeDestination.AppSwitcher), platform.homeOpenedAt)
        assertEquals(listOf("com.tcl.tvinput/.TvPassThroughService/HW15"), platform.switchedTo)
        assertEquals(listOf("com.spotify.tv.android"), platform.launched)
    }

    @Test
    fun `restoring a button brings back its factory app`() = runTest {
        val platform = FakeTvPlatform(apps = factoryApps)
        val buttons = buttons(platform)
        buttons.assign(ShortcutButton.YouTube, ButtonTarget.AppSwitcher)

        buttons.restoreFactory(ShortcutButton.YouTube)
        buttons.press(ShortcutButton.YouTube.scanCode)

        assertEquals(listOf("com.google.android.youtube.tv"), platform.launched)
        assertEquals(emptyMap<ShortcutButton, ButtonTarget>(), platform.buttonAssignments)
    }

    @Test
    fun `a button whose app isn't installed passes through untouched`() = runTest {
        val platform = FakeTvPlatform(apps = factoryApps.filterNot { it.packageName == "com.disney.disneyplus" })

        val decisions = buttons(platform).press(ShortcutButton.DisneyPlus.scanCode)

        assertEquals(listOf(KeyDecision.Pass, KeyDecision.Pass), decisions)
        assertEquals(emptyList<String>(), platform.launched)
    }

    @Test
    fun `other keys pass through untouched`() = runTest {
        val buttons = buttons(FakeTvPlatform(apps = factoryApps))

        assertEquals(listOf(KeyDecision.Pass, KeyDecision.Pass), buttons.press(scanCode = 28, keyCode = dpadCenter))
    }

    @Test
    fun `a short Home press is untouched`() = runTest {
        val buttons = buttons(FakeTvPlatform(apps = factoryApps))

        assertEquals(KeyDecision.Pass, buttons.onKey(172, home, down = true, repeatCount = 0))
        assertEquals(KeyDecision.Pass, buttons.onKey(172, home, down = false, repeatCount = 0, heldMillis = 120))
    }

    @Test
    fun `holding Home on a remote that doesn't repeat, like the owner's, opens the Notification Panel on release`() = runTest {
        val buttons = buttons(FakeTvPlatform(apps = factoryApps))

        assertEquals(KeyDecision.Pass, buttons.onKey(172, home, down = true, repeatCount = 0))
        assertEquals(KeyDecision.PassAndOpenNotifications, buttons.onKey(172, home, down = false, repeatCount = 0, heldMillis = 2_757))
    }

    @Test
    fun `holding Home on a remote that repeats opens the Notification Panel once, not again on release`() = runTest {
        val buttons = buttons(FakeTvPlatform(apps = factoryApps))

        assertEquals(KeyDecision.Pass, buttons.onKey(172, home, down = true, repeatCount = 0))
        assertEquals(KeyDecision.PassAndOpenNotifications, buttons.onKey(172, home, down = true, repeatCount = 1))
        assertEquals(KeyDecision.Pass, buttons.onKey(172, home, down = true, repeatCount = 2))
        assertEquals(KeyDecision.Pass, buttons.onKey(172, home, down = false, repeatCount = 0, heldMillis = 1_200))

        assertEquals(KeyDecision.Pass, buttons.onKey(172, home, down = true, repeatCount = 0))
        assertEquals(KeyDecision.PassAndOpenNotifications, buttons.onKey(172, home, down = false, repeatCount = 0, heldMillis = 900))
    }

    @Test
    fun `the Home turns its key filter back on when the system turned it off`() = runTest {
        val platform = FakeTvPlatform(apps = factoryApps, keyFilterEnabled = false)

        assertTrue(buttons(platform).ensureKeyFilter())
        assertTrue(platform.keyFilterEnabled)
    }

    @Test
    fun `without the permission Setup grants, the key filter can't be turned back on`() = runTest {
        val platform = FakeTvPlatform(apps = factoryApps, keyFilterEnabled = false, canWriteSecureSettings = false)

        assertFalse(buttons(platform).ensureKeyFilter())
    }
}
