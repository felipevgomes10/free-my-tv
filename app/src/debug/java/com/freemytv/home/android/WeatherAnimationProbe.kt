package com.freemytv.home.android

import android.util.Log

/** Native submission cadence; SurfaceFlinger separately measures actual presentation on the TV. */
object WeatherAnimationProbe {
    const val ENABLED = true
    private var start = 0L
    private var previous = 0L
    private var frames = 0
    private var maxGap = 0L
    fun reset() { start = 0L; previous = 0L; frames = 0; maxGap = 0L }
    fun frame(now: Long, error: Int) {
        if (error != 0) Log.e("WeatherAnimation", "gl_error=$error")
        if (start == 0L) { start = now; previous = now; return }
        maxGap = maxOf(maxGap, now - previous)
        previous = now
        frames++
        if (now - start >= 5_000_000_000L) {
            Log.i("WeatherAnimation", "fps=${frames * 1_000_000_000.0 / (now - start)} max_gap_ms=${maxGap / 1_000_000.0}")
            start = now; frames = 0; maxGap = 0L
        }
    }
    fun audio(event: String) { Log.i("WeatherAnimation", "audio_$event") }
}
