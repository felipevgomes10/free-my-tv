package com.freemytv.home.weather

import com.freemytv.home.platform.FakeTvPlatform
import com.freemytv.home.widgets.WidgetPreferences
import com.freemytv.home.widgets.WidgetSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes

class WeatherWidgetTest {

    // The fake clock is 2026-10-23 21:40 in São Paulo.
    private val now = LocalDateTime.of(2026, 10, 23, 21, 40)
    private val saoPaulo = Place("São Paulo", -23.55, -46.63)
    private val campinas = Place("Campinas", -22.9, -47.06)

    private fun forecast(temperature: Double = 18.4, code: Int = 2) = Forecast(
        current = CurrentConditions(now, temperature, code, humidity = 72, isDay = false),
        hourly = (18..59).map { h ->
            HourlyForecast(LocalDateTime.of(2026, 10, 23, 0, 0).plusHours(h.toLong()), 15.0 + h % 5, if (h % 2 == 0) 3 else 61, h % 24 in 6..18)
        },
        daily = (0..9).map { d ->
            DailyForecast(LocalDate.of(2026, 10, 22).plusDays(d.toLong()), if (d == 1) 95 else 1, 14.0 + d % 3, 23.6 + d % 4, rainChance = 30 + d)
        },
    )

    private class Widget(val widget: WeatherWidget, val settings: WidgetSettings)

    private suspend fun TestScope.widget(platform: FakeTvPlatform): Widget {
        val settings = WidgetSettings(platform).also { it.load() }
        val widget = WeatherWidget(platform, settings)
        backgroundScope.launch { widget.follow() }
        runCurrent()
        return Widget(widget, settings)
    }

    @Test
    fun `with Automático the widget reports on the city found from the TV's IP`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(ipPlace = saoPaulo, forecasts = mapOf("São Paulo" to forecast()))

        val report = widget(platform).widget.state.value.report!!

        assertEquals("São Paulo", report.place)
        assertEquals(18, report.temperature)
        assertEquals(Condition.PartlyCloudy, report.condition)
        assertEquals(listOf("ip", "forecast:São Paulo"), platform.networkRequests)
    }

    @Test
    fun `a chosen city wins over the TV's IP`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(
            widgetPreferences = WidgetPreferences(weatherCity = "Campinas"),
            ipPlace = saoPaulo,
            cities = mapOf("Campinas" to campinas),
            forecasts = mapOf("Campinas" to forecast(26.0, 0)),
        )

        val report = widget(platform).widget.state.value.report!!

        assertEquals("Campinas", report.place)
        assertEquals(listOf("geocode:Campinas", "forecast:Campinas"), platform.networkRequests)
    }

    @Test
    fun `choosing another city updates the widget right away`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(
            ipPlace = saoPaulo,
            cities = mapOf("Campinas" to campinas),
            forecasts = mapOf("São Paulo" to forecast(), "Campinas" to forecast(26.0, 0)),
        )
        val widget = widget(platform)

        widget.settings.setWeatherCity("Campinas")
        runCurrent()

        assertEquals("Campinas", widget.widget.state.value.report!!.place)
    }

    @Test
    fun `the report gives today's range, humidity, chance of rain, 12 hours and 7 days`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(ipPlace = saoPaulo, forecasts = mapOf("São Paulo" to forecast()))

        val report = widget(platform).widget.state.value.report!!

        assertEquals(15 to 25, report.todayMin to report.todayMax)
        assertEquals(72, report.humidity)
        assertEquals(31, report.rainChance)
        assertEquals(12, report.hours.size)
        assertEquals(LocalDateTime.of(2026, 10, 23, 21, 0), report.hours.first().time)
        assertEquals(listOf(true) + List(11) { false }, report.hours.map { it.isNow })
        assertEquals(7, report.days.size)
        assertEquals(LocalDate.of(2026, 10, 23), report.days.first().date)
        assertEquals(true, report.days.first().isToday)
        assertEquals(Condition.Thunderstorm, report.days.first().condition)
    }

    @Test
    fun `offline, the widget keeps showing the last forecast it got`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(ipPlace = saoPaulo, forecasts = mapOf("São Paulo" to forecast()))
        val widget = widget(platform)

        platform.forecasts = emptyMap()
        advanceTimeBy(31.minutes)

        assertEquals(18, widget.widget.state.value.report!!.temperature)
    }

    @Test
    fun `starting offline shows the forecast kept from before`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(ipPlace = null)
        platform.cached = CachedForecast(saoPaulo, forecast(21.0))

        val report = widget(platform).widget.state.value.report!!

        assertEquals(21, report.temperature)
    }

    @Test
    fun `the widget refreshes every half hour while visible`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(ipPlace = saoPaulo, forecasts = mapOf("São Paulo" to forecast()))
        widget(platform)

        advanceTimeBy(61.minutes)

        assertEquals(3, platform.networkRequests.count { it.startsWith("forecast") })
    }

    @Test
    fun `a Hidden Widget makes no network requests`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(
            widgetPreferences = WidgetPreferences(weatherVisible = false),
            ipPlace = saoPaulo,
            forecasts = mapOf("São Paulo" to forecast()),
        )
        val widget = widget(platform)

        advanceTimeBy(120.minutes)

        assertFalse(widget.widget.state.value.visible)
        assertNull(widget.widget.state.value.report)
        assertEquals(emptyList<String>(), platform.networkRequests)
    }

    @Test
    fun `hiding the widget stops its requests`() = runTest(UnconfinedTestDispatcher()) {
        val platform = FakeTvPlatform(ipPlace = saoPaulo, forecasts = mapOf("São Paulo" to forecast()))
        val widget = widget(platform)
        val before = platform.networkRequests.size

        widget.settings.setWeatherVisible(false)
        advanceTimeBy(120.minutes)

        assertFalse(widget.widget.state.value.visible)
        assertEquals(before, platform.networkRequests.size)
    }

    @Test
    fun `WMO codes map to the conditions the owner sees`() {
        assertEquals(
            listOf(Condition.Clear, Condition.PartlyCloudy, Condition.Cloudy, Condition.Fog, Condition.Drizzle, Condition.Rain, Condition.Snow, Condition.Thunderstorm),
            listOf(0, 2, 3, 45, 53, 80, 73, 95).map(Condition::fromWmo),
        )
    }
}
