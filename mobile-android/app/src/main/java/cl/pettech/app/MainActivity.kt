package cl.pettech.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.pettech.app.ui.navigation.PetTechRoot
import cl.pettech.app.ui.theme.PetTechTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PetTechApp).container
        setContent {
            PetTechTheme {
                PetTechRoot(sessionManager = container.sessionManager)
            }
        }
    }
}
