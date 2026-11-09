@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.clinics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.ClinicRepository
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.Clinic
import cl.pettech.app.ui.appContainer
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.FullScreenLoading
import cl.pettech.app.ui.components.InfoBanner
import cl.pettech.app.ui.components.KeyValueRow
import cl.pettech.app.ui.components.PetTechTopBar
import cl.pettech.app.ui.components.SectionCard
import cl.pettech.app.ui.components.openDialer
import cl.pettech.app.ui.components.openDirections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ClinicDetailUiState(val loading: Boolean = true, val clinic: Clinic? = null, val errorMessage: String? = null)

class ClinicDetailViewModel(private val repository: ClinicRepository, handle: SavedStateHandle) : ViewModel() {
    private val clinicId: String = checkNotNull(handle.get<String>("clinicId"))
    private val _ui = MutableStateFlow(ClinicDetailUiState())
    val ui: StateFlow<ClinicDetailUiState> = _ui.asStateFlow()

    fun load() {
        _ui.value = _ui.value.copy(loading = true, errorMessage = null)
        viewModelScope.launch {
            _ui.value = when (val r = repository.get(clinicId)) {
                is AppResult.Success -> ClinicDetailUiState(loading = false, clinic = r.data)
                is AppResult.Failure -> _ui.value.copy(loading = false, errorMessage = r.error.message)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ClinicDetailViewModel(appContainer().clinicRepository, createSavedStateHandle()) }
        }
    }
}

/** Ficha del establecimiento (HU-09): horario, servicios, urgencias y contacto, con origen y fecha del dato. */
@Composable
fun ClinicDetailScreen(
    onBack: () -> Unit,
    viewModel: ClinicDetailViewModel = viewModel(factory = ClinicDetailViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(topBar = { PetTechTopBar(title = "Ficha de la veterinaria", onBack = onBack) }) { padding ->
        val clinic = ui.clinic
        when {
            ui.loading && clinic == null -> Column(Modifier.padding(padding)) { FullScreenLoading() }
            clinic == null -> Column(Modifier.padding(padding).padding(20.dp)) {
                ErrorBanner(ui.errorMessage ?: "No se pudo cargar la ficha.")
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(clinic.name, style = MaterialTheme.typography.headlineSmall)
                ClinicTags(clinic)
                Text("${clinic.address}, ${clinic.commune}", style = MaterialTheme.typography.bodyMedium)
                VerificationLine(clinic)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { openDirections(context, clinic.latitude, clinic.longitude, clinic.name) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("Cómo llegar") }
                    if (clinic.phone.isNotBlank()) {
                        OutlinedButton(
                            onClick = { openDialer(context, clinic.phone) },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) { Text("Llamar") }
                    }
                }

                SectionCard("Horario declarado") {
                    Text(clinic.todayHours, style = MaterialTheme.typography.bodyMedium)
                    if (clinic.emergency24h) {
                        Text("Declara atención las 24 horas.", style = MaterialTheme.typography.bodyMedium)
                    } else if (clinic.openingHours.isEmpty()) {
                        Text("Sin horario registrado.", style = MaterialTheme.typography.bodyMedium)
                    }
                    clinic.openingHours.forEach { h -> KeyValueRow(h.weekdayLabel, "${h.opensAt} – ${h.closesAt}") }
                }
                SectionCard("Servicios declarados") {
                    if (clinic.services.isEmpty()) Text("Sin información.", style = MaterialTheme.typography.bodyMedium)
                    clinic.services.forEach { Text("• ${it.label}", style = MaterialTheme.typography.bodyMedium) }
                    KeyValueRow(
                        "Urgencias",
                        when {
                            clinic.emergency24h -> "Sí, 24 horas (declarado)"
                            clinic.attendsEmergencies -> "Sí, en su horario (declarado)"
                            else -> "No declara"
                        },
                    )
                }
                InfoBanner(
                    "\"Abierto\" se calcula con el horario declarado y la hora actual: no confirma que estén " +
                        "atendiendo ni que haya horas disponibles. Ante una urgencia, llama antes de ir.",
                )
            }
        }
    }
}
