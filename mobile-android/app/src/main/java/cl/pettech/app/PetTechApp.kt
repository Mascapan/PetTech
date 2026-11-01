package cl.pettech.app

import android.app.Application
import cl.pettech.app.di.AppContainer

/** Punto de entrada de la aplicación: crea el contenedor de dependencias una sola vez. */
class PetTechApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
