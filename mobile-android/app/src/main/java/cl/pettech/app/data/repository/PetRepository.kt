package cl.pettech.app.data.repository

import cl.pettech.app.data.remote.ApiErrorParser
import cl.pettech.app.data.remote.ApiService
import cl.pettech.app.data.remote.safeApiCall
import cl.pettech.app.data.remote.toDomain
import cl.pettech.app.data.remote.toRequest
import cl.pettech.app.domain.model.AppResult
import cl.pettech.app.domain.model.Pet
import cl.pettech.app.domain.model.PetForm
import retrofit2.HttpException

/** Acceso a las mascotas del tutor. El backend filtra siempre por el tutor del token. */
class PetRepository(
    private val api: ApiService,
    private val errors: ApiErrorParser,
) {
    suspend fun list(): AppResult<List<Pet>> = safeApiCall(errors) {
        api.listPets().results.map { it.toDomain() }
    }

    suspend fun get(id: String): AppResult<Pet> = safeApiCall(errors) {
        api.getPet(id).toDomain()
    }

    suspend fun create(form: PetForm): AppResult<Pet> = safeApiCall(errors) {
        api.createPet(form.toRequest()).toDomain()
    }

    suspend fun update(id: String, form: PetForm): AppResult<Pet> = safeApiCall(errors) {
        api.updatePet(id, form.toRequest()).toDomain()
    }

    suspend fun delete(id: String): AppResult<Unit> = safeApiCall(errors) {
        val response = api.deletePet(id)
        if (!response.isSuccessful) throw HttpException(response)
    }
}
