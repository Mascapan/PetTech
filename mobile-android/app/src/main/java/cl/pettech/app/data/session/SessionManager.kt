package cl.pettech.app.data.session

import cl.pettech.app.domain.model.Role
import cl.pettech.app.domain.model.User
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LogoutReason { NONE, USER_REQUEST, EXPIRED }

sealed interface SessionState {
    data object Loading : SessionState
    data class LoggedOut(val reason: LogoutReason = LogoutReason.NONE) : SessionState
    data class LoggedIn(val user: User) : SessionState
}

/**
 * Fuente única de verdad sobre la sesión en la app.
 * Restaura la sesión cifrada al iniciar y la descarta si expiró o si el servidor responde 401.
 */
class SessionManager(
    private val store: SessionStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    private var token: String? = null

    init {
        scope.launch { restore() }
    }

    fun currentToken(): String? = token

    private suspend fun restore() {
        val stored = store.read()
        if (stored != null && !isExpired(stored.expiry)) {
            token = stored.token
            _state.value = SessionState.LoggedIn(stored.toUser())
        } else {
            if (stored != null) store.clear()
            _state.value = SessionState.LoggedOut()
        }
    }

    suspend fun start(token: String, expiry: String?, user: User) {
        store.write(
            StoredSession(
                token = token,
                expiry = expiry,
                userId = user.id,
                email = user.email,
                fullName = user.fullName,
                role = user.role.name,
            ),
        )
        this.token = token
        _state.value = SessionState.LoggedIn(user)
    }

    suspend fun end(reason: LogoutReason) {
        token = null
        store.clear()
        _state.value = SessionState.LoggedOut(reason)
    }

    /** Llamado por el interceptor HTTP cuando el servidor rechaza el token (401). */
    fun onUnauthorized() {
        if (token == null) return
        token = null
        scope.launch { end(LogoutReason.EXPIRED) }
    }

    private fun StoredSession.toUser() = User(
        id = userId,
        email = email,
        fullName = fullName,
        role = Role.fromApi(role),
    )

    companion object {
        fun isExpired(expiry: String?, now: OffsetDateTime = OffsetDateTime.now()): Boolean {
            if (expiry == null) return false
            return runCatching { OffsetDateTime.parse(expiry).isBefore(now) }.getOrDefault(true)
        }
    }
}
