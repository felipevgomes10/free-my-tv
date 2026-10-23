package com.freemytv.home.android

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
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
    private class Reading(val media: PlayingMedia, val art: Bitmap?)

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
    fun resume(packageName: String): Boolean {
        val controller = runCatching { manager.getActiveSessions(listener) }.getOrNull()?.firstOrNull { it.packageName == packageName }
        val activity = controller?.sessionActivity ?: return false
        return runCatching { activity.send() }.isSuccess
    }

    private fun MediaController.read(): Reading {
        val state = playbackState
        val meta = metadata
        val description = meta?.description
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: description?.title?.toString()
        val subtitle = meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: description?.subtitle?.toString()
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: description?.iconBitmap
        return Reading(
            PlayingMedia(
                packageName = packageName,
                playing = state?.isPlaying() == true,
                title = title,
                subtitle = subtitle,
                positionMillis = state?.currentPosition(),
                durationMillis = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
            ),
            art,
        )
    }

    private fun PlaybackState.isPlaying() = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

    private fun PlaybackState.currentPosition(): Long? {
        if (position < 0) return null
        if (state != PlaybackState.STATE_PLAYING || lastPositionUpdateTime <= 0) return position
        return position + ((SystemClock.elapsedRealtime() - lastPositionUpdateTime) * playbackSpeed).toLong()
    }

    /** The stored artwork for this title: written once, at Top Shelf size at most (ADR 0004). */
    private fun store(reading: Reading): String? {
        val media = reading.media
        val file = File(artworkDir, "${hash("${media.packageName}|${media.title.orEmpty()}")}.jpg")
        if (file.exists()) return file.absolutePath
        val art = reading.art ?: return null
        return runCatching {
            artworkDir.mkdirs()
            val scale = min(1f, min(MAX_WIDTH.toFloat() / art.width, MAX_HEIGHT.toFloat() / art.height))
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(art, (art.width * scale).toInt(), (art.height * scale).toInt(), true) else art
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        }.getOrNull()
    }

    private fun hash(text: String) = MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "MediaSessions"
        const val POSITION_EVERY_MILLIS = 15_000L
        const val MAX_WIDTH = 1920
        const val MAX_HEIGHT = 1080
    }
}
