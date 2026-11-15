package cl.pettech.app.di

import android.content.Context
import cl.pettech.app.BuildConfig
import cl.pettech.app.data.local.CardTextRecognizer
import cl.pettech.app.data.local.LocationProvider
import cl.pettech.app.data.local.PetPhotoStore
import cl.pettech.app.data.local.PhotoTools
import cl.pettech.app.data.remote.ApiErrorParser
import cl.pettech.app.data.remote.ApiService
import cl.pettech.app.data.remote.AuthInterceptor
import cl.pettech.app.data.repository.AccessRepository
import cl.pettech.app.data.repository.AuthRepository
import cl.pettech.app.data.repository.ClinicRepository
import cl.pettech.app.data.repository.ClinicalRecordRepository
import cl.pettech.app.data.repository.PetRepository
import cl.pettech.app.data.session.SessionManager
import cl.pettech.app.data.session.SessionStore
import cl.pettech.app.data.session.TokenCipher
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Contenedor de dependencias manual.
 *
 * Se prefiere sobre un framework de inyección (Hilt/Koin) porque el grafo es pequeño y
 * así se evita complejidad de configuración dentro del plazo del proyecto (ver DEC-011).
 */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val sessionManager = SessionManager(
        store = SessionStore(context.applicationContext, TokenCipher(), json),
        scope = appScope,
    )

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        // El servidor del piloto (plan gratuito de Render) se suspende sin tráfico y tarda ~1 min en volver:
        // la primera solicitud debe poder esperar ese arranque en vez de fallar (DEC-028).
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(sessionManager))
        .apply {
            if (BuildConfig.HTTP_LOGGING) {
                // BASIC: solo método, URL y código. Nunca cuerpos (contienen contraseñas o tokens).
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    },
                )
            }
        }
        .build()

    private val api: ApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)

    private val errorParser = ApiErrorParser(json)

    val authRepository = AuthRepository(api, sessionManager, errorParser)
    val petRepository = PetRepository(api, errorParser)
    val clinicalRecordRepository = ClinicalRecordRepository(api, errorParser)
    val accessRepository = AccessRepository(api, errorParser)
    val clinicRepository = ClinicRepository(api, errorParser)

    val photoTools = PhotoTools(context.applicationContext)
    val petPhotoStore = PetPhotoStore(context.applicationContext, api, photoTools)
    val cardTextRecognizer = CardTextRecognizer()
    val locationProvider = LocationProvider(context.applicationContext)
}
