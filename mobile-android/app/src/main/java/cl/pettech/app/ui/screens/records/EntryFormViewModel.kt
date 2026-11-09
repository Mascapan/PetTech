package cl.pettech.app.ui.screens.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.ClinicalRecordRepository
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.EntryForm
import cl.pettech.app.domain.model.EntryType
import cl.pettech.app.domain.model.RecordTarget
import cl.pettech.app.domain.validation.BirthDateResult
import cl.pettech.app.domain.validation.Validators
import cl.pettech.app.ui.appContainer
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntryFormUiState(
    val type: EntryType = EntryType.VACCINE,
    val title: String = "",
    val occurredOn: String = Validators.formatDate(LocalDate.now(Validators.ZONE)),
    val nextDueOn: String = "",
    val weight: String = "",
    val lotNumber: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val reason: String = "",
    val errors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val loadingEntry: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
)

/** Registro de un antecedente nuevo o corrección versionada de uno existente (RNF-11). */
class EntryFormViewModel(
    private val repository: ClinicalRecordRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val target: RecordTarget = savedStateHandle.recordTarget()
    val correctingId: String? = savedStateHandle.get<String>("entryId")?.takeIf { it.isNotBlank() }
    val isCorrection: Boolean get() = correctingId != null

    private val _ui = MutableStateFlow(EntryFormUiState(loadingEntry = correctingId != null))
    val ui: StateFlow<EntryFormUiState> = _ui.asStateFlow()

    init {
        if (correctingId != null) prefill(correctingId)
    }

    private fun prefill(entryId: String) {
        viewModelScope.launch {
            when (val result = repository.load(target)) {
                is AppResult.Success -> {
                    val entry = result.data.entries.firstOrNull { it.id == entryId }
                    _ui.update {
                        if (entry == null) {
                            it.copy(loadingEntry = false, errorMessage = "El antecedente ya no está vigente.")
                        } else {
                            it.copy(
                                loadingEntry = false,
                                type = entry.type ?: EntryType.VISIT,
                                title = entry.title,
                                occurredOn = entry.occurredOn?.let(Validators::formatDate).orEmpty(),
                                nextDueOn = entry.nextDueOn?.let(Validators::formatDate).orEmpty(),
                                weight = entry.weightKg?.let { w -> formatKg(w) }.orEmpty(),
                                lotNumber = entry.lotNumber,
                                notes = entry.notes,
                                isActive = entry.isActive,
                            )
                        }
                    }
                }
                is AppResult.Failure -> _ui.update { it.copy(loadingEntry = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun edit(field: String, change: (EntryFormUiState) -> EntryFormUiState) =
        _ui.update { change(it).copy(errors = it.errors - field, errorMessage = null) }

    fun onType(v: EntryType) = edit("entry_type") { it.copy(type = v) }
    fun onTitle(v: String) = edit("title") { it.copy(title = v) }
    fun onOccurredOn(v: String) = edit("occurred_on") { it.copy(occurredOn = v) }
    fun onNextDueOn(v: String) = edit("next_due_on") { it.copy(nextDueOn = v) }
    fun onWeight(v: String) = edit("weight_kg") { it.copy(weight = v) }
    fun onLot(v: String) = edit("lot_number") { it.copy(lotNumber = v) }
    fun onNotes(v: String) = edit("notes") { it.copy(notes = v) }
    fun onActive(v: Boolean) = edit("is_active") { it.copy(isActive = v) }
    fun onReason(v: String) = edit("reason") { it.copy(reason = v) }

    fun save() {
        val s = _ui.value
        if (s.saving) return
        val errors = mutableMapOf<String, String>()
        if (s.title.isBlank()) errors["title"] = "Este campo es obligatorio."
        val occurred = when (val r = Validators.parseEventDate(s.occurredOn)) {
            is BirthDateResult.Valid -> r.date
            is BirthDateResult.Invalid -> { errors["occurred_on"] = r.message; null }
            BirthDateResult.Empty -> null
        }
        val next = if (s.type.hasNextDate) {
            when (val r = Validators.parseNextDate(s.nextDueOn, occurred)) {
                is BirthDateResult.Valid -> r.date
                is BirthDateResult.Invalid -> { errors["next_due_on"] = r.message; null }
                BirthDateResult.Empty -> null
            }
        } else {
            null
        }
        val weight = Validators.parseWeight(s.weight).getOrElse { errors["weight_kg"] = it.message.orEmpty(); null }
        if (s.type == EntryType.WEIGHT && weight == null && "weight_kg" !in errors) {
            errors["weight_kg"] = "Indica el peso en kilogramos."
        }
        if (isCorrection && s.reason.trim().length < 5) errors["reason"] = "Explica brevemente el motivo."
        if (errors.isNotEmpty() || occurred == null) {
            _ui.update { it.copy(errors = errors) }
            return
        }

        val form = EntryForm(
            type = s.type,
            title = s.title,
            occurredOn = occurred,
            nextDueOn = next,
            notes = s.notes,
            weightKg = weight,
            lotNumber = if (s.type == EntryType.VACCINE) s.lotNumber else "",
            isActive = if (s.type.hasActiveFlag) s.isActive else true,
        )
        _ui.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = if (correctingId != null) {
                repository.correct(target, correctingId, form, s.reason)
            } else {
                repository.create(target, form)
            }
            when (result) {
                is AppResult.Success -> _ui.update { it.copy(saving = false, saved = true) }
                is AppResult.Failure -> _ui.update {
                    it.copy(saving = false, errorMessage = result.error.message, errors = result.error.fieldErrors)
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { EntryFormViewModel(appContainer().clinicalRecordRepository, createSavedStateHandle()) }
        }
    }
}
