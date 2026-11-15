package cl.pettech.app.ui.screens.clinics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.pettech.app.BuildConfig
import cl.pettech.app.data.local.GeoPoint
import cl.pettech.app.data.local.LocationProvider
import cl.pettech.app.domain.model.Clinic
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import org.osmdroid.util.GeoPoint as OsmPoint

private const val NETWORK_COLOR = 0xFF00796B.toInt()
private const val DIRECTORY_COLOR = 0xFF8A9A98.toInt()
private const val USER_COLOR = 0xFF1D4E89.toInt()

/**
 * Mapa OpenStreetMap (osmdroid) con la ubicación de búsqueda y las veterinarias (DEC-030).
 * - Verde azulado con ✓: veterinarias de la Red PetTech. Gris: directorio (no afiliadas).
 * - Tocar un marcador lo selecciona; mantener presionado el mapa busca desde ese punto.
 * No requiere clave ni facturación. La atribución "© OpenStreetMap" es obligatoria y se muestra en el mapa.
 */
@Composable
fun ClinicsMap(
    point: GeoPoint?,
    clinics: List<Clinic>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onLongPress: (Double, Double) -> Unit,
    height: Dp = 280.dp,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val icons = remember { MarkerIcons(context) }
    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            minZoomLevel = 5.0
            maxZoomLevel = 19.0
            val start = point ?: LocationProvider.CHILLAN_CENTER
            controller.setZoom(14.0)
            controller.setCenter(OsmPoint(start.latitude, start.longitude))
        }
    }
    val centered = remember { arrayOfNulls<GeoPoint>(1) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Box(Modifier.fillMaxWidth().height(height)) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxWidth().height(height),
            update = { map ->
                map.overlays.clear()
                map.overlays.add(
                    MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: OsmPoint?): Boolean {
                            currentOnSelect(null)
                            return true
                        }

                        override fun longPressHelper(p: OsmPoint?): Boolean {
                            p?.let { currentOnLongPress(it.latitude, it.longitude) }
                            return true
                        }
                    }),
                )
                // Directorio primero y Red PetTech encima, para que no queden tapadas.
                clinics.sortedBy { it.inPetTechNetwork }.forEach { clinic ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = OsmPoint(clinic.latitude, clinic.longitude)
                            title = clinic.name
                            icon = icons.forClinic(clinic, selected = clinic.id == selectedId)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            setInfoWindow(null)
                            setOnMarkerClickListener { _, _ ->
                                currentOnSelect(clinic.id)
                                true
                            }
                        },
                    )
                }
                point?.let { p ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = OsmPoint(p.latitude, p.longitude)
                            title = "Punto de búsqueda"
                            icon = icons.user
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            setInfoWindow(null)
                        },
                    )
                    if (centered[0] != p) {
                        centered[0] = p
                        map.controller.animateTo(OsmPoint(p.latitude, p.longitude))
                    }
                }
                map.overlays.add(CopyrightOverlay(map.context))
                map.invalidate()
            },
        )
    }
    MapLegend()
}

@Composable
private fun MapLegend() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(Color(NETWORK_COLOR), "Red PetTech")
        LegendDot(Color(DIRECTORY_COLOR), "Directorio")
        LegendDot(Color(USER_COLOR), "Búsqueda")
        Text(
            "Mantén presionado para buscar ahí",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Configuración obligatoria de osmdroid: User-Agent propio (política de uso de tiles de OSM) y caché privada. */
private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    config.userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
    val base = File(context.cacheDir, "osmdroid")
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tiles")
}

/** Íconos de marcador dibujados en código (sin recursos externos). */
private class MarkerIcons(private val context: Context) {
    private val density = context.resources.displayMetrics.density
    val user: Drawable = circle(USER_COLOR, 16, null)
    private val network = circle(NETWORK_COLOR, 30, "✓")
    private val networkSelected = circle(NETWORK_COLOR, 40, "✓")
    private val directory = circle(DIRECTORY_COLOR, 22, null)
    private val directorySelected = circle(DIRECTORY_COLOR, 32, null)

    fun forClinic(clinic: Clinic, selected: Boolean): Drawable = when {
        clinic.inPetTechNetwork && selected -> networkSelected
        clinic.inPetTechNetwork -> network
        selected -> directorySelected
        else -> directory
    }

    private fun circle(color: Int, sizeDp: Int, label: String?): Drawable {
        val size = (sizeDp * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawCircle(r, r, r - stroke.strokeWidth, fill)
        canvas.drawCircle(r, r, r - stroke.strokeWidth, stroke)
        if (label != null) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.WHITE
                textSize = size * 0.5f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(label, r, r - (text.descent() + text.ascent()) / 2, text)
        }
        return BitmapDrawable(context.resources, bitmap)
    }
}
