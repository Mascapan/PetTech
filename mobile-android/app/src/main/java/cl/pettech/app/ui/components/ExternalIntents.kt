package cl.pettech.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Abre la ruta hacia un establecimiento en la aplicación de navegación del teléfono (HU-10).
 * PetTech no calcula rutas: delega en Google Maps u otra app compatible con "geo:".
 */
fun openDirections(context: Context, latitude: Double, longitude: Double, label: String) {
    val attempts = listOf(
        Uri.parse("google.navigation:q=$latitude,$longitude"),
        Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})"),
        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude"),
    )
    for (uri in attempts) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // Se intenta la siguiente alternativa.
        }
    }
}

/** Abre el marcador telefónico con el número (no llama directamente; no requiere permisos). */
fun openDialer(context: Context, phone: String) {
    val digits = phone.filter { it.isDigit() || it == '+' }
    if (digits.isEmpty()) return
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Dispositivo sin marcador (p. ej., tablet sin telefonía).
    }
}

/** URL pública del servidor (misma base que la API): política de privacidad y eliminación de cuenta. */
fun webUrl(path: String): String = cl.pettech.app.BuildConfig.API_BASE_URL.substringBefore("api/v1/") + path

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Sin navegador instalado.
    }
}
