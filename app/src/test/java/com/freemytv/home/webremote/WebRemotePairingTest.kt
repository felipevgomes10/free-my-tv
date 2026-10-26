package com.freemytv.home.webremote

import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Pairing from the TV's side, checked against the protocol's examples from the pinned page's zip. */
class WebRemotePairingTest {

    private val platform = FakeTvPlatform()
    private val phones = PairedPhones(platform)
    private val pairing = WebRemotePairing(platform, phones, Random(7))

    private fun request(code: String, name: String = example("pair-request").getString("name")) =
        JSONObject(example("pair-request").toString()).put("code", code).put("name", name).toString()

    private fun assertAnswer(expected: Pair<Int, String>, actual: Pair<Int, String>) {
        assertEquals(expected.first, actual.first)
        assertTrue("${actual.second} isn't ${expected.second}", JSONObject(expected.second).similar(JSONObject(actual.second)))
    }

    @Test
    fun `Pairing is refused while Parear aparelho is closed`() = runTest {
        assertAnswer(409 to example("pair-closed").toString(), pairing.answer(request("4827")))
    }

    @Test
    fun `opening shows the bridge's address and a four-digit code`() = runTest {
        pairing.open()

        val state = pairing.state.value
        assertEquals("192.168.100.18:$WEB_REMOTE_PORT", state.address)
        assertTrue(state.code.matches(Regex("\\d{4}")))
    }

    @Test
    fun `the right code and a name make a Paired Phone and answer its credential`() = runTest {
        pairing.open()
        val code = pairing.state.value.code

        val (status, body) = pairing.answer(request(code))

        assertEquals(200, status)
        val credential = JSONObject(body).getString("credential")
        assertEquals(example("pair-success").keySet(), JSONObject(body).keySet())
        val phone = phones.find(credential)!!
        assertEquals("iPhone da Ana", phone.name)
        assertEquals(listOf(phone), platform.pairedPhones)
        assertEquals("iPhone da Ana", pairing.state.value.justPaired)
    }

    @Test
    fun `a code is good for one phone only`() = runTest {
        pairing.open()
        val code = pairing.state.value.code
        pairing.answer(request(code))

        assertNotEquals(code, pairing.state.value.code)
        assertAnswer(401 to example("pair-wrong-code").toString(), pairing.answer(request(code, "iPad")))
    }

    @Test
    fun `wrong codes are refused, and the third changes the code`() = runTest {
        pairing.open()
        val code = pairing.state.value.code
        val wrong = if (code == "0000") "1111" else "0000"

        assertAnswer(401 to example("pair-wrong-code").toString(), pairing.answer(request(wrong)))
        assertAnswer(401 to example("pair-wrong-code").toString(), pairing.answer(request(wrong)))
        assertAnswer(429 to example("pair-too-many-tries").toString(), pairing.answer(request(wrong)))

        assertNotEquals(code, pairing.state.value.code)
        assertEquals(401, pairing.answer(request(code)).first)
        assertEquals(200, pairing.answer(request(pairing.state.value.code)).first)
    }

    @Test
    fun `closing Parear aparelho ends Pairing`() = runTest {
        pairing.open()
        val code = pairing.state.value.code
        pairing.close()

        assertEquals(409, pairing.answer(request(code)).first)
    }

    @Test
    fun `a request that isn't JSON is refused without pairing`() = runTest {
        pairing.open()

        assertEquals(400, pairing.answer("code=4827").first)
        assertTrue(platform.pairedPhones.isEmpty())
    }

    @Test
    fun `revoking a Paired Phone forgets it and tells the bridge`() = runTest {
        pairing.open()
        val credential = JSONObject(pairing.answer(request(pairing.state.value.code)).second).getString("credential")
        val revoked = async(UnconfinedTestDispatcher(testScheduler)) { phones.revoked.first() }

        phones.revoke(phones.find(credential)!!)

        assertEquals(null, phones.find(credential))
        assertTrue(platform.pairedPhones.isEmpty())
        assertEquals(sha256(credential), revoked.await())
    }

    @Test
    fun `Paired Phones survive the Home restarting`() = runTest {
        pairing.open()
        val credential = JSONObject(pairing.answer(request(pairing.state.value.code)).second).getString("credential")

        val later = PairedPhones(platform)
        later.load()

        assertEquals("iPhone da Ana", later.find(credential)?.name)
    }
}

/** One of the protocol's JSON examples, unpacked from the pinned page's zip into the test resources. */
internal fun example(name: String): JSONObject {
    val stream = WebRemotePairingTest::class.java.classLoader!!.getResourceAsStream("web-remote-protocol/examples/$name.json")
        ?: error("No protocol example $name: pin a page in gradle.properties, or build with -PwebRemoteZip=<path>")
    return JSONObject(stream.bufferedReader().readText())
}
