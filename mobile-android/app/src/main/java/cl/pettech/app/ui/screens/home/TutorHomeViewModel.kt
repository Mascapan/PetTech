package cl.pettech.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.AccessRepository
import cl.pettech.app.data.repository.PetRepository
import cl.pettech.app.domain.model.AccessStatus
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.Pet
import cl.pettech.app.ui.appContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TutorHomeUiState(
    val loading: Boolean = true,
    val pets: List<Pet> = emptyList(),
    val loaded: Boolean = false,
    val pendingRequests: Int = 0,
    val errorMessage: String? = null,
)

/** Vista resumen "Mis mascotas" (boceto, vista 2) + aviso de solicitudes de acceso pendientes. */
class TutorHomeViewModel(
    private val pets: PetRepository,
    private val access: AccessRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(TutorHomeUiState())
    val ui: StateFlow<TutorHomeUiState> = _ui.asStateFlow()

    fun refresh() {
        _ui.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            val pending = async { access.list(AccessStatus.PENDING) }
            when (val result = pets.list()) {
                is AppResult.Success -> _ui.update { it.copy(pets = result.data, loaded = true) }
                is AppResult.Failure -> _ui.update { it.copy(errorMessage = result.error.message) }
            }
            val count = (pending.await() as? AppResult.Success)?.data?.size ?: 0
            _ui.update { it.copy(loading = false, pendingRequests = count) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                TutorHomeViewModel(c.petRepository, c.accessRepository)
            }
        }
    }
}
