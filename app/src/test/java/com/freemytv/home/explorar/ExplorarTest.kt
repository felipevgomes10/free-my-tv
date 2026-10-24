package com.freemytv.home.explorar

import com.freemytv.home.platform.FakeTvPlatform
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExplorarTest {

    private val lupin = title(96677, TitleKind.Series, "Lupin")
    private val claro = title(1400940, TitleKind.Movie, "Cara-de-Barro")
    private val yaga = title(314360, TitleKind.Series, "YAGA", overview = null)
    private val platform = FakeTvPlatform().apply {
        trending = mapOf(
            (TrendWindow.Today to KindFilter.All) to TrendingLookup.Found(listOf(lupin, claro, yaga)),
            (TrendWindow.Week to KindFilter.All) to TrendingLookup.Found(listOf(claro, lupin)),
            (TrendWindow.Today to KindFilter.Movies) to TrendingLookup.Found(listOf(claro)),
        )
    }

    private fun TestScope.running(): Explorar = Explorar(platform).also { explorar ->
        backgroundScope.launch { explorar.run() }
        runCurrent()
    }

    @Test
    fun `Explorar opens on today's trending, the first Title in the hero`() = runTest {
        val explorar = running()

        val state = explorar.state.value
        assertEquals(TrendWindow.Today, state.window)
        assertEquals(listOf("Lupin", "Cara-de-Barro", "YAGA"), state.titles.map { it.name })
        assertEquals(lupin, state.hero)
    }

    @Test
    fun `the week and the movie filter bring their own highlights`() = runTest {
        val explorar = running()

        explorar.choose(TrendWindow.Week)
        runCurrent()
        assertEquals(listOf("Cara-de-Barro", "Lupin"), explorar.state.value.titles.map { it.name })

        explorar.choose(TrendWindow.Today)
        explorar.choose(KindFilter.Movies)
        runCurrent()
        assertEquals(listOf("Cara-de-Barro"), explorar.state.value.titles.map { it.name })
        assertEquals(KindFilter.Movies, explorar.state.value.filter)
    }

    @Test
    fun `the hero follows the focused card once focus rests`() = runTest {
        val explorar = running()

        explorar.focus(claro)
        advanceTimeBy(150)
        explorar.focus(yaga)
        advanceTimeBy(150)
        assertEquals(lupin, explorar.state.value.hero)

        advanceTimeBy(200)
        assertEquals(yaga, explorar.state.value.hero)
    }

    @Test
    fun `highlights are reused within the hour and fetched again after it`() = runTest {
        val explorar = running()
        explorar.choose(TrendWindow.Week)
        runCurrent()

        explorar.choose(TrendWindow.Today)
        runCurrent()
        assertEquals(2, platform.trendingCalls.size)

        platform.clock = platform.clock.plusMinutes(61)
        explorar.choose(TrendWindow.Week)
        runCurrent()
        assertEquals(3, platform.trendingCalls.size)
    }

    @Test
    fun `offline keeps what was shown last and says so`() = runTest {
        val explorar = running()
        platform.clock = platform.clock.plusMinutes(61)
        platform.trending = emptyMap()

        explorar.retry()

        assertEquals(ExplorarProblem.Offline, explorar.state.value.problem)
        assertEquals(listOf("Lupin", "Cara-de-Barro", "YAGA"), explorar.state.value.titles.map { it.name })
    }

    @Test
    fun `offline with nothing shown before says so with no highlights`() = runTest {
        platform.trending = emptyMap()

        val explorar = running()

        assertEquals(ExplorarProblem.Offline, explorar.state.value.problem)
        assertTrue(explorar.state.value.titles.isEmpty())
        assertNull(explorar.state.value.hero)
    }

    @Test
    fun `a missing token points to Setup`() = runTest {
        platform.trending = mapOf((TrendWindow.Today to KindFilter.All) to TrendingLookup.MissingToken)

        val explorar = running()

        assertEquals(ExplorarProblem.MissingToken, explorar.state.value.problem)
    }

    @Test
    fun `a Title without a Portuguese synopsis still shows`() = runTest {
        val explorar = running()

        assertNull(explorar.state.value.titles.single { it.name == "YAGA" }.overview)
    }

    private fun title(id: Int, kind: TitleKind, name: String, overview: String? = "Sinopse.") =
        TitleSummary(id, kind, name, overview, listOf("Drama"), 7.6, "/$id.jpg")
}
