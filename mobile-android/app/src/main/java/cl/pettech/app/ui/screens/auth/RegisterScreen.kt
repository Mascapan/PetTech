@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.FormTextField
import cl.pettech.app.ui.components.LabeledCheckbox
import cl.pettech.app.ui.components.LoadingButton
import cl.pettech.app.ui.components.PasswordField
import cl.pettech.app.ui.components.PetTechTopBar

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var showPolicy by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { PetTechTopBar(title = "Crear cuenta de tutor", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Registra tus datos para guardar el carnet y los antecedentes de tus mascotas.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ui.errorMessage?.let { ErrorBanner(it) }
            FormTextField(
                value = ui.fullName,
                onValueChange = viewModel::onFullNameChange,
                label = "Nombre completo",
                error = ui.fullNameError,
            )
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
                supportingText = "Mínimo 10 caracteres; evita contraseñas comunes.",
                imeAction = ImeAction.Next,
            )
            PasswordField(
                value = ui.confirmation,
                onValueChange = viewModel::onConfirmationChange,
                label = "Repite la contraseña",
                error = ui.confirmationError,
            )
            LabeledCheckbox(
                checked = ui.acceptPolicy,
                onCheckedChange = viewModel::onAcceptPolicyChange,
                label = "Acepto la política de privacidad de PetTech",
            )
            ui.policyError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { showPolicy = true }) { Text("Leer la política de privacidad") }
            LoadingButton(text = "Crear cuenta", loading = ui.loading, onClick = viewModel::submit)
        }
    }

    if (showPolicy) {
        AlertDialog(
            onDismissRequest = { showPolicy = false },
            confirmButton = { TextButton(onClick = { showPolicy = false }) { Text("Entendido") } },
            title = { Text("Política de privacidad (borrador)") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(PRIVACY_POLICY_DRAFT, style = MaterialTheme.typography.bodyMedium)
                }
            },
        )
    }
}

/**
 * Texto BORRADOR para el prototipo académico. Debe revisarse antes del piloto y no
 * constituye asesoría legal. Se alinea con los principios de la Ley N.º 21.719.
 */
private const val PRIVACY_POLICY_DRAFT =
    "Versión borrador del prototipo académico PetTech (noviembre de 2026).\n\n" +
        "• Datos que recopilamos: tu nombre, tu correo y los datos que declares sobre tus mascotas.\n" +
        "• Finalidad: mantener el carnet y los antecedentes de tus mascotas y, en próximas versiones, " +
        "compartirlos con veterinarias SOLO cuando tú lo autorices.\n" +
        "• Tu ubicación no se almacena asociada a tu cuenta.\n" +
        "• Puedes solicitar acceso, rectificación, supresión y portabilidad de tus datos.\n" +
        "• Los datos de esta etapa de pruebas son ficticios o de participantes que han dado su consentimiento."
