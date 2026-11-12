package com.example.canchasdeportivas.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.canchasdeportivas.BuildConfig

@Composable
fun AppScreen(m: AppViewModel) {
    BackHandler(enabled = m.profile != null && m.screen in listOf(Screen.DETAIL, Screen.COURT_FORM)) { m.back() }
    Scaffold(topBar = {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Canchas Deportivas", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (m.profile != null || m.sessionFailed) TextButton(onClick = m::logout, enabled = !m.busy) { Text("Salir") }
                }
                if (BuildConfig.FIREBASE_EMULATORS) Text("Prueba local · requiere emuladores de Firebase", style = MaterialTheme.typography.labelSmall)
            }
        }
    }, bottomBar = {
        if (m.profile != null && !m.sessionFailed) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            val tabs = listOf(Screen.COURTS to "Canchas", Screen.BOOKINGS to "Reservas", Screen.PROFILE to "Perfil") +
                if (m.profile?.isAdmin == true) listOf(Screen.ADMIN to "Gestión") else emptyList()
            tabs.forEachIndexed { index, (screen, title) ->
                NavigationBarItem(selected = m.screen == screen, enabled = !m.busy,
                    onClick = { m.navigate(screen) }, icon = { NavSymbol(index) }, label = { Text(title) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (m.busy || m.sessionLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (m.message.isNotBlank()) Surface(color = if (m.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
                Text(m.message, Modifier.fillMaxWidth().padding(12.dp))
            }
            when {
                m.sessionLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Conectando tu cuenta…") }
                m.sessionFailed -> Form("No se pudo cargar la cuenta") {
                    Text("Verifica la conexión con Firebase. Tus datos no se sustituirán por información de ejemplo.")
                    Action("Reintentar", m.busy, m::openSession)
                }
                m.profile == null -> AuthScreen(m)
                else -> key(m.screen) {
                    when (m.screen) {
                        Screen.COURTS -> CourtsScreen(m)
                        Screen.DETAIL -> CourtDetailScreen(m)
                        Screen.BOOKINGS -> BookingsScreen(m)
                        Screen.PROFILE -> ProfileScreen(m)
                        Screen.ADMIN -> AdminScreen(m)
                        Screen.COURT_FORM -> CourtFormScreen(m)
                    }
                }
            }
        }
    }
}
