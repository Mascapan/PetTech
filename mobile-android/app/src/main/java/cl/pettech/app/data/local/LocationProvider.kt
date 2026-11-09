package cl.pettech.app.data.local

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Ubicación aproximada en primer plano, solo al buscar (HU-07, RNF-03).
 * No se almacena ni se envía fuera de la consulta de búsqueda.
 */
class LocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // se verifica con hasPermission()
    suspend fun current(): GeoPoint? {
        if (!hasPermission()) return null
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<GeoPoint?> { cont ->
                val cancellation = CancellationTokenSource()
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                    .addOnSuccessListener { location ->
                        if (cont.isActive) cont.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
                    }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { cancellation.cancel() }
            }
        }
    }

    companion object {
        /** Plaza de Armas de Chillán: punto de referencia cuando no hay permiso de ubicación. */
        val CHILLAN_CENTER = GeoPoint(-36.6066, -72.1034)
    }
}
