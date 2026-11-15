package cl.pettech.app.data.reminders

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Reglas de los recordatorios (HU-06), separadas de Android para poder probarlas:
 * - se avisa desde 7 días antes de la próxima fecha y cada día hasta el mismo día;
 * - solo en horario prudente (09:00 a 20:59); fuera de él se espera a las 10:00;
 * - una sola notificación por antecedente y por día.
 */
object ReminderPolicy {
    const val DAYS_BEFORE = 7L
    val WINDOW_START: LocalTime = LocalTime.of(9, 0)
    val WINDOW_END: LocalTime = LocalTime.of(21, 0)
    val PREFERRED: LocalTime = LocalTime.of(10, 0)

    /** Días que faltan si corresponde avisar hoy (0 = es hoy), o null si no. */
    fun daysLeftToNotify(dueOn: LocalDate, today: LocalDate): Long? {
        val days = ChronoUnit.DAYS.between(today, dueOn)
        return days.takeIf { it in 0..DAYS_BEFORE }
    }

    fun isQuietHours(now: LocalDateTime): Boolean = now.toLocalTime() < WINDOW_START || now.toLocalTime() >= WINDOW_END

    /** Milisegundos hasta la próxima hora preferida (10:00). */
    fun delayToNextPreferred(now: LocalDateTime): Long {
        var next = now.toLocalDate().atTime(PREFERRED)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return ChronoUnit.MILLIS.between(now, next)
    }

    fun message(petName: String, title: String, daysLeft: Long): Pair<String, String> {
        val heading = "$petName: $title"
        val text = when (daysLeft) {
            0L -> "Es hoy. Recuerda acudir a tu veterinaria."
            1L -> "Es mañana. Agenda la atención con tu veterinaria."
            else -> "Faltan $daysLeft días. Agenda la atención con tu veterinaria."
        }
        return heading to text
    }
}
