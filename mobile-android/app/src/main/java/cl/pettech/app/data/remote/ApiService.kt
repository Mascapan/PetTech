package cl.pettech.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Contrato de la API REST v1 (rutas relativas a .../api/v1/). Ver docs/api/API.md. */
interface ApiService {

    @POST("auth/register/")
    suspend fun register(@Body body: RegisterRequest): AuthResponseDto

    @POST("auth/login/")
    suspend fun login(@Body body: LoginRequest): AuthResponseDto

    @POST("auth/logout/")
    suspend fun logout(): Response<Unit>

    @POST("auth/account-deletion/")
    suspend fun requestAccountDeletion(): MessageDto

    @GET("auth/me/")
    suspend fun me(): UserDto

    @GET("pets/")
    suspend fun listPets(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): PageDto<PetDto>

    @POST("pets/")
    suspend fun createPet(@Body body: PetWriteRequest): PetDto

    @GET("pets/{id}/")
    suspend fun getPet(@Path("id") id: String): PetDto

    @PATCH("pets/{id}/")
    suspend fun updatePet(@Path("id") id: String, @Body body: PetWriteRequest): PetDto

    @DELETE("pets/{id}/")
    suspend fun deletePet(@Path("id") id: String): Response<Unit>

    // ---------- Recuperación de contraseña (HU-01) ----------

    @POST("auth/password-reset/")
    suspend fun requestPasswordReset(@Body body: PasswordResetRequest): MessageDto

    @POST("auth/password-reset/confirm/")
    suspend fun confirmPasswordReset(@Body body: PasswordResetConfirmRequest): MessageDto

    // ---------- Ficha clínica: tutor (titular) ----------

    @GET("pets/{id}/clinical-record/")
    suspend fun ownerRecord(@Path("id") petId: String, @Query("type") type: String? = null): ClinicalRecordDto

    @POST("pets/{id}/clinical-record/entries/")
    suspend fun ownerCreateEntry(@Path("id") petId: String, @Body body: EntryWriteRequest): ClinicalEntryDto

    @POST("pets/{id}/clinical-record/entries/{entryId}/correct/")
    suspend fun ownerCorrectEntry(
        @Path("id") petId: String,
        @Path("entryId") entryId: String,
        @Body body: EntryWriteRequest,
    ): ClinicalEntryDto

    // ---------- Ficha clínica: profesional autorizado (por código PetTech) ----------

    @GET("professional/me/")
    suspend fun professionalProfile(): ProfessionalProfileDto

    // ---------- Fotografías de la ficha (DEC-031) ----------

    @Multipart
    @POST("pets/{id}/clinical-record/attachments/")
    suspend fun ownerUploadAttachment(
        @Path("id") petId: String,
        @Part file: MultipartBody.Part,
        @Part("kind") kind: RequestBody,
        @Part("entry") entry: RequestBody?,
    ): AttachmentDto

    @GET("pets/{id}/clinical-record/attachments/{attId}/")
    suspend fun ownerAttachment(@Path("id") petId: String, @Path("attId") attachmentId: String): ResponseBody

    @DELETE("pets/{id}/clinical-record/attachments/{attId}/")
    suspend fun ownerDeleteAttachment(@Path("id") petId: String, @Path("attId") attachmentId: String): Response<Unit>

    @GET("professional/pets/{code}/clinical-record/attachments/{attId}/")
    suspend fun professionalAttachment(@Path("code") code: String, @Path("attId") attachmentId: String): ResponseBody

    @GET("professional/pets/{code}/clinical-record/")
    suspend fun professionalRecord(@Path("code") code: String, @Query("type") type: String? = null): ClinicalRecordDto

    @POST("professional/pets/{code}/clinical-record/entries/")
    suspend fun professionalCreateEntry(@Path("code") code: String, @Body body: EntryWriteRequest): ClinicalEntryDto

    @POST("professional/pets/{code}/clinical-record/entries/{entryId}/correct/")
    suspend fun professionalCorrectEntry(
        @Path("code") code: String,
        @Path("entryId") entryId: String,
        @Body body: EntryWriteRequest,
    ): ClinicalEntryDto

    // ---------- Solicitudes de acceso y bitácora ----------

    @GET("access/requests/")
    suspend fun accessRequests(
        @Query("status") status: String? = null,
        @Query("pet") petId: String? = null,
    ): List<AccessRequestDto>

    @POST("access/requests/")
    suspend fun requestAccess(@Body body: AccessRequestCreate): AccessRequestDto

    @POST("access/requests/{id}/approve/")
    suspend fun approveAccess(@Path("id") id: String): AccessRequestDto

    @POST("access/requests/{id}/reject/")
    suspend fun rejectAccess(@Path("id") id: String): AccessRequestDto

    @POST("access/requests/{id}/revoke/")
    suspend fun revokeAccess(@Path("id") id: String): AccessRequestDto

    @GET("access/pets/{id}/log/")
    suspend fun petAuditLog(@Path("id") petId: String): List<AuditEventDto>

    // ---------- Establecimientos veterinarios ----------

    @GET("clinics/")
    suspend fun searchClinics(
        @Query("lat") lat: Double?,
        @Query("lng") lng: Double?,
        @Query("radius_km") radiusKm: Int,
        @Query("emergency") emergency: Boolean?,
        @Query("open_now") openNow: Boolean?,
        @Query("service") service: String?,
        @Query("network") network: Boolean?,
    ): ClinicSearchDto

    @GET("clinics/{id}/")
    suspend fun clinic(@Path("id") id: String): ClinicDto

    @GET("clinics/services/")
    suspend fun clinicServices(): List<ServiceDto>
}
