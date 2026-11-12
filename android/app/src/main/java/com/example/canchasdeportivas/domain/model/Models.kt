package com.example.canchasdeportivas.domain.model

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class Profile(val uid: String = "", val fullName: String = "", val email: String = "", val phone: String = "", val role: String = "client") {
    val isAdmin get() = role == "admin"
}
data class Court(val id: String = "", val name: String = "", val type: String = "", val location: String = "",
    val description: String = "", val priceCents: Long = 0, val openingHour: Long = 8, val closingHour: Long = 23, val active: Boolean = true, val latitude: Double = -8.1116, val longitude: Double = -79.0288)
data class Slot(val hour: Int, val available: Boolean, val blocked: Boolean)
data class PaymentSettings(val businessName: String = "", val instructions: String = "", val yape: String = "", val plin: String = "", val transfer: String = "")
data class Booking(val id: String = "", val userId: String = "", val userName: String = "", val courtId: String = "", val courtName: String = "",
    val date: String = "", val hour: Long = 0, val totalCents: Long = 0, val status: String = "", val createdAtMs: Long = 0,
    val holdUntilMs: Long = 0, val paymentMethod: String = "", val paymentReference: String = "", val reviewNote: String = "",
    val startMs: Long = 0, val updatedAtMs: Long = 0, val paymentKey: String = "", val reviewedBy: String = "", val cancellationReason: String = "", val refundedBy: String = "",
    val paid: Boolean = false, val refunded: Boolean = false, val refundPending: Boolean = false) {
    fun visibleStatus(now: Long = System.currentTimeMillis()) =
        if (status in listOf("Pendiente de pago", "En revisión") && holdUntilMs <= now) "Vencida" else status
    fun canPay(now: Long = System.currentTimeMillis()) = visibleStatus(now) == "Pendiente de pago"
}
val PERU: ZoneId = ZoneId.of("America/Lima")
fun today(): LocalDate = LocalDate.now(PERU)
fun money(cents: Long) = String.format(Locale.US, "S/ %.2f", cents / 100.0)
fun hourLabel(hour: Long) = "%02d:00 - %02d:00".format(hour, hour + 1)
