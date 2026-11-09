package cl.pettech.app.ui.screens.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
) {
    /** Antecedentes visibles con el filtro por tipo (HU-04). */
    val visibleEntries: List<ClinicalEntry>
        get() = record?.entries.orEmpty().filter { filter == null || it.type == filter }
}

class ClinicalRecordViewModel(
    private val repository: ClinicalRecordRepository,
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
            initializer { ClinicalRecordViewModel(appContainer().clinicalRecordRepository, createSavedStateHandle()) }
        }
    }
}
