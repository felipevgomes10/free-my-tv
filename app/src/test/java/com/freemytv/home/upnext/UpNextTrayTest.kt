package com.freemytv.home.upnext

import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.PackageEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpNextTrayTest {

    private val platform = FakeTvPlatform(
        labels = mapOf(DISNEY to "Disney+", NETFLIX to "Netflix", PRIME to "Prime Video", YOUTUBE to "YouTube"),
    )

    private fun TestScope.following(): UpNextTray = UpNextTray(platform).also { tray ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { tray.follow() }
    }

    private fun play(vararg media: PlayingMedia) {
        platform.nowPlaying.value = media.toList()
    }

    private fun later(minutes: Long) {
        platform.clock = platform.clock.plusMinutes(minutes)
    }

    @Test
    fun `something playing becomes an entry with its title, artwork and time left`() = runTest {
        val tray = following()

        play(PlayingMedia(DISNEY, playing = true, title = "Moana", positionMillis = 600_000, durationMillis = 6_420_000, artwork = "moana.jpg"))

        val entry = tray.state.value.entries.single()
        assertEquals("Moana", entry.title)
        assertEquals("Disney+", entry.appLabel)
        assertEquals("moana.jpg", entry.artwork)
        assertEquals(5_820_000L, entry.remainingMillis)
        assertEquals(listOf(entry), platform.upNext)
    }

    @Test
    fun `watching the same title again updates its entry instead of adding one`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana", positionMillis = 600_000, durationMillis = 6_420_000))
        later(60)

        play(PlayingMedia(DISNEY, playing = true, title = "Moana", positionMillis = 1_800_000, durationMillis = 6_420_000))

        val entry = tray.state.value.entries.single()
        assertEquals(1_800_000L, entry.positionMillis)
        assertEquals(platform.clock.toInstant(), entry.lastWatched)
    }

    @Test
    fun `an app that doesn't name what is playing gets one entry for the app`() = runTest {
        val tray = following()

        play(PlayingMedia(NETFLIX, playing = true, positionMillis = 950_452))
        later(10)
        play(PlayingMedia(NETFLIX, playing = true, positionMillis = 120_000))

        val entry = tray.state.value.entries.single()
        assertNull(entry.title)
        assertEquals("Netflix", entry.appLabel)
    }

    @Test
    fun `a title that is only the app's name counts as no title`() = runTest {
        val tray = following()

        play(PlayingMedia(PRIME, playing = true, title = "PrimeVideo"))

        assertNull(tray.state.value.entries.single().title)
    }

    @Test
    fun `the nameless moment before an app names the title doesn't leave an app entry behind`() = runTest {
        val tray = following()

        play(PlayingMedia(YOUTUBE, playing = true, positionMillis = 42))
        play(PlayingMedia(YOUTUBE, playing = true, title = "Como comprar pulseiras", subtitle = "Do Zero Ao Rolex", positionMillis = 3_000))

        assertEquals(listOf("Como comprar pulseiras"), tray.state.value.entries.map { it.title })
    }

    @Test
    fun `an older app entry stays when a titled one arrives much later`() = runTest {
        val tray = following()
        play(PlayingMedia(YOUTUBE, playing = true))
        later(30)

        play(PlayingMedia(YOUTUBE, playing = true, title = "Outro vídeo"))

        assertEquals(listOf("Outro vídeo", null), tray.state.value.entries.map { it.title })
    }

    @Test
    fun `the most recently watched comes first`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana"))
        later(5)
        play(PlayingMedia(NETFLIX, playing = true))
        later(5)

        play(PlayingMedia(DISNEY, playing = true, title = "Moana", positionMillis = 900_000), PlayingMedia(NETFLIX, playing = false))

        assertEquals(listOf(DISNEY, NETFLIX), tray.state.value.entries.map { it.packageName })
    }

    @Test
    fun `pausing keeps the position without moving the entry up`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana", positionMillis = 600_000))
        val started = platform.clock.toInstant()
        later(20)

        play(PlayingMedia(DISNEY, playing = false, title = "Moana", positionMillis = 1_800_000))

        val entry = tray.state.value.entries.single()
        assertEquals(1_800_000L, entry.positionMillis)
        assertEquals(started, entry.lastWatched)
    }

    @Test
    fun `something only ever seen paused is not an entry`() = runTest {
        val tray = following()

        play(PlayingMedia(DISNEY, playing = false, title = "Moana", positionMillis = 600_000))

        assertEquals(emptyList<UpNextEntry>(), tray.state.value.entries)
    }

    @Test
    fun `entries come back after a restart`() = runTest {
        val first = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana"))

        val restarted = UpNextTray(platform).also { it.load() }

        assertEquals(first.state.value.entries, restarted.state.value.entries)
    }

    @Test
    fun `selecting an entry goes back to it through its app`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana"))

        tray.select(tray.state.value.entries.single())

        assertEquals(listOf(DISNEY), platform.resumed)
    }

    @Test
    fun `an entry leaves at 95 percent`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana", positionMillis = 6_000_000, durationMillis = 6_420_000))

        play(PlayingMedia(DISNEY, playing = true, title = "Moana", positionMillis = 6_098_999, durationMillis = 6_420_000))
        assertEquals(1, tray.state.value.entries.size)
        play(PlayingMedia(DISNEY, playing = true, title = "Moana", positionMillis = 6_099_000, durationMillis = 6_420_000))

        assertEquals(emptyList<UpNextEntry>(), tray.state.value.entries)
    }

    @Test
    fun `an entry leaves with under two minutes left, so the credits count as the end`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Episódio 4", positionMillis = 600_000, durationMillis = 1_500_000))

        play(PlayingMedia(DISNEY, playing = true, title = "Episódio 4", positionMillis = 1_380_000, durationMillis = 1_500_000))
        assertEquals(1, tray.state.value.entries.size)
        play(PlayingMedia(DISNEY, playing = true, title = "Episódio 4", positionMillis = 1_380_001, durationMillis = 1_500_000))

        assertEquals(emptyList<UpNextEntry>(), tray.state.value.entries)
    }

    @Test
    fun `a finished title doesn't come back while its credits keep playing`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana", positionMillis = 6_300_000, durationMillis = 6_420_000))

        play(PlayingMedia(DISNEY, playing = true, title = "Moana", positionMillis = 6_400_000, durationMillis = 6_420_000))

        assertEquals(emptyList<UpNextEntry>(), tray.state.value.entries)
    }

    @Test
    fun `an entry left unwatched for 30 days is gone the next time the Home looks`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana", artwork = "moana.jpg"))
        later(60L * 24 * 30 - 1)
        tray.prune()
        assertEquals(1, tray.state.value.entries.size)

        later(1)
        tray.prune()

        assertEquals(emptyList<UpNextEntry>(), tray.state.value.entries)
        assertEquals(listOf("moana.jpg"), platform.deletedArtwork)
    }

    @Test
    fun `the Tray holds 20 entries and the 21st pushes out the oldest`() = runTest {
        val tray = following()
        repeat(21) { n ->
            play(PlayingMedia(DISNEY, playing = true, title = "Episódio ${n + 1}"))
            later(1)
        }

        val titles = tray.state.value.entries.map { it.title }
        assertEquals(20, titles.size)
        assertEquals("Episódio 21", titles.first())
        assertEquals("Episódio 2", titles.last())
    }

    @Test
    fun `uninstalling an app takes its entries with it`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana"), PlayingMedia(NETFLIX, playing = true))

        platform.packageEvents.emit(PackageEvent.Removed(DISNEY))

        assertEquals(listOf(NETFLIX), tray.state.value.entries.map { it.packageName })
    }

    @Test
    fun `music apps and short clips never become entries`() = runTest {
        val tray = following()

        play(
            PlayingMedia(SPOTIFY, playing = true, title = "Uma música", durationMillis = 420_000, audioApp = true),
            PlayingMedia(YOUTUBE, playing = true, title = "Um clipe", durationMillis = 4 * 60_000),
        )

        assertEquals(emptyList<UpNextEntry>(), tray.state.value.entries)
    }

    @Test
    fun `the owner can remove an entry`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana", artwork = "moana.jpg"), PlayingMedia(NETFLIX, playing = true))

        tray.remove(tray.state.value.entries.first { it.packageName == DISNEY })

        assertEquals(listOf(NETFLIX), tray.state.value.entries.map { it.packageName })
        assertEquals(listOf(NETFLIX), platform.upNext.map { it.packageName })
        assertEquals(listOf("moana.jpg"), platform.deletedArtwork)
    }

    private companion object {
        const val DISNEY = "com.disney.disneyplus"
        const val NETFLIX = "com.netflix.ninja"
        const val PRIME = "com.amazon.amazonvideo.livingroom"
        const val YOUTUBE = "com.google.android.youtube.tv"
        const val SPOTIFY = "com.spotify.tv.android"
    }
}
