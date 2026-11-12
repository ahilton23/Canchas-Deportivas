package com.example.canchasdeportivas

import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.canchasdeportivas.presentation.AppViewModel
import com.example.canchasdeportivas.presentation.AppScreen
import com.example.canchasdeportivas.ui.theme.CanchasDeportivasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CanchasDeportivasTheme() { LocalNetworkGate { val model: AppViewModel = viewModel(); AppScreen(model) } } }
    }
}

@Composable
private fun LocalNetworkGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val permission = "android.permission.ACCESS_LOCAL_NETWORK"
    val needsPermission = BuildConfig.FIREBASE_EMULATORS && Build.VERSION.SDK_INT >= 37
    var granted by remember { mutableStateOf(!needsPermission || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) content() else Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Conectar con Firebase local", style = MaterialTheme.typography.headlineSmall)
            Text("Esta versión de prueba utiliza los emuladores de Firebase de tu computadora. Android 17 requiere autorizar la conexión a la red local.")
            Text("Inicia primero Iniciar-Firebase.ps1 y Cargar-Demo.ps1. La versión conectada a Firebase en la nube no solicita este permiso.")
            Button(onClick = { launcher.launch(permission) }) { Text("Permitir conexión local") }
        }
    }
}
