package com.example.canchasdeportivas.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** Lectura puntual. El llamador obtiene permiso; no hay seguimiento ni envío a Firebase. */
class DeviceLocation(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    @SuppressLint("MissingPermission")
    suspend fun current(): Location = withTimeout(15000) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }
            }
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            val fine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val providers = (if (fine) listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER) else listOf(LocationManager.NETWORK_PROVIDER)).filter { manager.isProviderEnabled(it) }
            if (providers.isEmpty()) {
                continuation.cancel(IllegalStateException("Activa la ubicación del dispositivo"))
            } else {
                try { providers.forEach { manager.requestLocationUpdates(it, 0, 0f, listener, Looper.getMainLooper()) } }
                catch (e: SecurityException) { continuation.cancel(e) }
            }
        }
    }
}
