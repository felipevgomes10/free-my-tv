package com.freemytv.home.android

object WeatherAnimationProbe {
    const val ENABLED = false
    fun reset() = Unit
    fun frame(now: Long, error: Int) = Unit
    fun audio(event: String) = Unit
}
