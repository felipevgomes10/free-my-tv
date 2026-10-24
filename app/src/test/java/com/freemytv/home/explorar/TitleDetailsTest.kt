package com.freemytv.home.explorar

import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TitleDetailsTest {

    private val train = details(TitleKind.Movie, runtime = 128)
    private val shadows = details(TitleKind.Series, episode = 48, seasons = 2)

    @Test
    fun `a movie's facts are its kind, year, genre and length`() {
        assertEquals(listOf("Filme", "2025", "Suspense", "2 h 08 min"), train.facts("Filme"))
    }

    @Test
    fun `a series' facts are its episode length and seasons`() {
        assertEquals(listOf("Série", "2025", "Suspense", "48 min/episódio", "2 temporadas"), shadows.facts("Série"))
    }

    @Test
    fun `unknown facts are left out, never shown as zero`() {
        val bare = details(TitleKind.Series, year = null, genres = emptyList(), episode = 0, seasons = null)

        assertEquals(listOf("Série"), bare.facts("Série"))
    }

    @Test
    fun `lengths read naturally`() {
        assertEquals("48 min", movieLength(48))
        assertEquals("1 h 00 min", movieLength(60))
        assertEquals("1 temporada", seasonCount(1))
        assertNull(movieLength(null))
    }

    @Test
    fun `Brazil's ratings are recognized, anything else hides the badge`() {
        assertEquals(AgeRating.Free, ageRating("L"))
        assertEquals(AgeRating.Fourteen, ageRating("14"))
        assertEquals(AgeRating.Eighteen, ageRating(" 18 "))
        assertNull(ageRating("PG-13"))
        assertNull(ageRating(""))
        assertNull(ageRating(null))
    }

    @Test
    fun `the details show four of the cast`() {
        val crowded = train.copy(cast = (1..9).map { CastMember("Ator $it", "Papel $it") })

        assertEquals(listOf("Ator 1", "Ator 2", "Ator 3", "Ator 4"), crowded.leadCast().map { it.name })
    }

    @Test
    fun `opening a Title shows what the highlights knew, then its details`() = runTest {
        val summary = TitleSummary(7, TitleKind.Movie, "Último trem", "Sinopse.", listOf("Suspense"), 8.4, "/train.jpg")
        val platform = FakeTvPlatform().apply {
            trending = mapOf((TrendWindow.Today to KindFilter.All) to TrendingLookup.Found(listOf(summary)))
            details = mapOf(7 to DetailsLookup.Found(train))
        }
        val explorar = Explorar(platform)
        backgroundScope.launch { explorar.run() }
        runCurrent()

        explorar.openDetails(summary)

        assertEquals(train, explorar.state.value.details?.details)
        explorar.closeDetails()
        assertNull(explorar.state.value.details)
        assertEquals(summary, explorar.state.value.hero)
    }

    @Test
    fun `details offline say so`() = runTest {
        val summary = TitleSummary(8, TitleKind.Series, "Sombras do Norte", null, emptyList(), null, null)
        val explorar = Explorar(FakeTvPlatform())

        explorar.openDetails(summary)

        assertEquals(ExplorarProblem.Offline, explorar.state.value.details?.problem)
    }

    private fun details(
        kind: TitleKind,
        year: Int? = 2025,
        genres: List<String> = listOf("Suspense", "Drama"),
        runtime: Int? = null,
        episode: Int? = null,
        seasons: Int? = null,
    ) = TitleDetails(7, kind, "Último trem", year, genres, "Sinopse.", 8.4, "14", runtime, episode, seasons, emptyList(), "/train.jpg")
}
