package cl.pettech.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.AuthRepository
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.validation.Validators
import cl.pettech.app.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val acceptPolicy: Boolean = false,
    val fullNameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmationError: String? = null,
    val policyError: String? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

class RegisterViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _ui = MutableStateFlow(RegisterUiState())
    val ui: StateFlow<RegisterUiState> = _ui.asStateFlow()

    fun onFullNameChange(value: String) = _ui.update { it.copy(fullName = value, fullNameError = null, errorMessage = null) }
    fun onEmailChange(value: String) = _ui.update { it.copy(email = value, emailError = null, errorMessage = null) }
    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    fun onConfirmationChange(value: String) = _ui.update { it.copy(confirmation = value, confirmationError = null) }
    fun onAcceptPolicyChange(value: Boolean) = _ui.update { it.copy(acceptPolicy = value, policyError = null) }

    fun submit() {
        val s = _ui.value
        if (s.loading) return
        val errors = s.copy(
            fullNameError = Validators.fullName(s.fullName),
            emailError = Validators.email(s.email),
            passwordError = Validators.newPassword(s.password),
            confirmationError = Validators.passwordConfirmation(s.password, s.confirmation),
            policyError = if (s.acceptPolicy) null else "Debes aceptar la política de privacidad.",
        )
        if (listOf(errors.fullNameError, errors.emailError, errors.passwordError, errors.confirmationError, errors.policyError)
                .any { it != null }
        ) {
            _ui.value = errors
            return
        }
        _ui.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = auth.register(s.fullName, s.email, s.password, s.acceptPolicy)) {
                is AppResult.Success -> _ui.update { it.copy(loading = false, password = "", confirmation = "") }
                is AppResult.Failure -> {
                    val f = result.error.fieldErrors
                    _ui.update {
                        it.copy(
                            loading = false,
                            errorMessage = result.error.message,
                            fullNameError = f["full_name"],
                            emailError = f["email"],
                            passwordError = f["password"],
                            policyError = f["accept_privacy_policy"],
                        )
                    }
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { RegisterViewModel(appContainer().authRepository) }
        }
    }
}
