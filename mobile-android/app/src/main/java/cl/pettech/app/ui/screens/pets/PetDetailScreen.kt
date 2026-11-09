@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.pets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.pettech.app.domain.model.Pet
import cl.pettech.app.domain.validation.Validators
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.FullScreenLoading
import cl.pettech.app.ui.components.InfoRow
import cl.pettech.app.ui.components.PetAvatar
import cl.pettech.app.ui.components.PetTechTopBar

/**
 * Vista 3 del boceto: al presionar la ficha de la mascota se muestra su información general
 * (foto, nombre, fecha, raza...) y un botón "Ficha clínica". Incluye el código PetTech que
 * el tutor entrega a la veterinaria para que ésta solicite acceso.
 */
@Composable
fun PetDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenClinicalRecord: (String) -> Unit,
    onOpenAccess: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: PetDetailViewModel = viewModel(factory = PetDetailViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.savePhoto(uri)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }
    LaunchedEffect(ui.deleted) { if (ui.deleted) onDeleted() }

    Scaffold(
        topBar = {
            PetTechTopBar(
                title = "Ficha de la mascota",
                onBack = onBack,
                actions = {
                    if (ui.pet != null) {
                        IconButton(onClick = { onEdit(viewModel.petId) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar datos de la mascota")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (ui.pet != null) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = { onOpenClinicalRecord(viewModel.petId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .heightIn(min = 56.dp),
                    ) { Text("Ficha clínica", style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
    ) { padding ->
        val pet = ui.pet
        when {
            ui.loading && pet == null -> Column(Modifier.padding(padding)) { FullScreenLoading() }
            pet == null -> Column(
                Modifier.padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorBanner(ui.errorMessage ?: "No se pudo cargar la mascota.")
                Button(onClick = viewModel::load) { Text("Reintentar") }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ui.errorMessage?.let { ErrorBanner(it) }
                ui.photoMessage?.let { ErrorBanner(it) }

                GeneralInfoCard(
                    pet = pet,
                    onChangePhoto = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                PetCodeCard(pet.publicCode)

                OutlinedButton(
                    onClick = { onOpenAccess(viewModel.petId) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (ui.activeGrants) {
                            0 -> "Veterinarias con acceso: ninguna"
                            1 -> "Veterinarias con acceso: 1 vigente"
                            else -> "Veterinarias con acceso: ${ui.activeGrants} vigentes"
                        },
                    )
                }

                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !ui.deleting,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (ui.deleting) "Eliminando…" else "Eliminar mascota")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar mascota?") },
            text = {
                Text(
                    "Se eliminará el perfil de ${ui.pet?.name ?: "la mascota"} y sus antecedentes declarados. " +
                        "Los registros hechos por veterinarias no se eliminan desde la aplicación.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun GeneralInfoCard(pet: Pet, onChangePhoto: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PetAvatar(petId = pet.id, name = pet.name, size = 112.dp)
                    TextButton(onClick = onChangePhoto) { Text("Cambiar foto") }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(pet.name, style = MaterialTheme.typography.headlineSmall)
                    InfoRow(
                        "Fecha de nacimiento",
                        pet.birthDate?.let {
                            Validators.formatDate(it) + if (pet.birthDateIsEstimated) " (aprox.)" else ""
                        } ?: "No informada",
                    )
                    InfoRow("Raza", pet.breed.ifBlank { "No informada" })
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Información general", style = MaterialTheme.typography.titleMedium)
            InfoRow("Especie", pet.speciesLabel)
            InfoRow("Sexo", pet.sexLabel)
            InfoRow("Microchip (declarado)", pet.microchipNumber.ifBlank { "No informado" })
            if (pet.generalNotes.isNotBlank()) InfoRow("Antecedentes generales", pet.generalNotes)
            Text(
                "Datos declarados por el tutor. La foto se guarda solo en este teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PetCodeCard(code: String) {
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Código PetTech", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Código PetTech ${code.toList().joinToString(" ")}" },
                )
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Copiar") }
            }
            Text(
                "Entrégalo a tu veterinaria: con él solicitará acceso a la ficha clínica. " +
                    "El código no da acceso por sí solo; tú autorizas cada solicitud.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
