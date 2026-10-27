package com.freemytv.home.webremote

import com.freemytv.home.multitasking.OverlayResult
import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AppSwitcherFeatureTest {
    private val platform = FakeTvPlatform().apply { adbLinked = true }
    private val phones = PairedPhones(platform)
    private val bridge = WebRemoteBridge(platform, phones).apply { companion.addAppSwitcher(platform) }
    private class Phone : RemotePeer {
        val received = mutableListOf<JSONObject>()
        override fun send(text: String) { received += JSONObject(text) }
        override fun close() = Unit
    }
    private suspend fun connect(paired: Boolean = true): Pair<Phone, WebRemoteBridge.Session> {
        if (paired) phones.add(PairedPhone("Test", sha256(example("hello").getString("credential")), Instant.EPOCH))
        val phone = Phone()
        val session = bridge.connect(phone)
        session.onText(example("hello").toString())
        return phone to session
    }

    @Test fun `a ready phone opens the overlay with the shared same-id result and no Home or key`() = runTest {
        val (phone, session) = connect()
        session.onText(example("open-app-switcher").toString())
        assertTrue(example("app-switcher-shown").similar(phone.received.last()))
        assertEquals(1, platform.overlayOpens)
        assertTrue(platform.adbKeys.isEmpty())
        assertTrue(platform.homeOpenedAt.isEmpty())
    }

    @Test fun `overlay failures match the executable shared examples`() = runTest {
        val (phone, session) = connect()
        for ((result, file) in listOf(OverlayResult.Unavailable to "app-switcher-unavailable", OverlayResult.Busy to "app-switcher-busy", OverlayResult.Error to "app-switcher-error")) {
            platform.overlayResult = result
            session.onText(example("open-app-switcher").toString())
            assertTrue(example(file).similar(phone.received.last()))
        }
    }

    @Test fun `requests before hello, from an unknown phone, and while not linked cannot open a window`() = runTest {
        bridge.connect(Phone()).onText(example("open-app-switcher").toString())
        val (_, unknown) = connect(paired = false)
        unknown.onText(example("open-app-switcher").toString())
        platform.adbLinked = false
        val (_, notLinked) = connect()
        notLinked.onText(example("open-app-switcher").toString())
        assertEquals(0, platform.overlayOpens)
    }

    @Test fun `revocation stops an already connected phone opening the overlay`() = runTest {
        val (_, session) = connect()
        val hash = sha256(example("hello").getString("credential"))
        bridge.revoked(hash)
        session.onText(example("open-app-switcher").toString())
        assertEquals(0, platform.overlayOpens)
    }
}
