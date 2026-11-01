package cl.pettech.app.data.remote

import cl.pettech.app.domain.model.Pet
import cl.pettech.app.domain.model.PetForm
import cl.pettech.app.domain.model.Role
import cl.pettech.app.domain.model.Sex
import cl.pettech.app.domain.model.Species
import cl.pettech.app.domain.model.User
import java.time.LocalDate

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
