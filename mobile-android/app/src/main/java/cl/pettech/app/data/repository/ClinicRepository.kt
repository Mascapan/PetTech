package cl.pettech.app.data.repository

import cl.pettech.app.data.remote.ApiErrorParser
import cl.pettech.app.data.remote.ApiService
import cl.pettech.app.data.remote.safeApiCall
import cl.pettech.app.data.remote.toDomain
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.Clinic
import cl.pettech.app.domain.model.ClinicFilters
import cl.pettech.app.domain.model.ClinicSearchResult
import cl.pettech.app.domain.model.ServiceItem

/** Búsqueda de establecimientos. La ubicación se envía solo en la consulta y no se guarda (RNF-03). */
class ClinicRepository(
    private val api: ApiService,
    private val errors: ApiErrorParser,
) {
    suspend fun search(lat: Double?, lng: Double?, filters: ClinicFilters): AppResult<ClinicSearchResult> =
        safeApiCall(errors) {
            val dto = api.searchClinics(
                lat = lat,
                lng = lng,
                radiusKm = filters.radiusKm,
                emergency = filters.emergency.takeIf { it },
                openNow = filters.openNow.takeIf { it },
                service = filters.service,
                network = filters.networkOnly.takeIf { it },
            )
            ClinicSearchResult(dto.results.map { it.toDomain() }, dto.disclaimer)
        }

    suspend fun get(id: String): AppResult<Clinic> = safeApiCall(errors) { api.clinic(id).toDomain() }

    suspend fun services(): AppResult<List<ServiceItem>> = safeApiCall(errors) {
        api.clinicServices().map { ServiceItem(it.code, it.label) }
    }
}
