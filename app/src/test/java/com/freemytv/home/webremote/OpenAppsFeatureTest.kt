package com.freemytv.home.webremote

import com.freemytv.home.monitor.Importance
import com.freemytv.home.monitor.ProcessMemory
import com.freemytv.home.multitasking.Multitasking
import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.app
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** Abertos over the bridge, against the protocol's examples. */
class OpenAppsFeatureTest {

    private val youtube = app("com.google.android.youtube.tv", "YouTube")
    private val spotify = app("com.spotify.tv.android", "Spotify")
    private val netflix = app("com.netflix.ninja", "Netflix")
    private val now = Instant.parse("2026-10-24T00:40:00Z")

    private val platform = FakeTvPlatform(
        name = "TV da sala",
        apps = listOf(netflix, youtube, spotify),
        processes = listOf(
            ProcessMemory(netflix.packageName, 100, Importance.Background),
            ProcessMemory(spotify.packageName, 100, Importance.Perceptible),
            ProcessMemory(youtube.packageName, 100, Importance.Background),
        ),
        lastUsed = mapOf(youtube.packageName to now.minusSeconds(60), spotify.packageName to now.minusSeconds(600), netflix.packageName to now.minusSeconds(3600)),
    ).apply { adbLinked = true }
    private val multitasking = Multitasking(platform)
    private val features = CompanionFeatures().apply { addOpenApps(multitasking) }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    @Test
    fun `Open Apps are listed most recent first, with their pictures and playing, shaped like the example`() = runTest {
        val items = features.reply(example("list-request").put("of", "open-apps")).getJSONArray("items").objects()

        val expected = example("list-open-apps").getJSONArray("items").objects()
        assertEquals(expected.map { it.keySet() }, items.map { it.keySet() })
        assertEquals(expected.map { it.getString("package") }, items.map { it.getString("package") })
        assertEquals(expected.map { it.getBoolean("playing") }, items.map { it.getBoolean("playing") })
        assertEquals("app:${youtube.packageName}", items[0].getString("picture"))
    }

    @Test
    fun `closing an Open App force stops it, as the App Switcher's ✕ does`() = runTest {
        assertTrue(example("done").put("id", 0).similar(features.reply(example("close-app")).put("id", 0)))
        assertEquals(listOf(youtube.packageName), platform.forceStopped)
    }

    @Test
    fun `an app playing music, or one that isn't open, is never closed`() = runTest {
        assertEquals("playing", features.reply(example("close-app").put("package", spotify.packageName)).getString("reason"))
        assertEquals("not-open", features.reply(example("close-app").put("package", "gone.app")).getString("reason"))
        assertTrue(platform.forceStopped.isEmpty())
    }

    @Test
    fun `the list is pushed when the Open Apps change, only while a phone is ready`() = runTest {
        val phones = PairedPhones(platform)
        val bridge = WebRemoteBridge(platform, phones, features)
        val received = mutableListOf<JSONObject>()
        val watcher = launch { bridge.watchOpenApps(multitasking, every = 5.seconds) }
        runCurrent()

        platform.processes = platform.processes!!.drop(1)
        advanceTimeBy(6_000)
        assertTrue("nobody is listening yet", received.isEmpty())

        val credential = example("hello").getString("credential")
        phones.add(PairedPhone("iPhone", sha256(credential), Instant.EPOCH))
        bridge.connect(object : RemotePeer {
            override fun send(text: String) { received += JSONObject(text) }
            override fun close() {}
        }).onText(example("hello").toString())
        advanceTimeBy(6_000)
        assertEquals(listOf("state"), received.map { it.getString("type") })

        platform.processes = platform.processes!!.drop(1)
        advanceTimeBy(6_000)
        val pushed = received.last()
        assertEquals("open-apps", pushed.getString("of"))
        assertEquals(listOf(youtube.packageName), pushed.getJSONArray("items").objects().map { it.getString("package") })
        watcher.cancel()
    }
}
