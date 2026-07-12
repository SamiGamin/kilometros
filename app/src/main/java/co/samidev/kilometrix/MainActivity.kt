package co.samidev.kilometrix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.presentation.navigation.AppNavGraph
import co.samidev.kilometrix.presentation.navigation.Routes
import co.samidev.kilometrix.ui.theme.Background
import co.samidev.kilometrix.ui.theme.KiloMetrixTheme
import co.samidev.kilometrix.ui.theme.Primary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KiloMetrixTheme {
                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    try {
                        val auth = FirebaseAuth.getInstance()
                        val currentUser = auth.currentUser
                        if (currentUser == null) {
                            startDestination = Routes.ONBOARDING
                        } else {
                            // Recargar usuario para refrescar estado de verificación de correo
                            currentUser.reload().await()
                            if (!currentUser.isEmailVerified) {
                                startDestination = Routes.verifyEmail(currentUser.email ?: "")
                            } else {
                                // Verificar si completó el setup (ciudad registrada)
                                val db = FirebaseFirestore.getInstance()
                                val doc = db.collection("users").document(currentUser.uid).get().await()
                                if (doc.exists() && doc.contains("city") && !doc.getString("city").isNullOrEmpty()) {
                                    startDestination = Routes.MAIN
                                } else {
                                    startDestination = Routes.SETUP
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // En caso de error, ir a onboarding
                        startDestination = Routes.ONBOARDING
                    }
                }

                val destination = startDestination
                if (destination == null) {
                    // Pantalla de carga / Splash premium
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else {
                    AppNavGraph(startDestination = destination)
                }
            }
        }
    }
}