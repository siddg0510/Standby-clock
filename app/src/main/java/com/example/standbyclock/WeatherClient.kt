package com.example.standbyclock

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** Weather from Open-Meteo, a free service that needs no API key or account. */
object WeatherClient {

    private const val TAG = "StandbyWeather"

    data class Current(val temperature: Double, val code: Int, val unit: String)

    data class Place(val latitude: Double, val longitude: Double, val name: String)

    /** Blocking network call. Call it from a background thread only. */
    fun fetch(latitude: Double, longitude: Double, fahrenheit: Boolean): Current? {
        val unit = if (fahrenheit) "fahrenheit" else "celsius"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code&temperature_unit=$unit"
        return getJson(url)?.let { body ->
            try {
                val current = JSONObject(body).getJSONObject("current")
                Current(
                    temperature = current.getDouble("temperature_2m"),
                    code = current.getInt("weather_code"),
                    unit = if (fahrenheit) "°F" else "°C",
                )
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected weather reply: ${e.message}")
                null
            }
        }
    }

    data class Hour(val label: String, val temperature: Double, val code: Int)

    /**
     * The next [hours] hours of temperature and weather code, in local time.
     * Blocking network call. Call it from a background thread only.
     */
    fun fetchHourly(latitude: Double, longitude: Double, fahrenheit: Boolean, hours: Int = 6): List<Hour> {
        val unit = if (fahrenheit) "fahrenheit" else "celsius"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
            "&hourly=temperature_2m,weather_code&forecast_hours=$hours&timezone=auto&temperature_unit=$unit"
        return getJson(url)?.let { body ->
            try {
                val hourly = JSONObject(body).getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                val temps = hourly.getJSONArray("temperature_2m")
                val codes = hourly.getJSONArray("weather_code")
                // Times look like "2026-10-19T14:00"; the label keeps just the hour.
                (0 until minOf(times.length(), hours)).map { i ->
                    Hour(
                        label = times.getString(i).substringAfter('T'),
                        temperature = temps.getDouble(i),
                        code = codes.getInt(i),
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected hourly reply: ${e.message}")
                emptyList()
            }
        }.orEmpty()
    }

    /** Finds the first place matching [name] using Open-Meteo's geocoding. Blocking; call from a background thread. */
    fun geocode(name: String): Place? {
        val query = URLEncoder.encode(name.trim(), "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$query&count=1"
        return getJson(url)?.let { body ->
            try {
                val results = JSONObject(body).optJSONArray("results") ?: return@let null
                if (results.length() == 0) return@let null
                val first = results.getJSONObject(0)
                Place(first.getDouble("latitude"), first.getDouble("longitude"), first.optString("name", name))
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected geocoding reply: ${e.message}")
                null
            }
        }
    }

    /** GET a JSON document. Blocking; used for weather, geocoding and the ISS position. */
    fun getJson(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Request failed: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Short description for a WMO weather code. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> ""
    }
}
