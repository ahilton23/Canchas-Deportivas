package com.example.canchasdeportivas

import android.Manifest
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.canchasdeportivas.data.*
import com.example.canchasdeportivas.domain.model.Profile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class LocalServicesTest {
    @get:Rule val permissions = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun sqlitePersistsAcrossReopenAndSeparatesAccounts() {
        LocalCache(context).use { it.write("cache-test-a", "profile", Profile(uid="cache-test-a",fullName="Prueba SQLite")) }
        LocalCache(context).use {
            assertEquals("Prueba SQLite", it.read<Profile>("cache-test-a","profile",Profile::class.java)?.fullName)
            assertNull(it.read<Profile>("cache-test-b","profile",Profile::class.java))
            it.clear()
            assertNull(it.read<Profile>("cache-test-a","profile",Profile::class.java))
        }
    }
    @Test fun restRetrievesRealWeatherOverHttps() = runBlocking {
        val weather = withTimeout(30000) { WeatherApi().current(-8.1116,-79.0288) }
        assertTrue(weather.temperature in -70.0..70.0)
        assertTrue(weather.rain >= 0)
        assertTrue(weather.time.isNotBlank())
    }
    @Test fun repositoryReadsSqliteWithNetworkListenersDisabled() = runBlocking {
        assumeTrue(BuildConfig.FIREBASE_EMULATORS)
        val repo = (context.applicationContext as CanchasApp).repository
        val db = FirebaseFirestore.getInstance()
        try {
            withTimeout(30000) {
                repo.login("cliente@canchas.test","Canchas2026")
                repo.ensureProfile("Deportista")
                val owner = requireNotNull(repo.auth.currentUser).uid
                repo.profile(owner).first { LocalCache(context).use { disk -> disk.read<Profile>(owner,"profile",Profile::class.java) != null } }
                val courts = repo.courts(false).first { it.isNotEmpty() }
                db.disableNetwork().await()
                // Instancia nueva: no depende de los estados de Compose ni de colecciones en memoria.
                val reopened = FirebaseRepository(repo.auth,db,LocalCache(context))
                assertEquals(owner,reopened.profile(owner).first().uid)
                assertEquals(courts.map { it.id },reopened.courts(false).first().map { it.id })
                // disableNetwork() desactiva listeners; no simula un corte físico para los RPC de transacciones.
            }
        } finally { db.enableNetwork().await(); repo.auth.signOut(); repo.clearCache() }
    }
    @Suppress("DEPRECATION")
    @Test fun locationReadsGpsAndStopsAfterOnePosition() = runBlocking {
        assumeTrue(BuildConfig.FIREBASE_EMULATORS && Build.HARDWARE == "ranchu")
        // adb shell appops set com.example.canchasdeportivas android:mock_location allow
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        manager.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE)
        try {
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true)
            val reading = async { DeviceLocation(context).current() }
            repeat(5) {
                delay(300)
                manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
                    latitude=-8.1116; longitude=-79.0288; accuracy=5f; time=System.currentTimeMillis(); elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
                })
            }
            val position = reading.await()
            assertEquals(-8.1116,position.latitude,0.001)
            assertEquals(-79.0288,position.longitude,0.001)
        } finally { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
    }
}
