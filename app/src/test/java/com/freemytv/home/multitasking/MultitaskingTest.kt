package com.freemytv.home.multitasking

import com.freemytv.home.monitor.Importance
import com.freemytv.home.monitor.MemoryUsage
import com.freemytv.home.monitor.ProcessMemory
import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.app
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MultitaskingTest {

    private val mb = 1_048_576L
    private val now = Instant.parse("2026-10-24T00:40:00Z") // the fake clock, 21:40 in São Paulo
    private fun minutesAgo(m: Long) = now.minusSeconds(m * 60)

    private val netflix = app("com.netflix.ninja", "Netflix")
    private val youtube = app("com.google.android.youtube.tv", "YouTube")
    private val globoplay = app("com.globo.globotv", "Globoplay")
    private val appleTv = app("com.apple.atve.androidtv.appletv", "Apple TV")
    private val spotify = app("com.spotify.tv.android", "Spotify")
    private val plex = app("com.plexapp.android", "Plex")

    private fun process(pkg: String, importance: Importance = Importance.Background) = ProcessMemory(pkg, 100 * mb, importance)

    /** Plenty of memory unless a test says otherwise. */
    private fun platform(processes: List<ProcessMemory>?, lastUsed: Map<String, Instant>, limit: Int = 3) = FakeTvPlatform(
        apps = listOf(netflix, youtube, globoplay, appleTv, spotify, plex),
        processes = processes,
        lastUsed = lastUsed,
        openAppLimit = limit,
        memory = MemoryUsage(totalBytes = 2_000 * mb, availableBytes = 900 * mb),
    )

    private suspend fun multitasking(platform: FakeTvPlatform) = Multitasking(platform).also { it.load() }

    @Test
    fun `Open Apps are launchable apps with a live process, most recently used first`() = runTest {
        val platform = platform(
            processes = listOf(process(netflix.packageName), process(youtube.packageName), process("com.google.android.gms"), process("com.freemytv.home", Importance.Foreground)),
            lastUsed = mapOf(netflix.packageName to minutesAgo(30), youtube.packageName to minutesAgo(5), globoplay.packageName to minutesAgo(1)),
        )

        val open = multitasking(platform).openApps()

        assertEquals(listOf(youtube.packageName, netflix.packageName), open.map { it.packageName })
    }

    @Test
    fun `the App Switcher starts on the previous app so Home twice then OK flips between the last two`() = runTest {
        val platform = platform(
            processes = listOf(process(globoplay.packageName), process(youtube.packageName), process(appleTv.packageName)),
            lastUsed = mapOf(globoplay.packageName to minutesAgo(0), youtube.packageName to minutesAgo(5), appleTv.packageName to minutesAgo(40)),
        )
        val multitasking = multitasking(platform)

        multitasking.openSwitcher()
        val state = multitasking.switcher.value
        assertEquals(listOf("Globoplay", "YouTube", "Apple TV"), state.apps!!.map { it.label })
        assertEquals(1, state.initialFocus)

        multitasking.switchTo(state.apps!![state.initialFocus])
        assertEquals(listOf(youtube.packageName), platform.launched)
        assertNull(multitasking.switcher.value.apps)
    }

    @Test
    fun `apps playing media are marked and not counted`() = runTest {
        val platform = platform(
            processes = listOf(process(globoplay.packageName), process(spotify.packageName, Importance.Perceptible), process(spotify.packageName)),
            lastUsed = mapOf(globoplay.packageName to minutesAgo(1), spotify.packageName to minutesAgo(9)),
        )
        val multitasking = multitasking(platform)

        multitasking.openSwitcher()

        val state = multitasking.switcher.value
        assertTrue(state.apps!!.first { it.packageName == spotify.packageName }.playing)
        assertEquals(1, state.counted)
    }

    @Test
    fun `closing an app in the App Switcher ends it and takes it off the row`() = runTest {
        val platform = platform(
            processes = listOf(process(globoplay.packageName), process(youtube.packageName)),
            lastUsed = mapOf(globoplay.packageName to minutesAgo(1), youtube.packageName to minutesAgo(5)),
        )
        val multitasking = multitasking(platform)
        multitasking.openSwitcher()

        multitasking.close(multitasking.switcher.value.apps!!.last())

        assertEquals(listOf(youtube.packageName), platform.closed)
        assertEquals(listOf(globoplay.packageName), multitasking.switcher.value.apps!!.map { it.packageName })
    }

    @Test
    fun `over the limit, the least recently used Open App is closed`() = runTest {
        val platform = platform(
            processes = listOf(process(netflix.packageName), process(youtube.packageName), process(globoplay.packageName), process(appleTv.packageName)),
            lastUsed = mapOf(netflix.packageName to minutesAgo(1), youtube.packageName to minutesAgo(10), globoplay.packageName to minutesAgo(50), appleTv.packageName to minutesAgo(20)),
        )

        val closed = multitasking(platform).enforceLimit()

        assertEquals(listOf(globoplay.packageName), closed)
    }

    @Test
    fun `the foreground app, media and Cast Receivers are never closed, nor do media and receivers count`() = runTest {
        val platform = platform(
            processes = listOf(
                process(netflix.packageName, Importance.Foreground),
                process(spotify.packageName, Importance.Perceptible),
                process(youtube.packageName),
                process(globoplay.packageName),
                process(appleTv.packageName),
                process("com.google.android.apps.mediashell"),
            ),
            lastUsed = mapOf(netflix.packageName to minutesAgo(90), spotify.packageName to minutesAgo(80), youtube.packageName to minutesAgo(3), globoplay.packageName to minutesAgo(5), appleTv.packageName to minutesAgo(7)),
        )

        val closed = multitasking(platform).enforceLimit()

        // Counted: Netflix (in front), YouTube, Globoplay, Apple TV = 4 > 3; Netflix is the oldest but in front.
        assertEquals(listOf(appleTv.packageName), closed)
    }

    @Test
    fun `under the memory floor, apps are closed until there is room again`() = runTest {
        val platform = platform(
            processes = listOf(process(youtube.packageName), process(globoplay.packageName)),
            lastUsed = mapOf(youtube.packageName to minutesAgo(3), globoplay.packageName to minutesAgo(30)),
        ).apply { memory = MemoryUsage(totalBytes = 2_000 * mb, availableBytes = 250 * mb) }
        val multitasking = multitasking(platform)

        val closed = multitasking.enforceLimit()

        assertEquals(listOf(globoplay.packageName, youtube.packageName), closed)
    }

    @Test
    fun `the limit is adjustable from 2 to 5 and persists`() = runTest {
        val platform = platform(processes = emptyList(), lastUsed = emptyMap())
        val multitasking = multitasking(platform)

        multitasking.setLimit(9)
        assertEquals(5, multitasking.limit.value)
        multitasking.setLimit(1)
        assertEquals(2, multitasking.limit.value)
        assertEquals(2, platform.openAppLimit)
    }

    @Test
    fun `without the process list, recent apps stand in and the limit is not enforced`() = runTest {
        val platform = platform(
            processes = null,
            lastUsed = mapOf(netflix.packageName to minutesAgo(1), youtube.packageName to minutesAgo(2), globoplay.packageName to minutesAgo(3), appleTv.packageName to minutesAgo(4)),
        )
        val multitasking = multitasking(platform)

        assertEquals(4, multitasking.openApps().size)
        assertEquals(emptyList<String>(), multitasking.enforceLimit())
        assertEquals(emptyList<String>(), platform.closed)
    }

    @Test
    fun `two Home presses within 400 ms make a double press, slower ones don't`() {
        val presses = HomePresses()
        val t = Instant.parse("2026-10-23T21:40:00Z")

        assertFalse(presses.press(t))
        assertTrue(presses.press(t.plusMillis(300)))
        assertFalse(presses.press(t.plusMillis(1_000)))
        assertFalse(presses.press(t.plusMillis(1_500)))
        assertTrue(presses.press(t.plusMillis(1_800)))
    }
}
