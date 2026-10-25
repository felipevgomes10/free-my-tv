package com.freemytv.home.explorar

import java.net.URLEncoder
import java.text.Normalizer

/*
 * How each service's TV app opens a Title itself (ADR 0014). TMDB knows where a Title streams but
 * not its id at each service; Wikidata, which TMDB links every Title to, often does. Where it
 * doesn't, an app that has a search opens it with the Title's name; otherwise the app just opens.
 * The link formats are the ones each app's manifest accepts on the owner's TV.
 */

/** The Wikidata property holding this service's id for a movie or a series; null where Wikidata keeps none. */
fun StreamingService.wikidataProperty(kind: TitleKind): String? = when (this) {
    StreamingService.Netflix -> "P1874"
    StreamingService.DisneyPlus -> if (kind == TitleKind.Movie) "P7595" else "P7596"
    StreamingService.PrimeVideo -> "P8055"
    StreamingService.Max -> "P8298"
    StreamingService.Globoplay -> "P12356"
    StreamingService.AppleTv -> if (kind == TitleKind.Movie) "P9586" else "P9751"
    StreamingService.Crunchyroll -> "P4110"
    else -> null
}

/** Every property a Title's details ask Wikidata for. */
val WIKIDATA_PROPERTIES: Set<String> =
    StreamingService.entries.flatMap { service -> TitleKind.entries.mapNotNull { service.wikidataProperty(it) } }.toSet()

/** The app's own link to the Title, from its id at the service in [serviceIds]; null without one. */
fun StreamingService.titleLink(serviceIds: Map<String, String>, kind: TitleKind, name: String): String? {
    val id = wikidataProperty(kind)?.let { serviceIds[it] } ?: return null
    val slug = slug(name)
    return when (this) {
        StreamingService.Netflix -> "https://www.netflix.com/title/$id"
        StreamingService.DisneyPlus -> "https://www.disneyplus.com/${if (kind == TitleKind.Movie) "movies" else "series"}/$slug/$id"
        StreamingService.PrimeVideo -> "https://app.primevideo.com/detail?asin=$id"
        // Wikidata keeps Max's ids with their kind: "show/…", "movie/…".
        StreamingService.Max -> "https://play.max.com/$id"
        StreamingService.Globoplay -> "https://globoplay.globo.com/$slug/t/$id/"
        StreamingService.AppleTv -> "https://tv.apple.com/br/${if (kind == TitleKind.Movie) "movie" else "show"}/$slug/$id"
        StreamingService.Crunchyroll -> "crunchyroll://series/$id"
        else -> null
    }
}

/** The app's search for [name], for apps that open one from a link; null for the others. */
fun StreamingService.searchLink(name: String): String? {
    val query = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    return when (this) {
        StreamingService.Globoplay -> "https://globoplay.globo.com/busca/$query/"
        StreamingService.Crunchyroll -> "intent:#Intent;action=android.intent.action.SEARCH;S.query=$query;end"
        StreamingService.Max -> "https://play.max.com/search?q=$query"
        StreamingService.PrimeVideo -> "https://app.primevideo.com/search?phrase=$query"
        StreamingService.AppleTv -> "https://tv.apple.com/br/search?term=$query"
        StreamingService.YouTube -> "https://www.youtube.com/results?search_query=$query"
        else -> null
    }
}

/** "A Casa do Dragão" → "a-casa-do-dragao", the readable part of a link that the apps don't read. */
private fun slug(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
        .replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "titulo" }
