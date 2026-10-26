package com.freemytv.home.webremote

import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.setup.SetupCheck
import com.freemytv.home.setup.SetupWatchdog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WebRemoteLinkTest {

    private val platform = FakeTvPlatform()
    private val link = WebRemoteLink(platform)

    @Test
    fun `a Home never linked says so, without showing the prompt`() = runTest {
        link.refresh()

        assertEquals(LinkState.NotLinked, link.state.value)
        assertEquals(0, platform.adbLinkRequests)
    }

    @Test
    fun `accepting the prompt links the Home, and it stays linked`() = runTest {
        link.link()
        assertEquals(LinkState.Linked, link.state.value)

        val later = WebRemoteLink(platform)
        later.refresh()
        assertEquals(LinkState.Linked, later.state.value)
    }

    @Test
    fun `a prompt refused or left unanswered is told apart from success, and the Owner can try again`() = runTest {
        platform.adbAnswer = AdbLinkResult.NotAccepted
        link.link()
        assertEquals(LinkState.NotAccepted, link.state.value)

        link.refresh()
        assertEquals(LinkState.NotAccepted, link.state.value)

        platform.adbAnswer = AdbLinkResult.Linked
        link.link()
        assertEquals(LinkState.Linked, link.state.value)
        assertEquals(2, platform.adbLinkRequests)
    }

    @Test
    fun `network debugging off is told apart, before and while linking`() = runTest {
        platform.networkDebuggingOn = false
        link.refresh()
        assertEquals(LinkState.DebuggingOff, link.state.value)

        link.link()
        assertEquals(LinkState.DebuggingOff, link.state.value)
    }

    @Test
    fun `a linked Home whose network debugging went off says so`() = runTest {
        link.link()
        platform.networkDebuggingOn = false

        link.refresh()

        assertEquals(LinkState.DebuggingOff, link.state.value)
    }

    @Test
    fun `Setup status reports network debugging off`() = runTest {
        platform.setup = SetupCheck.entries.associateWith { it != SetupCheck.NetworkDebugging }
        val watchdog = SetupWatchdog(platform)

        watchdog.check()

        assertFalse(watchdog.state.value.checks.getValue(SetupCheck.NetworkDebugging))
        assertFalse(watchdog.state.value.active)
    }
}
