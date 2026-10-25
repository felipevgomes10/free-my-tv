package com.freemytv.home.weather

/** Finishes the visible crossfade before adopting the latest queued forecast. */
class WeatherSceneTransition(initial: WeatherAtmosphere) {
    var current = initial
        private set
    var next: WeatherAtmosphere? = null
        private set
    private var queued: WeatherAtmosphere? = null
    private var elapsed = 0f
    val fraction: Float get() {
        val t = (elapsed / DURATION).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    fun target(value: WeatherAtmosphere) {
        if (next != null) queued = value.takeUnless { it == next }
        else if (value != current) { next = value; elapsed = 0f }
    }

    fun advance(seconds: Float) {
        if (next == null) return
        elapsed += seconds.coerceAtLeast(0f)
        if (elapsed >= DURATION) {
            current = next!!
            next = null
            elapsed = 0f
            val pending = queued
            queued = null
            if (pending != null) target(pending)
        }
    }

    companion object { const val DURATION = 1.2f }
}
