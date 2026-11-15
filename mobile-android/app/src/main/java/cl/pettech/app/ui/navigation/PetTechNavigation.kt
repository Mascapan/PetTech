package cl.pettech.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.pettech.app.data.session.SessionManager
import cl.pettech.app.data.session.SessionState
import cl.pettech.app.domain.model.Role
import cl.pettech.app.ui.components.PetTechLogo
import cl.pettech.app.ui.screens.access.AccessScreen
import cl.pettech.app.ui.screens.auth.ForgotPasswordScreen
import cl.pettech.app.ui.screens.auth.LoginScreen
import cl.pettech.app.ui.screens.auth.RegisterScreen
import cl.pettech.app.ui.screens.clinics.ClinicDetailScreen
import cl.pettech.app.ui.screens.clinics.ClinicSearchScreen
import cl.pettech.app.ui.screens.home.ProfessionalHomeScreen
import cl.pettech.app.ui.screens.home.TutorHomeScreen
import cl.pettech.app.ui.screens.pets.PetDetailScreen
import cl.pettech.app.ui.screens.pets.PetFormScreen
import cl.pettech.app.ui.screens.records.ClinicalRecordScreen
import cl.pettech.app.ui.screens.records.scan.CardScanScreen
import cl.pettech.app.ui.screens.records.EntryFormScreen

/**
 * Rutas de la aplicación. Flujo del tutor según el boceto:
 *  1) Inicio de sesión → 2) Mis mascotas (+ Buscar veterinaria) → 3) Ficha de la mascota
 *  → 4) Ficha clínica; 5) Buscar veterinaria (lista + mapa + ruta).
 * Flujo del profesional: código PetTech → solicitud → (autorización del tutor) → ficha clínica.
 */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot-password"

    const val TUTOR_HOME = "tutor/home"
    const val PET_NEW = "tutor/pet-new"
    const val PET_DETAIL = "tutor/pet/{petId}"
    const val PET_EDIT = "tutor/pet/{petId}/edit"
    const val RECORD_OWNER = "tutor/pet/{petId}/record"
    const val ENTRY_OWNER = "tutor/pet/{petId}/record/entry?entryId={entryId}"
    const val SCAN_OWNER = "tutor/pet/{petId}/record/scan"
    const val ACCESS = "tutor/access?petId={petId}"
    const val CLINICS = "clinics"
    const val CLINIC_DETAIL = "clinics/{clinicId}"

    const val PRO_HOME = "pro/home"
    const val RECORD_PRO = "pro/record/{code}"
    const val ENTRY_PRO = "pro/record/{code}/entry?entryId={entryId}"

    val AUTH = setOf(LOGIN, REGISTER, FORGOT)

    fun petDetail(petId: String) = "tutor/pet/$petId"
    fun petEdit(petId: String) = "tutor/pet/$petId/edit"
    fun ownerRecord(petId: String) = "tutor/pet/$petId/record"
    fun ownerScan(petId: String) = "tutor/pet/$petId/record/scan"
    fun ownerEntry(petId: String, entryId: String? = null) =
        "tutor/pet/$petId/record/entry" + (entryId?.let { "?entryId=$it" } ?: "")
    fun access(petId: String? = null) = "tutor/access" + (petId?.let { "?petId=$it" } ?: "")
    fun clinicDetail(id: String) = "clinics/$id"
    fun proRecord(code: String) = "pro/record/$code"
    fun proEntry(code: String, entryId: String? = null) =
        "pro/record/$code/entry" + (entryId?.let { "?entryId=$it" } ?: "")

    /** Navegación diferenciada por rol dentro de una sola aplicación. */
    fun homeFor(role: Role): String = if (role == Role.TUTOR) TUTOR_HOME else PRO_HOME
}

@Composable
fun PetTechRoot(sessionManager: SessionManager) {
    val state by sessionManager.state.collectAsStateWithLifecycle()
    when (val current = state) {
        SessionState.Loading -> SplashScreen()
        else -> PetTechNavHost(sessionState = current)
    }
}

@Composable
private fun SplashScreen() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PetTechLogo(size = 96.dp)
                CircularProgressIndicator()
            }
        }
    }
}

private val optionalArg = { name: String ->
    navArgument(name) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }
}

@Composable
private fun PetTechNavHost(sessionState: SessionState) {
    val navController = rememberNavController()
    val startDestination = remember {
        if (sessionState is SessionState.LoggedIn) Routes.homeFor(sessionState.user.role) else Routes.LOGIN
    }
    var loginNotice by rememberSaveable { mutableStateOf<String?>(null) }

    // Reacciona a inicio/cierre/expiración de sesión limpiando la pila de navegación.
    LaunchedEffect(sessionState) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        when (sessionState) {
            is SessionState.LoggedIn -> {
                if (currentRoute == null || currentRoute in Routes.AUTH) {
                    loginNotice = null
                    navController.resetTo(Routes.homeFor(sessionState.user.role))
                }
            }
            is SessionState.LoggedOut -> {
                if (currentRoute != null && currentRoute !in Routes.AUTH) navController.resetTo(Routes.LOGIN)
            }
            SessionState.Loading -> Unit
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        // ---------------- Acceso ----------------
        composable(Routes.LOGIN) {
            LoginScreen(
                onGoToRegister = { navController.navigate(Routes.REGISTER) },
                onForgotPassword = { navController.navigate(Routes.FORGOT) },
                infoMessage = loginNotice,
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(onBack = { navController.backOrLogin() })
        }
        composable(Routes.FORGOT) {
            ForgotPasswordScreen(
                onBack = { navController.backOrLogin() },
                onDone = {
                    loginNotice = "Tu contraseña fue actualizada. Ingresa con la nueva contraseña."
                    navController.backOrLogin()
                },
            )
        }

        // ---------------- Tutor ----------------
        composable(Routes.TUTOR_HOME) {
            TutorHomeScreen(
                onOpenPet = { petId -> navController.navigate(Routes.petDetail(petId)) },
                onAddPet = { navController.navigate(Routes.PET_NEW) },
                onSearchClinics = { navController.navigate(Routes.CLINICS) },
                onOpenAccess = { navController.navigate(Routes.access()) },
            )
        }
        composable(Routes.PET_NEW) {
            PetFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { petId ->
                    navController.navigate(Routes.petDetail(petId)) {
                        popUpTo(Routes.PET_NEW) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.PET_DETAIL, arguments = listOf(navArgument("petId") { type = NavType.StringType })) {
            PetDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { petId -> navController.navigate(Routes.petEdit(petId)) },
                onOpenClinicalRecord = { petId -> navController.navigate(Routes.ownerRecord(petId)) },
                onOpenAccess = { petId -> navController.navigate(Routes.access(petId)) },
                onDeleted = { navController.popBackStack() },
            )
        }
        composable(Routes.PET_EDIT, arguments = listOf(navArgument("petId") { type = NavType.StringType })) {
            PetFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(Routes.RECORD_OWNER, arguments = listOf(navArgument("petId") { type = NavType.StringType })) { entry ->
            val petId = entry.arguments?.getString("petId").orEmpty()
            ClinicalRecordScreen(
                onBack = { navController.popBackStack() },
                onAddEntry = { navController.navigate(Routes.ownerEntry(petId)) },
                onCorrectEntry = { entryId -> navController.navigate(Routes.ownerEntry(petId, entryId)) },
                onScanCard = { navController.navigate(Routes.ownerScan(petId)) },
            )
        }
        composable(Routes.SCAN_OWNER, arguments = listOf(navArgument("petId") { type = NavType.StringType })) {
            CardScanScreen(onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
        }
        composable(
            Routes.ENTRY_OWNER,
            arguments = listOf(navArgument("petId") { type = NavType.StringType }, optionalArg("entryId")),
        ) {
            EntryFormScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
        }
        composable(Routes.ACCESS, arguments = listOf(optionalArg("petId"))) {
            AccessScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CLINICS) {
            ClinicSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenClinic = { id -> navController.navigate(Routes.clinicDetail(id)) },
            )
        }
        composable(Routes.CLINIC_DETAIL, arguments = listOf(navArgument("clinicId") { type = NavType.StringType })) {
            ClinicDetailScreen(onBack = { navController.popBackStack() })
        }

        // ---------------- Profesional ----------------
        composable(Routes.PRO_HOME) {
            ProfessionalHomeScreen(onOpenRecord = { code -> navController.navigate(Routes.proRecord(code)) })
        }
        composable(Routes.RECORD_PRO, arguments = listOf(navArgument("code") { type = NavType.StringType })) { entry ->
            val code = entry.arguments?.getString("code").orEmpty()
            ClinicalRecordScreen(
                onBack = { navController.popBackStack() },
                onAddEntry = { navController.navigate(Routes.proEntry(code)) },
                onCorrectEntry = { entryId -> navController.navigate(Routes.proEntry(code, entryId)) },
            )
        }
        composable(
            Routes.ENTRY_PRO,
            arguments = listOf(navArgument("code") { type = NavType.StringType }, optionalArg("entryId")),
        ) {
            EntryFormScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.resetTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.backOrLogin() {
    if (!popBackStack()) resetTo(Routes.LOGIN)
}
