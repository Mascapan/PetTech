package cl.pettech.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Contrato de la API REST v1 (rutas relativas a .../api/v1/). Ver docs/api/API.md. */
interface ApiService {

    @POST("auth/register/")
    suspend fun register(@Body body: RegisterRequest): AuthResponseDto

    @POST("auth/login/")
    suspend fun login(@Body body: LoginRequest): AuthResponseDto

    @POST("auth/logout/")
    suspend fun logout(): Response<Unit>

    @GET("auth/me/")
    suspend fun me(): UserDto

    @GET("pets/")
    suspend fun listPets(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): PageDto<PetDto>

    @POST("pets/")
    suspend fun createPet(@Body body: PetWriteRequest): PetDto

    @GET("pets/{id}/")
    suspend fun getPet(@Path("id") id: String): PetDto

    @PATCH("pets/{id}/")
    suspend fun updatePet(@Path("id") id: String, @Body body: PetWriteRequest): PetDto

    @DELETE("pets/{id}/")
    suspend fun deletePet(@Path("id") id: String): Response<Unit>
}
