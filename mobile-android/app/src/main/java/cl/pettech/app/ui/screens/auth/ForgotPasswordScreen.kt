package cl.pettech.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.AuthRepository
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.validation.Validators
import cl.pettech.app.ui.appContainer
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.FormTextField
import cl.pettech.app.ui.components.InfoBanner
import cl.pettech.app.ui.components.LoadingButton
import cl.pettech.app.ui.components.PasswordField
import cl.pettech.app.ui.components.PetTechTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val step: Int = 1,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val confirmation: String = "",
    val emailError: String? = null,
    val codeError: String? = null,
    val passwordError: String? = null,
    val confirmationError: String? = null,
    val info: String? = null,
    val errorMessage: String? = null,
    val loading: Boolean = false,
    val done: Boolean = false,
)

/** Recuperación de acceso por correo (HU-01): 1) pedir código · 2) ingresar código y nueva contraseña. */
class ForgotPasswordViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(ForgotPasswordUiState())
    val ui: StateFlow<ForgotPasswordUiState> = _ui.asStateFlow()

    fun onEmail(v: String) = _ui.update { it.copy(email = v, emailError = null, errorMessage = null) }
    fun onCode(v: String) = _ui.update { it.copy(code = v.filter(Char::isDigit).take(6), codeError = null, errorMessage = null) }
    fun onPassword(v: String) = _ui.update { it.copy(password = v, passwordError = null) }
    fun onConfirmation(v: String) = _ui.update { it.copy(confirmation = v, confirmationError = null) }
    fun backToEmail() = _ui.update { it.copy(step = 1, info = null, errorMessage = null) }

    fun sendCode() {
        val s = _ui.value
        val error = Validators.email(s.email)
        if (error != null) {
            _ui.update { it.copy(emailError = error) }
            return
        }
        _ui.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = auth.requestPasswordReset(s.email)) {
                is AppResult.Success -> _ui.update { it.copy(loading = false, step = 2, info = r.data) }
                is AppResult.Failure -> _ui.update { it.copy(loading = false, errorMessage = r.error.message) }
            }
        }
    }

    fun confirm() {
        val s = _ui.value
        val codeError = if (s.code.length != 6) "El código tiene 6 dígitos." else null
        val passwordError = Validators.newPassword(s.password)
        val confirmationError = Validators.passwordConfirmation(s.password, s.confirmation)
        if (codeError != null || passwordError != null || confirmationError != null) {
            _ui.update { it.copy(codeError = codeError, passwordError = passwordError, confirmationError = confirmationError) }
            return
        }
        _ui.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = auth.confirmPasswordReset(s.email, s.code, s.password)) {
                is AppResult.Success -> _ui.update { it.copy(loading = false, done = true) }
                is AppResult.Failure -> _ui.update {
                    it.copy(
                        loading = false,
                        errorMessage = r.error.message,
                        codeError = r.error.fieldErrors["code"],
                        passwordError = r.error.fieldErrors["new_password"],
                    )
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { ForgotPasswordViewModel(appContainer().authRepository) } }
    }
}

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel(factory = ForgotPasswordViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ui.done) { if (ui.done) onDone() }

    Scaffold(topBar = { PetTechTopBar(title = "Recuperar contraseña", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ui.errorMessage?.let { ErrorBanner(it) }
            if (ui.step == 1) {
                Text(
                    "Ingresa el correo de tu cuenta. Te enviaremos un código de 6 dígitos para crear una nueva contraseña.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FormTextField(
                    value = ui.email,
                    onValueChange = viewModel::onEmail,
                    label = "Correo electrónico",
                    error = ui.emailError,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                )
                LoadingButton("Enviar código", ui.loading, viewModel::sendCode)
            } else {
                ui.info?.let { InfoBanner(it) }
                FormTextField(
                    value = ui.code,
                    onValueChange = viewModel::onCode,
                    label = "Código de 6 dígitos",
                    error = ui.codeError,
                    keyboardType = KeyboardType.NumberPassword,
                )
                PasswordField(
                    value = ui.password,
                    onValueChange = viewModel::onPassword,
                    label = "Nueva contraseña",
                    error = ui.passwordError,
                    supportingText = "Mínimo 10 caracteres; no solo números.",
                    imeAction = ImeAction.Next,
                )
                PasswordField(
                    value = ui.confirmation,
                    onValueChange = viewModel::onConfirmation,
                    label = "Repite la nueva contraseña",
                    error = ui.confirmationError,
                )
                LoadingButton("Guardar nueva contraseña", ui.loading, viewModel::confirm)
                TextButton(onClick = viewModel::backToEmail) { Text("Usar otro correo o reenviar el código") }
            }
        }
    }
}
