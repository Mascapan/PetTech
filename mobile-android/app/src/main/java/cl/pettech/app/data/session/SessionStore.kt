package cl.pettech.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "pettech_session")

/** Datos mínimos de la sesión que se guardan cifrados en el dispositivo. */
@Serializable
data class StoredSession(
    val token: String,
    val expiry: String?,
    val userId: String,
    val email: String,
    val fullName: String,
    val role: String,
)

/** Persistencia de la sesión: DataStore + cifrado con clave del Android Keystore. */
class SessionStore(
    private val context: Context,
    private val cipher: TokenCipher,
    private val json: Json,
) {
    private val key = stringPreferencesKey("session_v1")

    suspend fun read(): StoredSession? = withContext(Dispatchers.IO) {
        runCatching {
            val encrypted = context.sessionDataStore.data.first()[key] ?: return@runCatching null
            val plain = cipher.decrypt(encrypted) ?: return@runCatching null
            json.decodeFromString(StoredSession.serializer(), plain)
        }.getOrNull()
    }

    suspend fun write(session: StoredSession) = withContext(Dispatchers.IO) {
        val encrypted = cipher.encrypt(json.encodeToString(StoredSession.serializer(), session))
        context.sessionDataStore.edit { prefs -> prefs[key] = encrypted }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { context.sessionDataStore.edit { prefs -> prefs.remove(key) } }
        Unit
    }
}
