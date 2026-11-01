package cl.pettech.app.data.remote

import cl.pettech.app.domain.model.AppError
import cl.pettech.app.domain.model.AppResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/** Traduce excepciones de red/HTTP a errores comprensibles para el usuario. */
class ApiErrorParser(private val json: Json) {

    fun parse(throwable: Throwable): AppError = when (throwable) {
        is HttpException -> fromHttp(throwable)
        is IOException -> AppError(
            message = "No se pudo conectar con PetTech. Revisa tu conexión a internet o que el servidor esté disponible.",
            kind = AppError.Kind.NETWORK,
        )
        is SerializationException -> AppError(
            message = "La respuesta del servidor no tiene el formato esperado.",
            kind = AppError.Kind.UNKNOWN,
        )
        else -> AppError(message = "Ocurrió un error inesperado. Intenta nuevamente.")
    }

    private fun fromHttp(exception: HttpException): AppError {
        val kind = when (exception.code()) {
            400 -> AppError.Kind.VALIDATION
            401 -> AppError.Kind.UNAUTHORIZED
            403 -> AppError.Kind.FORBIDDEN
            404 -> AppError.Kind.NOT_FOUND
            429 -> AppError.Kind.RATE_LIMITED
            in 500..599 -> AppError.Kind.SERVER
            else -> AppError.Kind.UNKNOWN
        }
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        val parsed = body?.let { raw ->
            runCatching { json.decodeFromString(ApiErrorEnvelope.serializer(), raw) }.getOrNull()
        }
        val message = parsed?.error?.message ?: defaultMessage(kind)
        val fields = parsed?.error?.fields?.mapValues { (_, messages) -> messages.joinToString(" ") }.orEmpty()
        return AppError(message = message, fieldErrors = fields, kind = kind)
    }

    private fun defaultMessage(kind: AppError.Kind): String = when (kind) {
        AppError.Kind.UNAUTHORIZED -> "Tu sesión expiró. Inicia sesión nuevamente."
        AppError.Kind.FORBIDDEN -> "No tienes permiso para realizar esta acción."
        AppError.Kind.NOT_FOUND -> "El recurso solicitado no existe o no tienes acceso a él."
        AppError.Kind.RATE_LIMITED -> "Demasiados intentos. Espera un momento."
        AppError.Kind.SERVER -> "El servidor tuvo un problema. Intenta más tarde."
        else -> "No se pudo completar la operación."
    }
}

/** Ejecuta una llamada remota y convierte cualquier fallo en [AppResult.Failure]. */
suspend fun <T> safeApiCall(parser: ApiErrorParser, block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(parser.parse(e))
    }
