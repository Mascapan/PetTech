package cl.pettech.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.AuthRepository
import cl.pettech.app.data.session.SessionManager
import cl.pettech.app.data.session.SessionState
import cl.pettech.app.domain.model.User
import cl.pettech.app.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Datos de la cuenta en sesión y cierre de sesión (compartido por los paneles de cada rol). */
class AccountViewModel(
    private val auth: AuthRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    val user: StateFlow<User?> = sessionManager.state
        .map { (it as? SessionState.LoggedIn)?.user }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            (sessionManager.state.value as? SessionState.LoggedIn)?.user,
        )

    private val _loggingOut = MutableStateFlow(false)
    val loggingOut: StateFlow<Boolean> = _loggingOut.asStateFlow()

    fun logout() {
        if (_loggingOut.value) return
        _loggingOut.value = true
        viewModelScope.launch {
            auth.logout()
            _loggingOut.value = false
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                AccountViewModel(container.authRepository, container.sessionManager)
            }
        }
    }
}
