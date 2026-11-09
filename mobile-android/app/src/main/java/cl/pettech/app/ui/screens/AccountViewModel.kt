package cl.pettech.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.AuthRepository
import cl.pettech.app.data.session.SessionManager
import cl.pettech.app.data.session.SessionState
import cl.pettech.app.domain.model.AppResult
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
    private val appContext: Context,
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

    private val _deletionMessage = MutableStateFlow<String?>(null)
    val deletionMessage: StateFlow<String?> = _deletionMessage.asStateFlow()

    fun requestDeletion() {
        viewModelScope.launch {
            when (val r = auth.requestAccountDeletion()) {
                is AppResult.Success -> {
                    // Toast con el contexto de la aplicación: sigue visible al volver a la pantalla de ingreso.
                    Toast.makeText(appContext, r.data, Toast.LENGTH_LONG).show()
                    auth.endLocalSession()
                }
                is AppResult.Failure -> {
                    _deletionMessage.value = r.error.message
                    Toast.makeText(appContext, r.error.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                AccountViewModel(container.authRepository, container.sessionManager, app.applicationContext)
            }
        }
    }
}
