package com.freemytv.home.webremote

import com.freemytv.home.buttons.HomeDestination
import com.freemytv.home.explorar.DetailsLookup
import com.freemytv.home.explorar.Explorar
import com.freemytv.home.explorar.KindFilter
import com.freemytv.home.explorar.OfferKind
import com.freemytv.home.explorar.SearchLookup
import com.freemytv.home.explorar.SearchResult
import com.freemytv.home.explorar.TitleDetails
import com.freemytv.home.explorar.TitleKind
import com.freemytv.home.explorar.TitleSummary
import com.freemytv.home.explorar.TitleVideo
import com.freemytv.home.explorar.TrendWindow
import com.freemytv.home.explorar.TrendingLookup
import com.freemytv.home.explorar.WatchOffer
import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.app
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Em alta hoje and a Title's screen over the bridge, against the protocol's examples. */
class ExplorarFeatureTest {

    private val ultimoTrem = TitleDetails(
        id = 1084242, kind = TitleKind.Movie, name = "Último trem", year = 2025, genres = listOf("Suspense", "Drama"),
        overview = "Na última viagem da noite, uma maquinista percebe que um dos passageiros não aparece nas câmeras do trem.",
        rating = 7.81, certification = "14", runtimeMinutes = 128, episodeMinutes = null, seasons = null, cast = emptyList(),
        backdrop = "/ultimo-trem.jpg",
        offers = listOf(WatchOffer(8, "Netflix", OfferKind.Subscription), WatchOffer(119, "Amazon Prime Video", OfferKind.RentOrBuy), WatchOffer(167, "Claro tv+", OfferKind.RentOrBuy)),
        videos = listOf(TitleVideo("abc123", "YouTube", "Trailer", "pt", official = true)),
    )

    private val platform = FakeTvPlatform(apps = listOf(app("com.netflix.ninja", "Netflix"))).apply {
        trending = mapOf(
            (TrendWindow.Today to KindFilter.All) to TrendingLookup.Found(
                listOf(
                    TitleSummary(1084242, TitleKind.Movie, "Último trem", null, listOf("Suspense"), 7.8, "/ultimo-trem.jpg"),
                    TitleSummary(245703, TitleKind.Series, "Maré alta", null, listOf("Drama"), 8.1, "/mare-alta.jpg"),
                    TitleSummary(933260, TitleKind.Movie, "Sem título ainda", null, emptyList(), null, null),
                ),
            ),
        )
        details = mapOf(ultimoTrem.id to DetailsLookup.Found(ultimoTrem))
    }
    private val explorar = Explorar(platform)
    private val features = CompanionFeatures().apply { addExplorar(explorar, platform) }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    @Test
    fun `trending Titles are listed with TMDB image URLs, shaped like the example, and kept for the next ask`() = runTest {
        val items = features.reply(example("list-request").put("of", "trending")).getJSONArray("items").objects()
        features.reply(example("list-request").put("of", "trending"))

        val expected = example("list-trending").getJSONArray("items").objects()
        assertEquals(expected.map { it.keySet() }, items.map { it.keySet() })
        for ((want, got) in expected.zip(items)) {
            for (field in listOf("kind", "tmdbId", "name", "genre")) assertEquals(field, want.get(field).toString(), got.get(field).toString())
        }
        assertEquals(expected.map { it.get("backdrop").toString() }, items.map { it.get("backdrop").toString() })
        assertEquals(1, platform.trendingCalls.size)
    }

    @Test
    fun `no token and offline are answered as such`() = runTest {
        platform.trending = mapOf((TrendWindow.Today to KindFilter.All) to TrendingLookup.MissingToken)
        assertEquals("missing-token", features.reply(example("list-request").put("of", "trending")).getString("reason"))
        assertEquals("offline", features.reply(example("title-request").put("tmdbId", 1)).getString("reason"))
    }

    @Test
    fun `a Title's screen is answered from Explorar, shaped like the example`() = runTest {
        val answer = features.reply(example("title-request"))

        val expected = example("title")
        assertEquals(expected.keySet() - "id", answer.keySet())
        for (field in listOf("type", "kind", "tmdbId", "name", "backdrop", "ageRating", "year", "runtimeMinutes", "seasons", "rating", "overview", "trailer")) {
            assertEquals(field, expected.get(field).toString(), answer.get(field).toString())
        }
        assertEquals(expected.getJSONArray("genres").toString(), answer.getJSONArray("genres").toString())
        val services = answer.getJSONArray("services").objects()
        assertEquals(expected.getJSONArray("services").objects().map { it.keySet() }, services.map { it.keySet() })
        assertEquals(listOf("com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "null"), services.map { it.get("service").toString() })
        assertEquals(listOf("subscription", "rent-or-buy", "rent-or-buy"), services.map { it.getString("offer") })
        assertEquals(listOf("app:com.netflix.ninja", "null", "null"), services.map { it.get("picture").toString() })
    }

    @Test
    fun `opening a service does what Onde assistir does`() = runTest {
        assertEquals("done", features.reply(example("open-service")).getString("type"))
        assertEquals("done", features.reply(example("open-service").put("service", "com.amazon.amazonvideo.livingroom")).getString("type"))
        assertEquals("not-offered", features.reply(example("open-service").put("service", "com.disney.disneyplus")).getString("reason"))

        assertTrue(platform.launched.contains("com.netflix.ninja") || platform.openedLinks.isNotEmpty())
        assertEquals(listOf("com.amazon.amazonvideo.livingroom"), platform.playStorePages)
    }

    @Test
    fun `opening the trailer puts the Title on Explorar and brings the Home to its Trailer screen`() = runTest {
        assertEquals("done", features.reply(example("open-trailer")).getString("type"))

        assertEquals(listOf(HomeDestination.Trailer), platform.homeOpenedAt)
        val details = explorar.state.value.details!!
        assertEquals("Último trem", details.summary.name)
        assertEquals("abc123", details.trailer?.key)
    }

    @Test
    fun `a search answers movies and series, shaped like the example`() = runTest {
        platform.searches = mapOf(
            "último trem" to SearchLookup.Found(
                listOf(
                    SearchResult(1084242, TitleKind.Movie, "Último trem", 2025, "/ultimo-trem.jpg", "/ultimo-trem-poster.jpg"),
                    SearchResult(81234, TitleKind.Series, "O Último Trem para Casa", 2019, null, "/trem-para-casa.jpg"),
                    SearchResult(55001, TitleKind.Movie, "Último Trem para Busan", null, null, null),
                ),
            ),
        )

        val answer = features.reply(example("search"))

        val expected = example("search-results")
        assertEquals(expected.keySet() - "id", answer.keySet())
        assertEquals(expected.getString("query"), answer.getString("query"))
        assertEquals(expected.getJSONArray("results").toString(), answer.getJSONArray("results").toString())
    }

    @Test
    fun `a search trims what was typed, and no token or offline are answered as such`() = runTest {
        features.reply(example("search").put("query", "  trem "))
        assertEquals(listOf("trem"), platform.searchQueries)

        platform.searches = mapOf("a" to SearchLookup.MissingToken, "b" to SearchLookup.Offline)
        assertEquals("missing-token", features.reply(example("search").put("query", "a")).getString("reason"))
        assertEquals("offline", features.reply(example("search").put("query", "b")).getString("reason"))
    }
}
