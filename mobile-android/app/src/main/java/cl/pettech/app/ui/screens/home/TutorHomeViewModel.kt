package cl.pettech.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.PetRepository
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TutorHomeUiState(
    val loading: Boolean = true,
    val petCount: Int? = null,
    val errorMessage: String? = null,
)

class TutorHomeViewModel(private val pets: PetRepository) : ViewModel() {
    private val _ui = MutableStateFlow(TutorHomeUiState())
    val ui: StateFlow<TutorHomeUiState> = _ui.asStateFlow()

    fun refresh() {
        _ui.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = pets.list()) {
                is AppResult.Success -> _ui.update { it.copy(loading = false, petCount = result.data.size) }
                is AppResult.Failure -> _ui.update { it.copy(loading = false, errorMessage = result.error.message) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TutorHomeViewModel(appContainer().petRepository) }
        }
    }
}
