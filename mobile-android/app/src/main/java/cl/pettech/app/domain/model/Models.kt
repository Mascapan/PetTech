package cl.pettech.app.domain.model

import java.time.LocalDate

/** Rol de la cuenta. La interfaz se adapta al rol, pero los permisos los decide el backend. */
enum class Role {
    TUTOR,
    PROFESSIONAL,
    CLINIC,
    ADMIN,
    UNKNOWN,
    ;

    companion object {
        fun fromApi(value: String): Role = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

data class User(
    val id: String,
    val email: String,
    val fullName: String,
    val role: Role,
) {
    val firstName: String get() = fullName.trim().substringBefore(' ').ifBlank { fullName }
}

enum class Species(val apiValue: String, val label: String) {
    DOG("DOG", "Perro"),
    CAT("CAT", "Gato"),
    ;

    companion object {
        fun fromApi(value: String): Species? = entries.firstOrNull { it.apiValue == value }
    }
}

enum class Sex(val apiValue: String, val label: String) {
    FEMALE("FEMALE", "Hembra"),
    MALE("MALE", "Macho"),
    UNKNOWN("UNKNOWN", "No informado"),
    ;

    companion object {
        fun fromApi(value: String): Sex = entries.firstOrNull { it.apiValue == value } ?: UNKNOWN
    }
}

/** Perfil de la mascota. Todos sus datos son declarados por el tutor (no certificados). */
data class Pet(
    val id: String,
    val publicCode: String,
    val name: String,
    val species: Species?,
    val speciesLabel: String,
    val breed: String,
    val sex: Sex,
    val sexLabel: String,
    val birthDate: LocalDate?,
    val birthDateIsEstimated: Boolean,
    val microchipNumber: String,
    val generalNotes: String,
)

/** Datos que el tutor ingresa al crear o editar una mascota. */
data class PetForm(
    val name: String,
    val species: Species,
    val breed: String,
    val sex: Sex,
    val birthDate: LocalDate?,
    val birthDateIsEstimated: Boolean,
    val microchipNumber: String,
    val generalNotes: String,
)
