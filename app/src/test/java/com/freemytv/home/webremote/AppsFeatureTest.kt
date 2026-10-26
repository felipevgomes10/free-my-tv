package com.freemytv.home.webremote

import com.freemytv.home.apps.AppLibrary
import com.freemytv.home.inputs.InputKind
import com.freemytv.home.inputs.InputPreferences
import com.freemytv.home.inputs.InputTray
import com.freemytv.home.inputs.Signal
import com.freemytv.home.inputs.TvInputSource
import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.InstalledApp
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

/** Todos os apps and Entradas over the bridge, against the protocol's examples. */
class AppsFeatureTest {

    private val tcl = "com.tcl.tvinput"
    private fun hdmi(n: Int) = TvInputSource("$tcl/.TvPassThroughService/HW${14 + n}", tcl, InputKind.Hdmi, true, null, "HDMI $n", Signal.Connected)
    private val hdmi1 = hdmi(1)
    private val hdmi2 = hdmi(2)
    private val hdmi3 = hdmi(3)
    private val antenna = TvInputSource("$tcl/.TunerInputService/HW2", tcl, InputKind.Tuner, false, null, "TV", Signal.Connected)

    private val platform = FakeTvPlatform(
        apps = listOf(
            InstalledApp("tv.plex.labs", "Plex", false),
            InstalledApp("com.google.android.youtube.tv", "YouTube", true),
            InstalledApp("com.netflix.ninja", "Netflix", true),
        ),
        pinned = listOf("com.netflix.ninja", "com.google.android.youtube.tv"),
        inputs = listOf(hdmi1, hdmi2, hdmi3, antenna),
        inputPreferences = InputPreferences(names = mapOf(hdmi1.id to "PlayStation"), hidden = setOf(hdmi3.id)),
    )

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    @Test
    fun `apps are listed App Tray first, then the rest by name, shaped like the example`() = runTest(UnconfinedTestDispatcher()) {
        val features = CompanionFeatures().apply { addApps(AppLibrary(platform), platform) }

        val items = features.reply(example("list-request").put("of", "apps")).getJSONArray("items").objects()

        val expected = example("list-apps").getJSONArray("items").objects()
        assertEquals(expected.map { it.keySet() }, items.map { it.keySet() })
        assertEquals(expected.map { it.getString("package") }, items.map { it.getString("package") })
        assertEquals(listOf(true, true, false), items.map { it.getBoolean("inTray") })
        assertEquals("app:tv.plex.labs", items[2].getString("picture"))
    }

    @Test
    fun `opening an app launches it, and one that isn't installed is refused`() = runTest(UnconfinedTestDispatcher()) {
        val features = CompanionFeatures().apply { addApps(AppLibrary(platform), platform) }

        assertEquals("done", features.reply(example("open-app")).getString("type"))
        assertEquals("not-installed", features.reply(example("open-app").put("package", "gone.app")).getString("reason"))
        assertEquals(listOf("com.netflix.ninja"), platform.launched)
    }

    @Test
    fun `Inputs are listed by the Owner's names, hidden ones left out, shaped like the example`() = runTest(UnconfinedTestDispatcher()) {
        val features = CompanionFeatures().apply { addInputs(InputTray(platform, backgroundScope)) }

        val items = features.reply(example("list-request").put("of", "inputs")).getJSONArray("items").objects()

        assertEquals(example("list-inputs").getJSONArray("items").objects().map { it.keySet() }, items.map { it.keySet() })
        assertEquals(listOf("PlayStation", "HDMI 2", "Antena"), items.map { it.getString("name") })
        assertEquals(listOf("HDMI 1", "HDMI 2", "TV aberta"), items.map { it.getString("port") })
        assertEquals(listOf("hdmi", "hdmi", "antenna"), items.map { it.getString("kind") })
    }

    @Test
    fun `opening an Input switches the TV to it`() = runTest(UnconfinedTestDispatcher()) {
        val features = CompanionFeatures().apply { addInputs(InputTray(platform, backgroundScope)) }
        features.reply(example("list-request").put("of", "inputs"))

        assertEquals("done", features.reply(example("open-input").put("input", hdmi2.id)).getString("type"))
        assertEquals(listOf(hdmi2.id), platform.switchedTo)
        assertEquals("unknown-input", features.reply(example("open-input").put("input", hdmi3.id)).getString("reason"))
    }
}
