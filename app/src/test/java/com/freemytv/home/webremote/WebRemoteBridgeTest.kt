package com.freemytv.home.webremote

import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The bridge's key path, checked against the protocol's examples from the pinned page's zip. */
class WebRemoteBridgeTest {

    private val platform = FakeTvPlatform(name = "TV da sala").apply { adbLinked = true }
    private val phones = PairedPhones(platform)
    private val bridge = WebRemoteBridge(platform, phones)

    private class Phone : RemotePeer {
        val received = mutableListOf<JSONObject>()
        var closed = false
        override fun send(text: String) {
            received += JSONObject(text)
        }
        override fun close() {
            closed = true
        }
        fun last() = received.last()
    }

    private suspend fun paired(credential: String = example("hello").getString("credential"), name: String = "iPhone da Ana") =
        phones.add(PairedPhone(name, sha256(credential), Instant.EPOCH))

    private suspend fun connected(hello: JSONObject = example("hello")): Pair<Phone, WebRemoteBridge.Session> {
        val phone = Phone()
        val session = bridge.connect(phone)
        session.onText(hello.toString())
        return phone to session
    }

    private fun assertSame(expected: JSONObject, actual: JSONObject) = assertTrue("$actual isn't $expected", expected.similar(actual))

    @Test
    fun `a Paired Phone's hello is answered ready, with the TV's name`() = runTest {
        paired()

        val (phone) = connected()

        assertSame(example("state-ready").put("tvName", "TV da sala"), phone.last())
    }

    @Test
    fun `an unknown credential is told it's revoked, and closed`() = runTest {
        val (phone) = connected()

        assertSame(example("state-revoked").put("tvName", "TV da sala"), phone.last())
        assertTrue(phone.closed)
    }

    @Test
    fun `a page speaking another protocol is told to reload`() = runTest {
        paired()

        val (phone) = connected(example("hello").put("protocol", 1))

        assertSame(example("reload"), phone.last())
        assertTrue(phone.closed)
    }

    @Test
    fun `a key from a Paired Phone reaches the TV with its key code, and is acknowledged`() = runTest {
        paired()
        val (phone, session) = connected()

        session.onText(example("key-press").toString())

        assertEquals(listOf(22 to false), platform.adbKeys)
        assertSame(example("ack"), phone.last())
    }

    @Test
    fun `holding OK is a long press, and its release sends nothing more`() = runTest {
        paired()
        val (phone, session) = connected()

        session.onText(example("key-hold-start").toString())
        session.onText(example("key-hold-end").toString())

        assertEquals(listOf(23 to true), platform.adbKeys)
        assertSame(JSONObject(example("ack").toString()).put("id", 2), phone.last())
    }

    @Test
    fun `holding Home opens the Notification Panel instead of sending a key`() = runTest {
        paired()
        val (phone, session) = connected()

        session.onText(example("key-hold-start").put("key", "home").toString())
        session.onText(example("key-hold-end").put("key", "home").toString())

        assertEquals(listOf(com.freemytv.home.buttons.HomeDestination.NotificationPanel), platform.homeOpenedAt)
        assertTrue(platform.adbKeys.isEmpty())
        assertEquals("ack", phone.last().getString("type"))
    }

    @Test
    fun `wake turns the TV on and never toggles its power`() = runTest {
        paired()
        val (_, session) = connected()

        session.onText(example("key-wake").toString())

        assertEquals(listOf(224 to false), platform.adbKeys)
    }

    @Test
    fun `every intention has its key code`() = runTest {
        paired()
        val (_, session) = connected()
        val codes = mapOf(
            "up" to 19, "down" to 20, "left" to 21, "right" to 22, "ok" to 23, "back" to 4, "home" to 3,
            "play-pause" to 85, "volume-up" to 24, "volume-down" to 25, "mute" to 164, "power" to 26, "wake" to 224,
        )

        codes.keys.forEachIndexed { i, key -> session.onText(example("key-press").put("id", i + 1).put("key", key).toString()) }

        assertEquals(codes.values.map { it to false }, platform.adbKeys)
    }

    @Test
    fun `keys before a hello, or from a phone without a credential, never reach the TV`() = runTest {
        val phone = Phone()
        val session = bridge.connect(phone)
        session.onText(example("key-press").toString())

        connected().second.onText(example("key-press").toString())

        assertTrue(platform.adbKeys.isEmpty())
    }

    @Test
    fun `a Home not linked says so, sends nothing, and turns ready once linked`() = runTest {
        paired()
        platform.adbLinked = false
        val (phone, session) = connected()

        assertSame(example("state-not-linked").put("tvName", "TV da sala"), phone.last())
        session.onText(example("key-press").toString())
        assertTrue(platform.adbKeys.isEmpty())

        platform.adbLinked = true
        bridge.linkChanged()
        assertSame(example("state-ready").put("tvName", "TV da sala"), phone.last())
        session.onText(example("key-press").toString())
        assertEquals(listOf(22 to false), platform.adbKeys)
    }

    @Test
    fun `a key the TV refuses turns the page to not linked`() = runTest {
        paired()
        val (phone, session) = connected()
        platform.adbTakesKeys = false
        platform.adbLinked = false

        session.onText(example("key-press").toString())

        assertEquals("not-linked", phone.last().getString("state"))
    }

    @Test
    fun `revoking a phone closes its connection, and the other phone's stays open`() = runTest {
        paired("ana")
        paired("felipe", "iPhone do Felipe")
        val (ana) = connected(example("hello").put("credential", "ana"))
        val (felipe, felipeSession) = connected(example("hello").put("credential", "felipe"))

        phones.find("ana")?.let { phones.revoke(it) }
        bridge.revoked(sha256("ana"))

        assertEquals("revoked", ana.last().getString("state"))
        assertTrue(ana.closed)
        assertEquals(false, felipe.closed)
        felipeSession.onText(example("key-press").toString())
        assertEquals(listOf(22 to false), platform.adbKeys)
    }
}
