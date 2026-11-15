@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.pettech.app.domain.model.Pet
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.FullScreenLoading
import cl.pettech.app.ui.components.PetAvatar
import cl.pettech.app.ui.components.PetTechTopBar
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import cl.pettech.app.data.reminders.ReminderWorker
import cl.pettech.app.ui.screens.AccountViewModel

/**
 * Vista 2 del boceto: al ingresar, resumen "Mis mascotas" con las fichas de cada mascota
 * y el botón inferior "Buscar veterinaria".
 */
@Composable
fun TutorHomeScreen(
    onOpenPet: (String) -> Unit,
    onAddPet: () -> Unit,
    onSearchClinics: () -> Unit,
    onOpenAccess: () -> Unit,
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
    viewModel: TutorHomeViewModel = viewModel(factory = TutorHomeViewModel.Factory),
) {
    val user by accountViewModel.user.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var showAccount by rememberSaveable { mutableStateOf(false) }

    if (showAccount) {
        AccountDialog(
            user = user,
            onDismiss = { showAccount = false },
            onLogout = {
                showAccount = false
                accountViewModel.logout()
            },
            onRequestDeletion = {
                showAccount = false
                accountViewModel.requestDeletion()
            },
        )
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // Recordatorios de vacunas (HU-06): en Android 13+ se pide permiso una vez; luego se revisan las fechas.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ReminderWorker.scheduleOnce(context)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ReminderWorker.scheduleOnce(context)
        }
    }

    Scaffold(
        topBar = {
            PetTechTopBar(
                title = "Mis mascotas",
                actions = {
                    IconButton(onClick = onOpenAccess) {
                        BadgedBox(badge = { if (ui.pendingRequests > 0) Badge { Text("${ui.pendingRequests}") } }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Solicitudes de veterinarias")
                        }
                    }
                    IconButton(onClick = { showAccount = true }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Mi cuenta")
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = onSearchClinics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buscar veterinaria", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading && !ui.loaded -> FullScreenLoading("Cargando tus mascotas…")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Hola, ${user?.firstName ?: "tutor"}", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Toca una ficha para ver los datos y la ficha clínica de tu mascota.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ui.errorMessage?.let { message ->
                        item(span = { GridItemSpan(maxLineSpan) }) { ErrorBanner(message) }
                    }
                    if (ui.pendingRequests > 0) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            PendingRequestsCard(ui.pendingRequests, onOpenAccess)
                        }
                    }
                    items(ui.pets, key = { it.id }) { pet -> PetTile(pet) { onOpenPet(pet.id) } }
                    item { AddPetTile(onAddPet) }
                    if (ui.loaded && ui.pets.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "Aún no registras mascotas. Agrega la primera para generar su carnet y su código PetTech.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PetTile(pet: Pet, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PetAvatar(
                petId = pet.id,
                name = pet.name,
                size = 120.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Text(
                pet.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                listOf(pet.speciesLabel, pet.breed).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AddPetTile(onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
            Text("Agregar mascota", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(" ", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PendingRequestsCard(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Notifications, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 1) "1 veterinaria solicita acceso" else "$count solicitudes de acceso",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text("Revisa y decide si autorizas ver la ficha clínica.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
