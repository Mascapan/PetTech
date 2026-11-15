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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onScanCard: (() -> Unit)? = null,
    viewModel: ClinicalRecordViewModel = viewModel(factory = ClinicalRecordViewModel.Factory),
) {
    var carnetView by rememberSaveable { mutableStateOf(false) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var photoEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    val pickPhoto = rememberPhotoPicker(viewModel::newCaptureUri) { uri ->
        photoEntryId?.let { viewModel.addPhoto(it, uri) }
    }
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
                ui.photoMessage?.let { item { InfoBanner(it) } }
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
                if (onScanCard != null) {
                    item {
                        OutlinedButton(onClick = onScanCard, modifier = Modifier.fillMaxWidth()) {
                            Text("📷 ¿Tienes el carnet en papel? Escanéalo")
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !carnetView, onClick = { carnetView = false }, label = { Text("Historial") })
                        FilterChip(selected = carnetView, onClick = { carnetView = true }, label = { Text("Ver como carnet") })
                    }
                }
                if (carnetView) {
                    item { CarnetView(record.entries) }
                    return@LazyColumn
                }
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
                        thumbnail = entry.attachments.firstOrNull()?.let { ui.photos[it.id] },
                        onNeedThumbnail = { entry.attachments.firstOrNull()?.let { viewModel.loadPhoto(it.id) } },
                    ) {
                        EntryPhotos(
                            entry = entry,
                            photos = ui.photos,
                            uploading = ui.uploadingEntryId == entry.id,
                            canAdd = viewModel.canAddPhotos,
                            onNeedPhoto = viewModel::loadPhoto,
                            onAdd = {
                                viewModel.clearPhotoMessage()
                                photoEntryId = entry.id
                                pickPhoto()
                            },
                        )
                    }
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

/**
 * Antecedente de la ficha. Cerrado muestra el resumen; al tocarlo se despliega TODO el detalle
 * (datos de la vacuna, signos vitales, SOAP, observaciones, fotos, autor y fecha de registro).
 */
@Composable
private fun EntryCard(
    entry: ClinicalEntry,
    canCorrect: Boolean,
    onCorrect: () -> Unit,
    thumbnail: ImageBitmap? = null,
    onNeedThumbnail: () -> Unit = {},
    photos: @Composable () -> Unit = {},
) {
    val professional = entry.origin == EntryOrigin.PROFESSIONAL
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    if (entry.attachments.isNotEmpty()) LaunchedEffect(entry.id) { onNeedThumbnail() }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 3.dp else 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusTag(entry.typeLabel, TagColors.BlueBg, TagColors.Blue)
                if (professional) {
                    StatusTag("Registro veterinario", TagColors.GreenBg, TagColors.Green)
                } else {
                    StatusTag("Declarado por el tutor", TagColors.GreyBg, TagColors.Grey)
                }
                if (entry.sourceAttachmentId != null) StatusTag("📄 Desde el carnet", TagColors.AmberBg, TagColors.Amber)
                if (entry.type?.hasActiveFlag == true && !entry.isActive) {
                    StatusTag("No vigente", TagColors.GreyBg, TagColors.Grey)
                }
                if (entry.isCorrection) StatusTag("Corregido", TagColors.AmberBg, TagColors.Amber)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                    EntrySummaryLine(entry, professional)
                }
                if (entry.attachments.isNotEmpty()) {
                    // Miniatura de la foto de respaldo (etiqueta o carnet), visible sin desplegar.
                    Box(
                        Modifier.padding(start = 10.dp).size(56.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (thumbnail != null) {
                            Image(thumbnail, contentDescription = "Foto de respaldo", contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                        } else {
                            Text("📷")
                        }
                    }
                }
            }
            if (!expanded) {
                val preview = listOf(entry.diagnosis, entry.notes, entry.anamnesis).firstOrNull { it.isNotBlank() }
                if (preview != null) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "Ver detalle ▾",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                HorizontalDivider()
                EntryDetail(entry, professional)
                photos()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canCorrect) {
                        TextButton(onClick = onCorrect, contentPadding = PaddingValues(0.dp)) { Text("Corregir") }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { expanded = false }) { Text("Ocultar ▴") }
                }
            }
        }
    }
}

@Composable
private fun EntryDetail(entry: ClinicalEntry, professional: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entry.occurredOn?.let { KeyValueRow("Fecha", Validators.formatDate(it)) }
        entry.nextDueOn?.let { KeyValueRow("Próxima fecha", Validators.formatDate(it)) }
        entry.weightKg?.let { KeyValueRow("Peso", "${formatKg(it)} kg") }
        if (entry.type?.hasActiveFlag == true) KeyValueRow("Vigente", if (entry.isActive) "Sí" else "No")

        // Producto aplicado (vacunas, antiparasitarios, fármacos)
        if (entry.manufacturer.isNotBlank()) KeyValueRow("Laboratorio", entry.manufacturer)
        if (entry.lotNumber.isNotBlank()) KeyValueRow("Lote / serie", entry.lotNumber)
        entry.lotExpiresOn?.let { KeyValueRow("Vence el lote", Validators.formatDate(it)) }
        if (entry.dose.isNotBlank()) KeyValueRow("Dosis y frecuencia", entry.dose)

        // Examen físico (S-O-A-P)
        val vitals = listOfNotNull(
            entry.temperatureC?.let { "T° ${formatKg(it)} °C" },
            entry.heartRateBpm?.let { "FC $it lpm" },
            entry.respiratoryRateRpm?.let { "FR $it rpm" },
            entry.bodyConditionScore?.let { "CC $it/9" },
        )
        if (vitals.isNotEmpty()) KeyValueRow("Signos vitales", vitals.joinToString(" · "))
        DetailBlock("S · Anamnesis", entry.anamnesis)
        DetailBlock("O · Examen físico", entry.physicalExam)
        DetailBlock("A · Diagnóstico", entry.diagnosis)
        DetailBlock("P · Tratamiento e indicaciones", entry.treatment)
        DetailBlock("Observaciones", entry.notes)

        HorizontalDivider()
        KeyValueRow(
            if (professional) "Atendió" else "Registrado por",
            if (professional) "${entry.authorName} · ${entry.clinicName}" else "${entry.authorName} (tutor)",
        )
        entry.createdAt?.let { KeyValueRow("Registrado el", Validators.formatDateTime(it)) }
        if (entry.sourceAttachmentId != null) {
            Text(
                "Transcrito por el tutor desde la foto de su carnet de papel (la foto queda como respaldo).",
                style = MaterialTheme.typography.bodySmall,
                color = TagColors.Amber,
            )
        }
        if (entry.isCorrection && entry.correctionReason.isNotBlank()) {
            Text(
                "Corrige una versión anterior · motivo: ${entry.correctionReason}",
                style = MaterialTheme.typography.bodySmall,
                color = TagColors.Amber,
            )
        }
    }
}

@Composable
private fun EntrySummaryLine(entry: ClinicalEntry, professional: Boolean) {
    Text(
        listOfNotNull(
            entry.occurredOn?.let { Validators.formatDate(it) },
            entry.nextDueOn?.let { "próxima: ${Validators.formatDate(it)}" },
            if (professional) entry.clinicName.ifBlank { null } else "tutor",
            entry.attachments.size.takeIf { it > 0 }?.let { "📷 $it" },
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Texto largo con su título encima (se lee completo, sin columnas estrechas). */
@Composable
private fun DetailBlock(label: String, text: String) {
    if (text.isBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun formatKg(value: Double): String = String.format(Locale("es", "CL"), "%.1f", value)


/**
 * Carnet digital: replica el formato del carnet de papel (tabla de vacunas y desparasitaciones con fecha,
 * producto, lote, próxima dosis y quién la aplicó), para mostrarlo en la consulta como el carnet de siempre.
 */
@Composable
private fun CarnetView(entries: List<ClinicalEntry>) {
    val sections = listOf(EntryType.VACCINE to "Vacunas", EntryType.DEWORMING to "Desparasitaciones")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sections.forEach { (type, title) ->
            val rows = entries.filter { it.type == type }.sortedBy { it.occurredOn }
            SectionCard(title) {
                if (rows.isEmpty()) {
                    Text("Sin registros.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Fecha" to 0.24f, "Producto / lote" to 0.38f, "Próxima" to 0.22f, "Firma" to 0.16f)
                            .forEach { (h, w) ->
                                Text(h, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(w))
                            }
                    }
                    HorizontalDivider()
                    rows.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(e.occurredOn?.let { Validators.formatDate(it) } ?: "—",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.24f))
                            Text(e.title + if (e.lotNumber.isNotBlank()) "\nLote ${e.lotNumber}" else "",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.38f))
                            Text(e.nextDueOn?.let { Validators.formatDate(it) } ?: "—",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.22f))
                            Text(
                                when {
                                    e.origin == EntryOrigin.PROFESSIONAL -> "✔ Vet."
                                    e.sourceAttachmentId != null -> "📄 Carnet"
                                    else -> "Tutor"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (e.origin == EntryOrigin.PROFESSIONAL) TagColors.Green else TagColors.Grey,
                                modifier = Modifier.weight(0.16f),
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
        Text(
            "✔ Vet.: registrado por una veterinaria de la Red PetTech · 📄 Carnet: transcrito por el tutor desde su " +
                "carnet de papel (con foto de respaldo) · Tutor: declarado por el tutor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
