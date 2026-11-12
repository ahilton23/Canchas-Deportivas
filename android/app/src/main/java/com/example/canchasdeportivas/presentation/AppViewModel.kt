package com.example.canchasdeportivas.presentation

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.canchasdeportivas.CanchasApp
import com.example.canchasdeportivas.domain.model.*
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import java.util.UUID

enum class Screen { COURTS, BOOKINGS, PROFILE, ADMIN, DETAIL, COURT_FORM }
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as CanchasApp).repository
    var profile by mutableStateOf<Profile?>(null); private set
    var courts by mutableStateOf<List<Court>>(emptyList()); private set
    var bookings by mutableStateOf<List<Booking>>(emptyList()); private set
    var slots by mutableStateOf<List<Slot>>(emptyList()); private set
    var screen by mutableStateOf(Screen.COURTS); private set
    var selectedCourt by mutableStateOf<Court?>(null); private set
    var editingCourt by mutableStateOf<Court?>(null); private set
    var selectedDate by mutableStateOf(today().toString()); private set
    var slotsLoading by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var sessionLoading by mutableStateOf(true); private set
    var sessionFailed by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    var isError by mutableStateOf(false); private set
    private var sessionJob: Job? = null
    private var slotsJob: Job? = null
    private val streams = mutableListOf<Job>()
    private var pendingName: String? = null
    private var requestId = UUID.randomUUID().toString()
    private var requestHour: Int? = null
    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser == null) clearSession() else openSession()
    }
    init { repo.auth.addAuthStateListener(authListener) }

    private fun clearSession() {
        sessionJob?.cancel(); slotsJob?.cancel(); streams.forEach { it.cancel() }; streams.clear()
        profile = null; courts = emptyList(); bookings = emptyList(); slots = emptyList()
        selectedCourt = null; editingCourt = null; screen = Screen.COURTS
        sessionLoading = false; sessionFailed = false
    }
    fun openSession() {
        val user = repo.auth.currentUser ?: return
        sessionJob?.cancel(); streams.forEach { it.cancel() }; streams.clear()
        sessionLoading = true; sessionFailed = false
        sessionJob = viewModelScope.launch {
            try {
                repo.ensureProfile(pendingName ?: user.displayName ?: "Deportista")
                repo.profile(user.uid).collect { next ->
                    val changedRole = profile?.role != next.role
                    profile = next; sessionLoading = false; sessionFailed = false; pendingName = null
                    if (changedRole || streams.isEmpty()) startStreams(next)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { sessionLoading = false; sessionFailed = true; report(errorText(e), true) }
        }
    }
    private fun startStreams(user: Profile) {
        streams.forEach { it.cancel() }; streams.clear()
        streams += viewModelScope.launch { repo.courts(user.isAdmin).catch { report(errorText(it), true) }.collect { courts = it } }
        streams += viewModelScope.launch { repo.bookings(user.uid, user.isAdmin).catch { report(errorText(it), true) }.collect { bookings = it } }
    }
    private fun work(action: suspend () -> Unit) {
        if (busy) return
        busy = true; message = ""; isError = false
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(errorText(e), true) }
            finally { busy = false }
        }
    }
    fun report(value: String, error: Boolean = false) { message = value; isError = error }
    fun navigate(target: Screen) {
        if (busy) return
        if (target in listOf(Screen.ADMIN, Screen.COURT_FORM) && profile?.isAdmin != true) return
        screen = target; message = ""; isError = false
    }
    fun back() { if (!busy) { screen = Screen.COURTS; selectedCourt = null; slotsJob?.cancel(); slots = emptyList() } }
    fun login(email: String, password: String) = work {
        require(email.isNotBlank() && password.isNotBlank()) { "Completa el correo y la contraseña" }
        repo.login(email, password)
    }
    fun register(name: String, email: String, password: String, confirm: String) = work {
        require(name.trim().length in 2..80) { "Ingresa tu nombre completo" }
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Correo inválido" }
        require(password.length >= 8 && password.any { it.isLetter() } && password.any { it.isDigit() }) { "Usa 8 caracteres o más, con letras y números" }
        require(password == confirm) { "Las contraseñas no coinciden" }
        pendingName = name.trim(); repo.register(name, email, password)
    }
    fun recover(email: String, done: () -> Unit) = work {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Ingresa un correo válido" }
        repo.recover(email); done(); report("Si la cuenta existe, recibirás instrucciones para restablecer la contraseña.")
    }
    fun logout() { if (!busy) { repo.clearCache(); repo.auth.signOut(); clearSession(); message = "" } }
    fun saveProfile(name: String, phone: String) = work { repo.saveProfile(name, phone); report("Perfil guardado") }
    fun password(current: String, new: String, confirm: String, done: () -> Unit) = work {
        require(new.length >= 8 && new.any { it.isLetter() } && new.any { it.isDigit() }) { "Usa 8 caracteres o más, con letras y números" }
        require(new == confirm) { "Las contraseñas no coinciden" }
        repo.changePassword(current, new); done(); report("Contraseña actualizada")
    }
    fun openCourt(court: Court) {
        selectedCourt = court; selectedDate = today().toString(); screen = Screen.DETAIL
        requestId = UUID.randomUUID().toString(); requestHour = null; refreshSlots()
    }
    fun date(value: String) {
        if (busy) return
        selectedDate = value; requestId = UUID.randomUUID().toString(); requestHour = null; refreshSlots()
    }
    fun refreshSlots() {
        val court = selectedCourt ?: return
        slotsJob?.cancel(); slots = emptyList(); slotsLoading = true
        val date = selectedDate
        slotsJob = viewModelScope.launch {
            try { slots = repo.slots(court.id, date) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(errorText(e), true) }
            finally { slotsLoading = false }
        }
    }
    fun reserve(hour: Int) = work {
        if (requestHour != hour) { requestId = UUID.randomUUID().toString(); requestHour = hour }
        repo.reserve(requireNotNull(selectedCourt), selectedDate, hour, requestId)
        requestId = UUID.randomUUID().toString(); requestHour = null
        slotsJob?.cancel(); screen = Screen.BOOKINGS
        report("Reserva registrada: tienes 15 minutos para enviar el comprobante. Todavía no está confirmada.")
    }
    fun cancel(id: String, done: () -> Unit) = work { repo.cancel(id); done(); report("Reserva cancelada. Si pagaste, el administrador gestionará la devolución.") }
    fun block(hour: Int, blocked: Boolean, reason: String) = work {
        repo.block(requireNotNull(selectedCourt).id, selectedDate, hour, blocked, reason)
        refreshSlots(); report(if (blocked) "Horario bloqueado" else "Horario habilitado")
    }
    fun editCourt(court: Court?) { if (!busy && profile?.isAdmin == true) { editingCourt = court; screen = Screen.COURT_FORM } }
    fun saveCourt(court: Court) = work { repo.saveCourt(court); screen = Screen.ADMIN; report("Cancha guardada") }
    fun payment(id: String, method: String, reference: String, image: String, done: () -> Unit) = work {
        repo.submitPayment(id, method, reference, image); done(); report("Comprobante enviado. La confirmación requiere revisión manual.")
    }
    suspend fun prepareProof(uri: Uri) = repo.prepareProof(getApplication(), uri)
    fun review(id: String, approved: Boolean, note: String, done: () -> Unit) = work {
        repo.review(id, approved, note); done(); report(if (approved) "Pago aprobado y reserva confirmada" else "Pago rechazado y horario liberado")
    }
    fun refund(id: String) = work { repo.refund(id); report("Devolución registrada como realizada") }
    suspend fun proof(id: String) = repo.proof(id)
    suspend fun paymentSettings() = repo.paymentSettings()
    fun savePaymentSettings(settings: PaymentSettings, done: () -> Unit) = work {
        repo.savePaymentSettings(settings); done(); report("Medios de cobro guardados")
    }

    private fun errorText(error: Throwable): String = when (error) {
        is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE -> "No se pudo conectar con Firebase. Comprueba la conexión y los emuladores."
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> "La sesión no es válida. Vuelve a ingresar."
            FirebaseFirestoreException.Code.INTERNAL -> "No se pudo completar la operación. Intenta nuevamente."
            else -> error.message ?: "Operación rechazada por el servidor"
        }
        is FirebaseNetworkException -> "Sin conexión. Revisa Internet o inicia los emuladores de Firebase."
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "El correo ya está registrado"
            "ERROR_INVALID_EMAIL" -> "Correo inválido"
            "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Espera y vuelve a probar."
            else -> "No se pudo verificar la cuenta o contraseña"
        }
        is IllegalArgumentException -> error.message ?: "Revisa los datos ingresados"
        else -> "No se pudo completar la operación. Comprueba la configuración de Firebase."
    }
    override fun onCleared() { repo.auth.removeAuthStateListener(authListener); streams.forEach { it.cancel() }; slotsJob?.cancel(); sessionJob?.cancel() }
}
