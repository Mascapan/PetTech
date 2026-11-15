package cl.pettech.app.domain

import cl.pettech.app.data.reminders.ReminderPolicy
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {
    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `avisa desde 7 dias antes hasta el mismo dia`() {
        assertNull(ReminderPolicy.daysLeftToNotify(today.plusDays(8), today))
        assertEquals(7L, ReminderPolicy.daysLeftToNotify(today.plusDays(7), today))
        assertEquals(0L, ReminderPolicy.daysLeftToNotify(today, today))
        assertNull(ReminderPolicy.daysLeftToNotify(today.minusDays(1), today))
    }

    @Test
    fun `solo en horario prudente`() {
        assertTrue(ReminderPolicy.isQuietHours(LocalDateTime.of(2026, 11, 15, 7, 30)))
        assertFalse(ReminderPolicy.isQuietHours(LocalDateTime.of(2026, 11, 15, 10, 0)))
        assertTrue(ReminderPolicy.isQuietHours(LocalDateTime.of(2026, 11, 15, 22, 0)))
        // A las 22:00 la próxima revisión es mañana a las 10:00 (12 horas).
        assertEquals(12 * 3600_000L, ReminderPolicy.delayToNextPreferred(LocalDateTime.of(2026, 11, 15, 22, 0)))
    }

    @Test
    fun `mensajes claros`() {
        assertEquals("Es hoy. Recuerda acudir a tu veterinaria.", ReminderPolicy.message("Luna", "Antirrábica", 0).second)
        assertTrue(ReminderPolicy.message("Luna", "Antirrábica", 5).second.startsWith("Faltan 5 días"))
    }
}
