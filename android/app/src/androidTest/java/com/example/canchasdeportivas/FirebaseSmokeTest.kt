package com.example.canchasdeportivas

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.canchasdeportivas.domain.model.hourLabel
import com.example.canchasdeportivas.domain.model.today
import com.example.canchasdeportivas.presentation.AppViewModel
import com.example.canchasdeportivas.presentation.Screen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.test.rule.GrantPermissionRule
import org.junit.rules.TestRule
import android.os.Build
import java.io.File

/** Requiere los emuladores de Firebase y scripts/seed.mjs --local. Nunca usa la nube. */
class FirebaseSmokeTest {
    @get:Rule(order = 0) val localNetwork: TestRule = if (Build.VERSION.SDK_INT >= 37)
        GrantPermissionRule.grant("android.permission.ACCESS_LOCAL_NETWORK") else TestRule { base, _ -> base }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule(order = 0) val location = GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
    private val model get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]
    private fun ready() = compose.waitUntil(60000) { !model.busy && !model.sessionLoading }
    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val folder = File(compose.activity.getExternalFilesDir(null), "evidencias").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun login(email: String) {
        compose.waitUntil(60000) { !model.sessionLoading && model.profile == null }
        compose.onNodeWithText("Correo electrónico").performTextInput(email)
        compose.onNodeWithText("Contraseña").performTextInput("Canchas2026")
        compose.onNodeWithText("Ingresar").performScrollTo().performClick()
        compose.waitUntil(60000) { model.profile != null && !model.sessionLoading && !model.busy }
    }

    @Test fun clientReservationAndAdminPaymentReviewUseFirebase() {
        assertTrue(BuildConfig.FIREBASE_EMULATORS)
        ready()
        if (model.profile != null) compose.runOnIdle { model.logout() }
        login("cliente@canchas.test")
        compose.waitUntil(60000) { model.courts.isNotEmpty() }
        shot("01-canchas")
        val court = model.courts.first()
        compose.runOnIdle { model.openCourt(court); model.date(today().plusDays(3).toString()) }
        compose.waitUntil(60000) { !model.slotsLoading && model.slots.any { it.available } }
        shot("02-horarios")
        compose.onNodeWithText("Consultar clima").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("°C", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Consultar clima").performScrollTo()
        shot("07-rest-clima-ubicacion")
        gpsEvidence()
        val hour = model.slots.first { it.available }.hour
        compose.onNodeWithText("${hourLabel(hour.toLong())} · Libre").performScrollTo().performClick()
        compose.onNodeWithText("Reservar ·", substring = true).performScrollTo().performClick()
        compose.waitUntil(60000) { !model.busy && model.screen == Screen.BOOKINGS && model.bookings.any { it.hour.toInt() == hour && it.canPay() } }
        val booking = model.bookings.first { it.hour.toInt() == hour && it.canPay() }
        assertFalse(booking.paid)
        shot("03-reserva-pendiente")
        compose.onNodeWithText("Perfil").performClick()
        shot("04-perfil")
        // Comprobante ficticio; backend ya valida formato, privacidad, estado y permisos.
        val image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJXcAAAAASUVORK5CYII="
        compose.runOnIdle { model.payment(booking.id, "Yape", "UI-${System.currentTimeMillis()}", image) {} }
        compose.waitUntil(60000) { !model.busy && model.bookings.any { it.id == booking.id && it.status == "En revisión" } }
        compose.runOnIdle { model.logout() }
        login("admin@canchas.test")
        compose.onNodeWithText("Gestión").performClick()
        shot("05-panel-admin")
        compose.runOnIdle { model.review(booking.id, true, "Pago ficticio verificado en prueba local") {} }
        compose.waitUntil(60000) { !model.busy && model.bookings.any { it.id == booking.id && it.status == "Confirmada" } }
        assertTrue(model.bookings.first { it.id == booking.id }.paid)
        compose.runOnIdle { model.navigate(Screen.PROFILE) }
        shot("06-perfil-administrador")
        compose.runOnIdle { model.cancel(booking.id) {} }
        ready()
        compose.runOnIdle { model.refund(booking.id) }
        ready()
        assertTrue(model.bookings.first { it.id == booking.id }.refunded)
        compose.runOnIdle { model.logout() }
    }

    @Suppress("DEPRECATION")
    private fun gpsEvidence() {
        // Solo el emulador de pruebas, con appops mock_location habilitado mediante adb.
        val manager = compose.activity.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val provider = android.location.LocationManager.GPS_PROVIDER
        manager.addTestProvider(provider,false,false,false,false,true,true,true,android.location.Criteria.POWER_LOW,android.location.Criteria.ACCURACY_FINE)
        try {
            manager.setTestProviderEnabled(provider,true)
            compose.onNodeWithText("Calcular distancia desde mi ubicación").performScrollTo().performClick()
            repeat(5) {
                Thread.sleep(200)
                manager.setTestProviderLocation(provider,android.location.Location(provider).apply {
                    latitude=-8.12; longitude=-79.03; accuracy=5f; time=System.currentTimeMillis(); elapsedRealtimeNanos=android.os.SystemClock.elapsedRealtimeNanos()
                })
            }
            compose.waitUntil(15000) { compose.onAllNodesWithText("Distancia en línea recta:", substring=true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Distancia en línea recta:",substring=true).performScrollTo()
            shot("08-geolocalizacion")
        } finally { manager.removeTestProvider(provider) }
    }
}
