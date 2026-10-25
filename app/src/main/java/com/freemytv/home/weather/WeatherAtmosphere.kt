package com.freemytv.home.weather

/** Offline Weather Animations configuration and the corresponding local nature recording. */
enum class WeatherAtmosphere(
    val file: String,
    val animationCondition: String,
    val animationTime: String = "day",
    val animationIntensity: String = "medium",
) {
    ClearDay("clear_day", "clear"), ClearNight("clear_night", "clear", "night"),
    PartlyCloudyDay("partly_cloudy_day", "cloudy", animationIntensity = "light"),
    PartlyCloudyNight("partly_cloudy_night", "cloudy", "night", "light"),
    Cloudy("cloudy", "overcast"), Fog("fog", "fog"), Drizzle("drizzle", "drizzle"),
    Rain("rain", "rain"), Snow("snow", "snow"), Thunderstorm("thunderstorm", "storm");

    val audioAsset: String get() = "weather/$file.m4a"

    companion object {
        /** Every code in Open-Meteo's WMO table, including 97. Unknown codes keep the quiet static sky. */
        fun fromWmo(code: Int, isDay: Boolean): WeatherAtmosphere? = when (code) {
            0, 1 -> if (isDay) ClearDay else ClearNight
            2 -> if (isDay) PartlyCloudyDay else PartlyCloudyNight
            3 -> Cloudy
            45, 48 -> Fog
            51, 53, 55, 56, 57 -> Drizzle
            61, 63, 65, 66, 67, 80, 81, 82 -> Rain
            71, 73, 75, 77, 85, 86 -> Snow
            95, 96, 97, 99 -> Thunderstorm
            else -> null
        }
    }
}
