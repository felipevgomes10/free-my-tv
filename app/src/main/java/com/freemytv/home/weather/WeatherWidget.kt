package com.freemytv.home.weather

import com.freemytv.home.platform.TvPlatform
import com.freemytv.home.widgets.WidgetSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

data class WeatherState(
    /** False for a Hidden Widget. */
    val visible: Boolean,
    /** Null until a forecast has ever been fetched. */
    val report: WeatherReport?,
)

data class WeatherReport(
    val place: String,
    val temperature: Int,
    val condition: Condition,
    val isDay: Boolean,
    val todayMax: Int,
    val todayMin: Int,
    val humidity: Int,
    val rainChance: Int,
    /** The next 12 hours, starting with the current one. */
    val hours: List<HourSlot>,
    /** The next 7 days, starting today. */
    val days: List<DaySlot>,
)

data class HourSlot(val time: LocalDateTime, val isNow: Boolean, val temperature: Int, val condition: Condition, val isDay: Boolean)

data class DaySlot(val date: java.time.LocalDate, val isToday: Boolean, val condition: Condition, val min: Int, val max: Int)

/**
 * The Weather Widget. While visible it reports on the owner's city, or the city found
 * from the TV's public IP, refreshing every half hour; offline, it keeps showing the
 * last forecast it got. A Hidden Widget makes no requests at all.
 */
class WeatherWidget(private val platform: TvPlatform, private val settings: WidgetSettings) {

    private val _state = MutableStateFlow(WeatherState(visible = true, report = null))
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    suspend fun follow() {
        settings.preferences
            .map { it.weatherVisible to it.weatherCity }
            .distinctUntilChanged()
            .collectLatest { (visible, city) ->
                if (!visible) {
                    _state.value = _state.value.copy(visible = false)
                    return@collectLatest
                }
                _state.value = _state.value.copy(visible = true)
                if (_state.value.report == null) platform.lastForecast()?.let { show(it) }
                while (true) {
                    refresh(city)
                    delay(REFRESH_EVERY)
                }
            }
    }

    private suspend fun refresh(city: String?) {
        val place = (if (city == null) platform.locateByIp() else platform.geocode(city)) ?: return
        val forecast = platform.forecast(place) ?: return
        show(CachedForecast(place, forecast))
    }

    private fun show(cached: CachedForecast) {
        _state.value = _state.value.copy(report = report(cached.place, cached.forecast, platform.now().toLocalDateTime()))
    }

    private companion object {
        val REFRESH_EVERY = 30.minutes

        fun report(place: Place, forecast: Forecast, now: LocalDateTime): WeatherReport {
            val thisHour = now.truncatedTo(ChronoUnit.HOURS)
            val today = now.toLocalDate()
            val todayForecast = forecast.daily.firstOrNull { it.date == today }
            val current = forecast.current
            return WeatherReport(
                place = place.name,
                temperature = current.temperature.roundToInt(),
                condition = Condition.fromWmo(current.weatherCode),
                isDay = current.isDay,
                todayMax = (todayForecast?.max ?: current.temperature).roundToInt(),
                todayMin = (todayForecast?.min ?: current.temperature).roundToInt(),
                humidity = current.humidity,
                rainChance = todayForecast?.rainChance ?: 0,
                hours = forecast.hourly.filter { !it.time.isBefore(thisHour) }.take(12).map {
                    HourSlot(it.time, it.time == thisHour, it.temperature.roundToInt(), Condition.fromWmo(it.weatherCode), it.isDay)
                },
                days = forecast.daily.filter { !it.date.isBefore(today) }.take(7).map {
                    DaySlot(it.date, it.date == today, Condition.fromWmo(it.weatherCode), it.min.roundToInt(), it.max.roundToInt())
                },
            )
        }
    }
}
