@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.pettech.app.domain.model.Sex
import cl.pettech.app.domain.model.Species
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.FormTextField
import cl.pettech.app.ui.components.FullScreenLoading
import cl.pettech.app.ui.components.InfoBanner
import cl.pettech.app.ui.components.LabeledCheckbox
import cl.pettech.app.ui.components.LoadingButton
import cl.pettech.app.ui.components.PetTechTopBar

@Composable
fun PetFormScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: PetFormViewModel = viewModel(factory = PetFormViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.savedPetId) {
        ui.savedPetId?.let(onSaved)
    }

    Scaffold(
        topBar = { PetTechTopBar(title = if (ui.isEdit) "Editar mascota" else "Agregar mascota", onBack = onBack) },
    ) { padding ->
        if (ui.loadingPet) {
            Column(Modifier.padding(padding)) { FullScreenLoading("Cargando datos de la mascota…") }
        } else {
            PetFormContent(ui = ui, viewModel = viewModel, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun PetFormContent(ui: PetFormUiState, viewModel: PetFormViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InfoBanner("Los datos de este perfil son declarados por ti y no reemplazan un certificado veterinario.")
        ui.errorMessage?.let { ErrorBanner(it) }

        FormTextField(
            value = ui.name,
            onValueChange = viewModel::onNameChange,
            label = "Nombre *",
            error = ui.nameError,
        )

        Text("Especie *", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Species.entries.forEach { option ->
                FilterChip(
                    selected = ui.species == option,
                    onClick = { viewModel.onSpeciesChange(option) },
                    label = { Text(option.label) },
                )
            }
        }
        ui.speciesError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Text("Sexo", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { option ->
                FilterChip(
                    selected = ui.sex == option,
                    onClick = { viewModel.onSexChange(option) },
                    label = { Text(option.label) },
                )
            }
        }

        FormTextField(
            value = ui.breed,
            onValueChange = viewModel::onBreedChange,
            label = "Raza (opcional)",
        )
        FormTextField(
            value = ui.birthDateText,
            onValueChange = viewModel::onBirthDateChange,
            label = "Fecha de nacimiento (opcional)",
            placeholder = "dd-mm-aaaa",
            error = ui.birthDateError,
            supportingText = "Ejemplo: 15-03-2021",
            keyboardType = KeyboardType.Number,
        )
        LabeledCheckbox(
            checked = ui.birthDateIsEstimated,
            onCheckedChange = viewModel::onEstimatedChange,
            label = "La fecha es aproximada",
        )
        FormTextField(
            value = ui.microchip,
            onValueChange = viewModel::onMicrochipChange,
            label = "N.º de microchip (opcional)",
            error = ui.microchipError,
            supportingText = "15 dígitos. Es un dato declarado: PetTech no lo verifica.",
            keyboardType = KeyboardType.Number,
        )
        FormTextField(
            value = ui.notes,
            onValueChange = viewModel::onNotesChange,
            label = "Antecedentes generales (opcional)",
            error = ui.notesError,
            supportingText = "${ui.notes.length}/${PetFormViewModel.MAX_NOTES} · p. ej., alergias conocidas o cuidados especiales",
            singleLine = false,
            minLines = 3,
            imeAction = ImeAction.Default,
        )
        LoadingButton(
            text = if (ui.isEdit) "Guardar cambios" else "Registrar mascota",
            loading = ui.saving,
            onClick = viewModel::save,
        )
    }
}
