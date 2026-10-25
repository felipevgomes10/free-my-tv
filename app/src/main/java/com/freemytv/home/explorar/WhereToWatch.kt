package com.freemytv.home.explorar

/** How a service offers a Title. */
enum class OfferKind {
    /** Included in a subscription, or free with ads. */
    Subscription,
    RentOrBuy,
}

/** One of TMDB's watch offers for Brazil, as it lists them, best first. */
data class WatchOffer(val providerId: Int, val name: String, val kind: OfferKind)

/** A line of "Onde assistir": a service, how it offers the Title, and its app on the TV if it has one. */
data class WatchOption(
    val name: String,
    val kind: OfferKind,
    /** The service's TV app; null when it has none, and then it can't be selected. */
    val packageName: String?,
    val installed: Boolean = false,
    /** The app's own link to the Title, when its id there is known. */
    val link: String? = null,
    /** The app's search for the Title, for apps that open one from a link. */
    val search: String? = null,
)

/**
 * The streaming services with apps on this TV, as TMDB knows them: by provider id, and by
 * name prefix for the variants TMDB keeps adding ("Netflix Standard with Ads").
 */
enum class StreamingService(val displayName: String, val packageName: String, val ids: Set<Int>, val prefixes: List<String>) {
    Netflix("Netflix", "com.netflix.ninja", setOf(8, 1796), listOf("netflix")),
    PrimeVideo("Prime Video", "com.amazon.amazonvideo.livingroom", setOf(9, 10, 119, 2100), listOf("amazon prime video", "prime video", "amazon video")),
    DisneyPlus("Disney+", "com.disney.disneyplus", setOf(337), listOf("disney plus", "disney+")),
    Globoplay("Globoplay", "com.globo.globotv", setOf(307), listOf("globoplay")),
    Max("Max", "com.wbd.stream", setOf(384, 1899), listOf("max", "hbo max")),
    AppleTv("Apple TV", "com.apple.atve.androidtv.appletv", setOf(2, 350), listOf("apple tv")),
    Crunchyroll("Crunchyroll", "com.crunchyroll.crunchyroid", setOf(283), listOf("crunchyroll")),
    ParamountPlus("Paramount+", "com.cbs.ca", setOf(531), listOf("paramount")),
    YouTube("YouTube", "com.google.android.youtube.tv", setOf(188, 192), listOf("youtube")),
    GooglePlay("Google Play Filmes", "com.google.android.videos", setOf(3), listOf("google play")),
    ;

    companion object {
        fun of(offer: WatchOffer): StreamingService? {
            entries.firstOrNull { offer.providerId in it.ids }?.let { return it }
            val name = offer.name.lowercase()
            // "Max" alone must not catch "Maxdome"-like names: whole word or followed by a space.
            return entries.firstOrNull { service -> service.prefixes.any { name == it || name.startsWith("$it ") || (it.length > 3 && name.startsWith(it)) } }
        }
    }
}

/**
 * "Onde assistir": each service once, under its strongest offer (a subscription beats renting or
 * buying), subscriptions first, otherwise in TMDB's order.
 */
fun whereToWatch(offers: List<WatchOffer>, isInstalled: (String) -> Boolean): List<WatchOption> {
    val byService = LinkedHashMap<String, WatchOption>()
    offers.forEach { offer ->
        val service = StreamingService.of(offer)
        val key = service?.name ?: offer.name.lowercase()
        val option = WatchOption(
            name = service?.displayName ?: offer.name,
            kind = offer.kind,
            packageName = service?.packageName,
            installed = service?.packageName?.let(isInstalled) ?: false,
        )
        val known = byService[key]
        if (known == null || (known.kind == OfferKind.RentOrBuy && offer.kind == OfferKind.Subscription)) byService[key] = option
    }
    return byService.values.sortedBy { it.kind.ordinal }
}
