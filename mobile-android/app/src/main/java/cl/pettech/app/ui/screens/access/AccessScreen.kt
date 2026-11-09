@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.access

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.pettech.app.data.repository.AccessRepository
import cl.pettech.app.domain.model.AccessRequest
import cl.pettech.app.domain.model.AccessStatus
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.AuditEvent
import cl.pettech.app.domain.validation.Validators
import cl.pettech.app.ui.appContainer
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.FullScreenLoading
import cl.pettech.app.ui.components.InfoBanner
import cl.pettech.app.ui.components.PetTechTopBar
import cl.pettech.app.ui.components.StatusTag
import cl.pettech.app.ui.components.TagColors
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccessUiState(
    val loading: Boolean = true,
    val requests: List<AccessRequest> = emptyList(),
    val log: List<AuditEvent> = emptyList(),
    val busyId: String? = null,
    val errorMessage: String? = null,
)

/** Solicitudes de acceso de veterinarias: aprobar, rechazar y revocar (HU-17, HU-18) + bitácora (HU-24). */
class AccessViewModel(
    private val repository: AccessRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val petId: String? = savedStateHandle.get<String>("petId")?.takeIf { it.isNotBlank() }

    private val _ui = MutableStateFlow(AccessUiState())
    val ui: StateFlow<AccessUiState> = _ui.asStateFlow()

    fun load() {
        _ui.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            val log = petId?.let { id -> async { repository.auditLog(id) } }
            when (val r = repository.list(petId = petId)) {
                is AppResult.Success -> _ui.update { it.copy(requests = r.data) }
                is AppResult.Failure -> _ui.update { it.copy(errorMessage = r.error.message) }
            }
            val events = (log?.await() as? AppResult.Success)?.data.orEmpty()
            _ui.update { it.copy(loading = false, log = events) }
        }
    }

    fun approve(id: String) = act(id) { repository.approve(id) }
    fun reject(id: String) = act(id) { repository.reject(id) }
    fun revoke(id: String) = act(id) { repository.revoke(id) }

    private fun act(id: String, block: suspend () -> AppResult<AccessRequest>) {
        if (_ui.value.busyId != null) return
        _ui.update { it.copy(busyId = id, errorMessage = null) }
        viewModelScope.launch {
            val r = block()
            _ui.update { it.copy(busyId = null, errorMessage = (r as? AppResult.Failure)?.error?.message) }
            load()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AccessViewModel(appContainer().accessRepository, createSavedStateHandle()) }
        }
    }
}

@Composable
fun AccessScreen(
    onBack: () -> Unit,
    viewModel: AccessViewModel = viewModel(factory = AccessViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }
    val pending = ui.requests.filter { it.status == AccessStatus.PENDING }
    val active = ui.requests.filter { it.status == AccessStatus.APPROVED }
    val past = ui.requests.filter { it.status != AccessStatus.PENDING && it.status != AccessStatus.APPROVED }

    Scaffold(
        topBar = {
            PetTechTopBar(
                title = "Acceso de veterinarias",
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::load) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
                },
            )
        },
    ) { padding ->
        if (ui.loading && ui.requests.isEmpty()) {
            Column(Modifier.padding(padding)) { FullScreenLoading() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ui.errorMessage?.let { item { ErrorBanner(it) } }
            item {
                InfoBanner(
                    "Una veterinaria solo ve la ficha clínica si tú la autorizas. La autorización dura 24 horas " +
                        "y puedes revocarla cuando quieras.",
                )
            }
            item { SectionTitle("Pendientes de tu respuesta") }
            if (pending.isEmpty()) item { EmptyText("No hay solicitudes pendientes.") }
            items(pending, key = { it.id }) { req ->
                RequestCard(req) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.reject(req.id) },
                            enabled = ui.busyId == null,
                            modifier = Modifier.weight(1f),
                        ) { Text("Rechazar") }
                        Button(
                            onClick = { viewModel.approve(req.id) },
                            enabled = ui.busyId == null,
                            modifier = Modifier.weight(1f),
                        ) { Text("Autorizar 24 h") }
                    }
                }
            }
            item { SectionTitle("Autorizaciones vigentes") }
            if (active.isEmpty()) item { EmptyText("Ninguna veterinaria tiene acceso en este momento.") }
            items(active, key = { it.id }) { req ->
                RequestCard(req) {
                    OutlinedButton(
                        onClick = { viewModel.revoke(req.id) },
                        enabled = ui.busyId == null,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Revocar acceso ahora") }
                }
            }
            if (past.isNotEmpty()) {
                item { SectionTitle("Historial") }
                items(past, key = { it.id }) { req -> RequestCard(req) {} }
            }
            if (viewModel.petId != null) {
                item { SectionTitle("Bitácora: quién consultó o registró") }
                if (ui.log.isEmpty()) item { EmptyText("Sin actividad de veterinarias.") }
                items(ui.log) { event -> AuditRow(event) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun EmptyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RequestCard(req: AccessRequest, actions: @Composable () -> Unit) {
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
                req.petName?.let { StatusTag(it, TagColors.BlueBg, TagColors.Blue) }
            }
            Text(req.clinicName, style = MaterialTheme.typography.titleMedium)
            Text("Profesional: ${req.professionalName}", style = MaterialTheme.typography.bodyMedium)
            if (req.reason.isNotBlank()) Text("Motivo: ${req.reason}", style = MaterialTheme.typography.bodyMedium)
            req.createdAt?.let {
                Text("Solicitada: ${Validators.formatDateTime(it)}", style = MaterialTheme.typography.bodySmall)
            }
            if (req.status == AccessStatus.APPROVED) {
                req.expiresAt?.let {
                    Text("Vence: ${Validators.formatDateTime(it)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            actions()
        }
    }
}

@Composable
private fun AuditRow(event: AuditEvent) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "${event.actionLabel}${if (event.denied) " (denegado)" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (event.denied) TagColors.Red else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            listOfNotNull(
                event.actorName.takeIf { it.isNotBlank() },
                event.clinicName.takeIf { it.isNotBlank() },
                event.createdAt?.let { Validators.formatDateTime(it) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
