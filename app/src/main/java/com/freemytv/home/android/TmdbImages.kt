package com.freemytv.home.android

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** TMDB's images, downloaded once into the Home's cache, which Android may clear when space runs low. */
class TmdbImages(context: Context) {

    private val dir = File(context.cacheDir, "tmdb")

    /** The local file for a TMDB image path at [width] ("w780", "w1280"), or null when it can't be had. */
    suspend fun file(path: String, width: String): String? = withContext(Dispatchers.IO) {
        val file = File(dir, width + path.replace('/', '_'))
        if (file.exists()) return@withContext file.absolutePath
        dir.mkdirs()
        val partial = File(file.path + ".part")
        try {
            val connection = URL(Tmdb.imageUrl(path, width)).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
            partial.renameTo(file)
            file.absolutePath
        } catch (e: IOException) {
            Log.w("TmdbImages", "No image for $path", e)
            partial.delete()
            null
        }
    }
}
