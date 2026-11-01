package cl.pettech.app.ui.screens.pets

import androidx.lifecycle.ViewModel
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

data class PetListUiState(
    val loading: Boolean = true,
    val pets: List<Pet> = emptyList(),
    val errorMessage: String? = null,
)

class PetListViewModel(private val repository: PetRepository) : ViewModel() {
    private val _ui = MutableStateFlow(PetListUiState())
    val ui: StateFlow<PetListUiState> = _ui.asStateFlow()

    fun load() {
        _ui.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.list()) {
                is AppResult.Success -> _ui.update { it.copy(loading = false, pets = result.data) }
                is AppResult.Failure -> _ui.update { it.copy(loading = false, errorMessage = result.error.message) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PetListViewModel(appContainer().petRepository) }
        }
    }
}
