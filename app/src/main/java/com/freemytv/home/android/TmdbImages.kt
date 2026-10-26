package com.freemytv.home.android

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** TMDB's images, kept within 32 MiB on disk, oldest unused first out. */
class TmdbImages(context: Context) {

    private val cache = ImageFiles(File(context.cacheDir, "tmdb"), 32 * 1024 * 1024)

    /** Runs off the main thread at startup, including before Explorar is first opened. */
    suspend fun trim() = withContext(Dispatchers.IO) { cache.trim() }

    /** The local file for a TMDB image path at [width] ("w780", "w1280"), or null when it can't be had. */
    suspend fun file(path: String, width: String): String? = withContext(Dispatchers.IO) {
        val name = width + path.replace('/', '_')
        cache.find(name)?.let { return@withContext it.absolutePath }
        val partial = cache.partial()
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
            cache.commit(partial, name)?.absolutePath
        } catch (e: IOException) {
            Log.w("TmdbImages", "No image for $path", e)
            null
        } finally {
            partial.delete()
        }
    }
}
