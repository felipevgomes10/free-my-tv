package com.freemytv.home.monitor

import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.app
import com.freemytv.home.widgets.WidgetPreferences
import com.freemytv.home.widgets.WidgetSettings
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
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SystemMonitorTest {

    private val mb = 1_000_000L
    private val spotify = app("com.spotify.tv.android", "Spotify")
    private val globoplay = app("com.globo.globotv", "Globoplay")
    private val youtube = app("com.google.android.youtube.tv", "YouTube")
    private val netflix = app("com.netflix.ninja", "Netflix")

    private fun platform(preferences: WidgetPreferences = WidgetPreferences()) = FakeTvPlatform(
        apps = listOf(spotify, globoplay, youtube, netflix),
        widgetPreferences = preferences,
        labels = mapOf("com.google.android.gms" to "Google Play Services"),
        storagePerApp = listOf(
            AppStorage(youtube.packageName, 1_100 * mb, removable = false),
            AppStorage(globoplay.packageName, 640 * mb, removable = true),
            AppStorage(netflix.packageName, 410 * mb, removable = true),
            AppStorage(spotify.packageName, 380 * mb, removable = true),
            AppStorage("org.xbmc.kodi", 290 * mb, removable = true),
            AppStorage("com.android.vending", 120 * mb, removable = false),
        ),
        processes = listOf(
            ProcessMemory(globoplay.packageName, 312 * mb, Importance.Background),
            ProcessMemory(spotify.packageName, 120 * mb, Importance.Perceptible),
            ProcessMemory(spotify.packageName, 68 * mb, Importance.Background),
            ProcessMemory("com.freemytv.home", 121 * mb, Importance.Foreground),
            ProcessMemory("com.google.android.gms", 104 * mb, Importance.Background),
            ProcessMemory("com.google.android.apps.mediashell", 84 * mb, Importance.Background),
            ProcessMemory(youtube.packageName, 66 * mb, Importance.Background),
            ProcessMemory("surfaceflinger", 400 * mb, Importance.System),
        ),
    )

    private class Monitor(val monitor: SystemMonitor, val settings: WidgetSettings)

    private suspend fun TestScope.monitor(platform: FakeTvPlatform): Monitor {
        val settings = WidgetSettings(platform).also { it.load() }
        val monitor = SystemMonitor(platform, settings)
        backgroundScope.launch { monitor.follow() }
        runCurrent()
        return Monitor(monitor, settings)
    }

    @Test
    fun `the compact widget shows storage, memory, the Home's frame rate and the display's refresh rate`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform()
        val monitor = monitor(platform).monitor

        platform.homeFrameRate.emit(58)

        val state = monitor.state.value
        assertEquals(4_600 * mb, state.storage!!.usedBytes)
        assertEquals(1_420 * mb, state.memory!!.usedBytes)
        assertEquals(58, state.homeFps)
        assertEquals(60, state.refreshRate)
        assertNull(state.detail)
    }

    @Test
    fun `the detail ranks the five apps using the most storage, offering Uninstall only where Android allows it`() = runTest(UnconfinedTestDispatcher()) {
        val monitor = monitor(platform()).monitor

        monitor.openDetail()
        runCurrent()

        val top = monitor.state.value.detail!!.topStorage!!
        assertEquals(listOf("YouTube", "Globoplay", "Netflix", "Spotify", "org.xbmc.kodi"), top.map { it.label })
        assertEquals(listOf(false, true, true, true, true), top.map { it.canUninstall })
    }

    @Test
    fun `the detail ranks apps by memory, summing an app's processes and leaving out system processes`() = runTest(UnconfinedTestDispatcher()) {
        val monitor = monitor(platform()).monitor

        monitor.openDetail()
        runCurrent()

        val top = monitor.state.value.detail!!.topMemory!!
        assertEquals(
            listOf(globoplay.packageName, spotify.packageName, "com.freemytv.home", "com.google.android.gms", "com.google.android.apps.mediashell"),
            top.map { it.packageName },
        )
        assertEquals(188 * mb, top.first { it.label == "Spotify" }.bytes)
        assertEquals("Google Play Services", top.first { it.packageName == "com.google.android.gms" }.label)
    }

    @Test
    fun `Close is offered only for background apps the owner launched, never for media, the Home or Cast Receivers`() = runTest(UnconfinedTestDispatcher()) {
        val monitor = monitor(platform()).monitor

        monitor.openDetail()
        runCurrent()

        val closable = monitor.state.value.detail!!.topMemory!!.filter { it.canClose }.map { it.packageName }
        assertEquals(listOf(globoplay.packageName), closable)
    }

    @Test
    fun `closing an app ends it and refreshes the ranking`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform()
        val monitor = monitor(platform).monitor
        monitor.openDetail()
        runCurrent()

        monitor.close(monitor.state.value.detail!!.topMemory!!.first())

        assertEquals(listOf(globoplay.packageName), platform.closed)
        assertFalse(monitor.state.value.detail!!.topMemory!!.any { it.packageName == globoplay.packageName })
    }

    @Test
    fun `the rankings show up even when measuring them takes longer than a frame-rate reading`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform().apply { appStorageTakes = 1_500.milliseconds }
        val monitor = monitor(platform).monitor
        backgroundScope.launch {
            while (true) {
                platform.homeFrameRate.emit(60)
                delay(1.seconds)
            }
        }

        monitor.openDetail()
        advanceTimeBy(3.seconds)

        assertEquals(5, monitor.state.value.detail!!.topStorage!!.size)
        assertEquals(5, monitor.state.value.detail!!.topMemory!!.size)
    }

    @Test
    fun `an app that can't be closed is never closed`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform()
        val monitor = monitor(platform).monitor
        monitor.openDetail()
        runCurrent()

        monitor.close(monitor.state.value.detail!!.topMemory!!.first { it.label == "Spotify" })

        assertEquals(emptyList<String>(), platform.closed)
    }

    @Test
    fun `without the access Setup grants, the rankings are unavailable rather than wrong`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform().apply {
            storagePerApp = null
            processes = null
        }
        val monitor = monitor(platform).monitor

        monitor.openDetail()
        runCurrent()

        assertNull(monitor.state.value.detail!!.topStorage)
        assertNull(monitor.state.value.detail!!.topMemory)
    }

    @Test
    fun `the detail keeps the last 60 seconds of frame rates`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform()
        val monitor = monitor(platform).monitor
        monitor.openDetail()
        runCurrent()

        repeat(75) { platform.homeFrameRate.emit(if (it == 74) 42 else 60) }

        val history = monitor.state.value.detail!!.fpsHistory
        assertEquals(60, history.size)
        assertEquals(42, history.last())
    }

    @Test
    fun `closing the detail stops the per-app rankings`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform()
        val monitor = monitor(platform).monitor
        monitor.openDetail()
        runCurrent()

        monitor.closeDetail()
        runCurrent()
        val before = platform.measurements.count { it == "processes" }
        advanceTimeBy(1.minutes)

        assertNull(monitor.state.value.detail)
        assertEquals(before, platform.measurements.count { it == "processes" })
    }

    @Test
    fun `a Hidden Widget takes no measurements at all`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform(WidgetPreferences(monitorVisible = false))
        val monitor = monitor(platform).monitor

        advanceTimeBy(10.minutes)

        assertFalse(monitor.state.value.visible)
        assertEquals(emptyList<String>(), platform.measurements)
        assertEquals(0, platform.homeFrameRate.subscriptionCount.value)
    }

    @Test
    fun `hiding the widget stops measuring`() = runTest(UnconfinedTestDispatcher()) {
        val platform = platform()
        val monitor = monitor(platform)
        advanceTimeBy(6.seconds)

        monitor.settings.setMonitorVisible(false)
        runCurrent()
        val before = platform.measurements.size
        advanceTimeBy(10.minutes)

        assertEquals(before, platform.measurements.size)
        assertEquals(0, platform.homeFrameRate.subscriptionCount.value)
    }
}
