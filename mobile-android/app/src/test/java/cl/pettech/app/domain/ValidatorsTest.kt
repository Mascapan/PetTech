package cl.pettech.app.domain

import cl.pettech.app.data.session.SessionManager
import cl.pettech.app.domain.validation.BirthDateResult
import cl.pettech.app.domain.validation.Validators
import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas unitarias de validaciones del cliente (se ejecutan en la JVM, sin emulador). */
class ValidatorsTest {

    private val today = LocalDate.of(2026, 11, 1)

    @Test
    fun email_valido_e_invalido() {
        assertNull(Validators.email("tutora@correo.cl"))
        assertNotNull(Validators.email(""))
        assertNotNull(Validators.email("sin-arroba.cl"))
    }

    @Test
    fun contrasena_nueva_exige_largo_y_no_solo_numeros() {
        assertNotNull(Validators.newPassword("corta"))
        assertNotNull(Validators.newPassword("1234567890"))
        assertNull(Validators.newPassword("Clave-Segura-2026"))
    }

    @Test
    fun confirmacion_de_contrasena() {
        assertNull(Validators.passwordConfirmation("abc", "abc"))
        assertNotNull(Validators.passwordConfirmation("abc", "abd"))
    }

    @Test
    fun microchip_opcional_o_15_digitos() {
        assertNull(Validators.microchip(""))
        assertNull(Validators.microchip("985112003456789"))
        assertNotNull(Validators.microchip("12345"))
    }

    @Test
    fun fecha_de_nacimiento_valida_con_y_sin_guiones() {
        assertEquals(BirthDateResult.Valid(LocalDate.of(2021, 3, 15)), Validators.parseBirthDate("15-03-2021", today))
        assertEquals(BirthDateResult.Valid(LocalDate.of(2021, 3, 15)), Validators.parseBirthDate("15032021", today))
    }

    @Test
    fun fecha_de_nacimiento_vacia_es_permitida() {
        assertEquals(BirthDateResult.Empty, Validators.parseBirthDate("", today))
    }

    @Test
    fun fecha_inexistente_futura_o_muy_antigua_es_rechazada() {
        assertTrue(Validators.parseBirthDate("31-02-2021", today) is BirthDateResult.Invalid)
        assertTrue(Validators.parseBirthDate("02-11-2026", today) is BirthDateResult.Invalid)
        assertTrue(Validators.parseBirthDate("01-01-1950", today) is BirthDateResult.Invalid)
        assertTrue(Validators.parseBirthDate("1-1-21", today) is BirthDateResult.Invalid)
    }

    @Test
    fun expiracion_de_sesion() {
        val now = OffsetDateTime.parse("2026-11-01T12:00:00-03:00")
        assertTrue(SessionManager.isExpired("2026-11-01T11:59:59-03:00", now))
        assertFalse(SessionManager.isExpired("2026-11-08T12:00:00.123456-03:00", now))
        assertTrue(SessionManager.isExpired("fecha-invalida", now))
        assertFalse(SessionManager.isExpired(null, now))
    }

    @Test
    fun fecha_de_evento_obligatoria_y_no_futura() {
        assertTrue(Validators.parseEventDate("", today) is BirthDateResult.Invalid)
        assertTrue(Validators.parseEventDate("02-11-2026", today) is BirthDateResult.Invalid)
        assertEquals(BirthDateResult.Valid(LocalDate.of(2026, 10, 1)), Validators.parseEventDate("01-10-2026", today))
    }

    @Test
    fun proxima_fecha_opcional_y_posterior_al_evento() {
        val occurred = LocalDate.of(2026, 10, 1)
        assertEquals(BirthDateResult.Empty, Validators.parseNextDate("", occurred))
        assertTrue(Validators.parseNextDate("30-09-2026", occurred) is BirthDateResult.Invalid)
        assertEquals(BirthDateResult.Valid(LocalDate.of(2027, 10, 1)), Validators.parseNextDate("01-10-2027", occurred))
    }

    @Test
    fun peso_acepta_coma_decimal_y_rango() {
        assertEquals(12.5, Validators.parseWeight("12,5").getOrNull())
        assertEquals(null, Validators.parseWeight("").getOrNull())
        assertTrue(Validators.parseWeight("0").isFailure)
        assertTrue(Validators.parseWeight("abc").isFailure)
    }

    @Test
    fun codigo_pettech_tolerante_al_dictado() {
        assertEquals("PT-AD4D-8CNT", Validators.normalizePetCode("pt ad4d 8cnt"))
        assertEquals("PT-1D0D-8CNT", Validators.normalizePetCode("PTID0D8CNT"))
        assertEquals(null, Validators.normalizePetCode("PT-123"))
    }
}
