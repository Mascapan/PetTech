package cl.pettech.app.data.repository

import cl.pettech.app.data.remote.AccessRequestCreate
import cl.pettech.app.data.remote.ApiErrorParser
import cl.pettech.app.data.remote.ApiService
import cl.pettech.app.data.remote.safeApiCall
import cl.pettech.app.data.remote.toDomain
import cl.pettech.app.domain.model.AccessRequest
import cl.pettech.app.domain.model.AccessStatus
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.AuditEvent

/** Solicitudes de acceso por código PetTech, decisión del tutor y bitácora. */
class AccessRepository(
    private val api: ApiService,
    private val errors: ApiErrorParser,
) {
    suspend fun list(status: AccessStatus? = null, petId: String? = null): AppResult<List<AccessRequest>> =
        safeApiCall(errors) { api.accessRequests(status?.name, petId).map { it.toDomain() } }

    suspend fun request(publicCode: String, reason: String): AppResult<AccessRequest> = safeApiCall(errors) {
        api.requestAccess(AccessRequestCreate(publicCode.trim(), reason.trim())).toDomain()
    }

    suspend fun approve(id: String): AppResult<AccessRequest> = safeApiCall(errors) { api.approveAccess(id).toDomain() }

    suspend fun reject(id: String): AppResult<AccessRequest> = safeApiCall(errors) { api.rejectAccess(id).toDomain() }

    suspend fun revoke(id: String): AppResult<AccessRequest> = safeApiCall(errors) { api.revokeAccess(id).toDomain() }

    suspend fun auditLog(petId: String): AppResult<List<AuditEvent>> = safeApiCall(errors) {
        api.petAuditLog(petId).map { it.toDomain() }
    }
}
