package cl.pettech.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Ink900,
    secondary = Coral600,
    onSecondary = Color.White,
    secondaryContainer = Coral100,
    onSecondaryContainer = Ink900,
    background = Mist50,
    onBackground = Ink900,
    surface = Color.White,
    onSurface = Ink900,
    surfaceVariant = Teal50,
    onSurfaceVariant = Ink600,
    error = ErrorRed,
    errorContainer = ErrorContainer,
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = TealDark200,
    onPrimary = Color(0xFF003731),
    primaryContainer = Teal800,
    onPrimaryContainer = Teal50,
    secondary = Color(0xFFFFB59F),
    onSecondary = Color(0xFF5D1900),
    background = SurfaceDark,
    onBackground = Color(0xFFE0E7E6),
    surface = SurfaceDarkVariant,
    onSurface = Color(0xFFE0E7E6),
    surfaceVariant = Color(0xFF2B3B39),
    onSurfaceVariant = Color(0xFFBFC9C7),
)

private val BaseTypography = Typography()

private val PetTechTypography = BaseTypography.copy(
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun PetTechTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PetTechTypography,
        content = content,
    )
}
