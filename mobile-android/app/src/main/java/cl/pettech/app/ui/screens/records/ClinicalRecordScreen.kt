@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package cl.pettech.app.ui.screens.records

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.pettech.app.domain.model.ClinicalEntry
import cl.pettech.app.domain.model.ClinicalRecord
import cl.pettech.app.domain.model.EntryOrigin
import cl.pettech.app.domain.model.EntryType
import cl.pettech.app.domain.model.RecordTarget
import cl.pettech.app.domain.validation.Validators
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.FullScreenLoading
import cl.pettech.app.ui.components.InfoBanner
import cl.pettech.app.ui.components.KeyValueRow
import cl.pettech.app.ui.components.PetAvatar
import cl.pettech.app.ui.components.PetTechTopBar
import cl.pettech.app.ui.components.SectionCard
import cl.pettech.app.ui.components.StatusTag
import cl.pettech.app.ui.components.TagColors
import java.util.Locale

/**
 * Vista 4 del boceto: "Ficha clínica" — carnet de control con los datos de la mascota y el
 * registro de vacunas, desparasitaciones, visitas y el veterinario que atendió. Se agregan las
 * variables clínicas críticas pedidas en la retroalimentación (alergias, patologías crónicas,
 * peso, cirugías y fármacos). La usa el tutor y también el veterinario autorizado.
 */
@Composable
fun ClinicalRecordScreen(
    onBack: () -> Unit,
    onAddEntry: () -> Unit,
    onCorrectEntry: (String) -> Unit,
    viewModel: ClinicalRecordViewModel = viewModel(factory = ClinicalRecordViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            PetTechTopBar(
                title = "Ficha clínica",
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::load) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
                },
            )
        },
        floatingActionButton = {
            if (ui.record != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddEntry,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(if (viewModel.isProfessional) "Registrar atención" else "Agregar antecedente") },
                )
            }
        },
    ) { padding ->
        val record = ui.record
        when {
            ui.loading && record == null -> Column(Modifier.padding(padding)) { FullScreenLoading("Cargando ficha clínica…") }
            record == null -> Column(
                Modifier.padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ui.accessLost) {
                    ErrorBanner("La autorización del tutor ya no está vigente (expiró o fue revocada). Solicita acceso nuevamente.")
                } else {
                    ErrorBanner(ui.errorMessage ?: "No se pudo cargar la ficha clínica.")
                }
                Button(onClick = viewModel::load) { Text("Reintentar") }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ui.errorMessage?.let { item { ErrorBanner(it) } }
                if (viewModel.isProfessional) {
                    item {
                        InfoBanner(
                            "Acceso autorizado por el tutor${ui.myClinicName?.let { " para $it" } ?: ""}. " +
                                "Cada consulta y registro queda en la bitácora que ve el tutor.",
                        )
                    }
                }
                item { PetHeader(record, (viewModel.target as? RecordTarget.Owner)?.petId) }
                item { SummaryCard(record) }
                item { TypeFilter(selected = ui.filter, onSelect = viewModel::setFilter) }
                val entries = ui.visibleEntries
                if (entries.isEmpty()) {
                    item {
                        Text(
                            if (ui.filter == null) "Aún no hay antecedentes registrados." else "No hay registros de este tipo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(entries, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        canCorrect = viewModel.canCorrect(entry),
                        onCorrect = { onCorrectEntry(entry.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PetHeader(record: ClinicalRecord, petId: String?) {
    val pet = record.pet
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PetAvatar(petId = petId, name = pet.name, size = 72.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pet.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOf(pet.speciesLabel, pet.breed, pet.sexLabel).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pet.birthDate?.let {
                    Text(
                        "Nacimiento: ${Validators.formatDate(it)}${if (pet.birthDateIsEstimated) " (aprox.)" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    pet.publicCode,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(record: ClinicalRecord) {
    val s = record.summary
    SectionCard("Resumen clínico") {
        KeyValueRow(
            "Peso",
            s.latestWeightKg?.let { "${formatKg(it)} kg" + (s.latestWeightOn?.let { d -> " (${Validators.formatDate(d)})" } ?: "") }
                ?: "Sin registro",
        )
        KeyValueRow(
            "Alergias",
            s.allergies.joinToString().ifBlank { "Ninguna registrada" },
            valueColor = if (s.allergies.isNotEmpty()) TagColors.Red else MaterialTheme.colorScheme.onSurface,
        )
        KeyValueRow("Patologías crónicas", s.chronicConditions.joinToString().ifBlank { "Ninguna registrada" })
        KeyValueRow("Fármacos activos", s.activeMedications.joinToString().ifBlank { "Ninguno registrado" })
        if (s.upcoming.isNotEmpty()) {
            HorizontalDivider()
            Text("Próximas fechas", style = MaterialTheme.typography.titleSmall)
            s.upcoming.take(4).forEach { item ->
                KeyValueRow(
                    item.dueOn?.let { Validators.formatDate(it) } ?: "—",
                    "${item.type?.label ?: ""}: ${item.title}" + if (item.overdue) " · vencida" else "",
                    valueColor = if (item.overdue) TagColors.Red else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun TypeFilter(selected: EntryType?, onSelect: (EntryType?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Todos") })
        EntryType.entries.forEach { type ->
            FilterChip(selected = selected == type, onClick = { onSelect(type) }, label = { Text(type.plural) })
        }
    }
}

@Composable
private fun EntryCard(entry: ClinicalEntry, canCorrect: Boolean, onCorrect: () -> Unit) {
    val professional = entry.origin == EntryOrigin.PROFESSIONAL
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusTag(entry.typeLabel, TagColors.BlueBg, TagColors.Blue)
                if (professional) {
                    StatusTag("Registro veterinario", TagColors.GreenBg, TagColors.Green)
                } else {
                    StatusTag("Declarado por el tutor", TagColors.GreyBg, TagColors.Grey)
                }
                if (entry.type?.hasActiveFlag == true && !entry.isActive) {
                    StatusTag("No vigente", TagColors.GreyBg, TagColors.Grey)
                }
                if (entry.isCorrection) StatusTag("Corregido", TagColors.AmberBg, TagColors.Amber)
            }
            Text(entry.title, style = MaterialTheme.typography.titleMedium)
            entry.occurredOn?.let { KeyValueRow("Fecha", Validators.formatDate(it)) }
            entry.nextDueOn?.let { KeyValueRow("Próxima fecha", Validators.formatDate(it)) }
            entry.weightKg?.let { KeyValueRow("Peso", "${formatKg(it)} kg") }
            if (entry.lotNumber.isNotBlank()) KeyValueRow("Lote / serie", entry.lotNumber)
            if (entry.notes.isNotBlank()) {
                Text(entry.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (professional) {
                    "Atendió: ${entry.authorName} · ${entry.clinicName}"
                } else {
                    "Registrado por: ${entry.authorName} (tutor)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.isCorrection && entry.correctionReason.isNotBlank()) {
                Text(
                    "Motivo de la corrección: ${entry.correctionReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TagColors.Amber,
                )
            }
            if (canCorrect) {
                TextButton(onClick = onCorrect, contentPadding = PaddingValues(0.dp)) { Text("Corregir") }
            }
        }
    }
}

internal fun formatKg(value: Double): String = String.format(Locale("es", "CL"), "%.1f", value)
