package com.freemytv.home.setup

import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupWatchdogTest {

    private val all = SetupCheck.entries.associateWith { true }

    private suspend fun FakeTvPlatform.check() = SetupWatchdog(this).also { it.check() }

    @Test
    fun `with everything in place Setup is active and nobody is bothered`() = runTest {
        val platform = FakeTvPlatform(setup = all)

        val state = platform.check().state.value

        assertTrue(state.active)
        assertEquals(emptyList<SetupCheck>(), platform.setupNotifications)
    }

    @Test
    fun `the Stock Launcher coming back after an update is announced`() = runTest {
        val platform = FakeTvPlatform(setup = all)
        platform.check()

        platform.setup = all + (SetupCheck.StockLauncherDisabled to false)
        val state = platform.check().state.value

        assertFalse(state.active)
        assertEquals(setOf(SetupCheck.StockLauncherDisabled), state.missing)
        assertEquals(listOf(SetupCheck.StockLauncherDisabled), platform.setupNotifications)
    }

    @Test
    fun `each lost permission is detected on its own`() = runTest {
        SetupCheck.entries.forEach { lost ->
            val platform = FakeTvPlatform(setup = all)
            platform.check()

            platform.setup = all + (lost to false)
            platform.check()

            assertEquals(listOf(lost), platform.setupNotifications)
        }
    }

    @Test
    fun `a regression is announced once, not on every check`() = runTest {
        val platform = FakeTvPlatform(setup = all)
        platform.check()
        platform.setup = all + (SetupCheck.KeyFilter to false)

        repeat(4) { platform.check() }

        assertEquals(listOf(SetupCheck.KeyFilter), platform.setupNotifications)
    }

    @Test
    fun `a check that comes back and is lost again is announced again`() = runTest {
        val platform = FakeTvPlatform(setup = all)
        platform.check()
        platform.setup = all + (SetupCheck.NotificationAccess to false)
        platform.check()

        platform.setup = all
        platform.check()
        platform.setup = all + (SetupCheck.NotificationAccess to false)
        platform.check()

        assertEquals(listOf(SetupCheck.NotificationAccess, SetupCheck.NotificationAccess), platform.setupNotifications)
    }

    @Test
    fun `on a TV where Setup never ran nothing is announced, and Preparação says so`() = runTest {
        val platform = FakeTvPlatform(setup = SetupCheck.entries.associateWith { false })

        val state = platform.check().state.value

        assertFalse(state.everSetUp)
        assertFalse(state.active)
        assertEquals(emptyList<SetupCheck>(), platform.setupNotifications)
    }
}
