package cl.pettech.app.domain

import cl.pettech.app.domain.model.EntryType
import cl.pettech.app.domain.ocr.CardTextParser
import cl.pettech.app.domain.ocr.OcrLine
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textos SIMULADOS de un carnet (no provienen de carnets reales). */
class CardTextParserTest {

    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `reconoce vacuna, fechas y lote en una fila`() {
        val r = CardTextParser.parse(listOf("12/03/2025  Antirrábica  Lote: AR123  Próx. 12/03/2026"), today)
        assertEquals(1, r.size)
        with(r[0]) {
            assertEquals(EntryType.VACCINE, type)
            assertEquals("Antirrábica", title)
            assertEquals(LocalDate.of(2025, 3, 12), occurredOn)
            assertEquals(LocalDate.of(2026, 3, 12), nextDueOn)
            assertEquals("AR123", lotNumber)
            assertTrue(titleRecognized)
        }
    }

    @Test
    fun `antiparasitarios, anio de dos digitos y sin acentos`() {
        val r = CardTextParser.parse(listOf("05-08-25 DESPARASITACION drontal", "20.10.2025 octuple serie 99A-1"), today)
        assertEquals(EntryType.DEWORMING, r[0].type)
        assertEquals("Drontal", r[0].title)
        assertEquals(LocalDate.of(2025, 8, 5), r[0].occurredOn)
        assertEquals("Óctuple", r[1].title)
        assertEquals("99A-1", r[1].lotNumber)
    }

    @Test
    fun `titulo en la fila anterior y fila sin producto queda para completar`() {
        val r = CardTextParser.parse(listOf("Triple felina", "01/02/2025", "03/04/2025 firma"), today)
        assertEquals("Triple felina", r[0].title)
        assertEquals("", r[1].title)
        assertFalse(r[1].titleRecognized)
    }

    @Test
    fun `fechas imposibles o futuras no se aceptan como aplicacion`() {
        val r = CardTextParser.parse(listOf("31/02/2025 antirrabica", "10/01/2027 antirrabica"), today)
        assertTrue(r.all { it.occurredOn == null })
        assertNull(r.firstOrNull { it.occurredOn != null })
    }

    @Test
    fun `reconstruye filas de una tabla por posicion`() {
        val lines = listOf(
            OcrLine("Antirrábica", top = 100, bottom = 120, left = 300),
            OcrLine("12/03/2025", top = 102, bottom = 121, left = 20),
            OcrLine("Óctuple", top = 160, bottom = 180, left = 300),
            OcrLine("01/04/2025", top = 158, bottom = 178, left = 20),
        )
        assertEquals(listOf("12/03/2025  Antirrábica", "01/04/2025  Óctuple"), CardTextParser.rows(lines))
    }
}
