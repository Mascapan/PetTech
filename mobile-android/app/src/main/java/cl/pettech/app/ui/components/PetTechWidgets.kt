package cl.pettech.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.pettech.app.PetTechApp

/**
 * Foto de la mascota (guardada solo en este teléfono) o, si no hay, su inicial.
 * [petId] nulo = vista del profesional, que no recibe fotos.
 */
@Composable
fun PetAvatar(
    petId: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
) {
    val store = (LocalContext.current.applicationContext as PetTechApp).container.petPhotoStore
    val version by store.version.collectAsState()
    val photo by produceState<ImageBitmap?>(initialValue = null, petId, version) {
        value = petId?.let { store.load(it) }
    }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer, shape),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = photo
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Foto de $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                name.trim().take(1).uppercase().ifBlank { "?" },
                style = if (size >= 80.dp) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Etiqueta compacta de estado (abierto, urgencias, origen del dato...). */
@Composable
fun StatusTag(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

object TagColors {
    val GreenBg = Color(0xFFDDF3E4)
    val Green = Color(0xFF1B6B3A)
    val GreyBg = Color(0xFFE8ECEB)
    val Grey = Color(0xFF4A5E5C)
    val RedBg = Color(0xFFFDE2DF)
    val Red = Color(0xFF9B2C20)
    val AmberBg = Color(0xFFFFF1CC)
    val Amber = Color(0xFF7A5300)
    val BlueBg = Color(0xFFDDEBFA)
    val Blue = Color(0xFF1D4E89)
}

/** Tarjeta con título de sección. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** Fila horizontal etiqueta: valor, para resúmenes compactos. */
@Composable
fun KeyValueRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(0.58f))
    }
}
