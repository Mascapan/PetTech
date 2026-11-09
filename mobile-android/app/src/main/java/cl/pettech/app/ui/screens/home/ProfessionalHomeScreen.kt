@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.AccessRepository
import cl.pettech.app.data.repository.ClinicalRecordRepository
import cl.pettech.app.domain.model.AccessRequest
import cl.pettech.app.domain.model.AccessStatus
import cl.pettech.app.domain.model.AppError
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.Role
import cl.pettech.app.domain.validation.Validators
import cl.pettech.app.ui.appContainer
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.FormTextField
import cl.pettech.app.ui.components.InfoBanner
import cl.pettech.app.ui.components.LoadingButton
import cl.pettech.app.ui.components.PetTechTopBar
import cl.pettech.app.ui.components.SectionCard
import cl.pettech.app.ui.components.StatusTag
import cl.pettech.app.ui.components.TagColors
import cl.pettech.app.ui.screens.AccountViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfessionalHomeUiState(
    val clinicName: String? = null,
    val notVerified: Boolean = false,
    val code: String = "",
    val reason: String = "",
    val codeError: String? = null,
    val sending: Boolean = false,
    val sentMessage: String? = null,
    val requests: List<AccessRequest> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Flujo del veterinario (HU-16): ingresa el CÓDIGO PetTech que le entrega el tutor y solicita acceso.
 * Cuando el tutor autoriza, puede abrir la ficha clínica durante 24 h. El código por sí solo no da acceso.
 */
class ProfessionalHomeViewModel(
    private val access: AccessRepository,
    private val records: ClinicalRecordRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ProfessionalHomeUiState())
    val ui: StateFlow<ProfessionalHomeUiState> = _ui.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            when (val profile = records.professionalClinicName()) {
                is AppResult.Success -> _ui.update { it.copy(clinicName = profile.data, notVerified = false) }
                is AppResult.Failure -> _ui.update {
                    it.copy(notVerified = profile.error.kind == AppError.Kind.FORBIDDEN, errorMessage = profile.error.message)
                }
            }
            when (val list = access.list()) {
                is AppResult.Success -> _ui.update { it.copy(requests = list.data) }
                is AppResult.Failure -> if (!_ui.value.notVerified) _ui.update { it.copy(errorMessage = list.error.message) }
            }
        }
    }

    fun onCode(v: String) = _ui.update { it.copy(code = v.uppercase(), codeError = null, sentMessage = null) }
    fun onReason(v: String) = _ui.update { it.copy(reason = v) }

    fun requestAccess() {
        val s = _ui.value
        val normalized = Validators.normalizePetCode(s.code)
        if (normalized == null) {
            _ui.update { it.copy(codeError = "El código tiene el formato PT-XXXX-XXXX.") }
            return
        }
        _ui.update { it.copy(sending = true, errorMessage = null, sentMessage = null) }
        viewModelScope.launch {
            when (val r = access.request(normalized, s.reason)) {
                is AppResult.Success -> {
                    val msg = if (r.data.status == AccessStatus.APPROVED) {
                        "Ya tienes una autorización vigente para ${r.data.petCode}."
                    } else {
                        "Solicitud enviada. El tutor debe autorizarla desde su app."
                    }
                    _ui.update { it.copy(sending = false, code = "", reason = "", sentMessage = msg) }
                    refresh()
                }
                is AppResult.Failure -> _ui.update {
                    it.copy(sending = false, errorMessage = r.error.message, codeError = r.error.fieldErrors["public_code"])
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                ProfessionalHomeViewModel(c.accessRepository, c.clinicalRecordRepository)
            }
        }
    }
}

@Composable
fun ProfessionalHomeScreen(
    onOpenRecord: (String) -> Unit,
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
    viewModel: ProfessionalHomeViewModel = viewModel(factory = ProfessionalHomeViewModel.Factory),
) {
    val user by accountViewModel.user.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Administrador y cuenta de veterinaria trabajan en el portal web, no en la app.
    val isAdmin = user?.role == Role.ADMIN || user?.role == Role.CLINIC

    LifecycleResumeEffect(Unit) {
        if (!isAdmin) viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            PetTechTopBar(
                title = if (isAdmin) "PetTech — Portal web" else "PetTech Profesional",
                actions = {
                    if (!isAdmin) {
                        IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
                    }
                    IconButton(onClick = accountViewModel::logout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar sesión")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("Hola, ${user?.fullName ?: ""}", style = MaterialTheme.typography.headlineSmall)
                    ui.clinicName?.let {
                        Text(
                            "Establecimiento: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (isAdmin) {
                item {
                    InfoBanner(
                        "Esta cuenta se usa en el portal web de PetTech (http://<servidor>/portal/). " +
                            "Desde ahí se administran veterinarias, profesionales y horarios.",
                    )
                }
                return@LazyColumn
            }
            if (ui.notVerified) {
                item {
                    ErrorBanner(
                        "Tu cuenta aún no está vinculada a un establecimiento verificado. " +
                            "Contacta a PetTech para habilitarla.",
                    )
                }
                return@LazyColumn
            }
            item {
                SectionCard("Ingresar código PetTech") {
                    Text(
                        "Pide al tutor el código de su mascota. Al enviarlo, el tutor recibirá la solicitud y decidirá " +
                            "si autoriza a tu establecimiento a ver y registrar en la ficha clínica por 24 horas.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ui.errorMessage?.let { ErrorBanner(it) }
                    ui.sentMessage?.let { InfoBanner(it) }
                    FormTextField(
                        value = ui.code,
                        onValueChange = viewModel::onCode,
                        label = "Código PetTech",
                        placeholder = "PT-XXXX-XXXX",
                        error = ui.codeError,
                    )
                    FormTextField(
                        value = ui.reason,
                        onValueChange = viewModel::onReason,
                        label = "Motivo (opcional)",
                        placeholder = "Ej.: control anual, urgencia",
                    )
                    LoadingButton("Solicitar acceso", ui.sending, viewModel::requestAccess)
                }
            }
            item { Text("Mis solicitudes", style = MaterialTheme.typography.titleMedium) }
            if (ui.requests.isEmpty()) {
                item {
                    Text(
                        "Aún no has solicitado acceso a ninguna ficha.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(ui.requests, key = { it.id }) { req -> ProRequestCard(req) { onOpenRecord(req.petCode) } }
        }
    }
}

@Composable
private fun ProRequestCard(req: AccessRequest, onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (bg, fg) = when (req.status) {
                    AccessStatus.PENDING -> TagColors.AmberBg to TagColors.Amber
                    AccessStatus.APPROVED -> TagColors.GreenBg to TagColors.Green
                    AccessStatus.REJECTED, AccessStatus.REVOKED -> TagColors.RedBg to TagColors.Red
                    else -> TagColors.GreyBg to TagColors.Grey
                }
                StatusTag(req.statusLabel, bg, fg)
            }
            Text(req.petCode, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
            req.petName?.let { Text("Mascota: $it", style = MaterialTheme.typography.bodyMedium) }
            when (req.status) {
                AccessStatus.PENDING -> Text("Esperando autorización del tutor.", style = MaterialTheme.typography.bodySmall)
                AccessStatus.APPROVED -> {
                    req.expiresAt?.let {
                        Text("Autorizado hasta ${Validators.formatDateTime(it)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Abrir ficha clínica") }
                }
                else -> Text("Sin acceso. Puedes solicitarlo nuevamente.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
