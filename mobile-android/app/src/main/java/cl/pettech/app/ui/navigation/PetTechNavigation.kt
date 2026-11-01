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
import androidx.compose.runtime.remember
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
import cl.pettech.app.data.session.LogoutReason
import cl.pettech.app.data.session.SessionManager
import cl.pettech.app.data.session.SessionState
import cl.pettech.app.domain.model.Role
import cl.pettech.app.ui.components.PetTechLogo
import cl.pettech.app.ui.screens.auth.LoginScreen
import cl.pettech.app.ui.screens.auth.RegisterScreen
import cl.pettech.app.ui.screens.home.ProfessionalHomeScreen
import cl.pettech.app.ui.screens.home.TutorHomeScreen
import cl.pettech.app.ui.screens.pets.PetDetailScreen
import cl.pettech.app.ui.screens.pets.PetFormScreen
import cl.pettech.app.ui.screens.pets.PetListScreen
import cl.pettech.app.ui.screens.welcome.WelcomeScreen

object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"

    const val TUTOR_HOME = "tutor/home"
    const val PET_LIST = "tutor/pets"
    const val PET_NEW = "tutor/pet-new"
    const val PET_DETAIL = "tutor/pet/{petId}"
    const val PET_EDIT = "tutor/pet/{petId}/edit"

    const val PRO_HOME = "pro/home"

    val AUTH = setOf(WELCOME, LOGIN, REGISTER)

    fun petDetail(petId: String) = "tutor/pet/$petId"
    fun petEdit(petId: String) = "tutor/pet/$petId/edit"

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

@Composable
private fun PetTechNavHost(sessionState: SessionState) {
    val navController = rememberNavController()
    // Destino inicial calculado una sola vez; los cambios posteriores de sesión se manejan abajo.
    val startDestination = remember {
        if (sessionState is SessionState.LoggedIn) Routes.homeFor(sessionState.user.role) else Routes.WELCOME
    }

    // Reacciona a inicio/cierre/expiración de sesión limpiando la pila de navegación.
    LaunchedEffect(sessionState) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        when (sessionState) {
            is SessionState.LoggedIn -> {
                if (currentRoute == null || currentRoute in Routes.AUTH) {
                    navController.resetTo(Routes.homeFor(sessionState.user.role))
                }
            }
            is SessionState.LoggedOut -> {
                if (currentRoute != null && currentRoute !in Routes.AUTH) {
                    val target = if (sessionState.reason == LogoutReason.EXPIRED) Routes.LOGIN else Routes.WELCOME
                    navController.resetTo(target)
                }
            }
            SessionState.Loading -> Unit
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onLogin = { navController.navigate(Routes.LOGIN) },
                onRegister = { navController.navigate(Routes.REGISTER) },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onGoToRegister = { navController.navigate(Routes.REGISTER) },
                onBack = { navController.backOrWelcome() },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(onBack = { navController.backOrWelcome() })
        }

        composable(Routes.TUTOR_HOME) {
            TutorHomeScreen(
                onOpenPets = { navController.navigate(Routes.PET_LIST) },
                onAddPet = { navController.navigate(Routes.PET_NEW) },
            )
        }
        composable(Routes.PET_LIST) {
            PetListScreen(
                onBack = { navController.popBackStack() },
                onAddPet = { navController.navigate(Routes.PET_NEW) },
                onOpenPet = { petId -> navController.navigate(Routes.petDetail(petId)) },
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
        composable(
            route = Routes.PET_DETAIL,
            arguments = listOf(navArgument("petId") { type = NavType.StringType }),
        ) {
            PetDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { petId -> navController.navigate(Routes.petEdit(petId)) },
                onDeleted = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PET_EDIT,
            arguments = listOf(navArgument("petId") { type = NavType.StringType }),
        ) {
            PetFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(Routes.PRO_HOME) {
            ProfessionalHomeScreen()
        }
    }
}

private fun NavHostController.resetTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.backOrWelcome() {
    if (!popBackStack()) resetTo(Routes.WELCOME)
}
