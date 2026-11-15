package cl.pettech.app.ui.screens.records

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cl.pettech.app.domain.model.ClinicalEntry
import cl.pettech.app.domain.model.EntryAttachment

/** Elegir origen de una foto: cámara (archivo temporal vía FileProvider) o galería (selector del sistema). */
@Composable
fun rememberPhotoPicker(newCaptureUri: () -> Uri, onPicked: (Uri) -> Unit): () -> Unit {
    var showChooser by remember { mutableStateOf(false) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCapture
        pendingCapture = null
        if (ok && uri != null) onPicked(Uri.parse(uri))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }
    if (showChooser) {
        AlertDialog(
            onDismissRequest = { showChooser = false },
            title = { Text("Agregar fotografía") },
            text = { Text("Toma la foto con buena luz y el texto enfocado (lote, vencimiento, fechas).") },
            confirmButton = {
                TextButton(onClick = {
                    showChooser = false
                    val uri = newCaptureUri()
                    pendingCapture = uri.toString()
                    camera.launch(uri)
                }) { Text("Tomar foto") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChooser = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Elegir de la galería") }
            },
        )
    }
    return { showChooser = true }
}

/** Miniaturas de las fotos de un antecedente y botón para agregar una (solo el tutor). */
@Composable
fun EntryPhotos(
    entry: ClinicalEntry,
    photos: Map<String, ImageBitmap>,
    uploading: Boolean,
    canAdd: Boolean,
    onNeedPhoto: (String) -> Unit,
    onAdd: () -> Unit,
) {
    var viewing by remember { mutableStateOf<EntryAttachment?>(null) }
    if (entry.attachments.isEmpty() && !canAdd) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entry.attachments.forEach { att ->
            LaunchedEffect(att.id) { onNeedPhoto(att.id) }
            Thumbnail(photos[att.id]) { viewing = att }
        }
        if (canAdd) {
            if (uploading) {
                CircularProgressIndicator(Modifier.size(28.dp))
            } else {
                OutlinedButton(onClick = onAdd) {
                    Text(if (entry.attachments.isEmpty()) "📷 Agregar foto de respaldo" else "📷 Otra foto")
                }
            }
        }
    }
    viewing?.let { att ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${att.kindLabel} · ${if (att.byProfessional) "veterinaria" else "tutor"}: ${att.authorName}",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewing = null }) { Text("Cerrar") }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val image = photos[att.id]
                    if (image == null) {
                        CircularProgressIndicator()
                    } else {
                        Image(image, contentDescription = att.kindLabel, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(image: ImageBitmap?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(72.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (image == null) {
            CircularProgressIndicator(Modifier.size(20.dp))
        } else {
            Image(image, contentDescription = "Foto de respaldo", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        }
    }
}
