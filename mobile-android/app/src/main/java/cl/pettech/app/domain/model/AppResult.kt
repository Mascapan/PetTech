package cl.pettech.app.domain.model

/** Resultado de una operación: éxito con datos o fallo con un error presentable al usuario. */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

data class AppError(
    val message: String,
    /** Mensajes por campo devueltos por la API (clave = nombre del campo en la API). */
    val fieldErrors: Map<String, String> = emptyMap(),
    val kind: Kind = Kind.UNKNOWN,
) {
    enum class Kind { NETWORK, VALIDATION, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, RATE_LIMITED, SERVER, UNKNOWN }
}
