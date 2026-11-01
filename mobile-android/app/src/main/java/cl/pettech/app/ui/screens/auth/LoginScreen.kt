@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.FormTextField
import cl.pettech.app.ui.components.InfoBanner
import cl.pettech.app.ui.components.LoadingButton
import cl.pettech.app.ui.components.PasswordField
import cl.pettech.app.ui.components.PetTechLogo
import cl.pettech.app.ui.components.PetTechTopBar

@Composable
fun LoginScreen(
    onGoToRegister: () -> Unit,
    onBack: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(topBar = { PetTechTopBar(title = "Iniciar sesión", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PetTechLogo(size = 72.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (ui.sessionExpired) {
                InfoBanner("Tu sesión expiró o fue cerrada. Ingresa nuevamente.")
            }
            ui.errorMessage?.let { ErrorBanner(it) }
            FormTextField(
                value = ui.email,
                onValueChange = viewModel::onEmailChange,
                label = "Correo electrónico",
                error = ui.emailError,
                keyboardType = KeyboardType.Email,
            )
            PasswordField(
                value = ui.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Contraseña",
                error = ui.passwordError,
                imeAction = ImeAction.Done,
            )
            LoadingButton(text = "Ingresar", loading = ui.loading, onClick = viewModel::submit)
            TextButton(onClick = onGoToRegister, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("¿No tienes cuenta? Crea una cuenta de tutor")
            }
            Text(
                "Las cuentas de profesionales veterinarios son creadas y habilitadas por PetTech.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
