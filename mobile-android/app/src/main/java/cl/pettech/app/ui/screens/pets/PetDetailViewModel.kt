package cl.pettech.app.ui.screens.pets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.PetRepository
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.Pet
import cl.pettech.app.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetDetailUiState(
    val loading: Boolean = true,
    val pet: Pet? = null,
    val errorMessage: String? = null,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
)

class PetDetailViewModel(
    private val repository: PetRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val petId: String = checkNotNull(savedStateHandle.get<String>("petId"))

    private val _ui = MutableStateFlow(PetDetailUiState())
    val ui: StateFlow<PetDetailUiState> = _ui.asStateFlow()

    fun load() {
        _ui.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.get(petId)) {
                is AppResult.Success -> _ui.update { it.copy(loading = false, pet = result.data) }
                is AppResult.Failure -> _ui.update { it.copy(loading = false, errorMessage = result.error.message) }
            }
        }
    }

    fun delete() {
        if (_ui.value.deleting) return
        _ui.update { it.copy(deleting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.delete(petId)) {
                is AppResult.Success -> _ui.update { it.copy(deleting = false, deleted = true) }
                is AppResult.Failure -> _ui.update { it.copy(deleting = false, errorMessage = result.error.message) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                PetDetailViewModel(appContainer().petRepository, createSavedStateHandle())
            }
        }
    }
}
