package cl.pettech.app.data.repository

import cl.pettech.app.data.remote.ApiErrorParser
import cl.pettech.app.data.remote.ApiService
import cl.pettech.app.data.remote.safeApiCall
import cl.pettech.app.data.remote.toDomain
import cl.pettech.app.data.remote.toRequest
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.ClinicalEntry
import cl.pettech.app.domain.model.ClinicalRecord
import cl.pettech.app.domain.model.EntryForm
import cl.pettech.app.domain.model.EntryType
import cl.pettech.app.domain.model.RecordTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Ficha clínica. El mismo contrato sirve al tutor (por id de su mascota) y al profesional
 * autorizado (por código PetTech). La autorización la decide siempre el servidor.
 */
class ClinicalRecordRepository(
    private val api: ApiService,
    private val errors: ApiErrorParser,
) {
    suspend fun load(target: RecordTarget, type: EntryType? = null): AppResult<ClinicalRecord> = safeApiCall(errors) {
        when (target) {
            is RecordTarget.Owner -> api.ownerRecord(target.petId, type?.apiValue)
            is RecordTarget.Professional -> api.professionalRecord(target.publicCode, type?.apiValue)
        }.toDomain()
    }

    suspend fun create(target: RecordTarget, form: EntryForm): AppResult<ClinicalEntry> = safeApiCall(errors) {
        when (target) {
            is RecordTarget.Owner -> api.ownerCreateEntry(target.petId, form.toRequest())
            is RecordTarget.Professional -> api.professionalCreateEntry(target.publicCode, form.toRequest())
        }.toDomain()
    }

    /** Crea una nueva versión del antecedente; la anterior se conserva en el servidor. */
    suspend fun correct(target: RecordTarget, entryId: String, form: EntryForm, reason: String): AppResult<ClinicalEntry> =
        safeApiCall(errors) {
            when (target) {
                is RecordTarget.Owner -> api.ownerCorrectEntry(target.petId, entryId, form.toRequest(reason))
                is RecordTarget.Professional ->
                    api.professionalCorrectEntry(target.publicCode, entryId, form.toRequest(reason))
            }.toDomain()
        }

    /** Sube una foto ya comprimida (JPEG). Devuelve el id de la foto. Solo el tutor sube desde la app. */
    suspend fun uploadPhoto(petId: String, jpeg: ByteArray, kind: String, entryId: String?): AppResult<String> =
        safeApiCall(errors) {
            val text = "text/plain".toMediaType()
            val part = MultipartBody.Part.createFormData(
                "file", "foto.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()),
            )
            api.ownerUploadAttachment(petId, part, kind.toRequestBody(text), entryId?.toRequestBody(text)).id
        }

    suspend fun photoBytes(target: RecordTarget, attachmentId: String): AppResult<ByteArray> = safeApiCall(errors) {
        when (target) {
            is RecordTarget.Owner -> api.ownerAttachment(target.petId, attachmentId)
            is RecordTarget.Professional -> api.professionalAttachment(target.publicCode, attachmentId)
        }.use { body -> withContext(Dispatchers.IO) { body.bytes() } }
    }

    suspend fun professionalClinicName(): AppResult<String> = safeApiCall(errors) {
        api.professionalProfile().clinicName
    }
}
