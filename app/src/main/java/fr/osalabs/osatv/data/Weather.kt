package fr.osalabs.osatv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class Weather(val city: String, val temperature: Int, val code: Int, val isDay: Boolean) {
    val symbol: String
        get() = when (code) {
            0 -> if (isDay) "☀️" else "🌙"
            1, 2 -> if (isDay) "⛅" else "☁️"
            3 -> "☁️"
            45, 48 -> "🌫️"
            in 51..57 -> "🌦️"
            in 61..67, in 80..82 -> "🌧️"
            in 71..77, 85, 86 -> "❄️"
            in 95..99 -> "⛈️"
            else -> "🌡️"
        }
}

private data class Place(val city: String, val latitude: Double, val longitude: Double)

/** Position approximative via l'IP, puis météo Open-Meteo (gratuit, sans clé). */
suspend fun fetchWeather(): Weather? = withContext(Dispatchers.IO) {
    runCatching {
        val place = locate() ?: return@runCatching null
        val json = JSONObject(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}" +
                    "&longitude=${place.longitude}&current=temperature_2m,weather_code,is_day&timezone=auto"
            )
        ).getJSONObject("current")
        Weather(
            city = place.city,
            temperature = json.getDouble("temperature_2m").roundToInt(),
            code = json.getInt("weather_code"),
            isDay = json.optInt("is_day", 1) == 1,
        )
    }.getOrNull()
}

private fun locate(): Place? {
    runCatching {
        val json = JSONObject(get("https://ipwho.is/?fields=success,city,latitude,longitude"))
        if (json.optBoolean("success")) {
            return Place(json.getString("city"), json.getDouble("latitude"), json.getDouble("longitude"))
        }
    }
    return runCatching {
        val json = JSONObject(get("https://ipapi.co/json/"))
        Place(json.getString("city"), json.getDouble("latitude"), json.getDouble("longitude"))
    }.getOrNull()
}

private fun get(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 8_000
    connection.readTimeout = 8_000
    connection.setRequestProperty("User-Agent", "OsaTV")
    return try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
