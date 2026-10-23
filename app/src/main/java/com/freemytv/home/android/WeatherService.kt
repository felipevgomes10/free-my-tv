package com.freemytv.home.android

import android.content.SharedPreferences
import android.util.Log
import com.freemytv.home.weather.CachedForecast
import com.freemytv.home.weather.CurrentConditions
import com.freemytv.home.weather.DailyForecast
import com.freemytv.home.weather.Forecast
import com.freemytv.home.weather.HourlyForecast
import com.freemytv.home.weather.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Weather over HTTPS: Open-Meteo for forecasts and city search (no API key), and
 * an IP geolocation service to place the TV. Every failure is reported as null.
 */
class WeatherService(private val prefs: SharedPreferences) {

    /** ipinfo.io, falling back to geojs.io; both answer over HTTPS without a key. */
    suspend fun locateByIp(): Place? = fromIpinfo() ?: fromGeojs()

    private suspend fun fromIpinfo(): Place? {
        val json = get("https://ipinfo.io/json") ?: return null
        val city = json.optString("city").takeIf { it.isNotEmpty() } ?: return null
        val (lat, lon) = json.optString("loc").split(",").mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == 2 } ?: return null
        return Place(city, lat, lon)
    }

    private suspend fun fromGeojs(): Place? {
        val json = get("https://get.geojs.io/v1/ip/geo.json") ?: return null
        val city = json.optString("city").takeIf { it.isNotEmpty() } ?: return null
        val lat = json.optString("latitude").toDoubleOrNull() ?: return null
        val lon = json.optString("longitude").toDoubleOrNull() ?: return null
        return Place(city, lat, lon)
    }

    suspend fun geocode(city: String): Place? {
        val query = URLEncoder.encode(city, "UTF-8")
        val json = get("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=1&language=pt") ?: return null
        val result = json.optJSONArray("results")?.optJSONObject(0) ?: return null
        return Place(result.getString("name"), result.getDouble("latitude"), result.getDouble("longitude"))
    }

    suspend fun forecast(place: Place): Forecast? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=8"
        val json = get(url) ?: return null
        val forecast = runCatching { parse(json) }.onFailure { Log.w(TAG, "Unexpected forecast", it) }.getOrNull() ?: return null
        prefs.edit()
            .putString(CACHED_FORECAST, json.toString())
            .putString(CACHED_PLACE, JSONObject().put("name", place.name).put("lat", place.latitude).put("lon", place.longitude).toString())
            .apply()
        return forecast
    }

    suspend fun lastForecast(): CachedForecast? = withContext(Dispatchers.IO) {
        val forecast = prefs.getString(CACHED_FORECAST, null) ?: return@withContext null
        val place = prefs.getString(CACHED_PLACE, null)?.let(::JSONObject) ?: return@withContext null
        runCatching {
            CachedForecast(Place(place.getString("name"), place.getDouble("lat"), place.getDouble("lon")), parse(JSONObject(forecast)))
        }.getOrNull()
    }

    private suspend fun get(url: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", "FreeMyTV")
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun parse(json: JSONObject): Forecast {
        val current = json.getJSONObject("current")
        val hourly = json.getJSONObject("hourly")
        val daily = json.getJSONObject("daily")
        val hourTimes = hourly.getJSONArray("time")
        val dayDates = daily.getJSONArray("time")
        return Forecast(
            current = CurrentConditions(
                time = LocalDateTime.parse(current.getString("time")),
                temperature = current.getDouble("temperature_2m"),
                weatherCode = current.getInt("weather_code"),
                humidity = current.getInt("relative_humidity_2m"),
                isDay = current.getInt("is_day") == 1,
            ),
            hourly = List(hourTimes.length()) { i ->
                HourlyForecast(
                    time = LocalDateTime.parse(hourTimes.getString(i)),
                    temperature = hourly.getJSONArray("temperature_2m").getDouble(i),
                    weatherCode = hourly.getJSONArray("weather_code").getInt(i),
                    isDay = hourly.getJSONArray("is_day").getInt(i) == 1,
                )
            },
            daily = List(dayDates.length()) { i ->
                DailyForecast(
                    date = LocalDate.parse(dayDates.getString(i)),
                    weatherCode = daily.getJSONArray("weather_code").getInt(i),
                    min = daily.getJSONArray("temperature_2m_min").getDouble(i),
                    max = daily.getJSONArray("temperature_2m_max").getDouble(i),
                    rainChance = daily.getJSONArray("precipitation_probability_max").optIntOrZero(i),
                )
            },
        )
    }

    private fun JSONArray.optIntOrZero(i: Int) = if (isNull(i)) 0 else getInt(i)

    private companion object {
        const val TAG = "WeatherService"
        const val TIMEOUT_MILLIS = 10_000
        const val CACHED_FORECAST = "cached_forecast"
        const val CACHED_PLACE = "cached_place"
    }
}
