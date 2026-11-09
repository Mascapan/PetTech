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

    /** Fecha de un evento ya realizado (vacuna, visita...): obligatoria y no posterior a hoy (HU-03). */
    fun parseEventDate(input: String, today: LocalDate = LocalDate.now(ZONE)): BirthDateResult =
        when (val parsed = parseAnyDate(input)) {
            BirthDateResult.Empty -> BirthDateResult.Invalid("Ingresa la fecha (dd-mm-aaaa).")
            is BirthDateResult.Valid ->
                if (parsed.date.isAfter(today)) BirthDateResult.Invalid("La fecha no puede ser posterior a hoy.") else parsed
            is BirthDateResult.Invalid -> parsed
        }

    /** Próxima fecha (opcional): debe ser igual o posterior a la fecha del evento. */
    fun parseNextDate(input: String, occurredOn: LocalDate?): BirthDateResult =
        when (val parsed = parseAnyDate(input)) {
            is BirthDateResult.Valid ->
                if (occurredOn != null && parsed.date.isBefore(occurredOn)) {
                    BirthDateResult.Invalid("Debe ser posterior a la fecha del evento.")
                } else {
                    parsed
                }
            else -> parsed
        }

    private fun parseAnyDate(input: String): BirthDateResult {
        if (input.isBlank()) return BirthDateResult.Empty
        val digits = input.filter { it.isDigit() }
        if (digits.length != 8) return BirthDateResult.Invalid("Usa el formato dd-mm-aaaa.")
        return try {
            BirthDateResult.Valid(LocalDate.parse(digits, INPUT_DATE))
        } catch (e: DateTimeParseException) {
            BirthDateResult.Invalid("La fecha no existe. Revisa día, mes y año.")
        }
    }

    /** Peso en kg (acepta coma decimal). Devuelve null si está vacío. */
    fun parseWeight(input: String): Result<Double?> {
        if (input.isBlank()) return Result.success(null)
        val value = input.trim().replace(',', '.').toDoubleOrNull()
            ?: return Result.failure(IllegalArgumentException("Ingresa un número, por ejemplo 12,5."))
        if (value < 0.05 || value > 150) {
            return Result.failure(IllegalArgumentException("El peso debe estar entre 0,05 y 150 kg."))
        }
        return Result.success(value)
    }

    /** Código PetTech con tolerancia al dictado (espacios, minúsculas, sin guiones). */
    fun normalizePetCode(input: String): String? {
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        var cleaned = input.uppercase().filterNot { it.isWhitespace() || it == '-' }
        if (cleaned.startsWith("PT")) cleaned = cleaned.drop(2)
        cleaned = cleaned.replace('I', '1').replace('L', '1').replace('O', '0')
        if (cleaned.length != 8 || cleaned.any { it !in alphabet }) return null
        return "PT-${cleaned.take(4)}-${cleaned.drop(4)}"
    }

    fun formatDateTime(value: java.time.OffsetDateTime): String =
        value.atZoneSameInstant(ZONE).format(DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm"))
}

sealed interface BirthDateResult {
    data object Empty : BirthDateResult
    data class Valid(val date: LocalDate) : BirthDateResult
    data class Invalid(val message: String) : BirthDateResult
}
