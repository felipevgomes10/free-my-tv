package com.freemytv.home.debug

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView

/**
 * Debug builds only: plays nothing, but publishes a media session the way a video app does,
 * so the Up Next Tray and the Top Shelf can be tried on an emulator without video apps.
 *
 *     adb shell am start -n com.freemytv.home/.debug.FakePlayerActivity \
 *         --es title "Moana" --es subtitle "Filme" --el position 600000 --el duration 6420000 --ez paused false
 *
 * Leave out the title to act like Netflix, which doesn't say what is playing.
 */
class FakePlayerActivity : Activity() {

    private var session: MediaSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Fake player: ${intent.getStringExtra("title") ?: "(no title)"}"; textSize = 32f })
        publish(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        publish(intent)
    }

    private fun publish(intent: Intent) {
        val title = intent.getStringExtra("title")
        val playing = !intent.getBooleanExtra("paused", false)
        val s = session ?: MediaSession(this, "FakePlayer").also { session = it }
        s.setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, FakePlayerActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        s.setMetadata(
            MediaMetadata.Builder().apply {
                if (title != null) {
                    putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    intent.getStringExtra("subtitle")?.let { putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, it) }
                    putBitmap(MediaMetadata.METADATA_KEY_ART, poster(title))
                }
                if (intent.hasExtra("duration")) putLong(MediaMetadata.METADATA_KEY_DURATION, intent.getLongExtra("duration", 0))
            }.build(),
        )
        s.setPlaybackState(
            PlaybackState.Builder()
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    intent.getLongExtra("position", 0),
                    if (playing) 1f else 0f,
                    SystemClock.elapsedRealtime(),
                )
                .build(),
        )
        s.isActive = true
    }

    /** A 16:9 picture with a color from the title, so different titles look different. */
    private fun poster(title: String): Bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.HSVToColor(floatArrayOf((title.hashCode() and 0xffff) % 360f, 0.6f, 0.5f)))
        canvas.drawText(title, 80f, 640f, Paint().apply { color = Color.WHITE; textSize = 96f; isAntiAlias = true })
    }

    override fun onDestroy() {
        session?.release()
        super.onDestroy()
    }
}
