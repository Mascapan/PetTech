package cl.pettech.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.AuthRepository
import cl.pettech.app.data.session.LogoutReason
import cl.pettech.app.data.session.SessionManager
import cl.pettech.app.data.session.SessionState
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.validation.Validators
import cl.pettech.app.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
)

class LoginViewModel(
    private val auth: AuthRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        LoginUiState(
            sessionExpired = (sessionManager.state.value as? SessionState.LoggedOut)?.reason == LogoutReason.EXPIRED,
        ),
    )
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    fun onEmailChange(value: String) {
        _ui.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _ui.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun submit() {
        val current = _ui.value
        if (current.loading) return
        val emailError = Validators.email(current.email)
        val passwordError = Validators.loginPassword(current.password)
        if (emailError != null || passwordError != null) {
            _ui.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _ui.update { it.copy(loading = true, errorMessage = null, sessionExpired = false) }
        viewModelScope.launch {
            when (val result = auth.login(current.email, current.password)) {
                // La navegación al panel ocurre al cambiar el estado de sesión.
                is AppResult.Success -> _ui.update { it.copy(loading = false, password = "") }
                is AppResult.Failure -> _ui.update {
                    it.copy(
                        loading = false,
                        errorMessage = result.error.message,
                        emailError = result.error.fieldErrors["email"],
                        passwordError = result.error.fieldErrors["password"],
                    )
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                LoginViewModel(container.authRepository, container.sessionManager)
            }
        }
    }
}
