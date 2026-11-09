package cl.pettech.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Foto de la mascota guardada SOLO en el almacenamiento interno de este teléfono.
 *
 * Decisión de alcance: subir archivos al servidor corresponde a HU-05 (Should) y requiere
 * almacenamiento de objetos; mientras tanto la foto mejora la identificación visual sin
 * enviar datos a terceros. Queda excluida de respaldos (allowBackup=false).
 */
class PetPhotoStore(context: Context) {

    private val dir = File(context.filesDir, "pet_photos")
    private val resolver = context.contentResolver

    /** Cambia cada vez que se guarda o elimina una foto, para refrescar la interfaz. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun fileFor(petId: String): File? {
        // Solo identificadores UUID: evita rutas manipuladas.
        if (!UUID_REGEX.matches(petId)) return null
        return File(dir, "$petId.jpg")
    }

    suspend fun load(petId: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = fileFor(petId)?.takeIf { it.exists() } ?: return@withContext null
        runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
    }

    /** Copia la imagen elegida, reducida a un máximo de [MAX_SIDE] px. */
    suspend fun save(petId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val file = fileFor(petId) ?: return@withContext false
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@runCatching false
            dir.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
            _version.value += 1
            true
        }.getOrDefault(false)
    }

    fun delete(petId: String) {
        fileFor(petId)?.delete()
        _version.value += 1
    }

    private companion object {
        const val MAX_SIDE = 1024
        val UUID_REGEX = Regex("^[0-9a-fA-F-]{36}$")
    }
}
