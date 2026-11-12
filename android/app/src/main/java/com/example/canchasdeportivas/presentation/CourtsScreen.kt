package com.example.canchasdeportivas.presentation

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.canchasdeportivas.domain.model.*
import java.time.LocalDate

@Composable
fun CourtsScreen(m: AppViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf("Todos") }
    val courts = m.courts.filter { it.active && (sport == "Todos" || it.type == sport) &&
        (it.name.contains(search, true) || it.location.contains(search, true)) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WelcomeBanner(m.profile?.fullName.orEmpty())
                Text("Explora las canchas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${courts.size} opciones para tu próximo partido", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Field("Buscar por nombre o dirección", search, { search = it })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("Todos") + m.courts.filter { it.active }.map { it.type }.distinct()).forEach { value ->
                        FilterChip(selected = sport == value, onClick = { sport = value }, label = { Text(value) })
                    }
                }
                if (courts.isEmpty()) Text("No hay canchas para mostrar. El administrador debe registrar o habilitar una.")
            }
        }
        items(courts, key = { it.id }) { court -> CourtCard(court, m.busy) { m.openCourt(court) } }
    }
}

@Composable
fun CourtCard(court: Court, busy: Boolean, select: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        CourtIllustration(court.type, Modifier.fillMaxWidth().height(142.dp))
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                Text(court.type, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(court.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(court.location, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(money(court.priceCents), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(" / hora", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text("${court.openingHour}:00–${court.closingHour}:00", style = MaterialTheme.typography.labelMedium)
            }
            Action("Ver horarios", busy, select)
        }
    }
}

@Composable
fun CourtDetailScreen(m: AppViewModel) {
    val court = m.selectedCourt ?: return
    val context = LocalContext.current
    var selected by remember(m.selectedDate) { mutableStateOf<Int?>(null) }
    var blockHour by remember { mutableStateOf<Int?>(null) }
    var reason by remember { mutableStateOf("") }
    Form(court.name) {
        Text("${court.type} · ${court.location}")
        Text(court.description)
        Text("${money(court.priceCents)} por una hora", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = {
            val initial = LocalDate.parse(m.selectedDate)
            DatePickerDialog(context, { _, year, month, day -> m.date(LocalDate.of(year, month + 1, day).toString()) },
                initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                datePicker.minDate = today().atStartOfDay(PERU).toInstant().toEpochMilli()
                datePicker.maxDate = today().plusDays(60).atTime(23, 59).atZone(PERU).toInstant().toEpochMilli()
            }.show()
        }, enabled = !m.busy, modifier = Modifier.fillMaxWidth()) { Text("Fecha: ${m.selectedDate} · Cambiar") }
        Text("Horarios de una hora · zona horaria de Perú")
        if (m.slotsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        m.slots.forEach { slot ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selected == slot.hour, enabled = slot.available && !m.busy,
                    onClick = { selected = slot.hour }, label = { Text("${hourLabel(slot.hour.toLong())} · ${if (slot.available) "Libre" else if (slot.blocked) "Bloqueado" else "Ocupado / no disponible"}") }, modifier = Modifier.weight(1f))
                if (m.profile?.isAdmin == true && (slot.available || slot.blocked)) TextButton(onClick = {
                    if (slot.blocked) m.block(slot.hour, false, "") else blockHour = slot.hour
                }, enabled = !m.busy) { Text(if (slot.blocked) "Habilitar" else "Bloquear") }
            }
        }
        OutlinedButton(onClick = { selected = null; m.refreshSlots() }, enabled = !m.busy) { Text("Actualizar horarios") }
        Button(onClick = { selected?.let(m::reserve) }, enabled = selected != null && !m.busy && !m.slotsLoading, modifier = Modifier.fillMaxWidth()) { Text("Reservar · ${money(court.priceCents)}") }
        Text("El servidor reserva el horario por 15 minutos. Envía tu comprobante para revisión manual; la reserva se confirma después de su aprobación.", style = MaterialTheme.typography.bodySmall)
        CourtServicesCard(court)
        TextButton(onClick = m::back, enabled = !m.busy) { Text("Volver a canchas") }
    }
    blockHour?.let { hour ->
        AlertDialog(onDismissRequest = { if (!m.busy) blockHour = null }, title = { Text("Bloquear ${hourLabel(hour.toLong())}") },
            text = { Column { Field("Motivo", reason, { reason = it }); if (m.isError) Text(m.message) } },
            confirmButton = { TextButton(onClick = { if (reason.trim().length < 3) m.report("Indica el motivo del bloqueo", true) else { m.block(hour, true, reason); blockHour = null; reason = "" } }, enabled = !m.busy) { Text("Bloquear") } },
            dismissButton = { TextButton(onClick = { blockHour = null }, enabled = !m.busy) { Text("Volver") } })
    }
}
