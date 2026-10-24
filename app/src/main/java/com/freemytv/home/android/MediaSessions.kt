package com.freemytv.home.android

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.freemytv.home.upnext.PlayingMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.min

/**
 * The TV's media sessions, read through the notification access Setup grants (ADR 0007).
 * Artwork is stored once per title, downscaled, so the Up Next Tray can show it later.
 */
class MediaSessions(private val context: Context) {

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, HomeNotificationListener::class.java)
    private val artworkDir = File(context.filesDir, "upnext")

    /** A session's reading, before its artwork is stored. */
    private class Reading(val media: PlayingMedia, val art: Bitmap?, val artUri: String?)

    // Per app, the title and picture last stored; and which titles have had their fields logged.
    private val lastPicture = java.util.concurrent.ConcurrentHashMap<String, Pair<String?, String>>()
    private val described: MutableSet<String> = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val nowPlaying: Flow<List<PlayingMedia>> = HomeNotificationListener.isConnected
        .flatMapLatest { connected -> if (connected) sessions() else flowOf(emptyList()) }
        .map { readings -> withContext(Dispatchers.IO) { readings.map { it.media.copy(artwork = store(it)) } } }

    private fun sessions(): Flow<List<Reading>> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val tracked = HashMap<MediaController, MediaController.Callback>()
        fun publish() {
            trySend(tracked.keys.map { it.read() })
        }
        fun track(controllers: List<MediaController>?) {
            val current = controllers.orEmpty()
            tracked.keys.filter { old -> current.none { it.sessionToken == old.sessionToken } }.forEach { gone ->
                tracked.remove(gone)?.let(gone::unregisterCallback)
            }
            current.filter { new -> tracked.keys.none { it.sessionToken == new.sessionToken } }.forEach { controller ->
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                    override fun onSessionDestroyed() = track(tracked.keys.filter { it.sessionToken != controller.sessionToken })
                }
                controller.registerCallback(callback, handler)
                tracked[controller] = callback
            }
            publish()
        }
        val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { track(it) }
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
            track(manager.getActiveSessions(listener))
        } catch (e: SecurityException) {
            Log.w(TAG, "No access to media sessions", e)
            trySend(emptyList())
        }
        // Positions move on without callbacks while something plays.
        launch {
            while (true) {
                delay(POSITION_EVERY_MILLIS)
                if (tracked.keys.any { it.playbackState?.isPlaying() == true }) publish()
            }
        }
        awaitClose {
            runCatching { manager.removeOnActiveSessionsChangedListener(sessionsChanged) }
            tracked.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        }
    }.flowOn(Dispatchers.Main)

    /** Reopens what [packageName] plays through its session, if it has one with somewhere to go. */
    /**
     * Reopens what [packageName] played through its live session, when it is still on [title]
     * (or any session, for an app that names nothing): playback resumes, then the app's player opens.
     */
    fun resume(packageName: String, title: String?): Boolean {
        val sessions = runCatching { manager.getActiveSessions(listener) }.getOrNull().orEmpty().filter { it.packageName == packageName }
        val controller = sessions.firstOrNull { title == null || it.metadata?.titleOrNull() == title } ?: return false
        runCatching { controller.transportControls.play() }
        val activity = controller.sessionActivity ?: return false
        return runCatching { activity.send() }.isSuccess
    }

    private fun MediaMetadata.titleOrNull(): String? = getString(MediaMetadata.METADATA_KEY_TITLE) ?: description?.title?.toString()

    private fun MediaController.read(): Reading {
        val state = playbackState
        val meta = metadata
        val description = meta?.description
        val title = meta?.titleOrNull()
        val subtitle = meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: description?.subtitle?.toString()
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        // Apps often hand a small bitmap and the full picture as a link: the link wins, then the biggest bitmap.
        val art = listOfNotNull(
            meta?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
            meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            description?.iconBitmap,
        ).maxByOrNull { it.width * it.height }
        val artUri = listOfNotNull(
            meta?.getString(MediaMetadata.METADATA_KEY_ART_URI),
            meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
            meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
            description?.iconUri?.toString(),
        ).firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
        val mediaId = meta?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: description?.mediaId
        if (meta != null && described.add("$packageName|$title")) {
            // Which fields each app fills, never their content: what the Up Next Tray can count on.
            Log.d(TAG, "$packageName metadata keys=${meta.keySet().sorted()} art=${art?.let { "${it.width}x${it.height}" }} artUri=${artUri != null} mediaId=${mediaId != null}")
        }
        return Reading(
            PlayingMedia(
                packageName = packageName,
                playing = state?.isPlaying() == true,
                title = title,
                subtitle = subtitle,
                positionMillis = state?.currentPosition(),
                durationMillis = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
                audioApp = runCatching { context.packageManager.getApplicationInfo(packageName, 0).category == ApplicationInfo.CATEGORY_AUDIO }
                    .getOrDefault(false),
                mediaId = mediaId,
            ),
            art,
            artUri,
        )
    }

    private fun PlaybackState.isPlaying() = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

    private fun PlaybackState.currentPosition(): Long? {
        if (position < 0) return null
        if (state != PlaybackState.STATE_PLAYING || lastPositionUpdateTime <= 0) return position
        return position + ((SystemClock.elapsedRealtime() - lastPositionUpdateTime) * playbackSpeed).toLong()
    }

    /**
     * The stored artwork for this title, one file per picture so a new picture is a new path.
     * Apps often announce a new title before its picture: a picture still the previous title's
     * is not taken for the new one.
     */
    private fun store(reading: Reading): String? {
        val media = reading.media
        val source = reading.artUri ?: reading.art?.let(::fingerprint) ?: return null
        val sourceHash = hash(source).take(12)
        val previous = lastPicture[media.packageName]
        if (previous != null && previous.second == sourceHash && previous.first != media.title) return null
        lastPicture[media.packageName] = media.title to sourceHash
        val file = File(artworkDir, "${hash("${media.packageName}|${media.title.orEmpty()}")}-$sourceHash.jpg")
        if (file.exists()) return file.absolutePath
        val picture = reading.artUri?.let(::download) ?: reading.art ?: return null
        return runCatching {
            artworkDir.mkdirs()
            val scale = min(1f, min(MAX_WIDTH.toFloat() / picture.width, MAX_HEIGHT.toFloat() / picture.height))
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(picture, (picture.width * scale).toInt(), (picture.height * scale).toInt(), true) else picture
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        }.getOrNull()
    }

    /** A picture behind a link; YouTube's thumbnails are asked for at their largest size first. */
    private fun download(url: String): Bitmap? {
        val candidates = if ("ytimg.com" in url) listOf(url.replace(YOUTUBE_THUMBNAIL, "/maxresdefault."), url).distinct() else listOf(url)
        return candidates.firstNotNullOfOrNull { candidate ->
            runCatching {
                val connection = URL(candidate).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) null else connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }

    /** What a bitmap shows, cheaply: its pixels at 8 × 8. */
    private fun fingerprint(bitmap: Bitmap): String {
        val tiny = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val pixels = IntArray(64).also { tiny.getPixels(it, 0, 8, 0, 0, 8, 8) }
        return pixels.joinToString(",")
    }

    fun deleteArtwork(path: String) {
        // Only ever files this class stored.
        File(path).takeIf { it.parentFile == artworkDir }?.delete()
    }

    private fun hash(text: String) = MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "MediaSessions"
        const val POSITION_EVERY_MILLIS = 15_000L
        const val MAX_WIDTH = 1920
        const val MAX_HEIGHT = 1080
        val YOUTUBE_THUMBNAIL = Regex("""/(hq|mq|sd|)default\.""")
    }
}
