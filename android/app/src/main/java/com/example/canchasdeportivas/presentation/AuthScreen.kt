package com.example.canchasdeportivas.presentation

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun AuthScreen(m: AppViewModel) {
    var page by rememberSaveable { mutableStateOf("login") }
    key(page) {
        var name by rememberSaveable { mutableStateOf("") }
        var email by rememberSaveable { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        Form(when (page) { "register" -> "Crea tu cuenta"; "recovery" -> "Recupera tu contraseña"; else -> "Bienvenido de nuevo" }) {
            if (page == "login") WelcomeBanner()
            if (page == "register") Field("Nombre completo", name, { name = it }, enabled = !m.busy)
            Field("Correo electrónico", email, { email = it }, enabled = !m.busy, keyboard = KeyboardType.Email)
            if (page != "recovery") Field("Contraseña", pass, { pass = it }, secret = true, enabled = !m.busy)
            if (page == "register") {
                Text("Mínimo 8 caracteres, con letras y números.")
                Field("Confirmar contraseña", confirm, { confirm = it }, secret = true, enabled = !m.busy)
            }
            Action(when (page) { "register" -> "Registrarme"; "recovery" -> "Enviar instrucciones"; else -> "Ingresar" }, m.busy) {
                when (page) {
                    "register" -> m.register(name, email, pass, confirm)
                    "recovery" -> m.recover(email) { page = "login" }
                    else -> m.login(email, pass)
                }
            }
            if (page == "login") {
                TextButton(onClick = { page = "register"; m.report("") }, enabled = !m.busy) { Text("Crear una cuenta") }
                TextButton(onClick = { page = "recovery"; m.report("") }, enabled = !m.busy) { Text("Olvidé mi contraseña") }
            } else TextButton(onClick = { page = "login"; m.report("") }, enabled = !m.busy) { Text("Volver al inicio") }
        }
    }
}
