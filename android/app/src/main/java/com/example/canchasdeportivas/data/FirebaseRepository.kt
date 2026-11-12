package com.example.canchasdeportivas.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.canchasdeportivas.domain.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.UUID

class FirebaseRepository(val auth: FirebaseAuth, private val db: FirebaseFirestore, private val cache: LocalCache) {
    private val uid get() = requireNotNull(auth.currentUser).uid
    fun clearCache() = cache.clear()
    suspend fun login(email: String, password: String) { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    suspend fun register(name: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
        auth.currentUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())?.await()
    }
    suspend fun recover(email: String) { auth.sendPasswordResetEmail(email.trim()).await() }
    suspend fun ensureProfile(name: String) {
        if (cache.read<Profile>(uid, "profile", Profile::class.java) != null) return
        val user = requireNotNull(auth.currentUser)
        val ref = db.document("users/$uid")
        db.runTransaction { tx ->
            if (!tx.get(ref).exists()) tx.set(ref, mapOf("uid" to uid, "fullName" to name.trim().take(80),
                "email" to user.email.orEmpty(), "phone" to "", "role" to "client"))
        }.await()
    }
    suspend fun saveProfile(name: String, phone: String) {
        require(name.trim().length in 2..80 && phone.length <= 25) { "Revisa nombre y teléfono" }
        val ref = db.document("users/$uid")
        db.runTransaction { tx -> tx.get(ref); tx.update(ref, mapOf("fullName" to name.trim(), "phone" to phone.trim())); Unit }.await()
    }
    suspend fun paymentSettings(): PaymentSettings = db.document("settings/payment").get(Source.SERVER).await().toObject(PaymentSettings::class.java) ?: PaymentSettings()
    suspend fun savePaymentSettings(settings: PaymentSettings) {
        val ref = db.document("settings/payment")
        db.runTransaction { tx -> tx.get(ref); tx.set(ref, settings); Unit }.await()
    }
    suspend fun changePassword(current: String, new: String) {
        val user = requireNotNull(auth.currentUser)
        user.reauthenticate(com.google.firebase.auth.EmailAuthProvider.getCredential(requireNotNull(user.email), current)).await()
        user.updatePassword(new).await()
    }
    fun profile(uid: String) = callbackFlow {
        cache.read<Profile>(uid, "profile", Profile::class.java)?.let { trySend(it) }
        val registration = db.document("users/$uid").addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
            if (error != null) close(error) else doc?.toObject(Profile::class.java)?.let {
                if (!doc.metadata.isFromCache && !doc.metadata.hasPendingWrites()) cache.write(uid, "profile", it)
                trySend(it)
            }
        }
        awaitClose { registration.remove() }
    }
    fun courts(admin: Boolean) = callbackFlow {
        val owner = uid; val key = "courts-$admin"
        cache.read<Array<Court>>(owner, key, Array<Court>::class.java)?.let { trySend(it.toList()) }
        val query = if (admin) db.collection("courts") else db.collection("courts").whereEqualTo("active", true)
        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) close(error) else if (snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) {
                val values = snapshot.toObjects(Court::class.java).sortedBy { it.name }
                cache.write(owner, key, values); trySend(values)
            }
        }
        awaitClose { registration.remove() }
    }
    fun bookings(uid: String, admin: Boolean) = callbackFlow {
        // No se conservan en SQLite las reservas de otros usuarios del panel administrativo.
        if (!admin) cache.read<Array<Booking>>(uid, "bookings", Array<Booking>::class.java)?.let { trySend(it.toList()) }
        val query: Query = if (admin) db.collection("bookings") else db.collection("bookings").whereEqualTo("userId", uid)
        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) close(error) else if (snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) {
                val values = snapshot.toObjects(Booking::class.java).sortedByDescending { it.createdAtMs }
                if (!admin) cache.write(uid, "bookings", values)
                trySend(values)
            }
        }
        awaitClose { registration.remove() }
    }
    private fun start(date: String, hour: Int) = LocalDate.parse(date).atTime(hour, 0).atZone(PERU).toInstant().toEpochMilli()
    private fun slot(court: String, date: String, hour: Long) = db.document("courts/$court/slots/${date}_$hour")
    private fun slotData(id: String, date: String, hour: Long, hold: Long, blocked: Boolean = false, reason: String = "") =
        mapOf("bookingId" to id, "date" to date, "hour" to hour, "holdUntilMs" to hold, "blocked" to blocked, "reason" to reason)
    suspend fun slots(courtId: String, date: String): List<Slot> {
        val court = requireNotNull(db.document("courts/$courtId").get(Source.SERVER).await().toObject(Court::class.java))
        val docs = db.collection("courts/$courtId/slots").whereEqualTo("date", date).get(Source.SERVER).await().documents.associateBy { it.getLong("hour") }
        val now = System.currentTimeMillis()
        return (court.openingHour until court.closingHour).map { hour ->
            val d = docs[hour]; val blocked = d?.getBoolean("blocked") == true
            Slot(hour.toInt(), court.active && start(date, hour.toInt()) > now && !blocked && (d?.getLong("holdUntilMs") ?: 0) <= now, blocked)
        }
    }
    suspend fun reserve(court: Court, date: String, hour: Int, requestId: String) {
        val book = db.document("bookings/$requestId"); val s = slot(court.id, date, hour.toLong())
        db.runTransaction { tx ->
            val existing = tx.get(book)
            if (existing.exists()) { require(existing.getString("userId") == uid) { "Solicitud inválida" }; return@runTransaction }
            val c = requireNotNull(tx.get(db.document("courts/${court.id}")).toObject(Court::class.java))
            val profile = requireNotNull(tx.get(db.document("users/$uid")).toObject(Profile::class.java))
            val occupied = tx.get(s); val now = System.currentTimeMillis(); val begin = start(date, hour)
            require(c.active && hour >= c.openingHour && hour < c.closingHour && begin > now && begin <= now + 61 * 86400000L) { "Fecha u horario inválido" }
            require(occupied.getBoolean("blocked") != true && (occupied.getLong("holdUntilMs") ?: 0) <= now) { "Este horario ya está ocupado. Actualiza la disponibilidad." }
            val hold = minOf(now + 900000, begin)
            tx.set(book, Booking(id=requestId, userId=uid, userName=profile.fullName, courtId=c.id, courtName=c.name,
                date=date, hour=hour.toLong(), totalCents=c.priceCents, status="Pendiente de pago", createdAtMs=now, updatedAtMs=now, startMs=begin, holdUntilMs=hold))
            tx.set(s, slotData(requestId, date, hour.toLong(), hold))
        }.await()
    }
    // Toda transición lee primero la reserva y el horario: Firestore reintenta ante conflictos.
    private suspend fun change(id: String, action: (Transaction, DocumentReference, Booking, DocumentReference, DocumentSnapshot) -> Unit) {
        val ref = db.document("bookings/$id")
        db.runTransaction { tx ->
            val b = requireNotNull(tx.get(ref).toObject(Booking::class.java)) { "Reserva inexistente" }
            val s = slot(b.courtId, b.date, b.hour); val current = tx.get(s)
            action(tx, ref, b, s, current)
        }.await()
    }
    private fun release(tx: Transaction, s: DocumentReference, current: DocumentSnapshot, b: Booking) {
        if (current.getString("bookingId") == b.id) tx.set(s, slotData("", b.date, b.hour, 0))
    }
    suspend fun cancel(id: String) = change(id) { tx, ref, b, s, current ->
        require(b.status != "Cancelada" && b.startMs > System.currentTimeMillis()) { "La reserva no se puede cancelar" }
        tx.update(ref, mapOf("status" to "Cancelada", "updatedAtMs" to System.currentTimeMillis(), "refundPending" to (b.paid && !b.refunded), "cancellationReason" to "Cancelación solicitada"))
        release(tx, s, current, b)
    }
    suspend fun block(courtId: String, date: String, hour: Int, blocked: Boolean, reason: String) {
        val ref = slot(courtId, date, hour.toLong())
        db.runTransaction { tx ->
            val old = tx.get(ref)
            require(old.getBoolean("blocked") == true || (old.getLong("holdUntilMs") ?: 0) <= System.currentTimeMillis()) { "Horario ocupado" }
            tx.set(ref, slotData("", date, hour.toLong(), if (blocked) 9007199254740991L else 0, blocked, reason.trim()))
        }.await()
    }
    suspend fun saveCourt(court: Court) {
        require(court.name.trim().length in 2..100 && court.priceCents in 100..1000000 && court.openingHour in 0..23 && court.closingHour in 1..24 && court.openingHour < court.closingHour) { "Revisa los datos de la cancha" }
        val id = court.id.ifBlank { UUID.randomUUID().toString() }
        val ref = db.document("courts/$id")
        db.runTransaction { tx -> tx.get(ref); tx.set(ref, court.copy(id=id)); Unit }.await()
    }
    suspend fun submitPayment(id: String, method: String, reference: String, image: String) {
        val normalized = reference.trim().uppercase(java.util.Locale.ROOT)
        require(method in listOf("Yape", "Plin", "Transferencia") && normalized.matches(Regex("[A-Z0-9-]{4,60}"))) { "Usa una referencia de 4 a 60 letras, números o guiones" }
        require(image.length in 8..270000) { "Adjunta un comprobante válido" }
        val key = "${method}_$normalized"
        change(id) { tx, ref, b, s, _ ->
            val paymentRef = db.document("paymentReferences/$key")
            val exists = tx.get(paymentRef).exists(); val now = System.currentTimeMillis()
            require(!exists) { "Esta referencia de pago ya fue utilizada" }
            require(b.userId == uid && b.canPay(now) && b.startMs > now) { "El plazo para enviar el comprobante terminó" }
            val hold = minOf(now + 86400000, b.startMs)
            tx.update(ref, mapOf("status" to "En revisión", "paymentMethod" to method, "paymentReference" to normalized, "paymentKey" to key, "holdUntilMs" to hold, "updatedAtMs" to now))
            tx.set(s, slotData(b.id, b.date, b.hour, hold))
            tx.set(db.document("paymentProofs/$id"), mapOf("userId" to uid, "imageBase64" to image, "mimeType" to "image/jpeg", "submittedAtMs" to now))
            tx.set(paymentRef, mapOf("userId" to uid, "bookingId" to id, "method" to method, "reference" to normalized))
        }
    }
    suspend fun review(id: String, approved: Boolean, note: String) = change(id) { tx, ref, b, s, current ->
        require(note.trim().length in 3..200) { "Escribe una observación de 3 a 200 caracteres" }
        require(b.visibleStatus() == "En revisión" && b.startMs > System.currentTimeMillis()) { "La reserva ya no admite revisión" }
        tx.update(ref, mapOf("status" to if (approved) "Confirmada" else "Cancelada", "paid" to approved, "reviewNote" to note.trim(), "reviewedBy" to uid, "updatedAtMs" to System.currentTimeMillis()))
        if (approved) tx.set(s, slotData(id, b.date, b.hour, 9007199254740991L)) else release(tx, s, current, b)
    }
    suspend fun refund(id: String) = change(id) { tx, ref, b, _, _ ->
        require(b.status == "Cancelada" && b.paid && !b.refunded && b.refundPending) { "No hay devolución pendiente" }
        tx.update(ref, mapOf("refunded" to true, "refundPending" to false, "refundedBy" to uid, "updatedAtMs" to System.currentTimeMillis()))
    }
    suspend fun proof(id: String): String = db.document("paymentProofs/$id").get(Source.SERVER).await().getString("imageBase64") ?: error("Sin comprobante")

    // Las imágenes académicas se limitan a 200 KB y se guardan en un documento privado independiente.
    suspend fun prepareProof(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { "Selecciona una imagen válida" }
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 1200) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri).use {
            requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }))
        }
        try {
            var quality = 80
            var bytes: ByteArray
            do {
                bytes = ByteArrayOutputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream); stream.toByteArray() }
                quality -= 15
            } while (bytes.size > 190000 && quality >= 20)
            require(bytes.size <= 190000) { "La imagen es demasiado grande. Usa otra captura." }
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally { bitmap.recycle() }
    }
}
