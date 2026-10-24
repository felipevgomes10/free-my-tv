package com.freemytv.home.android

import android.os.SystemClock
import android.util.Log

/** Debug-only measurement of the actual video frame delivery at a seven-second loop boundary. */
class WeatherLoopProbe {
    private var lastFrame = 0L
    private var lastPosition = 0L

    fun decoder(kind: String) { Log.i("WeatherLoop", "decoder=$kind") }
    fun audioUnderrun() { Log.i("WeatherLoop", "audio_underrun") }
    fun audioOutputStarted() { Log.i("WeatherLoop", "audio_output_started") }
    fun reset() { lastFrame = 0L; lastPosition = 0L }

    fun frame(position: Long) {
        val now = SystemClock.elapsedRealtimeNanos()
        if (lastFrame != 0L && lastPosition > 5_000 && position < 1_000) {
            Log.i("WeatherLoop", "boundary_gap_ms=${(now - lastFrame) / 1_000_000.0}")
        }
        lastFrame = now
        lastPosition = position
    }

    companion object { const val ENABLED = true }
}
