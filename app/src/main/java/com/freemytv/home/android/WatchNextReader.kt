package com.freemytv.home.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract.WatchNextPrograms
import android.net.Uri
import android.util.Log
import com.freemytv.home.upnext.WatchNextKind
import com.freemytv.home.upnext.WatchNextProgram
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant

/**
 * Reads what apps list in Android TV's Watch Next (ADR 0012). With READ_TV_LISTINGS, which Setup
 * grants, the TV provider answers with every app's searchable programs, which Watch Next's are by
 * default. Each picture is stored once, downscaled, so the Up Next Tray shows it without fetching
 * it again; pictures no program uses any more are deleted.
 */
internal class WatchNextReader(private val context: Context) {

    private val artworkDir = File(context.filesDir, "watchnext")

    /** Null without the access Setup grants. Runs on a background thread: pictures may be fetched. */
    fun read(): List<WatchNextProgram>? {
        if (context.checkSelfPermission(READ_TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) return null
        val rows = try {
            context.contentResolver.query(WatchNextPrograms.CONTENT_URI, COLUMNS, null, null, null)?.use { cursor ->
                buildList { while (cursor.moveToNext()) row(cursor)?.let(::add) }
            }.orEmpty()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Watch Next can't be read", e)
            return null
        }
        // Disabled and uninstalled apps keep their rows; asked here, off the main thread, once per app.
        val openable = rows.map { it.first.packageName }.distinct().filter(::canOpen).toSet()
        val programs = rows.filter { it.first.packageName in openable }.map { (program, picture) -> program.copy(artwork = picture?.let(::store)) }
        val kept = programs.mapNotNull { it.artwork }.toSet()
        artworkDir.listFiles()?.filter { it.path !in kept }?.forEach { it.delete() }
        return programs
    }

    private fun canOpen(packageName: String) = context.packageManager.run {
        getLeanbackLaunchIntentForPackage(packageName) != null || getLaunchIntentForPackage(packageName) != null
    }

    /** A program and its picture's address, thumbnail (a frame, 16:9) before poster. */
    private fun row(c: Cursor): Pair<WatchNextProgram, String?>? {
        val packageName = c.text(WatchNextPrograms.COLUMN_PACKAGE_NAME) ?: return null
        val title = c.text(WatchNextPrograms.COLUMN_TITLE) ?: return null
        val program = WatchNextProgram(
            packageName = packageName,
            kind = when (c.number(WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE)?.toInt()) {
                WatchNextPrograms.WATCH_NEXT_TYPE_NEXT -> WatchNextKind.Next
                WatchNextPrograms.WATCH_NEXT_TYPE_NEW -> WatchNextKind.New
                WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST -> WatchNextKind.Watchlist
                else -> WatchNextKind.Continue
            },
            title = title,
            season = c.text(WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER),
            episode = c.text(WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER),
            episodeTitle = c.text(WatchNextPrograms.COLUMN_EPISODE_TITLE),
            positionMillis = c.number(WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS),
            durationMillis = c.number(WatchNextPrograms.COLUMN_DURATION_MILLIS),
            link = c.text(WatchNextPrograms.COLUMN_INTENT_URI),
            lastEngagement = Instant.ofEpochMilli(c.number(WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS) ?: 0),
        )
        return program to (c.text(WatchNextPrograms.COLUMN_THUMBNAIL_URI) ?: c.text(WatchNextPrograms.COLUMN_POSTER_ART_URI))
    }

    /** The stored picture at [address], fetched and downscaled the first time; null if it can't be had. */
    private fun store(address: String): String? {
        val file = File(artworkDir, "${hash(address)}.jpg")
        if (file.exists()) return file.path
        return runCatching {
            val bytes = fetch(address) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= STORED_WIDTH) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            artworkDir.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bitmap.recycle()
            file.path
        }.getOrNull()
    }

    private fun fetch(address: String): ByteArray? {
        val uri = Uri.parse(address)
        return when (uri.scheme) {
            "http", "https" -> (URL(address).openConnection() as HttpURLConnection).run {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                try {
                    if (responseCode in 200..299) inputStream.use { it.readBytes() } else null
                } finally {
                    disconnect()
                }
            }
            else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    }

    private fun Cursor.text(column: String) = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }?.takeIf { it.isNotBlank() }

    private fun Cursor.number(column: String) = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) }

    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    companion object {
        const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
        private const val TAG = "WatchNextReader"
        private const val STORED_WIDTH = 640
        private const val JPEG_QUALITY = 85
        private const val TIMEOUT_MILLIS = 5_000

        private val COLUMNS = arrayOf(
            WatchNextPrograms.COLUMN_PACKAGE_NAME,
            WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
            WatchNextPrograms.COLUMN_TITLE,
            WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            WatchNextPrograms.COLUMN_EPISODE_TITLE,
            WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            WatchNextPrograms.COLUMN_DURATION_MILLIS,
            WatchNextPrograms.COLUMN_THUMBNAIL_URI,
            WatchNextPrograms.COLUMN_POSTER_ART_URI,
            WatchNextPrograms.COLUMN_INTENT_URI,
            WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
        )

        /**
         * The intent behind an app's link, made safe to start: it must open the app that listed it,
         * grants nothing of the Home's, and carries no selector. Null otherwise. It starts the app
         * afresh: an app already open would only come to the front and never read the link.
         */
        fun safeIntent(packageName: String, link: String, ownPackage: String): Intent? {
            val intent = runCatching { Intent.parseUri(link, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return null
            intent.selector = null
            intent.flags = intent.flags and GRANTS.inv()
            intent.clipData = null
            val target = intent.component?.packageName ?: intent.`package`
            if (target != null && target != packageName) return null
            if (packageName == ownPackage) return null
            if (target == null) intent.setPackage(packageName)
            return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        private const val GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}
