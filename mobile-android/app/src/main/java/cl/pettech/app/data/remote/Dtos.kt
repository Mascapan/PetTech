package cl.pettech.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------- Autenticación ----------------

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    @SerialName("full_name") val fullName: String,
    val email: String,
    val password: String,
    @SerialName("accept_privacy_policy") val acceptPrivacyPolicy: Boolean,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    @SerialName("role_label") val roleLabel: String = "",
)

@Serializable
data class AuthResponseDto(
    val token: String,
    val expiry: String? = null,
    val user: UserDto,
)

// ---------------- Mascotas ----------------

@Serializable
data class PetDto(
    val id: String,
    @SerialName("public_code") val publicCode: String,
    val name: String,
    val species: String,
    @SerialName("species_label") val speciesLabel: String = "",
    val breed: String = "",
    val sex: String = "UNKNOWN",
    @SerialName("sex_label") val sexLabel: String = "",
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("birth_date_is_estimated") val birthDateIsEstimated: Boolean = true,
    @SerialName("microchip_number") val microchipNumber: String = "",
    @SerialName("general_notes") val generalNotes: String = "",
    @SerialName("data_origin") val dataOrigin: String = "OWNER_DECLARED",
)

@Serializable
data class PetWriteRequest(
    val name: String,
    val species: String,
    val breed: String,
    val sex: String,
    @SerialName("birth_date") val birthDate: String?,
    @SerialName("birth_date_is_estimated") val birthDateIsEstimated: Boolean,
    @SerialName("microchip_number") val microchipNumber: String,
    @SerialName("general_notes") val generalNotes: String,
)

@Serializable
data class PageDto<T>(
    val count: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T>,
)

// ---------------- Errores (formato uniforme del backend) ----------------

@Serializable
data class ApiErrorEnvelope(val error: ApiErrorBody)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val fields: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class MessageDto(val detail: String = "")

// ---------------- Recuperación de contraseña (HU-01) ----------------

@Serializable
data class PasswordResetRequest(val email: String)

@Serializable
data class PasswordResetConfirmRequest(
    val email: String,
    val code: String,
    @SerialName("new_password") val newPassword: String,
)

// ---------------- Ficha clínica ----------------

@Serializable
data class ClinicalEntryDto(
    val id: String,
    @SerialName("entry_type") val entryType: String,
    @SerialName("entry_type_label") val entryTypeLabel: String = "",
    val title: String,
    @SerialName("occurred_on") val occurredOn: String,
    @SerialName("next_due_on") val nextDueOn: String? = null,
    val notes: String = "",
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("lot_number") val lotNumber: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    val origin: String,
    @SerialName("origin_label") val originLabel: String = "",
    @SerialName("author_name") val authorName: String = "",
    @SerialName("clinic_name") val clinicName: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_correction") val isCorrection: Boolean = false,
    @SerialName("correction_reason") val correctionReason: String = "",
)

/** Cuerpo para registrar o corregir un antecedente. [reason] solo se envía en correcciones. */
@Serializable
data class EntryWriteRequest(
    @SerialName("entry_type") val entryType: String,
    val title: String,
    @SerialName("occurred_on") val occurredOn: String,
    @SerialName("next_due_on") val nextDueOn: String?,
    val notes: String,
    @SerialName("weight_kg") val weightKg: Double?,
    @SerialName("lot_number") val lotNumber: String,
    @SerialName("is_active") val isActive: Boolean,
    val reason: String? = null,
)

@Serializable
data class RecordPetDto(
    @SerialName("public_code") val publicCode: String,
    val name: String,
    @SerialName("species_label") val speciesLabel: String = "",
    val species: String = "",
    val breed: String = "",
    @SerialName("sex_label") val sexLabel: String = "",
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("birth_date_is_estimated") val birthDateIsEstimated: Boolean = true,
    @SerialName("microchip_number") val microchipNumber: String = "",
    @SerialName("general_notes") val generalNotes: String = "",
)

@Serializable
data class UpcomingDto(
    @SerialName("entry_type") val entryType: String,
    val title: String,
    @SerialName("due_on") val dueOn: String,
    val overdue: Boolean = false,
)

@Serializable
data class RecordSummaryDto(
    @SerialName("latest_weight_kg") val latestWeightKg: Double? = null,
    @SerialName("latest_weight_on") val latestWeightOn: String? = null,
    val allergies: List<String> = emptyList(),
    @SerialName("chronic_conditions") val chronicConditions: List<String> = emptyList(),
    @SerialName("active_medications") val activeMedications: List<String> = emptyList(),
    val upcoming: List<UpcomingDto> = emptyList(),
)

@Serializable
data class ClinicalRecordDto(
    val pet: RecordPetDto,
    val summary: RecordSummaryDto,
    val entries: List<ClinicalEntryDto>,
)

@Serializable
data class ProfessionalProfileDto(
    @SerialName("full_name") val fullName: String,
    @SerialName("clinic_name") val clinicName: String,
)

// ---------------- Accesos y bitácora ----------------

@Serializable
data class AccessRequestCreate(
    @SerialName("public_code") val publicCode: String,
    val reason: String,
)

@Serializable
data class AccessRequestDto(
    val id: String,
    val status: String,
    @SerialName("status_label") val statusLabel: String = "",
    @SerialName("pet_id") val petId: String? = null,
    @SerialName("pet_code") val petCode: String,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("clinic_name") val clinicName: String,
    @SerialName("professional_name") val professionalName: String = "",
    val reason: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class AuditEventDto(
    @SerialName("created_at") val createdAt: String,
    @SerialName("actor_name") val actorName: String = "",
    @SerialName("clinic_name") val clinicName: String = "",
    @SerialName("action_label") val actionLabel: String = "",
    val result: String = "SUCCESS",
)

// ---------------- Establecimientos ----------------

@Serializable
data class ServiceDto(val code: String, val label: String)

@Serializable
data class OpeningHourDto(
    @SerialName("weekday_label") val weekdayLabel: String,
    @SerialName("opens_at") val opensAt: String,
    @SerialName("closes_at") val closesAt: String,
)

@Serializable
data class ClinicDto(
    val id: String,
    val name: String,
    val address: String = "",
    val commune: String = "",
    val phone: String = "",
    val latitude: Double,
    val longitude: Double,
    @SerialName("distance_km") val distanceKm: Double? = null,
    @SerialName("attends_emergencies") val attendsEmergencies: Boolean = false,
    @SerialName("emergency_24h") val emergency24h: Boolean = false,
    val services: List<ServiceDto> = emptyList(),
    @SerialName("is_open_now") val isOpenNow: Boolean = false,
    @SerialName("today_hours") val todayHours: String = "",
    @SerialName("opening_hours") val openingHours: List<OpeningHourDto> = emptyList(),
    @SerialName("info_origin") val infoOrigin: String = "",
    @SerialName("info_origin_label") val infoOriginLabel: String = "",
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("is_stale") val isStale: Boolean = false,
    @SerialName("accepts_pettech_records") val acceptsPettechRecords: Boolean = false,
)

@Serializable
data class ClinicSearchDto(
    val count: Int,
    val results: List<ClinicDto>,
    val disclaimer: String = "",
)
