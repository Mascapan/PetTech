package cl.pettech.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Preparación de fotografías en el teléfono (DEC-031):
 * - reduce a un máximo de [MAX_SIDE] px y comprime a JPEG antes de subir (menos datos móviles);
 * - ImageDecoder respeta la orientación EXIF y la imagen re-codificada no conserva metadatos (GPS).
 * El servidor vuelve a validar y normalizar: el teléfono no es una barrera de seguridad.
 */
class PhotoTools(context: Context) {

    private val appContext = context.applicationContext

    /** URI temporal para que la app de cámara guarde la foto (se sobrescribe en cada captura). */
    fun newCaptureUri(): Uri {
        val dir = File(appContext.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "captura.jpg")
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
    }

    suspend fun decodeForOcr(uri: Uri): Bitmap? = withContext(Dispatchers.IO) { decode(uri) }

    suspend fun compressForUpload(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        val bitmap = decode(uri) ?: return@withContext null
        toJpeg(bitmap)
    }

    fun toJpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        out.toByteArray()
    }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = MAX_SIDE.toFloat() / max(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()

    companion object {
        const val MAX_SIDE = 1600
        const val QUALITY = 85

        fun toImageBitmap(bytes: ByteArray): ImageBitmap? =
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
    }
}
