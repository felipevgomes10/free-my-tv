package com.freemytv.home.android

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * A Title's ids at the streaming services, from Wikidata (ADR 0014): only the properties asked
 * for, read from the entity's claims. Open data, no key; Wikimedia asks callers to say who they
 * are in the User-Agent. Anything that goes wrong means no ids, and the apps open on their search
 * or plainly instead.
 */
internal class Wikidata {

    suspend fun ids(entity: String, properties: Set<String>): Map<String, String> = withContext(Dispatchers.IO) {
        if (!ENTITY.matches(entity)) return@withContext emptyMap()
        val connection = URL("$API?action=wbgetentities&ids=$entity&props=claims&format=json").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyMap()
            val claims = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .getJSONObject("entities").getJSONObject(entity).optJSONObject("claims") ?: return@withContext emptyMap()
            properties.mapNotNull { property ->
                claims.optJSONArray(property)?.optJSONObject(0)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                    ?.optString("value")?.takeIf { it.isNotBlank() }?.let { property to it }
            }.toMap()
        } catch (e: IOException) {
            Log.w(TAG, "Wikidata unreachable for $entity", e)
            emptyMap()
        } catch (e: JSONException) {
            Log.w(TAG, "Unexpected Wikidata answer for $entity", e)
            emptyMap()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "Wikidata"
        const val API = "https://www.wikidata.org/w/api.php"
        const val USER_AGENT = "FreeMyTV (https://github.com/felipevgomes10/free-my-tv)"
        const val TIMEOUT_MILLIS = 5_000
        val ENTITY = Regex("""Q\d+""")
    }
}
