package com.freemytv.home.webremote

import com.freemytv.home.platform.InstalledApp
import com.freemytv.home.buttons.ButtonTarget
import com.freemytv.home.buttons.HomeDestination
import com.freemytv.home.buttons.ShortcutButton
import com.freemytv.home.buttons.ShortcutButtons
import com.freemytv.home.inputs.InputPreferences
import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutsFeatureTest {

    private val platform = FakeTvPlatform(
        apps = listOf(
            InstalledApp("com.netflix.ninja", "Netflix", true),
            InstalledApp("com.globo.globotv", "Globoplay", true),
            InstalledApp("com.amazon.amazonvideo.livingroom", "Prime Video", true),
            InstalledApp("com.disney.disneyplus", "Disney+", true),
            InstalledApp("com.google.android.youtube.tv", "YouTube", true),
            InstalledApp("tv.plex.labs", "Plex", true),
        ),
        inputPreferences = InputPreferences(names = mapOf("hdmi1" to "PlayStation")),
    )
    private val buttons = ShortcutButtons(platform)
    private val features = CompanionFeatures().apply { addShortcuts(buttons, platform) }

    private suspend fun reply(request: JSONObject) = features.reply(request)

    @Test
    fun `lists every button by what it does, shaped like the protocol's example`() = runTest {
        buttons.assign(ShortcutButton.Input, ButtonTarget.TvInput("hdmi1"))

        val answer = reply(example("list-request").put("of", "shortcuts"))

        val expected = example("list-shortcuts").getJSONArray("items")
        val items = answer.getJSONArray("items")
        assertEquals(expected.length(), items.length())
        for (i in 0 until items.length()) {
            assertEquals(expected.getJSONObject(i).keySet(), items.getJSONObject(i).keySet())
            assertEquals(expected.getJSONObject(i).getString("shortcut"), items.getJSONObject(i).getString("shortcut"))
        }
        assertEquals(listOf("Netflix", "Globoplay", "Prime Video", "Disney+", "YouTube", "PlayStation"), (0 until items.length()).map { items.getJSONObject(it).getString("label") })
    }

    @Test
    fun `names the Owner's reassignments`() = runTest {
        buttons.assign(ShortcutButton.Netflix, ButtonTarget.App("tv.plex.labs"))
        buttons.assign(ShortcutButton.YouTube, ButtonTarget.AppSwitcher)

        val items = reply(example("list-request").put("of", "shortcuts")).getJSONArray("items")

        assertEquals("Plex", items.getJSONObject(0).getString("label"))
        assertEquals("Apps abertos", items.getJSONObject(4).getString("label"))
        assertEquals("Entradas", items.getJSONObject(5).getString("label"))
    }

    @Test
    fun `opening a button does what the Remote's button does`() = runTest {
        assertEquals("done", reply(example("open-shortcut")).getString("type"))
        assertEquals(listOf("com.netflix.ninja"), platform.launched)

        reply(example("open-shortcut").put("shortcut", "input"))
        assertEquals(listOf(HomeDestination.InputTray), platform.homeOpenedAt)
    }

    @Test
    fun `a button whose app isn't installed is named as printed on the Remote`() = runTest {
        buttons.assign(ShortcutButton.Globoplay, ButtonTarget.App("gone.app"))

        val items = reply(example("list-request").put("of", "shortcuts")).getJSONArray("items")

        assertEquals("Globoplay", items.getJSONObject(1).getString("label"))
    }

    @Test
    fun `an app that isn't installed is refused`() = runTest {
        buttons.assign(ShortcutButton.Netflix, ButtonTarget.App("gone.app"))

        val answer = reply(example("open-shortcut"))

        assertEquals("not-installed", answer.getString("reason"))
        assertEquals(emptyList<String>(), platform.launched)
    }
}
