package com.freemytv.home.android

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.freemytv.home.weather.WeatherAtmosphere
import kotlin.math.max

/** One native decoder, sized at 720p, only while Weather is in front. No media session or network. */
@androidx.annotation.OptIn(UnstableApi::class)
class WeatherBackdrop(context: Context, private val atmosphere: WeatherAtmosphere) : TextureView(context), TextureView.SurfaceTextureListener {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            hasFocus = change == AudioManager.AUDIOFOCUS_GAIN
            // A call, Assistant or another player takes precedence over ambience.
            applyVolume()
        }
        .build()
    private var requestedFocus = false
    private var hasFocus = false
    private var player: ExoPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var active = false
    private var muted = true
    private var failed = false
    private var videoWidth = 1280
    private var videoHeight = 720
    private val loopProbe = WeatherLoopProbe()

    init {
        isOpaque = false
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        surfaceTextureListener = this
        alpha = 0f
    }

    /** Inactive includes navigation away, sleep and another activity in front, even during a fade. */
    fun playback(active: Boolean, muted: Boolean) {
        this.active = active
        this.muted = muted
        if (!active) {
            releasePlayback()
        } else {
            if (player == null && isAvailable && !failed) prepare()
            updateSound()
        }
    }

    private fun prepare() {
        val texture = surfaceTexture ?: return
        try {
            // Prebuffer the next repetition and preserve the decoder/audio sink across it.
            // The default video buffer is far too large for this 2 GB TV (ADR 0004).
            val buffers = DefaultLoadControl.Builder()
                .setBufferDurationsMs(1_000, 10_000, 100, 250)
                .setTargetBufferBytes(4 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()
            // Android 12's asynchronous c2 AAC adapter can fail after a few repetitions. Keep
            // native decoding, with synchronous queueing for the TV/emulator's older codec stack.
            val renderers = DefaultRenderersFactory(context).forceDisableMediaCodecAsynchronousQueueing()
            val next = ExoPlayer.Builder(context, renderers).setLoadControl(buffers).build()
            player = next
            surface = Surface(texture)
            next.setAudioAttributes(androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SONIFICATION)
                .build(), false) // Keep the existing mute/focus policy; don't request focus twice.
            next.volume = 0f
            next.setVideoSurface(surface)
            next.repeatMode = Player.REPEAT_MODE_ONE
            next.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(size: VideoSize) {
                    videoWidth = size.width
                    videoHeight = size.height
                    crop()
                }

                override fun onRenderedFirstFrame() {
                    if (active && player === next) alpha = 1f
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (player !== next) return
                    prepared = state == Player.STATE_READY
                    updateSound()
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (player !== next) return
                    Log.w(TAG, "Weather footage unavailable: ${atmosphere.file}", error)
                    failed = true
                    releasePlayback()
                }
            })
            if (WeatherLoopProbe.ENABLED) next.addAnalyticsListener(object : AnalyticsListener {
                override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) = loopProbe.decoder("video")
                override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) = loopProbe.decoder("audio")
                override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) = loopProbe.audioUnderrun()
                override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) = loopProbe.audioOutputStarted()
            })
            next.setMediaItem(MediaItem.fromUri("asset:///${atmosphere.asset}"))
            next.playWhenReady = true
            next.prepare()
        } catch (error: Exception) {
            Log.w(TAG, "Weather footage unavailable: ${atmosphere.file}", error)
            failed = true
            releasePlayback()
        }
    }

    private fun updateSound() {
        if (!active || muted || !prepared) {
            abandonFocus()
        } else if (!requestedFocus) {
            requestedFocus = true
            hasFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        applyVolume()
    }

    private fun applyVolume() {
        val level = if (active && !muted && hasFocus) 0.45f else 0f
        player?.volume = level
    }

    private fun abandonFocus() {
        if (requestedFocus) audio.abandonAudioFocusRequest(focus)
        requestedFocus = false
        hasFocus = false
    }

    fun releasePlayback() {
        abandonFocus()
        prepared = false
        val previous = player
        player = null
        previous?.release()
        loopProbe.reset()
        surface?.release()
        surface = null
        alpha = 0f
    }

    /** Fill every screen ratio without stretching or letterboxing. */
    private fun crop() {
        if (width == 0 || height == 0 || videoWidth == 0 || videoHeight == 0) return
        val scale = max(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        setTransform(Matrix().apply {
            setScale(videoWidth * scale / width, videoHeight * scale / height, width / 2f, height / 2f)
        })
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        crop()
        if (active && !failed) prepare()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = crop()
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
        if (WeatherLoopProbe.ENABLED && prepared) loopProbe.frame(player?.currentPosition ?: 0L)
    }
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        releasePlayback()
        return true // TextureView releases its texture; the decoder's Surface is released above.
    }

    private companion object {
        const val TAG = "WeatherBackdrop"
    }
}
