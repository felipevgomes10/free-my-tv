package com.freemytv.home.weather

import java.time.LocalDate
import java.time.LocalDateTime

/** A place the weather is reported for. */
data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/** A forecast as the weather service returns it, in the place's local time. Weather codes are WMO codes. */
data class Forecast(
    val current: CurrentConditions,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
)

data class CurrentConditions(
    val time: LocalDateTime,
    val temperature: Double,
    val weatherCode: Int,
    val humidity: Int,
    val isDay: Boolean,
)

data class HourlyForecast(
    val time: LocalDateTime,
    val temperature: Double,
    val weatherCode: Int,
    val isDay: Boolean,
)

data class DailyForecast(
    val date: LocalDate,
    val weatherCode: Int,
    val min: Double,
    val max: Double,
    /** Highest chance of rain during the day, 0–100. */
    val rainChance: Int,
)

/** The last forecast that was successfully fetched, kept for when the TV is offline. */
data class CachedForecast(
    val place: Place,
    val forecast: Forecast,
)

enum class Condition {
    Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Snow, Thunderstorm;

    companion object {
        /** WMO weather interpretation codes, as used by Open-Meteo. */
        fun fromWmo(code: Int): Condition = when (code) {
            0 -> Clear
            1, 2 -> PartlyCloudy
            3 -> Cloudy
            45, 48 -> Fog
            51, 53, 55, 56, 57 -> Drizzle
            61, 63, 65, 66, 67, 80, 81, 82 -> Rain
            71, 73, 75, 77, 85, 86 -> Snow
            95, 96, 97, 99 -> Thunderstorm
            else -> Cloudy
        }
    }
}
