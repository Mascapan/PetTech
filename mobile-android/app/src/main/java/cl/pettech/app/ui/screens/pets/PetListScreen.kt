@file:OptIn(ExperimentalMaterial3Api::class)

package cl.pettech.app.ui.screens.pets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.pettech.app.domain.model.Pet
import cl.pettech.app.ui.components.ErrorBanner
import cl.pettech.app.ui.components.FullScreenLoading
import cl.pettech.app.ui.components.PetTechLogo
import cl.pettech.app.ui.components.PetTechTopBar

@Composable
fun PetListScreen(
    onBack: () -> Unit,
    onAddPet: () -> Unit,
    onOpenPet: (String) -> Unit,
    viewModel: PetListViewModel = viewModel(factory = PetListViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // Recarga al volver a la pantalla (p. ej., después de agregar o editar una mascota).
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            PetTechTopBar(
                title = "Mis mascotas",
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPet,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Agregar mascota") },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                ui.loading && ui.pets.isEmpty() -> FullScreenLoading("Cargando tus mascotas…")
                ui.errorMessage != null && ui.pets.isEmpty() -> Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ErrorBanner(ui.errorMessage ?: "")
                    Button(onClick = viewModel::load) { Text("Reintentar") }
                }
                ui.pets.isEmpty() -> EmptyPets(onAddPet)
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ui.errorMessage?.let { message -> item { ErrorBanner(message) } }
                    items(ui.pets, key = { it.id }) { pet ->
                        PetCard(pet = pet, onClick = { onOpenPet(pet.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPets(onAddPet: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PetTechLogo(size = 80.dp)
        Spacer(Modifier.size(16.dp))
        Text("Aún no registras mascotas", style = MaterialTheme.typography.titleMedium)
        Text(
            "Agrega tu primera mascota para generar su carnet digital y su código PetTech.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))
        Button(onClick = onAddPet) { Text("Agregar mascota") }
    }
}

@Composable
private fun PetCard(pet: Pet, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    pet.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(pet.name, style = MaterialTheme.typography.titleMedium)
                val details = listOf(pet.speciesLabel, pet.sexLabel, pet.breed).filter { it.isNotBlank() }
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    pet.publicCode,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
