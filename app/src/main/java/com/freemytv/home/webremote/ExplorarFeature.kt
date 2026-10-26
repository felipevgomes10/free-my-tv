package com.freemytv.home.webremote

import com.freemytv.home.android.Tmdb
import com.freemytv.home.buttons.HomeDestination
import com.freemytv.home.explorar.DetailsState
import com.freemytv.home.explorar.Explorar
import com.freemytv.home.explorar.ExplorarProblem
import com.freemytv.home.explorar.KindFilter
import com.freemytv.home.explorar.OfferKind
import com.freemytv.home.explorar.SearchLookup
import com.freemytv.home.explorar.TitleKind
import com.freemytv.home.explorar.TitleSummary
import com.freemytv.home.explorar.TrendWindow
import com.freemytv.home.explorar.TrendingLookup
import com.freemytv.home.explorar.ageRating
import com.freemytv.home.platform.TvPlatform
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Em alta hoje, search and a Title's screen on the FMTV Controller's Assistir tab, from Explorar:
 * TMDB's trending Titles of the day, movies and series by name, a Title's details and Onde
 * assistir, and the Title opened in a service's app or its Trailer played on the Home, as
 * Explorar's own buttons do.
 */
fun CompanionFeatures.addExplorar(explorar: Explorar, platform: TvPlatform) {
    list("trending") {
        when (val lookup = explorar.trending(TrendWindow.Today, KindFilter.All)) {
            is TrendingLookup.Found -> JSONArray().apply {
                for (title in lookup.titles) {
                    put(
                        JSONObject()
                            .put("kind", title.kind.protocolName)
                            .put("tmdbId", title.id)
                            .put("name", title.name)
                            .put("genre", title.genres.firstOrNull() ?: JSONObject.NULL)
                            .put("backdrop", title.backdrop?.let { Tmdb.imageUrl(it, "w780") } ?: JSONObject.NULL),
                    )
                }
            }
            TrendingLookup.Offline -> throw Refused("offline")
            TrendingLookup.MissingToken -> throw Refused("missing-token")
        }
    }
    answer("search") { request ->
        val query = request.getString("query").trim()
        val results = if (query.isEmpty()) emptyList() else when (val lookup = platform.searchTitles(query)) {
            is SearchLookup.Found -> lookup.results
            SearchLookup.Offline -> throw Refused("offline")
            SearchLookup.MissingToken -> throw Refused("missing-token")
        }
        JSONObject()
            .put("type", "search-results")
            .put("query", request.getString("query"))
            .put(
                "results",
                JSONArray().apply {
                    for (result in results) {
                        put(
                            JSONObject()
                                .put("kind", result.kind.protocolName)
                                .put("tmdbId", result.id)
                                .put("name", result.name)
                                .put("year", result.year ?: JSONObject.NULL)
                                .put("backdrop", result.backdrop?.let { Tmdb.imageUrl(it, "w780") } ?: JSONObject.NULL)
                                .put("poster", result.poster?.let { Tmdb.imageUrl(it, "w342") } ?: JSONObject.NULL),
                        )
                    }
                },
            )
    }
    answer("title") { request ->
        val state = explorar.lookUpFor(request)
        val details = state.details!!
        JSONObject()
            .put("type", "title")
            .put("kind", details.kind.protocolName)
            .put("tmdbId", details.id)
            .put("name", details.name)
            .put("backdrop", details.backdrop?.let { Tmdb.imageUrl(it, "w1280") } ?: JSONObject.NULL)
            .put("ageRating", ageRating(details.certification)?.label ?: JSONObject.NULL)
            .put("year", details.year ?: JSONObject.NULL)
            .put("genres", JSONArray(details.genres))
            .put("runtimeMinutes", details.runtimeMinutes.takeIf { details.kind == TitleKind.Movie } ?: JSONObject.NULL)
            .put("seasons", details.seasons.takeIf { details.kind == TitleKind.Series } ?: JSONObject.NULL)
            .put("rating", details.rating?.let { (it * 10).roundToInt() / 10.0 } ?: JSONObject.NULL)
            .put("overview", details.overview ?: JSONObject.NULL)
            .put("trailer", state.trailer != null)
            .put(
                "services",
                JSONArray().apply {
                    for (option in state.whereToWatch) {
                        put(
                            JSONObject()
                                .put("service", option.packageName ?: JSONObject.NULL)
                                .put("name", option.name)
                                .put("offer", if (option.kind == OfferKind.Subscription) "subscription" else "rent-or-buy")
                                .put("picture", if (option.installed) "app:${option.packageName}" else JSONObject.NULL),
                        )
                    }
                },
            )
    }
    open("service") { request ->
        val state = explorar.lookUpFor(request)
        val option = state.whereToWatch.firstOrNull { it.packageName != null && it.packageName == request.getString("service") } ?: throw Refused("not-offered")
        explorar.watch(option)
    }
    open("trailer") { request ->
        val state = explorar.lookUpFor(request)
        if (state.trailer == null) throw Refused("no-trailer")
        val details = state.details!!
        explorar.showDetails(state.copy(summary = TitleSummary(details.id, details.kind, details.name, details.overview, details.genres, details.rating, details.backdrop)))
        platform.openHome(HomeDestination.Trailer)
    }
}

private val TitleKind.protocolName get() = if (this == TitleKind.Movie) "movie" else "series"

/** The Title a request names by `kind` and `tmdbId`, looked up; Explorar's problems become the page's reasons. */
private suspend fun Explorar.lookUpFor(request: JSONObject): DetailsState {
    val kind = when (request.getString("kind")) {
        "movie" -> TitleKind.Movie
        "series" -> TitleKind.Series
        else -> throw Refused("unknown-kind")
    }
    val id = request.getInt("tmdbId")
    val state = lookUp(TitleSummary(id, kind, name = "", overview = null, genres = emptyList(), rating = null, backdrop = null))
    return when (state.problem) {
        ExplorarProblem.Offline -> throw Refused("offline")
        ExplorarProblem.MissingToken -> throw Refused("missing-token")
        null -> state
    }
}
