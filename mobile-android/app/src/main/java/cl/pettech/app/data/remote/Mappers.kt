package cl.pettech.app.data.remote

import cl.pettech.app.domain.model.AccessRequest
import cl.pettech.app.domain.model.AccessStatus
import cl.pettech.app.domain.model.AuditEvent
import cl.pettech.app.domain.model.Clinic
import cl.pettech.app.domain.model.ClinicalEntry
import cl.pettech.app.domain.model.EntryAttachment
import cl.pettech.app.domain.model.ClinicalRecord
import cl.pettech.app.domain.model.EntryForm
import cl.pettech.app.domain.model.EntryOrigin
import cl.pettech.app.domain.model.EntryType
import cl.pettech.app.domain.model.OpeningHourItem
import cl.pettech.app.domain.model.Pet
import cl.pettech.app.domain.model.PetForm
import cl.pettech.app.domain.model.RecordPet
import cl.pettech.app.domain.model.RecordSummary
import cl.pettech.app.domain.model.Role
import cl.pettech.app.domain.model.ServiceItem
import cl.pettech.app.domain.model.Sex
import cl.pettech.app.domain.model.Species
import cl.pettech.app.domain.model.UpcomingItem
import cl.pettech.app.domain.model.User
import java.time.LocalDate
import java.time.OffsetDateTime

fun UserDto.toDomain(): User = User(
    id = id,
    email = email,
    fullName = fullName,
    role = Role.fromApi(role),
)

fun PetDto.toDomain(): Pet {
    val parsedSpecies = Species.fromApi(species)
    val parsedSex = Sex.fromApi(sex)
    return Pet(
        id = id,
        publicCode = publicCode,
        name = name,
        species = parsedSpecies,
        speciesLabel = speciesLabel.ifBlank { parsedSpecies?.label ?: species },
        breed = breed,
        sex = parsedSex,
        sexLabel = sexLabel.ifBlank { parsedSex.label },
        birthDate = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        birthDateIsEstimated = birthDateIsEstimated,
        microchipNumber = microchipNumber,
        generalNotes = generalNotes,
    )
}

fun PetForm.toRequest(): PetWriteRequest = PetWriteRequest(
    name = name.trim(),
    species = species.apiValue,
    breed = breed.trim(),
    sex = sex.apiValue,
    birthDate = birthDate?.toString(), // ISO-8601: aaaa-mm-dd
    birthDateIsEstimated = birthDateIsEstimated,
    microchipNumber = microchipNumber.trim(),
    generalNotes = generalNotes.trim(),
)

// ---------------- Ficha clínica, accesos y establecimientos ----------------

private fun String?.toLocalDateOrNull(): LocalDate? = this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun String?.toDateTimeOrNull(): OffsetDateTime? =
    this?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

fun ClinicalEntryDto.toDomain(): ClinicalEntry {
    val type = EntryType.fromApi(entryType)
    return ClinicalEntry(
        id = id,
        type = type,
        typeLabel = entryTypeLabel.ifBlank { type?.label ?: entryType },
        title = title,
        occurredOn = occurredOn.toLocalDateOrNull(),
        nextDueOn = nextDueOn.toLocalDateOrNull(),
        notes = notes,
        weightKg = weightKg,
        lotNumber = lotNumber,
        isActive = isActive,
        origin = if (origin == "PROFESSIONAL") EntryOrigin.PROFESSIONAL else EntryOrigin.OWNER_DECLARED,
        authorName = authorName,
        clinicName = clinicName,
        isCorrection = isCorrection,
        correctionReason = correctionReason,
        anamnesis = anamnesis,
        temperatureC = temperatureC,
        heartRateBpm = heartRateBpm,
        respiratoryRateRpm = respiratoryRateRpm,
        bodyConditionScore = bodyConditionScore,
        physicalExam = physicalExam,
        diagnosis = diagnosis,
        treatment = treatment,
        manufacturer = manufacturer,
        lotExpiresOn = lotExpiresOn.toLocalDateOrNull(),
        dose = dose,
        attachments = attachments.map {
            EntryAttachment(it.id, it.kindLabel.ifBlank { "Fotografía" }, it.origin == "PROFESSIONAL", it.authorName)
        },
        sourceAttachmentId = sourceAttachment,
    )
}

fun ClinicalRecordDto.toDomain(): ClinicalRecord = ClinicalRecord(
    pet = RecordPet(
        publicCode = pet.publicCode,
        name = pet.name,
        speciesLabel = pet.speciesLabel.ifBlank { Species.fromApi(pet.species)?.label ?: pet.species },
        breed = pet.breed,
        sexLabel = pet.sexLabel,
        birthDate = pet.birthDate.toLocalDateOrNull(),
        birthDateIsEstimated = pet.birthDateIsEstimated,
        microchipNumber = pet.microchipNumber,
        generalNotes = pet.generalNotes,
    ),
    summary = RecordSummary(
        latestWeightKg = summary.latestWeightKg,
        latestWeightOn = summary.latestWeightOn.toLocalDateOrNull(),
        allergies = summary.allergies,
        chronicConditions = summary.chronicConditions,
        activeMedications = summary.activeMedications,
        upcoming = summary.upcoming.map {
            UpcomingItem(EntryType.fromApi(it.entryType), it.title, it.dueOn.toLocalDateOrNull(), it.overdue)
        },
    ),
    entries = entries.map { it.toDomain() },
)

fun EntryForm.toRequest(reason: String? = null): EntryWriteRequest = EntryWriteRequest(
    entryType = type.apiValue,
    title = title.trim(),
    occurredOn = occurredOn.toString(),
    nextDueOn = if (type.hasNextDate) nextDueOn?.toString() else null,
    notes = notes.trim(),
    weightKg = weightKg,
    lotNumber = lotNumber.trim(),
    isActive = isActive,
    reason = reason?.trim(),
    sourceAttachment = sourceAttachmentId,
)

fun AccessRequestDto.toDomain(): AccessRequest = AccessRequest(
    id = id,
    status = AccessStatus.fromApi(status),
    statusLabel = statusLabel.ifBlank { status },
    petId = petId,
    petCode = petCode,
    petName = petName,
    clinicName = clinicName,
    professionalName = professionalName,
    reason = reason,
    createdAt = createdAt.toDateTimeOrNull(),
    expiresAt = expiresAt.toDateTimeOrNull(),
)

fun AuditEventDto.toDomain(): AuditEvent = AuditEvent(
    createdAt = createdAt.toDateTimeOrNull(),
    actorName = actorName,
    clinicName = clinicName,
    actionLabel = actionLabel,
    denied = result == "DENIED",
)

fun ClinicDto.toDomain(): Clinic = Clinic(
    id = id,
    name = name,
    address = address,
    commune = commune,
    phone = phone,
    latitude = latitude,
    longitude = longitude,
    distanceKm = distanceKm,
    attendsEmergencies = attendsEmergencies,
    emergency24h = emergency24h,
    services = services.map { ServiceItem(it.code, it.label) },
    isOpenNow = isOpenNow,
    todayHours = todayHours,
    openingHours = openingHours.map { OpeningHourItem(it.weekdayLabel, it.opensAt.take(5), it.closesAt.take(5)) },
    confirmedByClinic = infoOrigin == "CONFIRMED",
    infoOriginLabel = infoOriginLabel,
    verifiedAt = verifiedAt.toDateTimeOrNull(),
    isStale = isStale,
    // Compatibilidad: servidores anteriores no envían in_pettech_network.
    inPetTechNetwork = inPettechNetwork ?: acceptsPettechRecords,
    contactEmail = contactEmail,
    website = website,
    servicesDetail = servicesDetail,
    sourceNote = sourceNote,
)
