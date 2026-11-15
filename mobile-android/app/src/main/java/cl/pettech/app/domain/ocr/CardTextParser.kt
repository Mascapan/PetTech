package cl.pettech.app.domain.ocr

import cl.pettech.app.domain.model.EntryType
import java.text.Normalizer
import java.time.LocalDate

/** Línea reconocida por el OCR con su posición en la foto (píxeles). */
data class OcrLine(val text: String, val top: Int, val bottom: Int, val left: Int)

/** Antecedente PROPUESTO a partir del carnet. El tutor siempre lo revisa antes de guardarlo. */
data class ProposedEntry(
    val type: EntryType,
    val title: String,
    val occurredOn: LocalDate?,
    val nextDueOn: LocalDate?,
    val lotNumber: String,
    /** Texto de la fila del carnet de la que se obtuvo (para que el tutor compare). */
    val sourceText: String,
    /** true si se reconoció el producto por diccionario; false si el título debe completarse a mano. */
    val titleRecognized: Boolean,
)

/**
 * Interpreta el texto de una foto del carnet de vacunas en papel (DEC-032).
 *
 * Es deliberadamente simple y explicable (reglas + diccionario), no "inteligente": reconstruye las filas
 * de la tabla por posición, busca fechas, el nombre de la vacuna o antiparasitario y el lote. Lo que no
 * reconoce lo deja vacío para que el tutor lo complete. Nada se guarda sin su confirmación.
 */
object CardTextParser {

    private val DATE = Regex("""(?<!\d)(\d{1,2})\s*[/\-.]\s*(\d{1,2})\s*[/\-.]\s*(\d{2,4})(?!\d)""")
    private val LOT = Regex("""(?:lote|serie|lot|ser|l\s*[:#]|n[°ºo]\s*lote)\s*[:#.nº°]*\s*([a-z0-9][a-z0-9\-/]{2,15})""")
    private val NEXT_HINT = Regex("""pr[oó]x|revac|refuerzo|rev\.|vence|hasta""")

    /** (palabra clave normalizada, título propuesto, tipo). El orden importa: lo más específico primero. */
    private val DICTIONARY: List<Triple<Regex, String, EntryType>> = listOf(
        Triple(Regex("""antirr?abic|rabia|rabic|rabisin|defensor|nobivac\s*r"""), "Antirrábica", EntryType.VACCINE),
        Triple(Regex("""octuple|8\s*vias|ctuple"""), "Óctuple", EntryType.VACCINE),
        Triple(Regex("""sextuple|6\s*vias|xtuple"""), "Séxtuple", EntryType.VACCINE),
        Triple(Regex("""quintuple|5\s*vias"""), "Quíntuple", EntryType.VACCINE),
        Triple(Regex("""triple\s*fel|felocell|tricat|feligen|fvrcp"""), "Triple felina", EntryType.VACCINE),
        Triple(Regex("""leucemia|felv|leucogen"""), "Leucemia felina", EntryType.VACCINE),
        Triple(Regex("""\bkc\b|perreras|bordetella|tos\s*de"""), "Tos de las perreras (KC)", EntryType.VACCINE),
        Triple(Regex("""puppy|cachorro"""), "Vacuna de cachorro (revisar)", EntryType.VACCINE),
        Triple(Regex("""parvo"""), "Parvovirus", EntryType.VACCINE),
        Triple(Regex("""moquillo|distemper"""), "Moquillo", EntryType.VACCINE),
        Triple(Regex("""lepto"""), "Leptospirosis", EntryType.VACCINE),
        Triple(Regex("""giardia"""), "Giardia", EntryType.VACCINE),
        Triple(Regex("""vanguard|canigen|eurican|nobivac|recombitek|duramune|versican"""), "Vacuna (revisar tipo)", EntryType.VACCINE),
        Triple(Regex("""bravecto"""), "Bravecto", EntryType.DEWORMING),
        Triple(Regex("""nexgard|nex\s*gard"""), "NexGard", EntryType.DEWORMING),
        Triple(Regex("""simparica"""), "Simparica", EntryType.DEWORMING),
        Triple(Regex("""drontal"""), "Drontal", EntryType.DEWORMING),
        Triple(Regex("""milbemax"""), "Milbemax", EntryType.DEWORMING),
        Triple(Regex("""endogard"""), "Endogard", EntryType.DEWORMING),
        Triple(Regex("""frontline"""), "Frontline", EntryType.DEWORMING),
        Triple(Regex("""revolution"""), "Revolution", EntryType.DEWORMING),
        Triple(Regex("""advocate"""), "Advocate", EntryType.DEWORMING),
        Triple(Regex("""total\s*full"""), "Total Full", EntryType.DEWORMING),
        Triple(Regex("""desparasit|antiparasit|parasit|interno|externo|pulga|garrapata"""), "Antiparasitario", EntryType.DEWORMING),
        Triple(Regex("""vacun"""), "Vacuna (revisar tipo)", EntryType.VACCINE),
    )

    /** Agrupa líneas del OCR en filas de la tabla (misma altura) y las ordena de izquierda a derecha. */
    fun rows(lines: List<OcrLine>): List<String> {
        val sorted = lines.filter { it.text.isNotBlank() }.sortedBy { (it.top + it.bottom) / 2 }
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            val center = (line.top + line.bottom) / 2
            val height = (line.bottom - line.top).coerceAtLeast(1)
            val row = rows.lastOrNull()
            val rowCenter = row?.let { r -> r.sumOf { (it.top + it.bottom) / 2 } / r.size }
            if (row != null && rowCenter != null && kotlin.math.abs(center - rowCenter) <= height * 0.6) {
                row.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }
        return rows.map { r -> r.sortedBy { it.left }.joinToString("  ") { it.text.trim() } }
    }

    fun parse(rows: List<String>, today: LocalDate = LocalDate.now()): List<ProposedEntry> {
        val result = mutableListOf<ProposedEntry>()
        var pendingTitle: Pair<String, EntryType>? = null
        for (raw in rows) {
            val norm = normalize(raw)
            val match = DICTIONARY.firstOrNull { it.first.containsMatchIn(norm) }
            val dates = DATE.findAll(norm).mapNotNull { toDate(it) }.toList()
            if (dates.isEmpty()) {
                // Fila sin fecha: puede ser el encabezado con el nombre de la vacuna de la fila siguiente.
                pendingTitle = match?.let { it.second to it.third }
                continue
            }
            val (title, type, recognized) = when {
                match != null -> Triple(match.second, match.third, true)
                pendingTitle != null -> Triple(pendingTitle.first, pendingTitle.second, true)
                else -> Triple("", EntryType.VACCINE, false)
            }
            pendingTitle = null
            val (occurred, next) = splitDates(norm, dates, today)
            val lot = LOT.find(norm)?.groupValues?.get(1)?.uppercase().orEmpty()
            result += ProposedEntry(type, title, occurred, next, lot, raw.trim(), recognized)
        }
        return result.distinctBy { Triple(it.title, it.occurredOn, it.lotNumber) }
    }

    /** Primera fecha pasada = aplicación; fecha posterior (o tras "próx./refuerzo") = próxima dosis. */
    private fun splitDates(norm: String, dates: List<LocalDate>, today: LocalDate): Pair<LocalDate?, LocalDate?> {
        val hintAt = NEXT_HINT.find(norm)?.range?.first
        val positioned = DATE.findAll(norm).mapNotNull { m -> toDate(m)?.let { m.range.first to it } }.toList()
        val nextByHint = hintAt?.let { h -> positioned.firstOrNull { it.first > h }?.second }
        val occurred = positioned.map { it.second }.firstOrNull { it != nextByHint && !it.isAfter(today) }
        val next = nextByHint ?: dates.firstOrNull { occurred != null && it.isAfter(occurred) }
        return occurred to next
    }

    private fun toDate(m: MatchResult): LocalDate? {
        val (d, mo, y) = m.destructured
        val year = y.toInt().let { if (y.length == 2) 2000 + it else it }
        if (year !in 1990..2100) return null
        return runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()
    }

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
