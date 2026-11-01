package cl.pettech.app.ui.screens.pets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.PetRepository
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.PetForm
import cl.pettech.app.domain.model.Sex
import cl.pettech.app.domain.model.Species
import cl.pettech.app.domain.validation.BirthDateResult
import cl.pettech.app.domain.validation.Validators
import cl.pettech.app.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetFormUiState(
    val isEdit: Boolean = false,
    val loadingPet: Boolean = false,
    val name: String = "",
    val species: Species? = null,
    val breed: String = "",
    val sex: Sex = Sex.UNKNOWN,
    val birthDateText: String = "",
    val birthDateIsEstimated: Boolean = true,
    val microchip: String = "",
    val notes: String = "",
    val nameError: String? = null,
    val speciesError: String? = null,
    val birthDateError: String? = null,
    val microchipError: String? = null,
    val notesError: String? = null,
    val saving: Boolean = false,
    val errorMessage: String? = null,
    /** Se completa al guardar correctamente; la pantalla navega hacia atrás. */
    val savedPetId: String? = null,
)

class PetFormViewModel(
    private val repository: PetRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val petId: String? = savedStateHandle.get<String>("petId")

    private val _ui = MutableStateFlow(PetFormUiState(isEdit = petId != null, loadingPet = petId != null))
    val ui: StateFlow<PetFormUiState> = _ui.asStateFlow()

    init {
        if (petId != null) loadExisting(petId)
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            when (val result = repository.get(id)) {
                is AppResult.Success -> {
                    val pet = result.data
                    _ui.update {
                        it.copy(
                            loadingPet = false,
                            name = pet.name,
                            species = pet.species,
                            breed = pet.breed,
                            sex = pet.sex,
                            birthDateText = pet.birthDate?.let(Validators::formatDate).orEmpty(),
                            birthDateIsEstimated = pet.birthDateIsEstimated,
                            microchip = pet.microchipNumber,
                            notes = pet.generalNotes,
                        )
                    }
                }
                is AppResult.Failure -> _ui.update { it.copy(loadingPet = false, errorMessage = result.error.message) }
            }
        }
    }

    fun onNameChange(value: String) = _ui.update { it.copy(name = value, nameError = null) }
    fun onSpeciesChange(value: Species) = _ui.update { it.copy(species = value, speciesError = null) }
    fun onBreedChange(value: String) = _ui.update { it.copy(breed = value) }
    fun onSexChange(value: Sex) = _ui.update { it.copy(sex = value) }
    fun onBirthDateChange(value: String) = _ui.update { it.copy(birthDateText = value, birthDateError = null) }
    fun onEstimatedChange(value: Boolean) = _ui.update { it.copy(birthDateIsEstimated = value) }
    fun onMicrochipChange(value: String) =
        _ui.update { it.copy(microchip = value.filter { ch -> ch.isDigit() }.take(15), microchipError = null) }
    fun onNotesChange(value: String) = _ui.update { it.copy(notes = value.take(MAX_NOTES), notesError = null) }

    fun save() {
        val s = _ui.value
        if (s.saving || s.loadingPet) return

        val birth = Validators.parseBirthDate(s.birthDateText)
        val validated = s.copy(
            nameError = Validators.petName(s.name),
            speciesError = if (s.species == null) "Selecciona la especie." else null,
            birthDateError = (birth as? BirthDateResult.Invalid)?.message,
            microchipError = Validators.microchip(s.microchip),
        )
        val species = s.species
        if (species == null || listOf(validated.nameError, validated.birthDateError, validated.microchipError).any { it != null }) {
            _ui.value = validated
            return
        }

        val form = PetForm(
            name = s.name,
            species = species,
            breed = s.breed,
            sex = s.sex,
            birthDate = (birth as? BirthDateResult.Valid)?.date,
            birthDateIsEstimated = s.birthDateIsEstimated,
            microchipNumber = s.microchip,
            generalNotes = s.notes,
        )
        _ui.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = if (petId == null) repository.create(form) else repository.update(petId, form)
            when (result) {
                is AppResult.Success -> _ui.update { it.copy(saving = false, savedPetId = result.data.id) }
                is AppResult.Failure -> {
                    val f = result.error.fieldErrors
                    _ui.update {
                        it.copy(
                            saving = false,
                            errorMessage = result.error.message,
                            nameError = f["name"],
                            speciesError = f["species"],
                            birthDateError = f["birth_date"],
                            microchipError = f["microchip_number"],
                            notesError = f["general_notes"],
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val MAX_NOTES = 1000

        val Factory = viewModelFactory {
            initializer {
                PetFormViewModel(appContainer().petRepository, createSavedStateHandle())
            }
        }
    }
}
