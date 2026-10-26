package com.freemytv.home.webremote

import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.app
import com.freemytv.home.upnext.UpNextEntry
import com.freemytv.home.upnext.UpNextTray
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Continuar assistindo over the bridge, against the protocol's examples. */
class UpNextFeatureTest {

    private val netflix = "com.netflix.ninja"
    private val max = "com.hbo.hbonow"
    private val globoplay = "com.globo.globotv"
    private val watched = Instant.parse("2026-10-23T23:00:00Z")

    private val platform = FakeTvPlatform(
        apps = listOf(app(netflix, "Netflix"), app(max, "Max"), app(globoplay, "Globoplay")),
        labels = mapOf(netflix to "Netflix", max to "Max", globoplay to "Globoplay"),
    ).apply {
        upNext = listOf(
            UpNextEntry("$netflix|Sombras do Norte", netflix, "Netflix", "Sombras do Norte", "T2, E4: O farol", 29 * 60_000L + 1, 47 * 60_000L, "/data/upnext/sombras.jpg", watched),
            UpNextEntry("$max|Cidade de vidro", max, "Max", "Cidade de vidro", "T1, E7: Reflexos", 31 * 60_000L, 103 * 60_000L, "/data/upnext/cidade.jpg", watched.minusSeconds(60)),
            UpNextEntry(globoplay, globoplay, "Globoplay", null, null, null, null, null, watched.minusSeconds(120)),
        )
    }
    private val tray = UpNextTray(platform)
    private val features = CompanionFeatures().apply { addUpNext(tray) }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    @Test
    fun `entries are listed as the tray holds them, shaped like the example`() = runTest {
        tray.load()

        val items = features.reply(example("list-request").put("of", "up-next")).getJSONArray("items").objects()

        val expected = example("list-up-next").getJSONArray("items").objects()
        assertEquals(expected.map { it.keySet() }, items.map { it.keySet() })
        for ((want, got) in expected.zip(items)) {
            for (field in listOf("upNext", "title", "episode", "app", "minutesLeft", "picture")) assertEquals(field, want.get(field).toString(), got.get(field).toString())
        }
        assertEquals(0.62, items[0].getDouble("progress"), 0.0)
        assertEquals(JSONObject.NULL, items[2].get("progress"))
    }

    @Test
    fun `opening an entry resumes it as its tile does, and a gone entry is refused`() = runTest {
        tray.load()

        assertEquals("done", features.reply(example("open-up-next").put("upNext", globoplay)).getString("type"))
        assertEquals(listOf(globoplay), platform.launched)
        assertEquals("not-found", features.reply(example("open-up-next").put("upNext", "gone")).getString("reason"))
    }
}
