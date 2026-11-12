package com.example.canchasdeportivas

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.canchasdeportivas.presentation.AppViewModel
import com.example.canchasdeportivas.presentation.Screen
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Solo lectura en la nube. Requiere argumentos demoEmail y demoPassword; no crea reservas. */
class CloudDesignTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]
    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(compose.activity.getExternalFilesDir(null),"evidencias-ui").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun cloudCatalogAndNavigationRenderWithNewDesign() {
        val args = InstrumentationRegistry.getArguments()
        val email = args.getString("demoEmail")
        val password = args.getString("demoPassword")
        assumeTrue(!BuildConfig.FIREBASE_EMULATORS && email != null && password != null)
        compose.waitUntil(30000) { !model.sessionLoading }
        // No reemplaza una sesión existente de otra persona.
        assertNull(model.profile)
        try {
            compose.onNodeWithText("Correo electrónico").performTextInput(requireNotNull(email))
            compose.onNodeWithText("Contraseña").performTextInput(requireNotNull(password))
            compose.onNodeWithText("Ingresar").performScrollTo().performClick()
            compose.waitUntil(30000) { !model.busy && model.profile != null && model.courts.count { it.id.startsWith("demo-") } >= 6 }
            assertFalse(requireNotNull(model.profile).isAdmin)
            shot("02-catalogo-firebase")
            val court=model.courts.first { it.id.startsWith("demo-") }
            compose.runOnIdle { model.openCourt(court) }
            compose.waitUntil(30000) { !model.slotsLoading }
            assertTrue(model.slots.isNotEmpty())
            shot("03-detalle-cancha")
            compose.runOnIdle { model.navigate(Screen.PROFILE) }
            shot("04-perfil-cliente")
            compose.onNodeWithText("Reservas").performClick()
            shot("05-reservas")
        } finally { compose.runOnIdle { if (!model.busy) model.logout() } }
    }
}
