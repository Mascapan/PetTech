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
