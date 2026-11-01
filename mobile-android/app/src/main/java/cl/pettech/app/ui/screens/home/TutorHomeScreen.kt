@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.PetTechTopBar
import cl.pettech.app.ui.screens.AccountViewModel

@Composable
fun TutorHomeScreen(
    onOpenPets: () -> Unit,
    onAddPet: () -> Unit,
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
    viewModel: TutorHomeViewModel = viewModel(factory = TutorHomeViewModel.Factory),
) {
    val user by accountViewModel.user.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            PetTechTopBar(
                title = "PetTech",
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Hola, ${user?.firstName ?: "tutor"}",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Gestiona el carnet digital de tus mascotas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ui.errorMessage?.let { ErrorBanner(it) }

            val countText = when {
                ui.petCount == null && ui.loading -> "Cargando…"
                ui.petCount == 0 -> "Aún no registras mascotas"
                ui.petCount == 1 -> "1 mascota registrada"
                ui.petCount != null -> "${ui.petCount} mascotas registradas"
                else -> "Toca para ver tus mascotas"
            }
            DashboardCard(
                icon = Icons.Filled.Favorite,
                title = "Mis mascotas",
                subtitle = countText,
                onClick = onOpenPets,
            )
            DashboardCard(
                icon = Icons.Filled.Add,
                title = "Agregar mascota",
                subtitle = "Registra una mascota y obtén su código PetTech",
                onClick = onAddPet,
            )

            Text(
                "Próximos incrementos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            DashboardCard(
                icon = Icons.Filled.Lock,
                title = "Solicitudes y autorizaciones",
                subtitle = "Aprobarás o rechazarás el acceso de cada veterinaria (en desarrollo)",
                enabled = false,
            )
            DashboardCard(
                icon = Icons.Filled.DateRange,
                title = "Historial clínico y vacunas",
                subtitle = "Atenciones registradas por veterinarias autorizadas (en desarrollo)",
                enabled = false,
            )
            DashboardCard(
                icon = Icons.Filled.LocationOn,
                title = "Veterinarias cercanas",
                subtitle = "Horarios, servicios y urgencias declaradas (planificado)",
                enabled = false,
            )
        }
    }
}

@Composable
private fun DashboardCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 2.dp else 0.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
