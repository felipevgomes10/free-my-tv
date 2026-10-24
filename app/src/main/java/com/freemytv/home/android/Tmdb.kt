package com.freemytv.home.android

import android.content.Context
import android.util.Log
import com.freemytv.home.explorar.KindFilter
import com.freemytv.home.explorar.TitleKind
import com.freemytv.home.explorar.TitleSummary
import com.freemytv.home.explorar.TrendWindow
import com.freemytv.home.explorar.TrendingLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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

        /** A TMDB image at one of its standard widths ("w780", "w1280"). */
        fun imageUrl(path: String, width: String) = "https://image.tmdb.org/t/p/$width$path"
    }
}
