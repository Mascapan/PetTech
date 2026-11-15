package cl.pettech.app.ui.screens.pets

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.local.PetPhotoStore
import cl.pettech.app.data.repository.AccessRepository
import cl.pettech.app.data.repository.PetRepository
import cl.pettech.app.domain.model.AccessStatus
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
    val activeGrants: Int = 0,
    val errorMessage: String? = null,
    val photoMessage: String? = null,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
)

class PetDetailViewModel(
    private val repository: PetRepository,
    private val access: AccessRepository,
    private val photos: PetPhotoStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val petId: String = checkNotNull(savedStateHandle.get<String>("petId"))

    private val _ui = MutableStateFlow(PetDetailUiState())
    val ui: StateFlow<PetDetailUiState> = _ui.asStateFlow()

    fun load() {
        _ui.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.get(petId)) {
                is AppResult.Success -> {
                    _ui.update { it.copy(loading = false, pet = result.data) }
                    runCatching { photos.sync(petId, result.data.photoId) }
                }
                is AppResult.Failure -> _ui.update { it.copy(loading = false, errorMessage = result.error.message) }
            }
            val grants = (access.list(AccessStatus.APPROVED, petId) as? AppResult.Success)?.data?.size ?: 0
            _ui.update { it.copy(activeGrants = grants) }
        }
    }

    fun savePhoto(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(photoMessage = "Subiendo foto…") }
            val ok = photos.save(petId, uri)
            _ui.update {
                it.copy(photoMessage = if (ok) null else "No se pudo subir la foto. Revisa tu conexión o prueba con otra imagen.")
            }
        }
    }

    fun delete() {
        if (_ui.value.deleting) return
        _ui.update { it.copy(deleting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.delete(petId)) {
                is AppResult.Success -> {
                    photos.delete(petId, remote = false) // el servidor ya eliminó sus fotos
                    _ui.update { it.copy(deleting = false, deleted = true) }
                }
                // 409: la mascota tiene registros de veterinarias (no se eliminan; ver DEC-009).
                is AppResult.Failure -> _ui.update { it.copy(deleting = false, errorMessage = result.error.message) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                PetDetailViewModel(c.petRepository, c.accessRepository, c.petPhotoStore, createSavedStateHandle())
            }
        }
    }
}
