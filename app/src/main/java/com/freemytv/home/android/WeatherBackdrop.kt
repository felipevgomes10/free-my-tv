package com.freemytv.home.android

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.Choreographer
import com.freemytv.home.weather.WeatherAtmosphere
import kotlin.math.roundToInt

/** A bounded native GPU surface; no WebView, remote input or media session. */
class WeatherBackdrop(context: Context, atmosphere: WeatherAtmosphere) : GLSurfaceView(context), Choreographer.FrameCallback {
    private val renderer = WeatherSceneRenderer(atmosphere)
    private val ambience = WeatherAmbience(context)
    private var atmosphere = atmosphere
    private var active = false
    private var attached = false
    private var released = false
    private var muted = true
    private var lastFrame = 0L
    private var configured = false
    private var lifecycleResumed = false

    init {
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 0, 0, 0)
        preserveEGLContextOnPause = false
        setRenderer(renderer)
        configured = true
        renderMode = RENDERMODE_WHEN_DIRTY
        onPause()
        visibility = INVISIBLE
    }

    fun playback(atmosphere: WeatherAtmosphere, active: Boolean, muted: Boolean) {
        if (released) return
        if (this.atmosphere != atmosphere) {
            this.atmosphere = atmosphere
            queueEvent { renderer.scene(atmosphere) }
        }
        this.muted = muted
        if (this.active != active) { this.active = active; syncRendering() }
        ambience.update(atmosphere, shouldRender(), muted)
    }

    fun lifecycleResumed(resumed: Boolean) {
        if (lifecycleResumed != resumed) {
            lifecycleResumed = resumed
            syncRendering()
        }
    }

    private fun shouldRender() = active && attached && lifecycleResumed && !released && windowVisibility == VISIBLE

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // A covered/stopped Compose window may stop recomposing before it applies playback(false).
        // Release its EGL context/audio directly instead of keeping a second renderer under a Dream.
        if (configured) syncRendering()
    }

    private fun syncRendering() {
        val clock = Choreographer.getInstance()
        clock.removeFrameCallback(this)
        lastFrame = 0L
        if (shouldRender()) {
            visibility = VISIBLE
            onResume()
            clock.postFrameCallback(this)
            ambience.update(atmosphere, true, muted)
        } else {
            visibility = INVISIBLE
            onPause()
            ambience.release()
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!shouldRender()) return
        if (lastFrame == 0L || frameTimeNanos - lastFrame >= 32_000_000L) {
            lastFrame = frameTimeNanos
            requestRender()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val scale = minOf(1f, 640f / w, 360f / h)
            holder.setFixedSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        syncRendering()
        ambience.update(atmosphere, shouldRender(), muted)
    }

    override fun onDetachedFromWindow() {
        attached = false
        syncRendering()
        super.onDetachedFromWindow()
    }

    fun releasePlayback() { released = true; active = false; syncRendering() }
}
