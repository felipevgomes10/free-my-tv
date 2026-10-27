package com.freemytv.home.multitasking

import com.freemytv.home.monitor.Importance
import com.freemytv.home.monitor.ProcessMemory
import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.app
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SwitcherOverlayTest {
    private val platform = FakeTvPlatform(
        apps = listOf(app("current", "Atual"), app("previous", "Anterior"), app("music", "Música")),
        processes = listOf(ProcessMemory("current", 1, Importance.Foreground), ProcessMemory("previous", 1, Importance.Background), ProcessMemory("music", 1, Importance.Perceptible)),
        lastUsed = mapOf("current" to Instant.parse("2026-10-24T00:39:00Z"), "previous" to Instant.parse("2026-10-24T00:38:00Z")),
    )
    private val multitasking = Multitasking(platform)
    private val overlay = SwitcherOverlay(platform, multitasking)

    @Test fun `opening leaves the fullscreen switcher and current app alone`() = runTest {
        overlay.open("current")
        assertEquals("previous", overlay.state.value!!.switcher.apps!![overlay.state.value!!.switcher.initialFocus].packageName)
        assertNull(multitasking.switcher.value.apps)
        assertTrue(platform.launched.isEmpty())
        assertTrue(platform.homeOpenedAt.isEmpty())
    }

    @Test fun `repeated opening keeps the original snapshot and underlying app`() = runTest {
        overlay.open("current")
        val state = overlay.state.value
        overlay.open("previous")
        assertSame(state, overlay.state.value)
    }

    @Test fun `Back dismisses without launching anything and reopening can capture another app`() = runTest {
        overlay.open("current")
        overlay.dismiss()
        assertNull(overlay.state.value)
        assertTrue(platform.launched.isEmpty())
        overlay.open("previous")
        assertEquals("previous", overlay.state.value!!.underneath)
    }

    @Test fun `selecting the underlying app only dismisses, another app launches`() = runTest {
        overlay.open("current")
        overlay.select("current")
        assertNull(overlay.state.value)
        assertTrue(platform.launched.isEmpty())
        overlay.open("current")
        overlay.select("previous")
        assertNull(overlay.state.value)
        assertEquals(listOf("previous"), platform.launched)
    }

    @Test fun `underlying and media apps are never closed from the overlay`() = runTest {
        overlay.open("current")
        overlay.close("current")
        overlay.close("music")
        assertTrue(platform.forceStopped.isEmpty())
        overlay.close("previous")
        assertEquals(listOf("previous"), platform.forceStopped)
        assertFalse(overlay.state.value!!.switcher.apps!!.any { it.packageName == "previous" })
    }

    @Test fun `missing usage access withholds closing instead of risking the underlying app`() = runTest {
        overlay.open(null)
        overlay.close("previous")
        assertTrue(platform.forceStopped.isEmpty())
    }

    @Test fun `another visible or foreground process cannot be force stopped by the overlay`() = runTest {
        platform.processes = platform.processes!!.map { if (it.packageName == "previous") it.copy(importance = Importance.Visible) else it }
        overlay.open("current")
        overlay.close("previous")
        assertTrue(platform.forceStopped.isEmpty())
    }

    @Test fun `a stale card is refreshed instead of launching a missing app`() = runTest {
        overlay.open("current")
        platform.processes = platform.processes!!.filterNot { it.packageName == "previous" }
        overlay.select("previous")
        assertTrue(platform.launched.isEmpty())
        assertNotNull(overlay.state.value)
        assertFalse(overlay.state.value!!.switcher.apps!!.any { it.packageName == "previous" })
    }

    @Test fun `an empty overlay is open and dismissible`() = runTest {
        platform.processes = emptyList()
        overlay.open("com.freemytv.home")
        assertTrue(overlay.state.value!!.switcher.apps!!.isEmpty())
        overlay.dismiss()
        assertNull(overlay.state.value)
    }
}
