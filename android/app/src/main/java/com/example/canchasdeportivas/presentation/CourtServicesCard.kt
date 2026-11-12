package com.example.canchasdeportivas.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.canchasdeportivas.data.*
import com.example.canchasdeportivas.domain.model.Court
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CourtServicesCard(court: Court) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf<Location?>(null) }
    var locationMessage by remember { mutableStateOf("") }
    var weather by remember(court.id) { mutableStateOf<Weather?>(null) }
    var weatherMessage by remember { mutableStateOf("") }
    var weatherLoading by remember { mutableStateOf(false) }
    fun locate() {
        if (locating) return
        scope.launch {
            locating = true; locationMessage = "Buscando ubicación…"
            try { position = DeviceLocation(context).current(); locationMessage = "" }
            catch (_: TimeoutCancellationException) { locationMessage = "No se obtuvo una ubicación. Activa GPS y vuelve a intentar." }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { locationMessage = "No se pudo obtener la ubicación. Revisa el permiso y el GPS." }
            finally { locating = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locate() else locationMessage = "Permiso no concedido. Puedes reservar sin compartir tu ubicación."
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ubicación y clima", style = MaterialTheme.typography.titleMedium)
            Text("Coordenadas de la cancha: ${court.latitude}, ${court.longitude}")
            Text("Las canchas del catálogo de prueba usan ubicaciones de demostración.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${court.latitude},${court.longitude}?q=${court.latitude},${court.longitude}(${Uri.encode(court.name)})"))) }
                catch (_: Exception) { locationMessage = "Instala una aplicación de mapas para abrir esta ubicación." }
            }) { Text("Abrir en mapas") }
            Text("Tu ubicación se usa solo para calcular la distancia en este teléfono.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !locating, onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
                else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text(if (locating) "Localizando…" else "Calcular distancia desde mi ubicación") }
            position?.let {
                val result = FloatArray(1); Location.distanceBetween(it.latitude,it.longitude,court.latitude,court.longitude,result)
                Text(String.format(Locale.US,"Distancia en línea recta: %.2f km · precisión GPS ±%.0f m", result[0]/1000, it.accuracy))
            }
            if (locationMessage.isNotEmpty()) Text(locationMessage)
            HorizontalDivider()
            Text("Clima actual de la cancha")
            weather?.let { Text("${it.temperature} °C · lluvia ${it.rain} mm\nActualizado: ${it.time.replace('T',' ')} (Perú)") }
            if (weatherMessage.isNotEmpty()) Text(weatherMessage)
            OutlinedButton(enabled = !weatherLoading, onClick = {
                scope.launch {
                    weatherLoading = true; weatherMessage = ""
                    try { weather = WeatherApi().current(court.latitude,court.longitude) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { weatherMessage = "No se pudo consultar el clima. Comprueba Internet e intenta otra vez." }
                    finally { weatherLoading = false }
                }
            }) { Text(if (weatherLoading) "Consultando…" else "Consultar clima") }
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))) } }) { Text("Datos meteorológicos: Open-Meteo") }
        }
    }
}
