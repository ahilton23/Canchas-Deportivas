package com.example.canchasdeportivas.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun Form(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        content(); Spacer(Modifier.height(24.dp))
    }
}
@Composable
fun Field(label: String, value: String, change: (String) -> Unit, secret: Boolean = false, enabled: Boolean = true, keyboard: KeyboardType = KeyboardType.Text) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), enabled = enabled, label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(14.dp),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
        trailingIcon = if (secret) ({ TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar" else "Ver") } }) else null)
}
@Composable
fun Action(label: String, busy: Boolean, action: () -> Unit) {
    Button(onClick = action, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) { Text(label, fontWeight = FontWeight.SemiBold) }
}
@Composable
fun Confirm(title: String, description: String, busy: Boolean, close: () -> Unit, action: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(onClick = action, enabled = !busy) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = close, enabled = !busy) { Text("Volver") } })
}
