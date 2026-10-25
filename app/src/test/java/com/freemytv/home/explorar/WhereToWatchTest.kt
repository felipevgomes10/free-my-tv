package com.freemytv.home.explorar

import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.platform.InstalledApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhereToWatchTest {

    private val installed = setOf("com.netflix.ninja", "com.amazon.amazonvideo.livingroom")

    private fun options(vararg offers: WatchOffer) = whereToWatch(offers.toList()) { it in installed }

    @Test
    fun `a service's variants show once`() {
        val list = options(
            WatchOffer(8, "Netflix", OfferKind.Subscription),
            WatchOffer(1796, "Netflix Standard with Ads", OfferKind.Subscription),
        )

        assertEquals(listOf("Netflix"), list.map { it.name })
    }

    @Test
    fun `subscriptions come before renting or buying, and a service keeps its strongest offer`() {
        val list = options(
            WatchOffer(2, "Apple TV", OfferKind.RentOrBuy),
            WatchOffer(10, "Amazon Video", OfferKind.RentOrBuy),
            WatchOffer(119, "Amazon Prime Video", OfferKind.Subscription),
        )

        assertEquals(listOf("Prime Video" to OfferKind.Subscription, "Apple TV" to OfferKind.RentOrBuy), list.map { it.name to it.kind })
    }

    @Test
    fun `services map to their TV apps by id or by name`() {
        assertEquals(StreamingService.Globoplay, StreamingService.of(WatchOffer(307, "Globoplay", OfferKind.Subscription)))
        assertEquals(StreamingService.Netflix, StreamingService.of(WatchOffer(99999, "Netflix Kids", OfferKind.Subscription)))
        assertEquals(StreamingService.Max, StreamingService.of(WatchOffer(99998, "Max", OfferKind.Subscription)))
        assertNull(StreamingService.of(WatchOffer(99997, "Maxdome", OfferKind.Subscription)))
        assertNull(StreamingService.of(WatchOffer(1234, "Claro tv+", OfferKind.Subscription)))
    }

    @Test
    fun `an unknown service is listed without an app, and installed apps are marked`() {
        val list = options(WatchOffer(8, "Netflix", OfferKind.Subscription), WatchOffer(337, "Disney Plus", OfferKind.Subscription), WatchOffer(1234, "Claro tv+", OfferKind.Subscription))

        assertEquals(listOf(true, false, false), list.map { it.installed })
        assertNull(list.last().packageName)
    }

    @Test
    fun `selecting a service opens its app, or its Play Store page when it isn't installed`() = runTest {
        val platform = FakeTvPlatform(apps = listOf(InstalledApp("com.netflix.ninja", "Netflix", hasBanner = true)))
        val explorar = Explorar(platform)

        explorar.watch(WatchOption("Netflix", OfferKind.Subscription, "com.netflix.ninja", installed = true))
        explorar.watch(WatchOption("Disney+", OfferKind.Subscription, "com.disney.disneyplus"))
        explorar.watch(WatchOption("Claro tv+", OfferKind.Subscription, null))

        assertEquals(listOf("com.netflix.ninja"), platform.launched)
        assertEquals(listOf("com.disney.disneyplus"), platform.playStorePages)
    }

    @Test
    fun `the details carry where to watch, empty when nobody offers it in Brazil`() = runTest {
        val summary = TitleSummary(96677, TitleKind.Series, "Lupin", null, emptyList(), null, null)
        val lupin = TitleDetails(96677, TitleKind.Series, "Lupin", 2021, emptyList(), null, 7.7, "16", null, 52, 3, emptyList(), null, listOf(WatchOffer(8, "Netflix", OfferKind.Subscription)))
        val platform = FakeTvPlatform().apply { details = mapOf(96677 to DetailsLookup.Found(lupin), 1 to DetailsLookup.Found(lupin.copy(id = 1, offers = emptyList()))) }
        val explorar = Explorar(platform)

        explorar.openDetails(summary)
        assertEquals(listOf("Netflix"), explorar.state.value.details?.whereToWatch?.map { it.name })

        explorar.openDetails(summary.copy(id = 1))
        assertEquals(emptyList<WatchOption>(), explorar.state.value.details?.whereToWatch)
    }

    private suspend fun detailsOf(platform: FakeTvPlatform, details: TitleDetails): WatchOption {
        platform.details = mapOf(details.id to DetailsLookup.Found(details))
        val explorar = Explorar(platform)
        explorar.openDetails(TitleSummary(details.id, details.kind, details.name, null, emptyList(), null, null))
        return explorar.state.value.details!!.whereToWatch.first()
    }

    private fun title(id: Int, kind: TitleKind, name: String, offer: WatchOffer, serviceIds: Map<String, String> = emptyMap()) =
        TitleDetails(id, kind, name, 2021, emptyList(), null, null, null, null, null, null, emptyList(), null, listOf(offer), serviceIds = serviceIds)

    private val installedApps = listOf(
        InstalledApp("com.netflix.ninja", "Netflix", hasBanner = true),
        InstalledApp("com.globo.globotv", "Globoplay", hasBanner = true),
        InstalledApp("com.disney.disneyplus", "Disney+", hasBanner = true),
        InstalledApp("com.apple.atve.androidtv.appletv", "Apple TV", hasBanner = true),
    )

    @Test
    fun `selecting a service opens the Title itself in its app when Wikidata knows its id there`() = runTest {
        val platform = FakeTvPlatform(apps = installedApps)
        val tedLasso = detailsOf(platform, title(97546, TitleKind.Series, "Ted Lasso", WatchOffer(350, "Apple TV+", OfferKind.Subscription), mapOf("P9751" to "umc.cmc.vtoh0mn0xn7t3c643xqonfzy")))

        Explorar(platform).watch(tedLasso)

        assertEquals(listOf("https://tv.apple.com/br/show/ted-lasso/umc.cmc.vtoh0mn0xn7t3c643xqonfzy"), platform.openedLinks)
        assertEquals(emptyList<String>(), platform.launched)
    }

    @Test
    fun `without the Title's id there, the app's search opens with its name, where the app has one`() = runTest {
        val platform = FakeTvPlatform(apps = installedApps)
        val insideOut = detailsOf(platform, title(1022789, TitleKind.Movie, "Divertida Mente 2", WatchOffer(337, "Disney Plus", OfferKind.Subscription)))

        Explorar(platform).watch(insideOut)

        assertEquals(listOf("https://www.disneyplus.com/search?q=Divertida%20Mente%202"), platform.openedLinks)
    }

    @Test
    fun `with neither, or when the app turns the links down, the app opens`() = runTest {
        val platform = FakeTvPlatform(apps = installedApps)
        val wandinha = detailsOf(platform, title(119051, TitleKind.Series, "Wandinha", WatchOffer(8, "Netflix", OfferKind.Subscription)))
        val valeTudo = detailsOf(platform, title(46161, TitleKind.Series, "Vale Tudo", WatchOffer(307, "Globoplay", OfferKind.Subscription), mapOf("P12356" to "7RRVdT85Hw")))

        Explorar(platform).watch(wandinha)
        assertEquals(listOf("com.netflix.ninja"), platform.launched)
        assertEquals(emptyList<String>(), platform.openedLinks)

        platform.linksOpen = false
        Explorar(platform).watch(valeTudo)
        assertEquals(listOf("com.netflix.ninja", "com.globo.globotv"), platform.launched)
    }

    @Test
    fun `each service links to a Title by its own id, movies and series apart where Wikidata keeps them apart`() {
        assertEquals("https://play.max.com/show/93ba22b1", StreamingService.Max.titleLink(mapOf("P8298" to "show/93ba22b1"), TitleKind.Series, "The Last of Us"))
        assertEquals("https://globoplay.globo.com/vale-tudo/t/7RRVdT85Hw/", StreamingService.Globoplay.titleLink(mapOf("P12356" to "7RRVdT85Hw"), TitleKind.Series, "Vale Tudo"))
        assertEquals("https://tv.apple.com/br/movie/f1/umc.cmc.3t6dvnnr87zwd4wmvpdx5came", StreamingService.AppleTv.titleLink(mapOf("P9586" to "umc.cmc.3t6dvnnr87zwd4wmvpdx5came"), TitleKind.Movie, "F1"))
        assertEquals("crunchyroll://series/GRGGPG93R", StreamingService.Crunchyroll.titleLink(mapOf("P11330" to "GRGGPG93R"), TitleKind.Series, "Fullmetal Alchemist: Brotherhood"))
        assertEquals("P9751", StreamingService.AppleTv.wikidataProperty(TitleKind.Series))
    }

    @Test
    fun `services whose apps were seen to ignore Title links, or to stall on a search, get neither`() {
        assertNull(StreamingService.Netflix.titleLink(mapOf("P1874" to "80994082"), TitleKind.Series, "Lupin"))
        assertNull(StreamingService.PrimeVideo.titleLink(mapOf("P8055" to "B0FHGK5Q4H"), TitleKind.Movie, "Anaconda"))
        assertNull(StreamingService.DisneyPlus.titleLink(mapOf("P7595" to "6MeZYf9JkFii"), TitleKind.Movie, "Divertida Mente 2"))
        listOf(StreamingService.Netflix, StreamingService.PrimeVideo, StreamingService.Globoplay, StreamingService.Max, StreamingService.AppleTv, StreamingService.Crunchyroll)
            .forEach { assertNull(it.name, it.searchLink("Lupin")) }
    }
}
