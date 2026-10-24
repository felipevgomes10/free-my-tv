package com.freemytv.home.android

/** No diagnostics or clock/position reads are needed in the TV's optimized build. */
class WeatherLoopProbe {
    fun frame(position: Long) = Unit
    fun decoder(kind: String) = Unit
    fun audioUnderrun() = Unit
    fun audioOutputStarted() = Unit
    fun reset() = Unit
    companion object { const val ENABLED = false }
}
