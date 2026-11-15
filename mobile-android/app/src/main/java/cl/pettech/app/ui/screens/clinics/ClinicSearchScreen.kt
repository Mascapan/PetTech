@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package cl.pettech.app.ui.screens.clinics

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.pettech.app.domain.model.Clinic
import cl.pettech.app.domain.validation.Validators
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.InfoBanner
import cl.pettech.app.ui.components.PetTechTopBar
import cl.pettech.app.ui.components.StatusTag
import cl.pettech.app.ui.components.TagColors
import cl.pettech.app.ui.components.openDirections
import java.util.Locale

/**
 * Vista 5 del boceto: al presionar "Buscar veterinaria" se muestra una lista y un mapa (GPS),
 * priorizando las veterinarias que atienden a la hora de la consulta según su horario declarado,
 * con la opción de iniciar la ruta en la app de navegación del teléfono.
 */
@Composable
fun ClinicSearchScreen(
    onBack: () -> Unit,
    onOpenClinic: (String) -> Unit,
    viewModel: ClinicSearchViewModel = viewModel(factory = ClinicSearchViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionResult(it.values.any { granted -> granted })
    }

    // El permiso se pide solo al entrar a la búsqueda (HU-07), no al abrir la app.
    LaunchedEffect(Unit) {
        if (viewModel.needsInitialLocation) {
            if (viewModel.hasLocationPermission()) {
                viewModel.useMyLocation()
            } else {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                )
            }
        }
    }

    Scaffold(topBar = { PetTechTopBar(title = "Buscar veterinaria", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Filters(ui = ui, viewModel = viewModel, onAskLocation = {
                if (viewModel.hasLocationPermission()) {
                    viewModel.useMyLocation()
                } else {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                    )
                }
            })
            if (ui.loading || ui.locating) LinearProgressIndicator(Modifier.fillMaxWidth())

            ClinicsMap(
                point = ui.point,
                clinics = ui.clinics,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                onLongPress = { lat, lng ->
                    selectedId = null
                    viewModel.setMapPoint(lat, lng)
                },
            )

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ui.note?.let { item { InfoBanner(it) } }
                ui.errorMessage?.let { item { ErrorBanner(it) } }
                ui.clinics.firstOrNull { it.id == selectedId }?.let { selected ->
                    item(key = "selected") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    "Seleccionada en el mapa",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { selectedId = null }) { Text("Cerrar") }
                            }
                            ClinicCard(
                                clinic = selected,
                                onOpen = { onOpenClinic(selected.id) },
                                onDirections = {
                                    openDirections(context, selected.latitude, selected.longitude, selected.name)
                                },
                            )
                        }
                    }
                }
                if (ui.clinics.isNotEmpty()) {
                    item(key = "summary") {
                        val network = ui.clinics.count { it.inPetTechNetwork }
                        Text(
                            "${ui.clinics.size} veterinarias · $network en la Red PetTech",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (ui.searched && ui.clinics.isEmpty() && !ui.loading) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "No encontramos veterinarias con estos filtros en un radio de ${ui.filters.radiusKm} km.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "Prueba ampliar el radio, quitar filtros, buscar desde el centro de Chillán o mantener presionado otro punto del mapa.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = { viewModel.useChillan() }) { Text("Buscar desde el centro de Chillán") }
                        }
                    }
                }
                items(ui.clinics, key = { it.id }) { clinic ->
                    ClinicCard(
                        clinic = clinic,
                        onOpen = { onOpenClinic(clinic.id) },
                        onDirections = { openDirections(context, clinic.latitude, clinic.longitude, clinic.name) },
                    )
                }
                if (ui.disclaimer.isNotBlank() && ui.clinics.isNotEmpty()) {
                    item {
                        Text(
                            ui.disclaimer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(ui: ClinicSearchUiState, viewModel: ClinicSearchViewModel, onAskLocation: () -> Unit) {
    var servicesOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Buscar desde", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = ui.origin == SearchOrigin.MY_LOCATION,
                onClick = onAskLocation,
                label = { Text(SearchOrigin.MY_LOCATION.label) },
            )
            FilterChip(
                selected = ui.origin == SearchOrigin.CHILLAN,
                onClick = { viewModel.useChillan() },
                label = { Text(SearchOrigin.CHILLAN.label) },
            )
            if (ui.origin == SearchOrigin.MAP_POINT) {
                FilterChip(selected = true, onClick = {}, label = { Text(SearchOrigin.MAP_POINT.label) })
            }
        }
        Text("Filtros", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = ui.filters.networkOnly,
                onClick = viewModel::toggleNetworkOnly,
                label = { Text("✓ Red PetTech") },
            )
            FilterChip(
                selected = ui.filters.openNow,
                onClick = viewModel::toggleOpenNow,
                label = { Text("Abierto ahora") },
            )
            FilterChip(
                selected = ui.filters.emergency,
                onClick = viewModel::toggleEmergency,
                label = { Text("Urgencias") },
            )
            Box {
                FilterChip(
                    selected = ui.filters.service != null,
                    onClick = { servicesOpen = true },
                    label = {
                        Text(ui.services.firstOrNull { it.code == ui.filters.service }?.label ?: "Servicio ▾")
                    },
                )
                DropdownMenu(expanded = servicesOpen, onDismissRequest = { servicesOpen = false }) {
                    DropdownMenuItem(text = { Text("Todos los servicios") }, onClick = {
                        servicesOpen = false
                        viewModel.setService(null)
                    })
                    ui.services.forEach { service ->
                        DropdownMenuItem(text = { Text(service.label) }, onClick = {
                            servicesOpen = false
                            viewModel.setService(service.code)
                        })
                    }
                }
            }
            listOf(5, 10, 25).forEach { km ->
                FilterChip(
                    selected = ui.filters.radiusKm == km,
                    onClick = { viewModel.setRadius(km) },
                    label = { Text("$km km") },
                )
            }
        }
    }
}

@Composable
fun ClinicCard(clinic: Clinic, onOpen: () -> Unit, onDirections: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(clinic.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                clinic.distanceKm?.let {
                    Text(formatDistance(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            ClinicTags(clinic)
            Text(clinic.todayHours, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${clinic.address}${if (clinic.commune.isNotBlank()) ", ${clinic.commune}" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VerificationLine(clinic)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Ver ficha") }
                Button(onClick = onDirections, modifier = Modifier.weight(1f)) { Text("Cómo llegar") }
            }
        }
    }
}

@Composable
fun ClinicTags(clinic: Clinic) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (clinic.isOpenNow) {
            StatusTag("Abierto según horario", TagColors.GreenBg, TagColors.Green)
        } else {
            StatusTag("Cerrado según horario", TagColors.GreyBg, TagColors.Grey)
        }
        if (clinic.emergency24h) {
            StatusTag("Urgencias 24 h", TagColors.RedBg, TagColors.Red)
        } else if (clinic.attendsEmergencies) {
            StatusTag("Atiende urgencias", TagColors.RedBg, TagColors.Red)
        }
        if (clinic.inPetTechNetwork) {
            StatusTag("✓ Red PetTech", TagColors.TealBg, TagColors.Teal)
        } else {
            StatusTag("Directorio · no afiliada", TagColors.GreyBg, TagColors.Grey)
        }
    }
}

/** Origen y fecha de verificación del dato (HU-09); destaca la información antigua. */
@Composable
fun VerificationLine(clinic: Clinic) {
    val date = clinic.verifiedAt?.let { Validators.formatDateTime(it).substringBefore(' ') } ?: "sin fecha"
    Text(
        "${clinic.infoOriginLabel} · verificado el $date" + if (clinic.isStale) " · información antigua" else "",
        style = MaterialTheme.typography.bodySmall,
        color = if (clinic.isStale || !clinic.confirmedByClinic) TagColors.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatDistance(km: Double): String =
    if (km < 1) "${(km * 1000).toInt()} m" else String.format(Locale("es", "CL"), "%.1f km", km)
