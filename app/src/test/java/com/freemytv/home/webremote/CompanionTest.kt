package com.freemytv.home.webremote

import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Protocol 2's requests, answers, pushes and pictures, against the protocol's examples (ADR 0006). */
class CompanionTest {

    private val platform = FakeTvPlatform(name = "TV da sala").apply { adbLinked = true }
    private val phones = PairedPhones(platform)
    private val bridge = WebRemoteBridge(platform, phones)
    private val opened = mutableListOf<String>()

    init {
        bridge.companion.list("apps") { JSONArray().put(JSONObject().put("package", "com.netflix.ninja")) }
        bridge.companion.open("package") { request ->
            val pkg = request.getString("package")
            if (pkg == "missing") throw Refused("not-installed")
            opened += pkg
        }
    }

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

    private suspend fun ready(credential: String = example("hello").getString("credential")): Pair<Phone, WebRemoteBridge.Session> {
        if (phones.find(credential) == null) phones.add(PairedPhone("iPhone", sha256(credential), Instant.EPOCH))
        val phone = Phone()
        val session = bridge.connect(phone)
        session.onText(example("hello").put("credential", credential).toString())
        return phone to session
    }

    private fun assertShape(expected: JSONObject, actual: JSONObject) =
        assertEquals("$actual isn't shaped like $expected", expected.keySet(), actual.keySet())

    @Test
    fun `a list is answered with the request's id, shaped like the protocol's example`() = runTest {
        val (phone, session) = ready()

        session.onText(example("list-request").toString())

        val answer = phone.last()
        assertShape(example("list-answer"), answer)
        assertEquals(example("list-request").getInt("id"), answer.getInt("id"))
        assertEquals("com.netflix.ninja", answer.getJSONArray("items").getJSONObject(0).getString("package"))
    }

    @Test
    fun `an open is carried out and answered done`() = runTest {
        val (phone, session) = ready()

        session.onText(JSONObject().put("type", "open").put("id", 9).put("package", "com.netflix.ninja").toString())

        assertEquals(listOf("com.netflix.ninja"), opened)
        assertTrue(example("done").put("id", 9).similar(phone.last()))
    }

    @Test
    fun `a refused open is answered failed, with its reason`() = runTest {
        val (phone, session) = ready()

        session.onText(JSONObject().put("type", "open").put("id", 5).put("package", "missing").toString())

        assertTrue(example("failed").put("reason", "not-installed").similar(phone.last()))
    }

    @Test
    fun `unknown lists, targets and requests fail, and nothing is opened`() = runTest {
        val (phone, session) = ready()

        session.onText(JSONObject().put("type", "list").put("id", 1).put("of", "nope").toString())
        assertEquals("unknown-list", phone.last().getString("reason"))
        session.onText(JSONObject().put("type", "open").put("id", 2).put("nope", "x").toString())
        assertEquals("unknown-target", phone.last().getString("reason"))
        session.onText(JSONObject().put("type", "nope").put("id", 3).toString())
        assertEquals("unknown-request", phone.last().getString("reason"))
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `requests before the hello, or while the Home isn't linked, get nothing`() = runTest {
        val early = Phone()
        bridge.connect(early).onText(example("list-request").toString())
        assertTrue(early.received.isEmpty())

        platform.adbLinked = false
        val (phone, session) = ready()
        session.onText(example("list-request").toString())
        assertEquals("state", phone.last().getString("type"))
    }

    @Test
    fun `a pushed list reaches every ready connection, without an id`() = runTest {
        val (first) = ready("one")
        val (second) = ready("two")
        val stranger = Phone().also { bridge.connect(it) }

        bridge.push("apps")

        for (phone in listOf(first, second)) {
            assertShape(example("list-pushed"), phone.last())
            assertEquals("apps", phone.last().getString("of"))
        }
        assertTrue(stranger.received.isEmpty())
    }

    @Test
    fun `pictures need a Paired Phone's credential`() = runTest {
        val pictures = WebRemotePictures(phones)
        pictures.source("app") { pkg -> pkg.toByteArray() }
        phones.add(PairedPhone("iPhone", sha256("cred"), Instant.EPOCH))

        assertEquals(401, pictures.serve("app:com.netflix.ninja", null).first)
        assertEquals(401, pictures.serve("app:com.netflix.ninja", "Bearer someone-else").first)
        val (status, png) = pictures.serve("app:com.netflix.ninja", "Bearer cred")
        assertEquals(200, status)
        assertEquals("com.netflix.ninja", String(png!!))
        assertEquals(404 to null, pictures.serve("nope:x", "Bearer cred"))
    }

    @Test
    fun `a picture is rendered once, then kept within the budget`() = runTest {
        var renders = 0
        val pictures = WebRemotePictures(phones, budgetBytes = 10)
        pictures.source("app") { pkg -> renders++; ByteArray(6) { pkg.length.toByte() } }
        phones.add(PairedPhone("iPhone", sha256("cred"), Instant.EPOCH))

        pictures.serve("app:a", "Bearer cred")
        pictures.serve("app:a", "Bearer cred")
        assertEquals(1, renders)

        pictures.serve("app:bb", "Bearer cred")
        pictures.serve("app:a", "Bearer cred")
        assertEquals(3, renders)
    }

    @Test
    fun `memory pressure releases cached pictures and a paired phone can fetch them again`() = runTest {
        var renders = 0
        val pictures = WebRemotePictures(phones, budgetBytes = 10)
        pictures.source("app") { renders++; ByteArray(6) }
        phones.add(PairedPhone("iPhone", sha256("cred"), Instant.EPOCH))
        pictures.serve("app:a", "Bearer cred")
        pictures.clear()

        assertEquals(401, pictures.serve("app:a", null).first)
        assertEquals(200, pictures.serve("app:a", "Bearer cred").first)
        pictures.serve("app:a", "Bearer cred")
        assertEquals(2, renders)
        pictures.serve("app:b", "Bearer cred")
        pictures.serve("app:a", "Bearer cred")
        assertEquals(4, renders)
    }
}
