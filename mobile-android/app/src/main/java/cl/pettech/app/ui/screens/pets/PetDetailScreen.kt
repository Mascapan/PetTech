@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import cl.pettech.app.ui.components.InfoBanner
import cl.pettech.app.ui.components.InfoRow
import cl.pettech.app.ui.components.PetTechLogo
import cl.pettech.app.ui.components.PetTechTopBar

/** Carnet digital de la mascota (datos declarados por el tutor + código PetTech). */
@Composable
fun PetDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: PetDetailViewModel = viewModel(factory = PetDetailViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }
    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onDeleted()
    }

    Scaffold(
        topBar = {
            PetTechTopBar(
                title = "Carnet digital",
                onBack = onBack,
                actions = {
                    if (ui.pet != null) {
                        IconButton(onClick = { onEdit(viewModel.petId) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar mascota")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val pet = ui.pet
        when {
            ui.loading && pet == null -> Column(Modifier.padding(padding)) { FullScreenLoading() }
            pet == null -> Column(
                Modifier
                    .padding(padding)
                    .padding(20.dp),
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
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ui.errorMessage?.let { ErrorBanner(it) }
                PetCardView(pet)
                InfoBanner(
                    "Este código identifica a tu mascota, pero NO da acceso a su historial: " +
                        "cada veterinaria deberá solicitar tu autorización.",
                )
                Text("Historial clínico", style = MaterialTheme.typography.titleMedium)
                InfoBanner("El historial compartido con veterinarias autorizadas se habilitará en el próximo incremento.")
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !ui.deleting,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
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
            text = { Text("Se eliminará el perfil de ${ui.pet?.name ?: "la mascota"}. Esta acción no se puede deshacer.") },
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
private fun PetCardView(pet: Pet) {
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PetTechLogo(size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("PetTech", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Carnet digital de mascota", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(pet.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))

            Text(
                "Código PetTech",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pet.publicCode,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Código PetTech ${pet.publicCode.toList().joinToString(" ")}" },
                )
                TextButton(onClick = { clipboard.setText(AnnotatedString(pet.publicCode)) }) { Text("Copiar") }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            InfoRow("Especie", pet.speciesLabel)
            InfoRow("Sexo", pet.sexLabel)
            if (pet.breed.isNotBlank()) InfoRow("Raza", pet.breed)
            InfoRow(
                "Fecha de nacimiento",
                pet.birthDate?.let { Validators.formatDate(it) + if (pet.birthDateIsEstimated) " (aproximada)" else "" }
                    ?: "No informada",
            )
            InfoRow("Microchip (declarado)", pet.microchipNumber.ifBlank { "No informado" })
            if (pet.generalNotes.isNotBlank()) InfoRow("Antecedentes generales (declarados)", pet.generalNotes)
            Text(
                "Origen de los datos: declarados por el tutor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
