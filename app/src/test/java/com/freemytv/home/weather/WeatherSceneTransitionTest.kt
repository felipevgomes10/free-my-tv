package com.freemytv.home.weather

import org.junit.Assert.*
import org.junit.Test

class WeatherSceneTransitionTest {
    @Test fun `the latest forecast waits for the currently visible transition`() {
        val transition = WeatherSceneTransition(WeatherAtmosphere.ClearDay)
        transition.target(WeatherAtmosphere.Rain)
        transition.advance(0.6f)
        val visible = transition.fraction
        transition.target(WeatherAtmosphere.Snow)
        transition.target(WeatherAtmosphere.Fog)
        assertEquals(visible, transition.fraction)
        assertEquals(WeatherAtmosphere.Rain, transition.next)
        transition.advance(0.6f)
        assertEquals(WeatherAtmosphere.Rain, transition.current)
        assertEquals(WeatherAtmosphere.Fog, transition.next)
        assertEquals(0f, transition.fraction)
        transition.advance(2f)
        assertEquals(WeatherAtmosphere.Fog, transition.current)
        assertNull(transition.next)
    }

    @Test fun `duplicate forecast and mute recomposition do not restart the crossfade`() {
        val transition = WeatherSceneTransition(WeatherAtmosphere.ClearDay)
        transition.target(WeatherAtmosphere.Rain)
        transition.advance(0.6f)
        transition.target(WeatherAtmosphere.Rain)
        transition.advance(0.6f)
        assertEquals(WeatherAtmosphere.Rain, transition.current)
        assertNull(transition.next)
    }

    @Test fun `returning to the current forecast queues a smooth return instead of jumping`() {
        val transition = WeatherSceneTransition(WeatherAtmosphere.ClearDay)
        transition.target(WeatherAtmosphere.Rain)
        transition.advance(0.4f)
        transition.target(WeatherAtmosphere.ClearDay)
        transition.advance(1f)
        assertEquals(WeatherAtmosphere.Rain, transition.current)
        assertEquals(WeatherAtmosphere.ClearDay, transition.next)
    }
}
