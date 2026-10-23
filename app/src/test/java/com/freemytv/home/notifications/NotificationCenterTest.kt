package com.freemytv.home.notifications

import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class NotificationCenterTest {

    private fun notification(key: String, minute: Long, ongoing: Boolean = false, clearable: Boolean = true) = TvNotification(
        key = key,
        packageName = "com.globo.globotv",
        appLabel = "Globoplay",
        title = "Novo episódio disponível",
        text = "Sombras do Norte, episódio $minute.",
        postedAt = Instant.parse("2026-10-23T21:00:00Z").plusSeconds(minute * 60),
        ongoing = ongoing,
        clearable = clearable,
    )

    private suspend fun TestScope.center(platform: FakeTvPlatform) = NotificationCenter(platform).also {
        backgroundScope.launch { it.follow() }
        runCurrent()
    }

    @Test
    fun `a new notification shows a banner for 5 seconds`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform()
        val center = center(platform)

        platform.post(notification("a", 1))
        assertEquals("a", center.state.value.banner?.key)

        advanceTimeBy(4.9.seconds)
        assertEquals("a", center.state.value.banner?.key)
        advanceTimeBy(0.2.seconds)
        assertNull(center.state.value.banner)
    }

    @Test
    fun `notifications already there when the Home starts are not announced`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform()
        platform.post(notification("old", 1))

        val center = center(platform)

        assertNull(center.state.value.banner)
        assertEquals(listOf("old"), center.state.value.notifications.map { it.key })
    }

    @Test
    fun `an update to a notification is not announced again`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform()
        val center = center(platform)
        platform.post(notification("a", 1))
        advanceTimeBy(6.seconds)

        platform.post(notification("a", 2))

        assertNull(center.state.value.banner)
    }

    @Test
    fun `the list is newest first and leaves out ongoing notifications`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform()
        val center = center(platform)

        platform.post(notification("first", 1))
        platform.post(notification("music", 2, ongoing = true))
        platform.post(notification("second", 3))

        assertEquals(listOf("second", "first"), center.state.value.notifications.map { it.key })
    }

    @Test
    fun `an ongoing notification never gets a banner`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform()
        val center = center(platform)

        platform.post(notification("music", 1, ongoing = true))

        assertNull(center.state.value.banner)
    }

    @Test
    fun `opening a notification opens what it points to`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform()
        val center = center(platform)
        platform.post(notification("a", 1))

        center.open(center.state.value.notifications.single())

        assertEquals(listOf("a"), platform.openedNotifications)
    }

    @Test
    fun `Limpar tudo dismisses every clearable notification`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform()
        val center = center(platform)
        platform.post(notification("a", 1))
        platform.post(notification("sticky", 2, clearable = false))

        center.clearAll()

        assertEquals(listOf("sticky"), center.state.value.notifications.map { it.key })
    }

    @Test
    fun `without notification access the Home knows it`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform()
        platform.notificationAccess.value = false

        assertFalse(center(platform).state.value.hasAccess)
    }
}
