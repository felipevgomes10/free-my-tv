package com.freemytv.home.android

import android.util.Log
import com.freemytv.home.updates.Release
import com.freemytv.home.updates.ReleaseAsset
import com.freemytv.home.updates.ReleaseLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Public GitHub releases, read without a token (60 requests an hour is plenty for a daily check). */
internal class GitHubReleases(private val api: () -> String, private val downloads: TemporaryDownloads) {

    suspend fun latest(repository: String): ReleaseLookup = withContext(Dispatchers.IO) {
        val connection = URL("${api()}/repos/$repository/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val assets = json.optJSONArray("assets")
                    ReleaseLookup.Found(
                        Release(
                            tag = json.getString("tag_name"),
                            assets = List(assets?.length() ?: 0) { assets!!.getJSONObject(it) }.map {
                                ReleaseAsset(it.getString("name"), it.getString("browser_download_url"), it.optLong("size"))
                            },
                        ),
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> ReleaseLookup.NotFound
                // Rate limits and outages are GitHub being unreachable, not the repository missing.
                else -> ReleaseLookup.Offline
            }
        } catch (e: IOException) {
            Log.w(TAG, "No release for $repository", e)
            ReleaseLookup.Offline
        } catch (e: org.json.JSONException) {
            Log.w(TAG, "Unexpected release for $repository", e)
            ReleaseLookup.Offline
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(asset: ReleaseAsset): String? = withContext(Dispatchers.IO) {
        val file = File(downloads.directory(), asset.name.replace(Regex("""[^\w.-]"""), "_"))
        try {
            // GitHub answers with a redirect to its file storage, on another host.
            var url = URL(asset.url)
            repeat(MAX_REDIRECTS) {
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                try {
                    when (connection.responseCode) {
                        in 300..399 -> url = URL(url, connection.getHeaderField("Location") ?: return@withContext null)
                        HttpURLConnection.HTTP_OK -> {
                            connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                            return@withContext file.absolutePath
                        }
                        else -> return@withContext null
                    }
                } finally {
                    connection.disconnect()
                }
            }
            null
        } catch (e: IOException) {
            Log.w(TAG, "Download failed: ${asset.url}", e)
            file.delete()
            null
        }
    }

    private companion object {
        const val TAG = "GitHubReleases"
        const val TIMEOUT_MILLIS = 20_000
        const val MAX_REDIRECTS = 5
    }
}
