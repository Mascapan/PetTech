package cl.pettech.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.pettech.app.domain.model.User
import cl.pettech.app.ui.components.openUrl
import cl.pettech.app.ui.components.webUrl

/**
 * Menú "Mi cuenta": política de privacidad, solicitud de eliminación de la cuenta (requisito de
 * Google Play y derecho del titular, Ley 21.719) y cierre de sesión.
 */
@Composable
fun AccountDialog(
    user: User?,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onRequestDeletion: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDeletion by rememberSaveable { mutableStateOf(false) }

    if (confirmDeletion) {
        AlertDialog(
            onDismissRequest = { confirmDeletion = false },
            title = { Text("¿Solicitar la eliminación de tu cuenta?") },
            text = {
                Text(
                    "Se cerrará tu sesión y el equipo PetTech te escribirá para confirmar. Se eliminarán tu cuenta, " +
                        "tus mascotas y los antecedentes que declaraste. Los registros hechos por veterinarias se " +
                        "desvinculan de tus datos personales.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeletion = false
                    onRequestDeletion()
                }) { Text("Solicitar eliminación", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeletion = false }) { Text("Cancelar") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mi cuenta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user?.fullName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(Modifier.fillMaxWidth())
                TextButton(onClick = { openUrl(context, webUrl("privacidad/")) }) { Text("Política de privacidad") }
                TextButton(onClick = { confirmDeletion = true }) {
                    Text("Eliminar mi cuenta", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onLogout) { Text("Cerrar sesión") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Volver") } },
    )
}
