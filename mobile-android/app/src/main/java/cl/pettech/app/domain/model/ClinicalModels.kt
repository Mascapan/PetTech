package cl.pettech.app.domain.model

import java.time.LocalDate
import java.time.OffsetDateTime

/** Tipos de antecedente de la ficha clínica estandarizada (mismos códigos que la API). */
enum class EntryType(val apiValue: String, val label: String, val plural: String) {
    VACCINE("VACCINE", "Vacuna", "Vacunas"),
    DEWORMING("DEWORMING", "Desparasitación", "Desparasitaciones"),
    VISIT("VISIT", "Visita / control", "Visitas"),
    ALLERGY("ALLERGY", "Alergia", "Alergias"),
    CHRONIC_CONDITION("CHRONIC_CONDITION", "Patología crónica", "Patologías crónicas"),
    SURGERY("SURGERY", "Cirugía", "Cirugías"),
    MEDICATION("MEDICATION", "Fármaco", "Fármacos"),
    WEIGHT("WEIGHT", "Peso", "Peso"),
    ;

    /** Tipos que admiten próxima fecha (refuerzo, próximo control, término del tratamiento). */
    val hasNextDate: Boolean get() = this in setOf(VACCINE, DEWORMING, VISIT, MEDICATION)

    /** Tipos que pueden seguir vigentes o no (alergia activa, fármaco en uso...). */
    val hasActiveFlag: Boolean get() = this in setOf(ALLERGY, CHRONIC_CONDITION, MEDICATION)

    val titleHint: String
        get() = when (this) {
            VACCINE -> "Vacuna aplicada (p. ej., antirrábica)"
            DEWORMING -> "Producto (p. ej., antiparasitario interno)"
            VISIT -> "Motivo de la visita"
            ALLERGY -> "Alergia (p. ej., penicilina)"
            CHRONIC_CONDITION -> "Patología (p. ej., insuficiencia renal)"
            SURGERY -> "Cirugía (p. ej., esterilización)"
            MEDICATION -> "Fármaco y dosis"
            WEIGHT -> "Descripción (p. ej., control de peso)"
        }

    companion object {
        fun fromApi(value: String): EntryType? = entries.firstOrNull { it.apiValue == value }
    }
}

enum class EntryOrigin { OWNER_DECLARED, PROFESSIONAL }

data class ClinicalEntry(
    val id: String,
    val type: EntryType?,
    val typeLabel: String,
    val title: String,
    val occurredOn: LocalDate?,
    val nextDueOn: LocalDate?,
    val notes: String,
    val weightKg: Double?,
    val lotNumber: String,
    val isActive: Boolean,
    val origin: EntryOrigin,
    val authorName: String,
    val clinicName: String,
    val isCorrection: Boolean,
    val correctionReason: String,
    val anamnesis: String = "",
    val temperatureC: Double? = null,
    val heartRateBpm: Int? = null,
    val respiratoryRateRpm: Int? = null,
    val bodyConditionScore: Int? = null,
    val physicalExam: String = "",
    val diagnosis: String = "",
    val treatment: String = "",
    val manufacturer: String = "",
    val lotExpiresOn: LocalDate? = null,
    val dose: String = "",
    val attachments: List<EntryAttachment> = emptyList(),
    /** Foto del carnet de la que el tutor transcribió este antecedente (OCR). */
    val sourceAttachmentId: String? = null,
)

/** Foto de respaldo de un antecedente (etiqueta de vacuna o carnet). */
data class EntryAttachment(
    val id: String,
    val kindLabel: String,
    val byProfessional: Boolean,
    val authorName: String,
)

/** Identificación de la mascota en la ficha (el profesional no recibe el id interno). */
data class RecordPet(
    val publicCode: String,
    val name: String,
    val speciesLabel: String,
    val breed: String,
    val sexLabel: String,
    val birthDate: LocalDate?,
    val birthDateIsEstimated: Boolean,
    val microchipNumber: String,
    val generalNotes: String,
)

data class UpcomingItem(val type: EntryType?, val title: String, val dueOn: LocalDate?, val overdue: Boolean)

data class RecordSummary(
    val latestWeightKg: Double?,
    val latestWeightOn: LocalDate?,
    val allergies: List<String>,
    val chronicConditions: List<String>,
    val activeMedications: List<String>,
    val upcoming: List<UpcomingItem>,
)

data class ClinicalRecord(val pet: RecordPet, val summary: RecordSummary, val entries: List<ClinicalEntry>)

/** Datos del formulario de antecedente (registro o corrección). */
data class EntryForm(
    val type: EntryType,
    val title: String,
    val occurredOn: LocalDate,
    val nextDueOn: LocalDate?,
    val notes: String,
    val weightKg: Double?,
    val lotNumber: String,
    val isActive: Boolean,
    val sourceAttachmentId: String? = null,
)

/** A quién pertenece la ficha que se consulta: al tutor (por id) o al profesional autorizado (por código). */
sealed interface RecordTarget {
    data class Owner(val petId: String) : RecordTarget
    data class Professional(val publicCode: String) : RecordTarget
}

// ---------------- Accesos ----------------

enum class AccessStatus { PENDING, APPROVED, REJECTED, REVOKED, EXPIRED, UNKNOWN;

    companion object {
        fun fromApi(value: String) = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

data class AccessRequest(
    val id: String,
    val status: AccessStatus,
    val statusLabel: String,
    val petId: String?,
    val petCode: String,
    val petName: String?,
    val clinicName: String,
    val professionalName: String,
    val reason: String,
    val createdAt: OffsetDateTime?,
    val expiresAt: OffsetDateTime?,
)

data class AuditEvent(
    val createdAt: OffsetDateTime?,
    val actorName: String,
    val clinicName: String,
    val actionLabel: String,
    val denied: Boolean,
)

// ---------------- Establecimientos ----------------

data class ServiceItem(val code: String, val label: String)

data class OpeningHourItem(val weekdayLabel: String, val opensAt: String, val closesAt: String)

data class Clinic(
    val id: String,
    val name: String,
    val address: String,
    val commune: String,
    val phone: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double?,
    val attendsEmergencies: Boolean,
    val emergency24h: Boolean,
    val services: List<ServiceItem>,
    val isOpenNow: Boolean,
    val todayHours: String,
    val openingHours: List<OpeningHourItem>,
    val confirmedByClinic: Boolean,
    val infoOriginLabel: String,
    val verifiedAt: OffsetDateTime?,
    val isStale: Boolean,
    val inPetTechNetwork: Boolean,
    val contactEmail: String = "",
    val website: String = "",
    val servicesDetail: String = "",
    val sourceNote: String = "",
)

data class ClinicSearchResult(val clinics: List<Clinic>, val disclaimer: String)

data class ClinicFilters(
    val openNow: Boolean = false,
    val emergency: Boolean = false,
    val service: String? = null,
    val radiusKm: Int = 10,
    val networkOnly: Boolean = false,
)
