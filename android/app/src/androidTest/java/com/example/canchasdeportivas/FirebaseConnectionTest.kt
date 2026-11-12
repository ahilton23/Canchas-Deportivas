package com.example.canchasdeportivas

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TestRule
import android.os.Build
import androidx.test.rule.GrantPermissionRule
import java.net.HttpURLConnection
import java.net.URL

class FirebaseConnectionTest {
    @get:Rule val localNetwork: TestRule = if (Build.VERSION.SDK_INT >= 37)
        GrantPermissionRule.grant("android.permission.ACCESS_LOCAL_NETWORK") else TestRule { base, _ -> base }
    @Test fun authenticationAndFirestoreAreReachableFromAndroid() = runBlocking {
        assertTrue(BuildConfig.FIREBASE_EMULATORS)
        val connection = URL("http://${BuildConfig.EMULATOR_HOST}:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-api-key").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write("""{"email":"cliente@canchas.test","password":"Canchas2026","returnSecureToken":true}""".toByteArray()) }
            assertEquals(200, connection.responseCode)
        } finally { connection.disconnect() }
        val repo = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CanchasApp).repository
        try {
            withTimeout(30000) { repo.login("cliente@canchas.test", "Canchas2026"); repo.ensureProfile("Cliente") }
            assertNotNull(repo.auth.currentUser)
        } finally { repo.auth.signOut() }
    }
}
