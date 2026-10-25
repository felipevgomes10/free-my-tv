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
    )

    @Test
    fun `selecting a service opens the Title itself in its app when Wikidata knows its id there`() = runTest {
        val platform = FakeTvPlatform(apps = installedApps)
        val lupin = detailsOf(platform, title(96677, TitleKind.Series, "Lupin", WatchOffer(8, "Netflix", OfferKind.Subscription), mapOf("P1874" to "80994082")))

        Explorar(platform).watch(lupin)

        assertEquals(listOf("https://www.netflix.com/title/80994082"), platform.openedLinks)
        assertEquals(emptyList<String>(), platform.launched)
    }

    @Test
    fun `without the Title's id there, the app's search opens with its name, where the app has one`() = runTest {
        val platform = FakeTvPlatform(apps = installedApps)
        val valeTudo = detailsOf(platform, title(46161, TitleKind.Series, "Vale Tudo", WatchOffer(307, "Globoplay", OfferKind.Subscription)))

        Explorar(platform).watch(valeTudo)

        assertEquals(listOf("https://globoplay.globo.com/busca/Vale%20Tudo/"), platform.openedLinks)
    }

    @Test
    fun `with neither, or when the app turns the links down, the app opens`() = runTest {
        val platform = FakeTvPlatform(apps = installedApps)
        val wandinha = detailsOf(platform, title(119051, TitleKind.Series, "Wandinha", WatchOffer(8, "Netflix", OfferKind.Subscription)))
        val moana = detailsOf(platform, title(1022789, TitleKind.Movie, "Moana 2", WatchOffer(337, "Disney Plus", OfferKind.Subscription), mapOf("P7595" to "6MeZYf9JkFii")))
        platform.linksOpen = false

        Explorar(platform).watch(wandinha)
        Explorar(platform).watch(moana)

        assertEquals(listOf("com.netflix.ninja", "com.disney.disneyplus"), platform.launched)
    }

    @Test
    fun `each service links to a Title by its own id, movies and series apart where Wikidata keeps them apart`() {
        assertEquals("https://www.netflix.com/title/80994082", StreamingService.Netflix.titleLink(mapOf("P1874" to "80994082"), TitleKind.Series, "Lupin"))
        assertEquals("P7595", StreamingService.DisneyPlus.wikidataProperty(TitleKind.Movie))
        assertEquals("P7596", StreamingService.DisneyPlus.wikidataProperty(TitleKind.Series))
        assertEquals("https://play.max.com/show/93ba22b1", StreamingService.Max.titleLink(mapOf("P8298" to "show/93ba22b1"), TitleKind.Series, "The Last of Us"))
        assertNull(StreamingService.Netflix.titleLink(mapOf("P7595" to "x"), TitleKind.Movie, "Lupin"))
        assertNull(StreamingService.Netflix.searchLink("Lupin"))
    }
}
