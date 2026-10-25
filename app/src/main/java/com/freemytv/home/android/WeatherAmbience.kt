package com.freemytv.home.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.freemytv.home.weather.WeatherAtmosphere

/** Local recorded ambience, with gapless playback and no video decoder or media session. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class WeatherAmbience(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setOnAudioFocusChangeListener { change ->
            hasFocus = change == AudioManager.AUDIOFOCUS_GAIN
            applyVolume()
        }.build()
    private var requestedFocus = false
    private var hasFocus = false
    private var player: ExoPlayer? = null
    private var atmosphere: WeatherAtmosphere? = null
    private var muted = true
    private var prepared = false

    fun update(scene: WeatherAtmosphere, active: Boolean, muted: Boolean) {
        this.muted = muted
        if (!active) { release(); return }
        // Muting never stops the animation. No audio resources are needed until unmuted.
        if (player == null && !muted) prepare(scene)
        val next = player
        if (next != null && atmosphere != scene) {
            atmosphere = scene
            prepared = false
            next.volume = 0f
            next.setMediaItem(MediaItem.fromUri("asset:///${scene.audioAsset}"))
            next.prepare()
        }
        updateFocus()
    }

    private fun prepare(scene: WeatherAtmosphere) {
        try {
            val buffers = DefaultLoadControl.Builder()
                .setBufferDurationsMs(1_000, 10_000, 100, 250)
                .setTargetBufferBytes(256 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false).build()
            // Android 12's async AAC codec failed during the previous extended loop test.
            val renderers = DefaultRenderersFactory(context).forceDisableMediaCodecAsynchronousQueueing()
            val next = ExoPlayer.Builder(context, renderers).setLoadControl(buffers).build()
            player = next
            atmosphere = scene
            next.setAudioAttributes(androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SONIFICATION).build(), false)
            next.volume = 0f
            next.repeatMode = Player.REPEAT_MODE_ONE
            next.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (WeatherAnimationProbe.ENABLED && player === next && reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                        WeatherAnimationProbe.audio("repeat")
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (player !== next) return
                    prepared = state == Player.STATE_READY
                    updateFocus()
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (player !== next) return
                    Log.w("WeatherAmbience", "Ambient sound unavailable", error)
                    release()
                }
            })
            if (WeatherAnimationProbe.ENABLED) next.addAnalyticsListener(object : AnalyticsListener {
                override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) = WeatherAnimationProbe.audio("decoder")
                override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) = WeatherAnimationProbe.audio("underrun")
                override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) = WeatherAnimationProbe.audio("output_started")
            })
            next.setMediaItem(MediaItem.fromUri("asset:///${scene.audioAsset}"))
            next.playWhenReady = true
            next.prepare()
        } catch (error: Exception) {
            Log.w("WeatherAmbience", "Ambient sound unavailable", error)
            release()
        }
    }

    private fun updateFocus() {
        if (muted || !prepared) abandonFocus()
        else if (!requestedFocus) {
            requestedFocus = true
            hasFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        applyVolume()
    }

    private fun applyVolume() { player?.volume = if (!muted && hasFocus) 0.45f else 0f }
    private fun abandonFocus() {
        if (requestedFocus) audio.abandonAudioFocusRequest(focus)
        requestedFocus = false
        hasFocus = false
    }

    fun release() {
        abandonFocus()
        prepared = false
        val previous = player
        player = null
        previous?.release()
        atmosphere = null
    }
}
