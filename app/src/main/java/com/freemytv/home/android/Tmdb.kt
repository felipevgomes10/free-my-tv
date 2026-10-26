package com.freemytv.home.android

import android.content.Context
import android.util.Log
import com.freemytv.home.explorar.CastMember
import com.freemytv.home.explorar.DetailsLookup
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
import com.freemytv.home.explorar.WIKIDATA_PROPERTIES
import com.freemytv.home.explorar.WatchOffer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * TMDB's API in Portuguese, for Brazil. The token Setup handed over goes in a bearer header,
 * never in a URL (ADR 0008).
 */
class Tmdb(private val context: Context) {

    /** A TMDB response: the JSON, or why there is none. */
    private sealed interface Answer {
        data class Ok(val json: JSONObject) : Answer
        data object Offline : Answer
        data object NoToken : Answer
    }

    private val wikidata = Wikidata()

    // Genre names per kind, fetched once per process.
    private val genres = HashMap<TitleKind, Map<Int, String>>()

    suspend fun trending(window: TrendWindow, filter: KindFilter): TrendingLookup {
        val kind = when (filter) {
            KindFilter.All -> "all"
            KindFilter.Movies -> "movie"
            KindFilter.Series -> "tv"
        }
        val period = if (window == TrendWindow.Today) "day" else "week"
        return when (val answer = get("trending/$kind/$period?language=pt-BR")) {
            Answer.NoToken -> TrendingLookup.MissingToken
            Answer.Offline -> TrendingLookup.Offline
            is Answer.Ok -> {
                val results = answer.json.optJSONArray("results") ?: return TrendingLookup.Offline
                val titles = List(results.length()) { results.getJSONObject(it) }.mapNotNull { item ->
                    val titleKind = when (item.optString("media_type", if (filter == KindFilter.Movies) "movie" else "tv")) {
                        "movie" -> TitleKind.Movie
                        "tv" -> TitleKind.Series
                        else -> return@mapNotNull null
                    }
                    val names = genreNames(titleKind)
                    TitleSummary(
                        id = item.getInt("id"),
                        kind = titleKind,
                        name = item.optString(if (titleKind == TitleKind.Movie) "title" else "name"),
                        overview = item.optString("overview").takeIf { it.isNotBlank() },
                        genres = item.optJSONArray("genre_ids")?.let { ids -> List(ids.length()) { ids.getInt(it) } }.orEmpty().mapNotNull { names[it] },
                        rating = item.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 && item.optInt("vote_count") > 0 },
                        backdrop = item.optString("backdrop_path").takeIf { it.startsWith("/") },
                    )
                }
                TrendingLookup.Found(titles)
            }
        }
    }

    /** Everything the details screen shows, appended to the details call so it is one request. */
    suspend fun details(kind: TitleKind, id: Int): DetailsLookup {
        val path = if (kind == TitleKind.Movie) {
            "movie/$id?language=pt-BR&append_to_response=release_dates,credits,videos,watch/providers,external_ids&include_video_language=pt,en"
        } else {
            "tv/$id?language=pt-BR&append_to_response=content_ratings,aggregate_credits,videos,watch/providers,external_ids&include_video_language=pt,en"
        }
        return when (val answer = get(path)) {
            Answer.NoToken -> DetailsLookup.MissingToken
            Answer.Offline -> DetailsLookup.Offline
            is Answer.Ok -> runCatching { parseDetails(kind, answer.json) }
                .onFailure { Log.w(TAG, "Unexpected details for $kind $id", it) }
                .getOrNull()
                ?.let { details -> DetailsLookup.Found(details.copy(serviceIds = serviceIds(answer.json))) }
                ?: DetailsLookup.Offline
        }
    }

    /** The Title's ids at the streaming services, through the Wikidata entity TMDB links it to. */
    private suspend fun serviceIds(json: JSONObject): Map<String, String> =
        json.optJSONObject("external_ids")?.optString("wikidata_id")?.takeIf { it.isNotBlank() }
            ?.let { wikidata.ids(it, WIKIDATA_PROPERTIES) }.orEmpty()

    private fun parseDetails(kind: TitleKind, json: JSONObject): TitleDetails {
        val movie = kind == TitleKind.Movie
        val genres = json.optJSONArray("genres")?.let { list -> List(list.length()) { list.getJSONObject(it).getString("name") } }.orEmpty()
        val cast = if (movie) {
            json.optJSONObject("credits")?.optJSONArray("cast")?.let { list ->
                List(list.length()) { list.getJSONObject(it) }.map { CastMember(it.getString("name"), it.optString("character").takeIf(String::isNotBlank)) }
            }
        } else {
            json.optJSONObject("aggregate_credits")?.optJSONArray("cast")?.let { list ->
                List(list.length()) { list.getJSONObject(it) }.map { person ->
                    CastMember(person.getString("name"), person.optJSONArray("roles")?.optJSONObject(0)?.optString("character")?.takeIf(String::isNotBlank))
                }
            }
        }.orEmpty()
        return TitleDetails(
            id = json.getInt("id"),
            kind = kind,
            name = json.optString(if (movie) "title" else "name"),
            year = json.optString(if (movie) "release_date" else "first_air_date").take(4).toIntOrNull(),
            genres = genres,
            overview = json.optString("overview").takeIf { it.isNotBlank() },
            rating = json.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 && json.optInt("vote_count") > 0 },
            certification = if (movie) movieCertification(json) else seriesCertification(json),
            runtimeMinutes = if (movie) json.optInt("runtime").takeIf { it > 0 } else null,
            episodeMinutes = if (movie) null else json.optJSONArray("episode_run_time")?.optInt(0)?.takeIf { it > 0 }
                ?: json.optJSONObject("last_episode_to_air")?.optInt("runtime")?.takeIf { it > 0 },
            seasons = if (movie) null else json.optInt("number_of_seasons").takeIf { it > 0 },
            cast = cast,
            backdrop = json.optString("backdrop_path").takeIf { it.startsWith("/") },
            offers = brazilOffers(json),
            videos = json.optJSONObject("videos")?.optJSONArray("results")?.let { list ->
                List(list.length()) { list.getJSONObject(it) }.map {
                    TitleVideo(it.optString("key"), it.optString("site"), it.optString("type"), it.optString("iso_639_1").takeIf(String::isNotBlank), it.optBoolean("official"))
                }
            }.orEmpty(),
        )
    }

    /** Brazil's offers: subscriptions (and free with ads), then rent and buy, each by TMDB's priority. */
    private fun brazilOffers(json: JSONObject): List<WatchOffer> {
        val brazil = json.optJSONObject("watch/providers")?.optJSONObject("results")?.optJSONObject(BRAZIL) ?: return emptyList()
        return listOf("flatrate" to OfferKind.Subscription, "free" to OfferKind.Subscription, "ads" to OfferKind.Subscription, "rent" to OfferKind.RentOrBuy, "buy" to OfferKind.RentOrBuy)
            .flatMap { (field, kind) ->
                val list = brazil.optJSONArray(field) ?: return@flatMap emptyList()
                List(list.length()) { list.getJSONObject(it) }
                    .sortedBy { it.optInt("display_priority") }
                    .map { WatchOffer(it.getInt("provider_id"), it.getString("provider_name"), kind) }
            }
    }

    /** Brazil's rating from the movie's release dates: the theatrical one, else the first given. */
    private fun movieCertification(json: JSONObject): String? {
        val results = json.optJSONObject("release_dates")?.optJSONArray("results") ?: return null
        val brazil = List(results.length()) { results.getJSONObject(it) }.firstOrNull { it.optString("iso_3166_1") == BRAZIL } ?: return null
        val dates = brazil.optJSONArray("release_dates") ?: return null
        val rated = List(dates.length()) { dates.getJSONObject(it) }.filter { it.optString("certification").isNotBlank() }
        return (rated.firstOrNull { it.optInt("type") == THEATRICAL } ?: rated.firstOrNull())?.optString("certification")
    }

    private fun seriesCertification(json: JSONObject): String? {
        val results = json.optJSONObject("content_ratings")?.optJSONArray("results") ?: return null
        return List(results.length()) { results.getJSONObject(it) }.firstOrNull { it.optString("iso_3166_1") == BRAZIL }?.optString("rating")?.takeIf { it.isNotBlank() }
    }

    /** Movies and series whose name matches [query], in Portuguese, best first; never people. */
    suspend fun search(query: String): SearchLookup {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        return when (val answer = get("search/multi?language=pt-BR&include_adult=false&query=$encoded")) {
            Answer.NoToken -> SearchLookup.MissingToken
            Answer.Offline -> SearchLookup.Offline
            is Answer.Ok -> {
                val results = answer.json.optJSONArray("results") ?: return SearchLookup.Offline
                SearchLookup.Found(
                    List(results.length()) { results.getJSONObject(it) }.mapNotNull { item ->
                        val kind = when (item.optString("media_type")) {
                            "movie" -> TitleKind.Movie
                            "tv" -> TitleKind.Series
                            else -> return@mapNotNull null
                        }
                        val movie = kind == TitleKind.Movie
                        SearchResult(
                            id = item.getInt("id"),
                            kind = kind,
                            name = item.optString(if (movie) "title" else "name"),
                            year = item.optString(if (movie) "release_date" else "first_air_date").take(4).toIntOrNull(),
                            backdrop = item.optString("backdrop_path").takeIf { it.startsWith("/") },
                            poster = item.optString("poster_path").takeIf { it.startsWith("/") },
                        )
                    },
                )
            }
        }
    }

    private suspend fun genreNames(kind: TitleKind): Map<Int, String> = genres[kind] ?: run {
        val path = if (kind == TitleKind.Movie) "genre/movie/list?language=pt-BR" else "genre/tv/list?language=pt-BR"
        val list = (get(path) as? Answer.Ok)?.json?.optJSONArray("genres") ?: return emptyMap()
        List(list.length()) { list.getJSONObject(it) }.associate { it.getInt("id") to it.getString("name") }.also { genres[kind] = it }
    }

    private suspend fun get(path: String): Answer = withContext(Dispatchers.IO) {
        val token = TmdbToken.read(context) ?: return@withContext Answer.NoToken
        val connection = URL("$API/$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Answer.Ok(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
                // A revoked or mistyped token: the owner fixes it the same way as a missing one.
                HttpURLConnection.HTTP_UNAUTHORIZED -> Answer.NoToken
                else -> Answer.Offline
            }
        } catch (e: IOException) {
            Log.w(TAG, "TMDB unreachable: $path", e)
            Answer.Offline
        } catch (e: JSONException) {
            Log.w(TAG, "Unexpected TMDB answer: $path", e)
            Answer.Offline
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "Tmdb"
        private const val API = "https://api.themoviedb.org/3"
        private const val TIMEOUT_MILLIS = 15_000
        private const val BRAZIL = "BR"
        private const val THEATRICAL = 3

        /** A TMDB image at one of its standard widths ("w780", "w1280"). */
        fun imageUrl(path: String, width: String) = "https://image.tmdb.org/t/p/$width$path"
    }
}
