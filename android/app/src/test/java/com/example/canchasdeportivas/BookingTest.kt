package com.example.canchasdeportivas

import com.example.canchasdeportivas.domain.model.Booking
import com.example.canchasdeportivas.domain.model.money
import org.junit.Assert.*
import org.junit.Test

class BookingTest {
    @Test fun pendingBookingsExpireAndConfirmedBookingsDoNot() {
        val b = Booking(status = "Pendiente de pago", holdUntilMs = 1000)
        assertTrue(b.canPay(999)); assertFalse(b.canPay(1000))
        assertEquals("Vencida", b.visibleStatus(1001))
        assertEquals("Confirmada", b.copy(status = "Confirmada").visibleStatus(1001))
        assertEquals("Cancelada", b.copy(status = "Cancelada").visibleStatus(1001))
    }
    @Test fun moneyUsesCentsWithoutChangingTheAmount() { assertEquals("S/ 60.50", money(6050)) }
}
