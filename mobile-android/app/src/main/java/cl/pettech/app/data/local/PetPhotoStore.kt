package cl.pettech.app.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cl.pettech.app.data.remote.ApiService
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * Foto de perfil de la mascota guardada EN EL SERVIDOR (DEC-031), con una copia en caché en el teléfono.
 *
 * - Al elegir una foto se comprime y se sube; el servidor la normaliza (JPEG sin EXIF/GPS).
 * - La caché evita descargarla en cada pantalla; se invalida cuando el servidor informa otra foto (photo_id).
 * - Migración: si el teléfono tiene una foto de la versión anterior (solo local) y el servidor no, se sube.
 */
class PetPhotoStore(
    context: Context,
    private val api: ApiService,
    private val tools: PhotoTools,
) {

    private val dir = File(context.filesDir, "pet_photos")

    /** Cambia cada vez que se guarda o elimina una foto, para refrescar la interfaz. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun fileFor(petId: String): File? {
        // Solo identificadores UUID: evita rutas manipuladas.
        if (!UUID_REGEX.matches(petId)) return null
        return File(dir, "$petId.jpg")
    }

    private fun idFileFor(petId: String): File? = fileFor(petId)?.let { File(dir, "$petId.id") }

    /** Imagen en caché; si no existe, la descarga del servidor (null si la mascota no tiene foto). */
    suspend fun load(petId: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = fileFor(petId) ?: return@withContext null
        if (!file.exists()) {
            val bytes = try {
                api.petPhoto(petId).use { it.bytes() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // 404 (sin foto) o sin conexión: se muestra la inicial.
            } ?: return@withContext null
            dir.mkdirs()
            file.writeBytes(bytes)
        }
        runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
    }

    /**
     * Sincroniza con lo que informa el servidor para la mascota ([serverPhotoId]):
     * descarta la caché si cambió y sube la foto local antigua si el servidor aún no tiene una.
     */
    suspend fun sync(petId: String, serverPhotoId: String?) = withContext(Dispatchers.IO) {
        val file = fileFor(petId) ?: return@withContext
        val idFile = idFileFor(petId) ?: return@withContext
        val cachedId = idFile.takeIf { it.exists() }?.readText()
        when {
            serverPhotoId == null && file.exists() && cachedId == null -> {
                // Foto de la versión anterior de la app (solo en este teléfono): se sube una vez.
                upload(petId, file.readBytes())
            }
            serverPhotoId != null && serverPhotoId != cachedId -> {
                file.delete()
                idFile.writeText(serverPhotoId)
                _version.value += 1
            }
            serverPhotoId == null && cachedId != null -> {
                file.delete()
                idFile.delete()
                _version.value += 1
            }
        }
    }

    /** Comprime, sube y guarda en caché la foto elegida. */
    suspend fun save(petId: String, uri: Uri): Boolean {
        val jpeg = tools.compressForUpload(uri) ?: return false
        return withContext(Dispatchers.IO) { upload(petId, jpeg) }
    }

    private suspend fun upload(petId: String, jpeg: ByteArray): Boolean {
        val file = fileFor(petId) ?: return false
        return try {
            val part = MultipartBody.Part.createFormData("file", "mascota.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            val photoId = api.uploadPetPhoto(petId, part).photoId
            dir.mkdirs()
            file.delete() // se descargará la versión normalizada por el servidor
            idFileFor(petId)?.writeText(photoId)
            _version.value += 1
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun delete(petId: String, remote: Boolean = true) = withContext(Dispatchers.IO) {
        if (remote) {
            try {
                api.deletePetPhoto(petId)
            } catch (e: HttpException) {
                // 404: la mascota ya no existe o no tenía foto.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin conexión: se limpia la caché local igualmente.
            }
        }
        fileFor(petId)?.delete()
        idFileFor(petId)?.delete()
        _version.value += 1
    }

    private companion object {
        val UUID_REGEX = Regex("^[0-9a-fA-F-]{36}$")
    }
}
