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
}
