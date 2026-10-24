package com.freemytv.home.weather

/** Local, looping footage with a quiet, purpose-made noise bed; never fetched by the TV. */
enum class WeatherAtmosphere(val file: String) {
    ClearDay("clear_day"), ClearNight("clear_night"),
    PartlyCloudyDay("partly_cloudy_day"), PartlyCloudyNight("partly_cloudy_night"),
    Cloudy("cloudy"), Fog("fog"), Drizzle("drizzle"), Rain("rain"), Snow("snow"), Thunderstorm("thunderstorm");

    val asset: String get() = "weather/$file.mp4"

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
