package com.freemytv.home.upnext

import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.PackageEvent
import com.freemytv.home.platform.app
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
        apps = listOf(DISNEY, NETFLIX, PRIME, YOUTUBE, GLOBOPLAY).map { app(it, it) },
        labels = mapOf(DISNEY to "Disney+", NETFLIX to "Netflix", PRIME to "Prime Video", YOUTUBE to "YouTube", GLOBOPLAY to "Globoplay"),
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
    fun `selecting an entry resumes it through its live session`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana"))
        platform.liveSessions = setOf(DISNEY)

        tray.select(tray.state.value.entries.single())

        assertEquals(listOf(DISNEY), platform.resumed)
        assertEquals(emptyList<String>(), platform.launched)
    }

    @Test
    fun `with its session gone, an entry opens its app`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana"))

        tray.select(tray.state.value.entries.single())

        assertEquals(listOf(DISNEY), platform.launched)
    }

    @Test
    fun `a YouTube video with its session gone and no id is searched for by title and channel`() = runTest {
        val tray = following()
        play(PlayingMedia(YOUTUBE, playing = true, title = "VOLTEI A PROGRAMAR EM 2026!", subtitle = "Nerds de Negócios"))

        tray.select(tray.state.value.entries.single())

        assertEquals(listOf("VOLTEI A PROGRAMAR EM 2026! Nerds de Negócios"), platform.youTubeSearches)
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
        tray.refresh()
        assertEquals(1, tray.state.value.entries.size)

        later(1)
        tray.refresh()

        assertEquals(emptyList<UpNextEntry>(), tray.state.value.entries)
        assertEquals(listOf("moana.jpg"), platform.deletedArtwork)
    }

    @Test
    fun `the Tray holds 20 entries and the 21st pushes out the oldest`() = runTest {
        val tray = following()
        repeat(21) { n ->
            // Five apps take turns, so no app reaches its own limit of five first.
            play(PlayingMedia("app.number${n % 5}", playing = true, title = "Título ${n + 1}"))
            later(1)
        }

        val titles = tray.state.value.entries.map { it.title }
        assertEquals(20, titles.size)
        assertEquals("Título 21", titles.first())
        assertEquals("Título 2", titles.last())
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

    @Test
    fun `one app keeps at most five entries, its latest`() = runTest {
        val tray = following()
        repeat(7) { n ->
            play(PlayingMedia(DISNEY, playing = true, title = "Episódio ${n + 1}"))
            later(1)
        }
        play(PlayingMedia(NETFLIX, playing = true))

        val titles = tray.state.value.entries.filter { it.packageName == DISNEY }.map { it.title }
        assertEquals(listOf("Episódio 7", "Episódio 6", "Episódio 5", "Episódio 4", "Episódio 3"), titles)
        assertEquals(6, tray.state.value.entries.size)
    }

    @Test
    fun `a YouTube video reopens at its position from its id`() = runTest {
        val tray = following()
        play(PlayingMedia(YOUTUBE, playing = true, title = "Voltei a programar", positionMillis = 125_400, mediaId = "WpSweeyuHbM"))

        tray.select(tray.state.value.entries.single())

        assertEquals(listOf("WpSweeyuHbM"), platform.youTubeVideos)
        assertEquals(listOf(125), platform.youTubeStarts)
        assertEquals(emptyList<String>(), platform.resumed)
    }

    @Test
    fun `an id that isn't a YouTube video's is ignored`() = runTest {
        val tray = following()
        play(PlayingMedia(YOUTUBE, playing = true, title = "Algo", mediaId = "not-a-video-id/123"))

        tray.select(tray.state.value.entries.single())

        assertEquals(emptyList<String>(), platform.youTubeVideos)
        assertEquals(listOf("Algo"), platform.youTubeSearches)
    }

    @Test
    fun `new artwork for a title replaces the old one, which is deleted`() = runTest {
        val tray = following()
        play(PlayingMedia(DISNEY, playing = true, title = "Moana", artwork = "moana-1.jpg"))

        play(PlayingMedia(DISNEY, playing = true, title = "Moana", artwork = "moana-2.jpg"))

        assertEquals("moana-2.jpg", tray.state.value.entries.single().artwork)
        assertEquals(listOf("moana-1.jpg"), platform.deletedArtwork)
    }

    private fun listed(
        packageName: String,
        title: String,
        minutesAgo: Long = 0,
        kind: WatchNextKind = WatchNextKind.Continue,
        season: String? = null,
        episode: String? = null,
        episodeTitle: String? = null,
        positionMillis: Long? = null,
        durationMillis: Long? = null,
        artwork: String? = null,
        link: String? = null,
    ) = WatchNextProgram(
        packageName, kind, title, season, episode, episodeTitle, positionMillis, durationMillis, artwork, link,
        lastEngagement = platform.clock.toInstant().minusSeconds(minutesAgo * 60),
    )

    @Test
    fun `what apps list in Watch Next becomes entries, with the episode, the time left and the picture`() = runTest {
        platform.watchNextPrograms = listOf(
            listed(GLOBOPLAY, "Vale Tudo", season = "1", episode = "42", episodeTitle = "Capítulo 42", positionMillis = 600_000, durationMillis = 2_400_000, artwork = "vale-tudo.jpg"),
        )

        val entry = UpNextTray(platform).also { it.load() }.state.value.entries.single()

        assertEquals("Vale Tudo", entry.title)
        assertEquals("T1:E42 Capítulo 42", entry.subtitle)
        assertEquals("Globoplay", entry.appLabel)
        assertEquals(1_800_000L, entry.remainingMillis)
        assertEquals("vale-tudo.jpg", entry.artwork)
    }

    @Test
    fun `a movie in Watch Next has no episode line`() = runTest {
        platform.watchNextPrograms = listOf(listed(DISNEY, "Moana 2"))

        assertNull(UpNextTray(platform).also { it.load() }.state.value.entries.single().subtitle)
    }

    @Test
    fun `what is being watched and the next episode count, not the watchlist nor new episodes`() = runTest {
        platform.watchNextPrograms = listOf(
            listed(DISNEY, "Andor", kind = WatchNextKind.Next),
            listed(DISNEY, "Moana 2", kind = WatchNextKind.Continue),
            listed(DISNEY, "Percy Jackson", kind = WatchNextKind.Watchlist),
            listed(DISNEY, "Bluey", kind = WatchNextKind.New),
        )

        val titles = UpNextTray(platform).also { it.load() }.state.value.entries.map { it.title }

        assertEquals(setOf("Andor", "Moana 2"), titles.toSet())
    }

    @Test
    fun `selecting a Watch Next entry opens it through the app's own link`() = runTest {
        platform.watchNextPrograms = listOf(listed(NETFLIX, "Wandinha", link = "intent://watch/81231974#Intent;package=com.netflix.ninja;end"))
        val tray = UpNextTray(platform).also { it.load() }

        tray.select(tray.state.value.entries.single())

        assertEquals(listOf("intent://watch/81231974#Intent;package=com.netflix.ninja;end"), platform.openedLinks)
        assertEquals(emptyList<String>(), platform.launched)
    }

    @Test
    fun `a Watch Next entry whose link doesn't open, or that has none, opens its app`() = runTest {
        platform.watchNextPrograms = listOf(listed(NETFLIX, "Wandinha", link = "broken"), listed(DISNEY, "Moana 2"))
        platform.linksOpen = false
        val tray = UpNextTray(platform).also { it.load() }

        tray.state.value.entries.forEach { tray.select(it) }

        assertEquals(setOf(NETFLIX, DISNEY), platform.launched.toSet())
    }

    @Test
    fun `an app listing in Watch Next takes over from what the Home saw it play`() = runTest {
        val tray = following()
        play(PlayingMedia(NETFLIX, playing = true))
        play(PlayingMedia(YOUTUBE, playing = true, title = "Lofi", positionMillis = 60_000, durationMillis = 3_600_000))
        platform.watchNextPrograms = listOf(listed(NETFLIX, "Wandinha"))

        tray.refresh()

        assertEquals(listOf("Wandinha" to NETFLIX, "Lofi" to YOUTUBE).toSet(), tray.state.value.entries.map { it.title to it.packageName }.toSet())
        assertEquals(listOf(YOUTUBE), platform.upNext.map { it.packageName })
    }

    @Test
    fun `Watch Next and what the Home saw play mix by when they were watched`() = runTest {
        val tray = following()
        play(PlayingMedia(YOUTUBE, playing = true, title = "Lofi"))
        platform.watchNextPrograms = listOf(listed(DISNEY, "Moana 2", minutesAgo = 10), listed(NETFLIX, "Wandinha", minutesAgo = -1))

        tray.refresh()

        assertEquals(listOf("Wandinha", "Lofi", "Moana 2"), tray.state.value.entries.map { it.title })
    }

    @Test
    fun `a removed Watch Next entry stays hidden until the app updates it`() = runTest {
        platform.watchNextPrograms = listOf(listed(DISNEY, "Moana 2", minutesAgo = 30))
        val tray = UpNextTray(platform).also { it.load() }

        tray.remove(tray.state.value.entries.single())
        UpNextTray(platform).also { it.load() }.state.value.entries.let { assertEquals(emptyList<UpNextEntry>(), it) }

        platform.watchNextPrograms = listOf(listed(DISNEY, "Moana 2", minutesAgo = 0))
        tray.refresh()
        assertEquals(listOf("Moana 2"), tray.state.value.entries.map { it.title })
    }

    @Test
    fun `Watch Next is read again whenever the Home shows`() = runTest {
        val tray = UpNextTray(platform).also { it.load() }
        platform.watchNextPrograms = listOf(listed(DISNEY, "Moana 2"))

        tray.refresh()

        assertEquals(listOf("Moana 2"), tray.state.value.entries.map { it.title })
    }

    @Test
    fun `Watch Next entries of an app that isn't installed any more don't show`() = runTest {
        platform.watchNextPrograms = listOf(listed(DISNEY, "Moana 2"), listed("com.gone.app", "Old show"))

        assertEquals(listOf("Moana 2"), UpNextTray(platform).also { it.load() }.state.value.entries.map { it.title })
    }

    private companion object {
        const val DISNEY = "com.disney.disneyplus"
        const val NETFLIX = "com.netflix.ninja"
        const val PRIME = "com.amazon.amazonvideo.livingroom"
        const val YOUTUBE = "com.google.android.youtube.tv"
        const val SPOTIFY = "com.spotify.tv.android"
        const val GLOBOPLAY = "com.globo.globotv"
    }
}
