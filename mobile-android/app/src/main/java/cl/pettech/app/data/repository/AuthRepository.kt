package cl.pettech.app.data.repository

import cl.pettech.app.data.remote.ApiErrorParser
import cl.pettech.app.data.remote.ApiService
import cl.pettech.app.data.remote.LoginRequest
import cl.pettech.app.data.remote.PasswordResetConfirmRequest
import cl.pettech.app.data.remote.PasswordResetRequest
import cl.pettech.app.data.remote.RegisterRequest
import cl.pettech.app.data.remote.safeApiCall
import cl.pettech.app.data.remote.toDomain
import cl.pettech.app.data.session.LogoutReason
import cl.pettech.app.data.session.SessionManager
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.User

class AuthRepository(
    private val api: ApiService,
    private val session: SessionManager,
    private val errors: ApiErrorParser,
) {
    suspend fun login(email: String, password: String): AppResult<User> = safeApiCall(errors) {
        val response = api.login(LoginRequest(email = email.trim(), password = password))
        val user = response.user.toDomain()
        session.start(response.token, response.expiry, user)
        user
    }

    suspend fun register(
        fullName: String,
        email: String,
        password: String,
        acceptPrivacyPolicy: Boolean,
    ): AppResult<User> = safeApiCall(errors) {
        val response = api.register(
            RegisterRequest(
                fullName = fullName.trim(),
                email = email.trim(),
                password = password,
                acceptPrivacyPolicy = acceptPrivacyPolicy,
            ),
        )
        val user = response.user.toDomain()
        session.start(response.token, response.expiry, user)
        user
    }

    /** Revoca el token en el servidor (si hay conexión) y siempre elimina la sesión local. */
    suspend fun logout() {
        safeApiCall(errors) { api.logout() }
        session.end(LogoutReason.USER_REQUEST)
    }

    /** Solicita el código de recuperación (HU-01). El servidor responde igual exista o no el correo. */
    suspend fun requestPasswordReset(email: String): AppResult<String> = safeApiCall(errors) {
        api.requestPasswordReset(PasswordResetRequest(email.trim())).detail
    }

    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): AppResult<String> =
        safeApiCall(errors) {
            api.confirmPasswordReset(PasswordResetConfirmRequest(email.trim(), code.trim(), newPassword)).detail
        }

    /** Solicita eliminar la cuenta (requisito de Google Play). El servidor cierra todas las sesiones. */
    suspend fun requestAccountDeletion(): AppResult<String> =
        safeApiCall(errors) { api.requestAccountDeletion().detail }

    /** Cierra la sesión local (el servidor ya revocó los tokens). */
    suspend fun endLocalSession() {
        session.end(LogoutReason.USER_REQUEST)
    }
}
