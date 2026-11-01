package cl.pettech.app.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import cl.pettech.app.PetTechApp
import cl.pettech.app.di.AppContainer

/** Acceso al contenedor de dependencias desde las fábricas de ViewModel. */
fun CreationExtras.appContainer(): AppContainer = (checkNotNull(this[APPLICATION_KEY]) as PetTechApp).container
