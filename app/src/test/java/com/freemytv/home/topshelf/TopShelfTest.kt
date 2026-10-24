package com.freemytv.home.topshelf

import com.freemytv.home.upnext.UpNextEntry
import com.freemytv.home.upnext.UpNextState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TopShelfTest {

    private val upNext = MutableStateFlow(
        UpNextState(listOf(entry(DISNEY, "Moana", 3), entry(NETFLIX, null, 2), entry(DISNEY, "Encanto", 1))),
    )

    private fun TestScope.running(): Pair<TopShelf, MutableList<TopShelfState>> {
        val shelf = TopShelf(upNext)
        val seen = mutableListOf<TopShelfState>()
        backgroundScope.launch { shelf.run() }
        backgroundScope.launch { shelf.state.collect { seen += it } }
        runCurrent()
        return shelf to seen
    }

    @Test
    fun `focusing an app shows what was last watched in it`() = runTest {
        val (shelf, _) = running()

        shelf.focus(ShelfApp(DISNEY, "Disney+"))
        advanceTimeBy(301)

        assertEquals(listOf("Moana", "Encanto"), shelf.state.value.entries.map { it.title })
        assertEquals("Moana", shelf.state.value.current?.title)
    }

    @Test
    fun `an app with nothing watched shows nothing`() = runTest {
        val (shelf, _) = running()

        shelf.focus(ShelfApp(YOUTUBE, "YouTube"))
        advanceTimeBy(301)

        assertEquals(YOUTUBE, shelf.state.value.app?.packageName)
        assertNull(shelf.state.value.current)
    }

    @Test
    fun `several entries take turns while focus rests`() = runTest {
        val (shelf, _) = running()
        shelf.focus(ShelfApp(DISNEY, "Disney+"))
        advanceTimeBy(301)

        advanceTimeBy(8_000)
        assertEquals("Encanto", shelf.state.value.current?.title)
        advanceTimeBy(8_000)
        assertEquals("Moana", shelf.state.value.current?.title)
    }

    @Test
    fun `running along the App Tray only shows the app focus settles on`() = runTest {
        val (shelf, seen) = running()

        shelf.focus(ShelfApp(DISNEY, "Disney+"))
        advanceTimeBy(150)
        shelf.focus(ShelfApp(YOUTUBE, "YouTube"))
        advanceTimeBy(150)
        shelf.focus(ShelfApp(NETFLIX, "Netflix"))
        advanceTimeBy(301)

        assertEquals(NETFLIX, shelf.state.value.app?.packageName)
        assertTrue(seen.none { it.app?.packageName == DISNEY || it.app?.packageName == YOUTUBE })
    }

    @Test
    fun `a position update keeps the entry on screen`() = runTest {
        val (shelf, _) = running()
        shelf.focus(ShelfApp(DISNEY, "Disney+"))
        advanceTimeBy(301)
        advanceTimeBy(8_000)

        upNext.value = UpNextState(listOf(entry(DISNEY, "Moana", 3, position = 1_000), entry(NETFLIX, null, 2), entry(DISNEY, "Encanto", 1, position = 5_000)))
        runCurrent()

        assertEquals("Encanto", shelf.state.value.current?.title)
        assertEquals(5_000L, shelf.state.value.current?.positionMillis)
    }

    private fun entry(packageName: String, title: String?, minutesAgo: Long, position: Long? = null) = UpNextEntry(
        key = if (title == null) packageName else "$packageName|$title",
        packageName = packageName,
        appLabel = packageName,
        title = title,
        subtitle = null,
        positionMillis = position,
        durationMillis = null,
        artwork = null,
        lastWatched = Instant.parse("2026-10-23T21:00:00Z").minusSeconds(minutesAgo * 60),
    )

    private companion object {
        const val DISNEY = "com.disney.disneyplus"
        const val NETFLIX = "com.netflix.ninja"
        const val YOUTUBE = "com.google.android.youtube.tv"
    }
}
