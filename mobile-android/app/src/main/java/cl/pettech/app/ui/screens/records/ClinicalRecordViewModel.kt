package cl.pettech.app.ui.screens.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import cl.pettech.app.data.local.PhotoTools
import cl.pettech.app.data.repository.ClinicalRecordRepository
import cl.pettech.app.domain.model.AppError
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.ClinicalEntry
import cl.pettech.app.domain.model.ClinicalRecord
import cl.pettech.app.domain.model.EntryOrigin
import cl.pettech.app.domain.model.EntryType
import cl.pettech.app.domain.model.RecordTarget
import cl.pettech.app.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lee el destino de la ficha desde los argumentos de navegación ("petId" del tutor o "code" del profesional). */
fun SavedStateHandle.recordTarget(): RecordTarget {
    val code = get<String>("code")
    return if (!code.isNullOrBlank()) RecordTarget.Professional(code) else RecordTarget.Owner(checkNotNull(get<String>("petId")))
}

data class ClinicalRecordUiState(
    val loading: Boolean = true,
    val record: ClinicalRecord? = null,
    val filter: EntryType? = null,
    val myClinicName: String? = null,
    val errorMessage: String? = null,
    /** El profesional perdió la autorización (expiró o fue revocada). */
    val accessLost: Boolean = false,
    /** Fotos ya descargadas (id → imagen). */
    val photos: Map<String, ImageBitmap> = emptyMap(),
    /** Antecedente al que se está subiendo una foto. */
    val uploadingEntryId: String? = null,
    val photoMessage: String? = null,
) {
    /** Antecedentes visibles con el filtro por tipo (HU-04). */
    val visibleEntries: List<ClinicalEntry>
        get() = record?.entries.orEmpty().filter { filter == null || it.type == filter }
}

class ClinicalRecordViewModel(
    private val repository: ClinicalRecordRepository,
    private val photoTools: PhotoTools,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val target: RecordTarget = savedStateHandle.recordTarget()
    val isProfessional: Boolean get() = target is RecordTarget.Professional

    private val _ui = MutableStateFlow(ClinicalRecordUiState())
    val ui: StateFlow<ClinicalRecordUiState> = _ui.asStateFlow()

    fun load() {
        _ui.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            if (isProfessional && _ui.value.myClinicName == null) {
                (repository.professionalClinicName() as? AppResult.Success)?.let { r ->
                    _ui.update { it.copy(myClinicName = r.data) }
                }
            }
            when (val result = repository.load(target)) {
                is AppResult.Success -> _ui.update { it.copy(loading = false, record = result.data, accessLost = false) }
                is AppResult.Failure -> _ui.update {
                    it.copy(
                        loading = false,
                        errorMessage = result.error.message,
                        accessLost = isProfessional && result.error.kind == AppError.Kind.NOT_FOUND,
                        record = if (isProfessional && result.error.kind == AppError.Kind.NOT_FOUND) null else it.record,
                    )
                }
            }
        }
    }

    fun setFilter(type: EntryType?) = _ui.update { it.copy(filter = type) }

    // ------------------------------------------------------------ Fotografías (DEC-031)

    private val photosInFlight = mutableSetOf<String>()

    /** El tutor puede respaldar con foto cualquier antecedente de su mascota (p. ej. la etiqueta de la vacuna). */
    val canAddPhotos: Boolean get() = target is RecordTarget.Owner

    fun newCaptureUri(): Uri = photoTools.newCaptureUri()

    fun loadPhoto(attachmentId: String) {
        if (attachmentId in _ui.value.photos || !photosInFlight.add(attachmentId)) return
        viewModelScope.launch {
            val result = repository.photoBytes(target, attachmentId)
            photosInFlight.remove(attachmentId)
            val image = (result as? AppResult.Success)?.data?.let { PhotoTools.toImageBitmap(it) } ?: return@launch
            _ui.update { it.copy(photos = it.photos + (attachmentId to image)) }
        }
    }

    fun addPhoto(entryId: String, uri: Uri) {
        val owner = target as? RecordTarget.Owner ?: return
        _ui.update { it.copy(uploadingEntryId = entryId, photoMessage = null) }
        viewModelScope.launch {
            val jpeg = photoTools.compressForUpload(uri)
            if (jpeg == null) {
                _ui.update { it.copy(uploadingEntryId = null, photoMessage = "No se pudo leer la imagen.") }
                return@launch
            }
            when (val r = repository.uploadPhoto(owner.petId, jpeg, "VACCINE_LABEL", entryId)) {
                is AppResult.Success -> {
                    _ui.update { it.copy(uploadingEntryId = null, photoMessage = "Foto agregada a la ficha.") }
                    load()
                }
                is AppResult.Failure -> _ui.update {
                    it.copy(uploadingEntryId = null, photoMessage = r.error.message)
                }
            }
        }
    }

    fun clearPhotoMessage() = _ui.update { it.copy(photoMessage = null) }

    /**
     * Quién puede corregir (la regla definitiva la aplica el servidor):
     * tutor → solo lo que él declaró; profesional → solo registros de su establecimiento (RN-14).
     */
    fun canCorrect(entry: ClinicalEntry): Boolean = when (target) {
        is RecordTarget.Owner -> entry.origin == EntryOrigin.OWNER_DECLARED
        is RecordTarget.Professional ->
            entry.origin == EntryOrigin.PROFESSIONAL && entry.clinicName == _ui.value.myClinicName
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                ClinicalRecordViewModel(c.clinicalRecordRepository, c.photoTools, createSavedStateHandle())
            }
        }
    }
}
