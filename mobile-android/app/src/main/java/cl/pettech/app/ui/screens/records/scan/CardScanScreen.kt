@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.records.scan

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.pettech.app.domain.model.EntryType
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.InfoBanner
import cl.pettech.app.ui.components.PetTechTopBar
import cl.pettech.app.ui.components.StatusTag
import cl.pettech.app.ui.components.TagColors
import cl.pettech.app.ui.screens.records.rememberPhotoPicker

/**
 * "Escanear carnet": el tutor fotografía su carnet en papel; la app propone los registros y él los revisa.
 * Pensado para quien prefiere no digitar: botones grandes y todo editable antes de guardar.
 */
@Composable
fun CardScanScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: CardScanViewModel = viewModel(factory = CardScanViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pick = rememberPhotoPicker(viewModel::newCaptureUri, viewModel::onImage)

    Scaffold(topBar = { PetTechTopBar(title = "Escanear carnet", onBack = onBack) }) { padding ->
        when (ui.stage) {
            ScanStage.PICK -> Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Pasa tu carnet de papel a la app", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "1. Fotografía una página del carnet (vacunas o desparasitaciones).\n" +
                        "2. La app lee el texto en tu teléfono y te propone los registros.\n" +
                        "3. Revisa y corrige lo que haga falta. Solo se guarda lo que confirmes.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                InfoBanner(
                    "Consejos: buena luz, sin reflejos, la página completa y derecha. La letra manuscrita " +
                        "puede leerse mal: por eso siempre revisas antes de guardar. La foto queda como respaldo.",
                )
                ui.message?.let { ErrorBanner(it) }
                Button(onClick = pick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("📷 Fotografiar o elegir imagen", style = MaterialTheme.typography.titleMedium)
                }
            }
            ScanStage.READING, ScanStage.SAVING -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(if (ui.stage == ScanStage.READING) "Leyendo el carnet…" else "Guardando en la ficha…")
                }
            }
            ScanStage.REVIEW -> Review(ui, viewModel, Modifier.padding(padding))
            ScanStage.DONE -> Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("¡Listo!", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Se agregaron ${ui.saved} registros a la ficha, vinculados a la foto del carnet.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (ui.proposedCount > 0) {
                    Text(
                        "La app propuso ${ui.proposedCount} registros y corregiste ${ui.editedFields} datos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Ver la ficha") }
                OutlinedButton(onClick = viewModel::restart, modifier = Modifier.fillMaxWidth()) { Text("Escanear otra página") }
            }
        }
    }
}

@Composable
private fun Review(ui: CardScanUiState, viewModel: CardScanViewModel, modifier: Modifier) {
    var showText by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Revisa los registros propuestos", style = MaterialTheme.typography.titleLarge)
            Text(
                "Compara con la foto. Desmarca lo que no corresponda y corrige lo necesario.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ui.preview?.let { image ->
            item {
                Image(
                    image, contentDescription = "Foto del carnet", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                )
            }
        }
        ui.message?.let { item { ErrorBanner(it) } }
        items(ui.rows, key = { it.key }) { row -> RowEditor(row, viewModel) }
        item {
            OutlinedButton(onClick = viewModel::addRow, modifier = Modifier.fillMaxWidth()) { Text("＋ Agregar registro a mano") }
        }
        item {
            TextButton(onClick = { showText = !showText }) {
                Text(if (showText) "Ocultar texto leído" else "Ver texto leído por la app")
            }
            if (showText) {
                Text(
                    ui.rawRows.joinToString("\n").ifBlank { "(sin texto)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            val count = ui.rows.count { it.include }
            Button(
                onClick = viewModel::save,
                enabled = count > 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Guardar $count registros en la ficha") }
            TextButton(onClick = viewModel::restart, modifier = Modifier.fillMaxWidth()) { Text("Tomar otra foto") }
        }
    }
}

@Composable
private fun RowEditor(row: ScanRow, viewModel: CardScanViewModel) {
    val problem = row.problem()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = row.include, onCheckedChange = { v -> viewModel.update(row.key) { it.copy(include = v) } })
                Text(if (row.include) "Guardar este registro" else "No guardar", modifier = Modifier.weight(1f))
                if (row.proposed != null && !row.proposed.titleRecognized) {
                    StatusTag("Completa el nombre", TagColors.AmberBg, TagColors.Amber)
                }
            }
            if (row.sourceText.isNotBlank()) {
                Text("Leído: «${row.sourceText}»", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (row.include) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EntryType.VACCINE, EntryType.DEWORMING).forEach { type ->
                        FilterChip(
                            selected = row.type == type,
                            onClick = { viewModel.update(row.key) { it.copy(type = type) } },
                            label = { Text(type.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = row.title, onValueChange = { v -> viewModel.update(row.key) { it.copy(title = v.take(120)) } },
                    label = { Text(if (row.type == EntryType.VACCINE) "Vacuna" else "Producto") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("Aplicada (dd-mm-aaaa)", row.occurredOn, Modifier.weight(1f)) { v ->
                        viewModel.update(row.key) { it.copy(occurredOn = v) }
                    }
                    DateField("Próxima (opcional)", row.nextDueOn, Modifier.weight(1f)) { v ->
                        viewModel.update(row.key) { it.copy(nextDueOn = v) }
                    }
                }
                OutlinedTextField(
                    value = row.lotNumber, onValueChange = { v -> viewModel.update(row.key) { it.copy(lotNumber = v.take(40)) } },
                    label = { Text("Lote / serie (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun DateField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '-' || it == '/' }.take(10)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
