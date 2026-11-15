package cl.pettech.app.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import cl.pettech.app.MainActivity
import cl.pettech.app.PetTechApp
import cl.pettech.app.R
import cl.pettech.app.data.session.SessionState
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.RecordTarget
import cl.pettech.app.domain.model.Role
import cl.pettech.app.domain.validation.Validators
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Revisa una vez al día las próximas fechas de las fichas del tutor y avisa desde 7 días antes (HU-06).
 * No usa servicios de notificaciones push (sin Firebase, CLAUDE.md): el teléfono consulta la API.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        if (ReminderPolicy.isQuietHours(now)) {
            // Horario no prudente: se reintenta a las 10:00.
            scheduleOnce(applicationContext, ReminderPolicy.delayToNextPreferred(now))
            return Result.success()
        }
        val container = (applicationContext as PetTechApp).container
        val session = container.sessionManager.state.first { it !is SessionState.Loading }
        if (session !is SessionState.LoggedIn || session.user.role != Role.TUTOR) return Result.success()
        if (!canNotify(applicationContext)) return Result.success()

        val pets = when (val r = container.petRepository.list()) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return Result.retry()
        }
        val today = LocalDate.now()
        val prefs = applicationContext.getSharedPreferences("pettech_reminders", Context.MODE_PRIVATE)
        for (pet in pets) {
            val record = (container.clinicalRecordRepository.load(RecordTarget.Owner(pet.id)) as? AppResult.Success)?.data
                ?: continue
            for (item in record.summary.upcoming) {
                val due = item.dueOn ?: continue
                val daysLeft = ReminderPolicy.daysLeftToNotify(due, today) ?: continue
                val key = "${pet.id}|${item.title}|$due"
                if (prefs.getString(key, null) == today.toString()) continue // ya se avisó hoy
                val (heading, text) = ReminderPolicy.message(pet.name, item.title, daysLeft)
                notify(applicationContext, key.hashCode(), heading, "$text (${Validators.formatDate(due)})")
                prefs.edit().putString(key, today.toString()).apply()
            }
        }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "pettech_reminders"
        private const val PERIODIC = "pettech-reminders-daily"
        private const val ONCE = "pettech-reminders-once"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID, "Recordatorios de vacunas y controles", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Avisos desde 7 días antes de la próxima vacuna, desparasitación o control." }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Revisión diaria alrededor de las 10:00 (WorkManager ajusta la hora exacta para ahorrar batería). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(ReminderPolicy.delayToNextPreferred(LocalDateTime.now()), TimeUnit.MILLISECONDS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Revisión puntual (p. ej. al abrir la app o fuera de horario). */
        fun scheduleOnce(context: Context, delayMillis: Long = 0) {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, request)
        }

        fun canNotify(context: Context): Boolean =
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()

        @android.annotation.SuppressLint("MissingPermission") // se verifica en canNotify()
        private fun notify(context: Context, id: Int, title: String, text: String) {
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            if (canNotify(context)) NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
