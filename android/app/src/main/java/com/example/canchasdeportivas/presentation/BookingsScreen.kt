package com.example.canchasdeportivas.presentation

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.canchasdeportivas.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun BookingsScreen(m: AppViewModel) {
    var cancelled by remember { mutableStateOf<Booking?>(null) }
    var payment by remember { mutableStateOf<Booking?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30000); now = System.currentTimeMillis() } }
    val mine = m.bookings.filter { it.userId == m.profile?.uid }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Mis reservas", style = MaterialTheme.typography.headlineSmall)
            if (mine.isEmpty()) Text("Aún no tienes reservas. Elige una cancha para comenzar.")
        }
        items(mine, key = { it.id }) { b ->
            BookingCard(b, now) {
                if (b.canPay(now)) Action("Enviar comprobante", m.busy) { payment = b }
                val future = runCatching { LocalDate.parse(b.date).atTime(b.hour.toInt(), 0).atZone(PERU).toInstant().toEpochMilli() > now }.getOrDefault(false)
                if (b.visibleStatus(now) !in listOf("Cancelada", "Vencida") && future) OutlinedButton(onClick = { cancelled = b }, enabled = !m.busy) { Text("Cancelar reserva") }
            }
        }
    }
    cancelled?.let { b -> Confirm("¿Cancelar la reserva?", "Se liberará el horario. Si existe un pago aprobado, quedará una devolución pendiente de gestión manual.", m.busy,
        { cancelled = null }) { m.cancel(b.id) { cancelled = null } } }
    payment?.let { b -> PaymentDialog(m, b) { payment = null } }
}

@Composable
fun BookingCard(b: Booking, now: Long = System.currentTimeMillis(), actions: @Composable ColumnScope.() -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(b.courtName, style = MaterialTheme.typography.titleMedium)
            Text("${b.date} · ${hourLabel(b.hour)} · ${money(b.totalCents)}")
            ReservationStatus(b.visibleStatus(now))
            Text("Código: ${b.id.takeLast(12)}", style = MaterialTheme.typography.bodySmall)
            if (b.paymentReference.isNotEmpty()) Text("${b.paymentMethod} · Operación ${b.paymentReference}")
            if (b.reviewNote.isNotEmpty()) Text("Revisión: ${b.reviewNote}")
            if (b.refundPending) Text("Devolución pendiente de gestión manual", color = MaterialTheme.colorScheme.error)
            if (b.refunded) Text("Devolución registrada")
            actions()
        }
    }
}

@Composable
fun Modal(title: String, busy: Boolean, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) close() }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
                TextButton(onClick = close, enabled = !busy) { Text("Cerrar") }
            }
        }
    }
}

@Composable
fun ProofImage(encoded: String) {
    val bitmap = remember(encoded) { runCatching { val bytes = Base64.decode(encoded, Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }
    if (bitmap != null) Image(bitmap.asImageBitmap(), "Comprobante de pago", Modifier.fillMaxWidth().height(230.dp))
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
}

@Composable
private fun PaymentDialog(m: AppViewModel, b: Booking, close: () -> Unit) {
    var method by remember { mutableStateOf("Yape") }
    var reference by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf<PaymentSettings?>(null) }
    LaunchedEffect(Unit) {
        try { settings = m.paymentSettings() } catch (_: Exception) { localError = "No se pudieron cargar los datos del cobro." }
    }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            loading = true; localError = ""
            try { image = m.prepareProof(uri) } catch (e: Exception) { localError = e.message ?: "No se pudo leer la imagen" }
            finally { loading = false }
        }
    }
    Modal("Enviar comprobante", m.busy || loading, close) {
        Text("${b.courtName} · ${money(b.totalCents)}")
        settings?.let { config ->
            if (config.businessName.isNotBlank()) {
                Text("Titular: ${config.businessName}", style = MaterialTheme.typography.titleMedium)
                Text(config.instructions)
                Text(when (method) { "Yape" -> config.yape; "Plin" -> config.plin; else -> config.transfer }.ifBlank { "Consulta con el administrador el destino de este medio de pago." })
            } else Text("El administrador aún no registró los medios de cobro. Consúltale antes de realizar un pago.")
        }
        Text("El administrador debe comprobar el pago recibido. Adjuntar una imagen no confirma automáticamente la reserva.")
        listOf("Yape", "Plin", "Transferencia").forEach { value ->
            FilterChip(selected = method == value, onClick = { method = value }, enabled = !m.busy && !loading, label = { Text(value) })
        }
        Field("Número de operación", reference, { reference = it }, enabled = !m.busy && !loading)
        OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !m.busy && !loading) { Text(if (image.isEmpty()) "Elegir comprobante" else "Cambiar imagen") }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (image.isNotEmpty()) ProofImage(image)
        if (localError.isNotBlank()) Text(localError, color = MaterialTheme.colorScheme.error)
        if (m.isError) Text(m.message, color = MaterialTheme.colorScheme.error)
        Button(onClick = { m.payment(b.id, method, reference, image, close) }, enabled = image.isNotEmpty() && reference.trim().length >= 4 && !m.busy && !loading, modifier = Modifier.fillMaxWidth()) { Text("Enviar a revisión") }
    }
}
