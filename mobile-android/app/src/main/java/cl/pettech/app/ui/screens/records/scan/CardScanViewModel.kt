package cl.pettech.app.ui.screens.records.scan

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.local.CardTextRecognizer
import cl.pettech.app.data.local.PhotoTools
import cl.pettech.app.data.repository.ClinicalRecordRepository
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.EntryForm
import cl.pettech.app.domain.model.EntryType
import cl.pettech.app.domain.model.RecordTarget
import cl.pettech.app.domain.ocr.CardTextParser
import cl.pettech.app.domain.ocr.ProposedEntry
import cl.pettech.app.ui.appContainer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val DMY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

/** Fila editable de la revisión. Las fechas se editan como texto dd-mm-aaaa. */
data class ScanRow(
    val key: Int,
    val include: Boolean = true,
    val type: EntryType = EntryType.VACCINE,
    val title: String = "",
    val occurredOn: String = "",
    val nextDueOn: String = "",
    val lotNumber: String = "",
    val sourceText: String = "",
    val proposed: ProposedEntry? = null,
) {
    fun occurredDate(): LocalDate? = parseDate(occurredOn)
    fun nextDate(): LocalDate? = parseDate(nextDueOn)

    /** Mensaje de error de la fila o null si está lista para guardar. */
    fun problem(today: LocalDate = LocalDate.now()): String? {
        if (!include) return null
        val occurred = occurredDate()
        return when {
            title.isBlank() -> "Indica la vacuna o el producto."
            occurred == null -> "Fecha de aplicación inválida (dd-mm-aaaa)."
            occurred.isAfter(today) -> "La fecha de aplicación no puede ser futura."
            nextDueOn.isNotBlank() && nextDate() == null -> "Próxima fecha inválida (dd-mm-aaaa)."
            nextDate()?.isBefore(occurred) == true -> "La próxima fecha debe ser posterior."
            else -> null
        }
    }

    /** Campos que el tutor cambió respecto de la propuesta del OCR (indicador de calidad del reconocimiento). */
    fun editedFields(): Int {
        val p = proposed ?: return 0
        return listOf(
            title != p.title,
            occurredOn != (p.occurredOn?.format(DMY) ?: ""),
            nextDueOn != (p.nextDueOn?.format(DMY) ?: ""),
            lotNumber != p.lotNumber,
            type != p.type,
        ).count { it }
    }

    companion object {
        fun parseDate(text: String): LocalDate? =
            runCatching { LocalDate.parse(text.trim().replace('/', '-'), DMY) }.getOrNull()

        fun from(key: Int, p: ProposedEntry) = ScanRow(
            key = key, type = p.type, title = p.title, occurredOn = p.occurredOn?.format(DMY) ?: "",
            nextDueOn = p.nextDueOn?.format(DMY) ?: "", lotNumber = p.lotNumber, sourceText = p.sourceText, proposed = p,
        )
    }
}

enum class ScanStage { PICK, READING, REVIEW, SAVING, DONE }

data class CardScanUiState(
    val stage: ScanStage = ScanStage.PICK,
    val preview: ImageBitmap? = null,
    val rawRows: List<String> = emptyList(),
    val rows: List<ScanRow> = emptyList(),
    val message: String? = null,
    val saved: Int = 0,
    val editedFields: Int = 0,
    val proposedCount: Int = 0,
)

/**
 * Transcripción asistida del carnet en papel (DEC-032): foto → OCR en el teléfono → propuesta editable →
 * el tutor confirma → se sube la foto como respaldo y se crean los antecedentes vinculados a ella.
 * Nada se guarda sin revisión: el OCR de letra manuscrita es poco confiable.
 */
class CardScanViewModel(
    private val repository: ClinicalRecordRepository,
    private val photoTools: PhotoTools,
    private val recognizer: CardTextRecognizer,
    handle: SavedStateHandle,
) : ViewModel() {

    private val petId: String = checkNotNull(handle.get<String>("petId"))
    private val _ui = MutableStateFlow(CardScanUiState())
    val ui: StateFlow<CardScanUiState> = _ui.asStateFlow()
    private var bitmap: Bitmap? = null
    private var nextKey = 0
    /** Si un guardado falla a medias, se reutiliza la foto ya subida (el servidor rechaza duplicados). */
    private var uploadedAttachmentId: String? = null

    fun newCaptureUri(): Uri = photoTools.newCaptureUri()

    fun onImage(uri: Uri) {
        _ui.update { it.copy(stage = ScanStage.READING, message = null) }
        viewModelScope.launch {
            val bmp = photoTools.decodeForOcr(uri)
            if (bmp == null) {
                _ui.update { it.copy(stage = ScanStage.PICK, message = "No se pudo abrir la imagen.") }
                return@launch
            }
            bitmap = bmp
            val lines = runCatching { recognizer.recognize(bmp) }.getOrElse {
                _ui.update { s -> s.copy(stage = ScanStage.PICK, message = "No se pudo leer el texto. Intenta con otra foto.") }
                return@launch
            }
            val rows = CardTextParser.rows(lines)
            val proposals = CardTextParser.parse(rows)
            _ui.update {
                it.copy(
                    stage = ScanStage.REVIEW,
                    preview = bmp.asImageBitmap(),
                    rawRows = rows,
                    rows = proposals.map { p -> ScanRow.from(nextKey++, p) },
                    proposedCount = proposals.size,
                    message = if (proposals.isEmpty()) {
                        "No reconocimos fechas en la foto. Puedes agregar los registros a mano mirando la imagen."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun update(key: Int, change: (ScanRow) -> ScanRow) =
        _ui.update { s -> s.copy(rows = s.rows.map { if (it.key == key) change(it) else it }) }

    fun addRow() = _ui.update { it.copy(rows = it.rows + ScanRow(key = nextKey++)) }

    fun restart() {
        bitmap = null
        uploadedAttachmentId = null
        _ui.value = CardScanUiState()
    }

    fun save() {
        val state = _ui.value
        val bmp = bitmap ?: return
        val selected = state.rows.filter { it.include }
        val firstProblem = selected.firstNotNullOfOrNull { it.problem() }
        if (selected.isEmpty() || firstProblem != null) {
            _ui.update { it.copy(message = firstProblem ?: "Selecciona al menos un registro.") }
            return
        }
        _ui.update { it.copy(stage = ScanStage.SAVING, message = null) }
        viewModelScope.launch {
            val attachmentId = uploadedAttachmentId ?: run {
                val upload = repository.uploadPhoto(petId, photoTools.toJpeg(bmp), "CARD_SCAN", null)
                (upload as? AppResult.Success)?.data ?: run {
                    val msg = (upload as? AppResult.Failure)?.error?.message ?: "No se pudo subir la foto."
                    _ui.update { it.copy(stage = ScanStage.REVIEW, message = msg) }
                    return@launch
                }
            }
            uploadedAttachmentId = attachmentId
            var saved = 0
            for (row in selected) {
                val form = EntryForm(
                    type = row.type, title = row.title.trim(), occurredOn = row.occurredDate()!!,
                    nextDueOn = row.nextDate(),
                    notes = buildString {
                        append("Transcrito desde la foto del carnet y revisado por el tutor.")
                        if (row.sourceText.isNotBlank()) append("\nTexto leído en el carnet: «${row.sourceText.take(500)}»")
                    },
                    weightKg = null, lotNumber = row.lotNumber.trim(), isActive = true, sourceAttachmentId = attachmentId,
                )
                when (val r = repository.create(RecordTarget.Owner(petId), form)) {
                    is AppResult.Success -> saved++
                    is AppResult.Failure -> {
                        _ui.update {
                            it.copy(
                                stage = ScanStage.REVIEW,
                                rows = it.rows.filterNot { x -> x.key in selected.take(saved).map { s -> s.key } },
                                message = "Se guardaron $saved registros; falló «${row.title}»: ${r.error.message}",
                            )
                        }
                        return@launch
                    }
                }
            }
            _ui.update {
                it.copy(stage = ScanStage.DONE, saved = saved, editedFields = selected.sumOf { r -> r.editedFields() })
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                CardScanViewModel(c.clinicalRecordRepository, c.photoTools, c.cardTextRecognizer, createSavedStateHandle())
            }
        }
    }
}
