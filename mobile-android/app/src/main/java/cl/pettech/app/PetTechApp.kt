package cl.pettech.app

import android.app.Application
import cl.pettech.app.data.reminders.ReminderWorker
import cl.pettech.app.di.AppContainer

/** Punto de entrada de la aplicación: crea el contenedor de dependencias una sola vez. */
class PetTechApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Recordatorios de vacunas/controles (HU-06): canal + revisión diaria (el trabajo no hace nada sin sesión).
        ReminderWorker.createChannel(this)
        ReminderWorker.schedule(this)
    }
}
