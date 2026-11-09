package cl.pettech.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

/**
 * Vista 1 del boceto: "Inicio de sesión".
 * Usuario (correo) · Contraseña · ¿Olvidaste tu contraseña? · Registrarse · Continuar · Inicio con Google.
 */
@Composable
fun LoginScreen(
    onGoToRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    infoMessage: String? = null,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var googleNotice by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PetTechLogo(size = 88.dp)
                Text(
                    "PetTech",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "El carnet clínico de tu mascota, en un solo lugar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Inicio de sesión", style = MaterialTheme.typography.titleLarge)
                        infoMessage?.let { InfoBanner(it) }
                        if (ui.sessionExpired) InfoBanner("Tu sesión expiró o fue cerrada. Ingresa nuevamente.")
                        ui.errorMessage?.let { ErrorBanner(it) }

                        FormTextField(
                            value = ui.email,
                            onValueChange = viewModel::onEmailChange,
                            label = "Usuario (correo electrónico)",
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
                        Row(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onForgotPassword) { Text("¿Olvidaste tu contraseña?") }
                        }
                        LoadingButton(text = "Continuar", loading = ui.loading, onClick = viewModel::submit)

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text(
                                "  o  ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider(Modifier.weight(1f))
                        }
                        OutlinedButton(
                            onClick = { googleNotice = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Text("G", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.size(12.dp))
                            Text("Continuar con Google")
                        }
                        if (googleNotice) {
                            InfoBanner(
                                "El inicio con Google se habilitará en una próxima versión. " +
                                    "Por ahora ingresa con tu correo y contraseña.",
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("¿No tienes cuenta?", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onGoToRegister) { Text("Registrarse") }
                }
                Text(
                    "¿Eres veterinario? Tu cuenta profesional la habilita PetTech junto a tu establecimiento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
