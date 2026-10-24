package com.freemytv.home.android

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import com.freemytv.home.weather.WeatherAtmosphere
import kotlin.math.max

/** One native decoder, sized at 720p, only while Weather is in front. No media session or network. */
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
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var active = false
    private var muted = true
    private var failed = false
    private var videoWidth = 1280
    private var videoHeight = 720

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
        val next = MediaPlayer()
        player = next
        surface = Surface(texture)
        try {
            next.setAudioAttributes(attributes)
            next.setVolume(0f, 0f)
            next.setSurface(surface)
            context.assets.openFd(atmosphere.asset).use { next.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            next.isLooping = true
            next.setOnVideoSizeChangedListener { _, width, height ->
                videoWidth = width
                videoHeight = height
                crop()
            }
            next.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) alpha = 1f
                false
            }
            next.setOnPreparedListener {
                if (player === it && active) {
                    prepared = true
                    it.start()
                    updateSound()
                }
            }
            next.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Weather footage unavailable ($what/$extra): ${atmosphere.file}")
                failed = true
                releasePlayback()
                true
            }
            next.prepareAsync()
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
        player?.setVolume(level, level)
    }

    private fun abandonFocus() {
        if (requestedFocus) audio.abandonAudioFocusRequest(focus)
        requestedFocus = false
        hasFocus = false
    }

    fun releasePlayback() {
        abandonFocus()
        prepared = false
        player?.apply {
            setOnPreparedListener(null)
            setOnErrorListener(null)
            setOnInfoListener(null)
            setOnVideoSizeChangedListener(null)
            release()
        }
        player = null
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
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        releasePlayback()
        return true // TextureView releases its texture; the decoder's Surface is released above.
    }

    private companion object {
        const val TAG = "WeatherBackdrop"
    }
}
