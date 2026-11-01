package cl.pettech.app.domain.validation

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/**
 * Validaciones de formulario en el cliente.
 *
 * Mejoran la experiencia (mensajes inmediatos), pero NO reemplazan la validación del
 * servidor, que es la única autoritativa.
 */
object Validators {
    val ZONE: ZoneId = ZoneId.of("America/Santiago")
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val MICROCHIP = Regex("^\\d{15}$")
    private val INPUT_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT)
    val DISPLAY_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT)
    const val MAX_PET_AGE_YEARS = 40L

    fun email(value: String): String? = when {
        value.isBlank() -> "Ingresa tu correo electrónico."
        !EMAIL.matches(value.trim()) -> "Ingresa un correo válido, por ejemplo nombre@correo.cl."
        else -> null
    }

    fun loginPassword(value: String): String? =
        if (value.isEmpty()) "Ingresa tu contraseña." else null

    fun newPassword(value: String): String? = when {
        value.length < 10 -> "La contraseña debe tener al menos 10 caracteres."
        value.all { it.isDigit() } -> "La contraseña no puede contener solo números."
        else -> null
    }

    fun passwordConfirmation(password: String, confirmation: String): String? =
        if (password != confirmation) "Las contraseñas no coinciden." else null

    fun fullName(value: String): String? =
        if (value.trim().length < 3) "Ingresa tu nombre completo." else null

    fun petName(value: String): String? = when {
        value.isBlank() -> "Ingresa el nombre de la mascota."
        value.trim().length > 60 -> "El nombre admite hasta 60 caracteres."
        else -> null
    }

    fun microchip(value: String): String? =
        if (value.isNotBlank() && !MICROCHIP.matches(value.trim())) {
            "El microchip debe tener exactamente 15 dígitos."
        } else {
            null
        }

    /**
     * Interpreta la fecha de nacimiento escrita como dd-mm-aaaa (o ddmmaaaa).
     * Vacía es válida (la fecha es opcional).
     */
    fun parseBirthDate(input: String, today: LocalDate = LocalDate.now(ZONE)): BirthDateResult {
        val digits = input.filter { it.isDigit() }
        if (input.isBlank()) return BirthDateResult.Empty
        if (digits.length != 8) return BirthDateResult.Invalid("Usa el formato dd-mm-aaaa, por ejemplo 15-03-2021.")
        val date = try {
            LocalDate.parse(digits, INPUT_DATE)
        } catch (e: DateTimeParseException) {
            return BirthDateResult.Invalid("La fecha no existe. Revisa día, mes y año.")
        }
        return when {
            date.isAfter(today) -> BirthDateResult.Invalid("La fecha no puede ser posterior a hoy.")
            date.year < today.year - MAX_PET_AGE_YEARS ->
                BirthDateResult.Invalid("La fecha no puede ser anterior a $MAX_PET_AGE_YEARS años.")
            else -> BirthDateResult.Valid(date)
        }
    }

    fun formatDate(date: LocalDate): String = date.format(DISPLAY_DATE)
}

sealed interface BirthDateResult {
    data object Empty : BirthDateResult
    data class Valid(val date: LocalDate) : BirthDateResult
    data class Invalid(val message: String) : BirthDateResult
}
