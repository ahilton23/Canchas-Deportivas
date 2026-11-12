package com.example.canchasdeportivas.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.canchasdeportivas.domain.model.*
import java.math.RoundingMode

@Composable
fun AdminScreen(m: AppViewModel) {
    if (m.profile?.isAdmin != true) return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var review by remember { mutableStateOf<Booking?>(null) }
    var cancel by remember { mutableStateOf<Booking?>(null) }
    var refund by remember { mutableStateOf<Booking?>(null) }
    var paymentSettings by remember { mutableStateOf(false) }
    val paid = m.bookings.filter { it.paid }.sumOf { it.totalCents }
    val refunded = m.bookings.filter { it.refunded }.sumOf { it.totalCents }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gestión del complejo", style = MaterialTheme.typography.headlineSmall)
                Text("Pagos aprobados: ${money(paid)} · Devuelto: ${money(refunded)}")
                Text("Neto registrado: ${money(paid - refunded)}", style = MaterialTheme.typography.titleMedium)
                Text("Devoluciones pendientes: ${money(m.bookings.filter { it.refundPending }.sumOf { it.totalCents })}")
                Text("El resumen refleja registros aprobados manualmente; no consulta saldos bancarios.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { paymentSettings = true }, enabled = !m.busy) { Text("Configurar medios de cobro") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(tab == 0, { tab = 0 }, label = { Text("Canchas") })
                    FilterChip(tab == 1, { tab = 1 }, label = { Text("Reservas") })
                }
                if (tab == 0) Action("Agregar cancha", m.busy) { m.editCourt(null) }
            }
        }
        if (tab == 0) items(m.courts, key = { it.id }) { court ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(court.name, style = MaterialTheme.typography.titleMedium)
                    Text("${court.type} · ${money(court.priceCents)} · ${if (court.active) "Activa" else "Inactiva"}")
                    Action("Editar cancha", m.busy) { m.editCourt(court) }
                    if (court.active) OutlinedButton(onClick = { m.openCourt(court) }, enabled = !m.busy) { Text("Gestionar horarios") }
                }
            }
        } else items(m.bookings, key = { it.id }) { b ->
            BookingCard(b) {
                Text("Cliente: ${b.userName}")
                if (b.visibleStatus() == "En revisión") Action("Revisar comprobante", m.busy) { review = b }
                if (b.refundPending) Action("Registrar devolución realizada", m.busy) { refund = b }
                if (b.visibleStatus() !in listOf("Cancelada", "Vencida")) OutlinedButton(onClick = { cancel = b }, enabled = !m.busy) { Text("Cancelar reserva") }
            }
        }
    }
    review?.let { b -> ReviewDialog(m, b) { review = null } }
    if (paymentSettings) PaymentSettingsDialog(m) { paymentSettings = false }
    cancel?.let { b -> Confirm("Cancelar reserva", "Se liberará el horario de ${b.userName}. Un pago aprobado quedará pendiente de devolución manual.", m.busy, { cancel = null }) { m.cancel(b.id) { cancel = null } } }
    refund?.let { b -> Confirm("¿La devolución ya se realizó?", "Confirma solo después de devolver ${money(b.totalCents)} por el medio acordado. Esta acción solo registra la operación; no transfiere dinero.", m.busy, { refund = null }) { m.refund(b.id); refund = null } }
}

@Composable
private fun PaymentSettingsDialog(m: AppViewModel, close: () -> Unit) {
    var config by remember { mutableStateOf(PaymentSettings()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        try { config = m.paymentSettings() } catch (_: Exception) { m.report("No se pudieron cargar los medios de cobro", true) }
        finally { loading = false }
    }
    Modal("Medios de cobro", m.busy || loading, close) {
        Field("Titular o nombre del complejo", config.businessName, { config = config.copy(businessName = it) }, enabled = !m.busy && !loading)
        Field("Instrucciones", config.instructions, { config = config.copy(instructions = it) }, enabled = !m.busy && !loading)
        Field("Yape (número y titular)", config.yape, { config = config.copy(yape = it) }, enabled = !m.busy && !loading)
        Field("Plin (número y titular)", config.plin, { config = config.copy(plin = it) }, enabled = !m.busy && !loading)
        Field("Banco, cuenta o CCI", config.transfer, { config = config.copy(transfer = it) }, enabled = !m.busy && !loading)
        if (m.isError) Text(m.message, color = MaterialTheme.colorScheme.error)
        Action("Guardar medios de cobro", m.busy || loading) { m.savePaymentSettings(config, close) }
    }
}

@Composable
private fun ReviewDialog(m: AppViewModel, b: Booking, close: () -> Unit) {
    var proof by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(b.id) { try { proof = m.proof(b.id) } catch (_: Exception) { error = "No se pudo cargar el comprobante. Cierra e intenta nuevamente." } }
    Modal("Revisar pago", m.busy, close) {
        Text("${b.userName} · ${money(b.totalCents)}")
        Text("${b.paymentMethod} · Operación ${b.paymentReference}")
        Text("Verifica el ingreso en la cuenta del complejo antes de aprobar. Una captura puede ser falsa.")
        if (proof.isNotEmpty()) ProofImage(proof) else Text(if (error.isNotEmpty()) error else "Cargando comprobante…")
        Field("Observación (obligatoria al rechazar)", note, { note = it }, enabled = !m.busy)
        if (m.isError) Text(m.message, color = MaterialTheme.colorScheme.error)
        Button(onClick = { m.review(b.id, true, note.ifBlank { "Pago verificado manualmente" }, close) }, enabled = proof.isNotEmpty() && !m.busy, modifier = Modifier.fillMaxWidth()) { Text("Aprobar y confirmar reserva") }
        OutlinedButton(onClick = { m.review(b.id, false, note, close) }, enabled = note.trim().length >= 3 && !m.busy) { Text("Rechazar y liberar horario") }
    }
}

@Composable
fun CourtFormScreen(m: AppViewModel) {
    if (m.profile?.isAdmin != true) return
    val original = m.editingCourt ?: Court()
    var name by rememberSaveable { mutableStateOf(original.name) }
    var type by rememberSaveable { mutableStateOf(original.type.ifBlank { "Fútbol 7" }) }
    var location by rememberSaveable { mutableStateOf(original.location) }
    var description by rememberSaveable { mutableStateOf(original.description) }
    var price by rememberSaveable { mutableStateOf(if (original.priceCents > 0) (original.priceCents / 100.0).toString() else "") }
    var opening by rememberSaveable { mutableStateOf(original.openingHour.toString()) }
    var closing by rememberSaveable { mutableStateOf(original.closingHour.toString()) }
    var latitude by rememberSaveable { mutableStateOf(original.latitude.toString()) }
    var longitude by rememberSaveable { mutableStateOf(original.longitude.toString()) }
    var active by rememberSaveable { mutableStateOf(original.active) }
    Form(if (original.id.isEmpty()) "Nueva cancha" else "Editar cancha") {
        Field("Nombre", name, { name = it }, enabled = !m.busy)
        Field("Deporte", type, { type = it }, enabled = !m.busy)
        Field("Dirección", location, { location = it }, enabled = !m.busy)
        Field("Descripción", description, { description = it }, enabled = !m.busy)
        Field("Precio por hora (soles)", price, { price = it }, enabled = !m.busy, keyboard = KeyboardType.Decimal)
        Field("Hora de apertura (0 a 22)", opening, { opening = it }, enabled = !m.busy, keyboard = KeyboardType.Number)
        Field("Hora de cierre (1 a 23)", closing, { closing = it }, enabled = !m.busy, keyboard = KeyboardType.Number)
        Field("Latitud (-90 a 90)", latitude, { latitude = it }, enabled = !m.busy)
        Field("Longitud (-180 a 180)", longitude, { longitude = it }, enabled = !m.busy)
        Row { Text("Cancha activa", Modifier.weight(1f)); Switch(active, { active = it }, enabled = !m.busy) }
        Text("Desactivar oculta la cancha a nuevos clientes; las reservas existentes siguen disponibles para su gestión.")
        Action("Guardar cancha", m.busy) {
            val cents = runCatching { price.replace(',', '.').toBigDecimal().setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
            val start = opening.toLongOrNull(); val end = closing.toLongOrNull()
            if (cents == null || start == null || end == null || latitude.toDoubleOrNull()?.let { it in -90.0..90.0 } != true || longitude.toDoubleOrNull()?.let { it in -180.0..180.0 } != true) m.report("Ingresa un precio con hasta dos decimales y horas enteras", true)
            else m.saveCourt(original.copy(name = name, type = type, location = location, description = description, priceCents = cents, openingHour = start, closingHour = end, active = active, latitude = latitude.toDouble(), longitude = longitude.toDouble()))
        }
        TextButton(onClick = { m.navigate(Screen.ADMIN) }, enabled = !m.busy) { Text("Volver a gestión") }
    }
}
