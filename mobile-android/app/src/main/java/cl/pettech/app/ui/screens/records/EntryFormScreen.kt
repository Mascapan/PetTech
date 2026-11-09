@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package cl.pettech.app.ui.screens.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.pettech.app.domain.model.EntryType
import cl.pettech.app.domain.model.RecordTarget
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.FormTextField
import cl.pettech.app.ui.components.FullScreenLoading
import cl.pettech.app.ui.components.InfoBanner
import cl.pettech.app.ui.components.LabeledCheckbox
import cl.pettech.app.ui.components.LoadingButton
import cl.pettech.app.ui.components.PetTechTopBar

@Composable
fun EntryFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EntryFormViewModel = viewModel(factory = EntryFormViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ui.saved) { if (ui.saved) onSaved() }
    val professional = viewModel.target is RecordTarget.Professional

    Scaffold(
        topBar = {
            PetTechTopBar(
                title = when {
                    viewModel.isCorrection -> "Corregir antecedente"
                    professional -> "Registrar atención"
                    else -> "Agregar antecedente"
                },
                onBack = onBack,
            )
        },
    ) { padding ->
        if (ui.loadingEntry) {
            Column(Modifier.padding(padding)) { FullScreenLoading() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ui.errorMessage?.let { ErrorBanner(it) }
            InfoBanner(
                when {
                    viewModel.isCorrection ->
                        "La corrección crea una nueva versión; la anterior se conserva con su autor, fecha y motivo."
                    professional -> "Quedará registrado a tu nombre y el de tu establecimiento; el tutor podrá verlo."
                    else -> "Este antecedente quedará marcado como \"declarado por el tutor\"."
                },
            )

            Text("Tipo de antecedente", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryType.entries.forEach { type ->
                    FilterChip(
                        selected = ui.type == type,
                        onClick = { viewModel.onType(type) },
                        label = { Text(type.label) },
                        enabled = !viewModel.isCorrection || ui.type == type,
                    )
                }
            }

            FormTextField(
                value = ui.title,
                onValueChange = viewModel::onTitle,
                label = "Descripción",
                placeholder = ui.type.titleHint,
                error = ui.errors["title"],
            )
            FormTextField(
                value = ui.occurredOn,
                onValueChange = viewModel::onOccurredOn,
                label = if (ui.type.hasActiveFlag) "Fecha de diagnóstico o inicio" else "Fecha",
                placeholder = "dd-mm-aaaa",
                supportingText = "Formato dd-mm-aaaa. No puede ser posterior a hoy.",
                error = ui.errors["occurred_on"],
                keyboardType = KeyboardType.Number,
            )
            if (ui.type.hasNextDate) {
                FormTextField(
                    value = ui.nextDueOn,
                    onValueChange = viewModel::onNextDueOn,
                    label = when (ui.type) {
                        EntryType.VACCINE -> "Próxima dosis (opcional)"
                        EntryType.MEDICATION -> "Fin del tratamiento (opcional)"
                        else -> "Próximo control (opcional)"
                    },
                    placeholder = "dd-mm-aaaa",
                    error = ui.errors["next_due_on"],
                    keyboardType = KeyboardType.Number,
                )
            }
            if (ui.type == EntryType.WEIGHT || ui.type == EntryType.VISIT) {
                FormTextField(
                    value = ui.weight,
                    onValueChange = viewModel::onWeight,
                    label = if (ui.type == EntryType.WEIGHT) "Peso (kg)" else "Peso (kg, opcional)",
                    placeholder = "12,5",
                    error = ui.errors["weight_kg"],
                    keyboardType = KeyboardType.Decimal,
                )
            }
            if (ui.type == EntryType.VACCINE) {
                FormTextField(
                    value = ui.lotNumber,
                    onValueChange = viewModel::onLot,
                    label = "Lote o serie (opcional)",
                    error = ui.errors["lot_number"],
                )
            }
            if (ui.type.hasActiveFlag) {
                LabeledCheckbox(checked = ui.isActive, onCheckedChange = viewModel::onActive, label = "Sigue vigente")
            }
            FormTextField(
                value = ui.notes,
                onValueChange = viewModel::onNotes,
                label = if (professional) "Observaciones, procedimiento o tratamiento" else "Observaciones (opcional)",
                error = ui.errors["notes"],
                singleLine = false,
                minLines = 3,
            )
            if (viewModel.isCorrection) {
                FormTextField(
                    value = ui.reason,
                    onValueChange = viewModel::onReason,
                    label = "Motivo de la corrección",
                    error = ui.errors["reason"],
                )
            }
            LoadingButton(
                text = if (viewModel.isCorrection) "Guardar corrección" else "Guardar",
                loading = ui.saving,
                onClick = viewModel::save,
            )
        }
    }
}
