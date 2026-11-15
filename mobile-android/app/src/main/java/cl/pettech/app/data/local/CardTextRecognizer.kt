package cl.pettech.app.data.local

import android.graphics.Bitmap
import cl.pettech.app.domain.ocr.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Reconocimiento de texto EN EL TELÉFONO con ML Kit (modelo latino incluido en la app, DEC-032).
 * La foto no sale del dispositivo para el OCR; solo se sube después, si el tutor confirma, como respaldo.
 */
class CardTextRecognizer {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): List<OcrLine> = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    OcrLine(line.text, box.top, box.bottom, box.left)
                }
                if (cont.isActive) cont.resume(lines)
            }
            .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }
}
