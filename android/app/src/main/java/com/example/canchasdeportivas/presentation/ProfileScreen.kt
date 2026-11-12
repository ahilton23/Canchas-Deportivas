package com.example.canchasdeportivas.presentation

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ProfileScreen(m: AppViewModel) {
    val user = m.profile ?: return
    var name by rememberSaveable(user.uid) { mutableStateOf(user.fullName) }
    var phone by rememberSaveable(user.uid) { mutableStateOf(user.phone) }
    var current by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Form("Mi perfil") {
        Text(user.email)
        Text(if (user.isAdmin) "Administrador" else "Deportista", style = MaterialTheme.typography.labelLarge)
        Field("Nombre completo", name, { name = it }, enabled = !m.busy)
        Field("Teléfono", phone, { phone = it }, enabled = !m.busy, keyboard = KeyboardType.Phone)
        Action("Guardar perfil", m.busy) { m.saveProfile(name, phone) }
        HorizontalDivider()
        Text("Cambiar contraseña", style = MaterialTheme.typography.titleMedium)
        Field("Contraseña actual", current, { current = it }, secret = true, enabled = !m.busy)
        Field("Nueva contraseña", pass, { pass = it }, secret = true, enabled = !m.busy)
        Field("Confirmar nueva contraseña", confirm, { confirm = it }, secret = true, enabled = !m.busy)
        Action("Actualizar contraseña", m.busy) { m.password(current, pass, confirm) { current = ""; pass = ""; confirm = "" } }
    }
}
