package com.example.canchasdeportivas.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Weather(val temperature: Double, val rain: Double, val time: String)

/** API REST pública HTTPS, sin clave ni SDK de mapas. Datos de la ubicación de la cancha. */
class WeatherApi {
    suspend fun current(latitude: Double, longitude: Double): Weather = withContext(Dispatchers.IO) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val connection = URL("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,precipitation&timezone=America%2FLima").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000; connection.readTimeout = 10000
            check(connection.responseCode == 200) { "El servicio de clima no está disponible" }
            val data = connection.inputStream.bufferedReader().use { JSONObject(it.readText()).getJSONObject("current") }
            Weather(data.getDouble("temperature_2m"), data.getDouble("precipitation"), data.getString("time"))
        } finally { connection.disconnect() }
    }
}
