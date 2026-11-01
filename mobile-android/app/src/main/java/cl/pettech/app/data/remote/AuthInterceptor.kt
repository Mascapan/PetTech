package cl.pettech.app.data.remote

import cl.pettech.app.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Agrega el token de sesión a cada solicitud y reacciona ante un 401
 * (token expirado o revocado) cerrando la sesión local.
 */
class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionManager.currentToken()
        val builder = chain.request().newBuilder().header("Accept", "application/json")
        if (token != null) {
            builder.header("Authorization", "Token $token")
        }
        val response = chain.proceed(builder.build())
        if (response.code == 401 && token != null) {
            sessionManager.onUnauthorized()
        }
        return response
    }
}
