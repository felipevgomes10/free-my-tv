package com.freemytv.home.weather

import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.widgets.WidgetSettings
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.test.runTest

class WeatherAtmosphereTest {
    @Test
    fun `every published Open-Meteo code has footage by day and by night`() {
        val codes = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 97, 99)
        assertEquals(29, codes.size)
        codes.forEach { code ->
            listOf(true, false).forEach { day ->
                assertNotNull("No footage for WMO $code (day=$day)", WeatherAtmosphere.fromWmo(code, day))
            }
        }
        assertEquals(Condition.Thunderstorm, Condition.fromWmo(97))
    }

    @Test
    fun `clear and partially cloudy skies follow day and night from the forecast`() {
        assertEquals(WeatherAtmosphere.ClearDay, WeatherAtmosphere.fromWmo(0, true))
        assertEquals(WeatherAtmosphere.ClearNight, WeatherAtmosphere.fromWmo(0, false))
        assertEquals(WeatherAtmosphere.ClearDay, WeatherAtmosphere.fromWmo(1, true))
        assertEquals(WeatherAtmosphere.PartlyCloudyDay, WeatherAtmosphere.fromWmo(2, true))
        assertEquals(WeatherAtmosphere.PartlyCloudyNight, WeatherAtmosphere.fromWmo(2, false))
    }

    @Test
    fun `unknown conditions keep the static sky and make no sound`() {
        listOf(-1, 4, 94, 100).forEach { assertNull(WeatherAtmosphere.fromWmo(it, true)) }
    }

    @Test
    fun `muting survives a restart without changing the city or hiding the widget`() = runTest {
        val platform = FakeTvPlatform()
        val settings = WidgetSettings(platform)
        settings.load()
        settings.setWeatherCity("Belém")
        settings.setWeatherSoundMuted(true)

        val restarted = WidgetSettings(platform).also { it.load() }
        assertTrue(restarted.preferences.value.weatherSoundMuted)
        assertEquals("Belém", restarted.preferences.value.weatherCity)
        assertTrue(restarted.preferences.value.weatherVisible)

        restarted.setWeatherSoundMuted(false)
        assertFalse(platform.widgetPreferences.weatherSoundMuted)
    }
}
