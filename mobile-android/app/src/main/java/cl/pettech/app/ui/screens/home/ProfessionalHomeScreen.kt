@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.pettech.app.domain.model.Role
import cl.pettech.app.ui.components.InfoBanner
import cl.pettech.app.ui.components.PetTechTopBar
import cl.pettech.app.ui.screens.AccountViewModel

/**
 * Panel profesional (incremento 1: solo identificación de la cuenta).
 * Búsqueda por código, solicitudes de acceso y registro clínico llegan en las fases 2 y 3.
 */
@Composable
fun ProfessionalHomeScreen(
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
) {
    val user by accountViewModel.user.collectAsStateWithLifecycle()
    val isAdmin = user?.role == Role.ADMIN

    Scaffold(
        topBar = {
            PetTechTopBar(
                title = if (isAdmin) "PetTech — Administración" else "PetTech Profesional",
                actions = {
                    IconButton(onClick = accountViewModel::logout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar sesión")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Hola, ${user?.fullName ?: ""}", style = MaterialTheme.typography.headlineMedium)
            if (isAdmin) {
                InfoBanner(
                    "Las cuentas de administración gestionan PetTech desde el panel web (Django Admin). " +
                        "La aplicación móvil está destinada a tutores y profesionales.",
                )
            } else {
                InfoBanner("Tu cuenta profesional está activa.")
                Text(
                    "En el próximo incremento podrás buscar mascotas por su código PetTech, solicitar " +
                        "autorización al tutor y registrar atenciones y vacunas. Conocer un código no da " +
                        "acceso al historial: siempre se requerirá la autorización vigente del tutor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
