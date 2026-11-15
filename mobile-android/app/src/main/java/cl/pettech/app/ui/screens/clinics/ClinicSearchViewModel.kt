package cl.pettech.app.ui.screens.clinics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.local.GeoPoint
import cl.pettech.app.data.local.LocationProvider
import cl.pettech.app.data.repository.ClinicRepository
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.Clinic
import cl.pettech.app.domain.model.ClinicFilters
import cl.pettech.app.domain.model.ServiceItem
import cl.pettech.app.ui.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchOrigin(val label: String) {
    MY_LOCATION("Mi ubicación"),
    CHILLAN("Centro de Chillán"),
    MAP_POINT("Punto en el mapa"),
}

data class ClinicSearchUiState(
    val origin: SearchOrigin? = null,
    val point: GeoPoint? = null,
    val filters: ClinicFilters = ClinicFilters(),
    val services: List<ServiceItem> = emptyList(),
    val clinics: List<Clinic> = emptyList(),
    val disclaimer: String = "",
    val loading: Boolean = false,
    val locating: Boolean = false,
    val note: String? = null,
    val errorMessage: String? = null,
    val searched: Boolean = false,
)

/**
 * Búsqueda de veterinarias (HU-07, HU-08, HU-10). La ubicación se usa solo en la consulta y no se
 * almacena (RNF-03). Si el permiso se rechaza, se busca desde el centro de Chillán y el usuario
 * puede elegir otro punto tocando el mapa.
 */
class ClinicSearchViewModel(
    private val repository: ClinicRepository,
    private val location: LocationProvider,
) : ViewModel() {

    private val _ui = MutableStateFlow(ClinicSearchUiState())
    val ui: StateFlow<ClinicSearchUiState> = _ui.asStateFlow()
    private var searchJob: Job? = null

    val needsInitialLocation: Boolean get() = _ui.value.origin == null

    init {
        viewModelScope.launch {
            (repository.services() as? AppResult.Success)?.let { r -> _ui.update { it.copy(services = r.data) } }
        }
    }

    fun hasLocationPermission(): Boolean = location.hasPermission()

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            useMyLocation()
        } else {
            useChillan("Sin permiso de ubicación: se busca desde el centro de Chillán. Puedes tocar el mapa para elegir otro punto.")
        }
    }

    fun useMyLocation() {
        _ui.update { it.copy(locating = true, note = null) }
        viewModelScope.launch {
            val here = location.current()
            if (here == null) {
                useChillan("No se pudo obtener tu ubicación. Se muestra desde el centro de Chillán.")
            } else {
                _ui.update { it.copy(locating = false, origin = SearchOrigin.MY_LOCATION, point = here) }
                search()
            }
        }
    }

    fun useChillan(note: String? = null) {
        _ui.update {
            it.copy(locating = false, origin = SearchOrigin.CHILLAN, point = LocationProvider.CHILLAN_CENTER, note = note)
        }
        search()
    }

    fun setMapPoint(latitude: Double, longitude: Double) {
        _ui.update { it.copy(origin = SearchOrigin.MAP_POINT, point = GeoPoint(latitude, longitude), note = null) }
        search()
    }

    fun toggleOpenNow() = updateFilters { it.copy(openNow = !it.openNow) }
    fun toggleEmergency() = updateFilters { it.copy(emergency = !it.emergency) }
    fun toggleNetworkOnly() = updateFilters { it.copy(networkOnly = !it.networkOnly) }
    fun setService(code: String?) = updateFilters { it.copy(service = code) }
    fun setRadius(km: Int) = updateFilters { it.copy(radiusKm = km) }

    private fun updateFilters(change: (ClinicFilters) -> ClinicFilters) {
        _ui.update { it.copy(filters = change(it.filters)) }
        search()
    }

    fun search() {
        val state = _ui.value
        val point = state.point ?: return
        searchJob?.cancel()
        _ui.update { it.copy(loading = true, errorMessage = null) }
        searchJob = viewModelScope.launch {
            when (val r = repository.search(point.latitude, point.longitude, state.filters)) {
                is AppResult.Success -> {
                    _ui.update {
                        it.copy(loading = false, clinics = r.data.clinics, disclaimer = r.data.disclaimer, searched = true)
                    }
                    // El piloto cubre Chillán/Ñuble: si la ubicación está lejos (p. ej. el emulador, que por defecto
                    // está en EE. UU.) y no hay resultados, se muestran las veterinarias desde el centro de Chillán.
                    if (r.data.clinics.isEmpty() && state.origin != SearchOrigin.CHILLAN &&
                        distanceKm(point, LocationProvider.CHILLAN_CENTER) > FAR_FROM_PILOT_KM
                    ) {
                        useChillan(
                            "Tu ubicación está fuera de la zona del piloto (Chillán y Ñuble): " +
                                "te mostramos las veterinarias desde el centro de Chillán.",
                        )
                    }
                }
                is AppResult.Failure -> _ui.update { it.copy(loading = false, errorMessage = r.error.message) }
            }
        }
    }

    companion object {
        private const val FAR_FROM_PILOT_KM = 40.0

        /** Distancia aproximada (haversine) en km. */
        fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
            val r = 6371.0
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLng = Math.toRadians(b.longitude - a.longitude)
            val h = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) *
                Math.sin(dLng / 2).let { it * it }
            return 2 * r * Math.asin(Math.sqrt(h))
        }

        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                ClinicSearchViewModel(c.clinicRepository, c.locationProvider)
            }
        }
    }
}
