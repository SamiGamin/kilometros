package co.samidev.kilometrix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.samidev.kilometrix.presentation.navigation.AppNavGraph
import co.samidev.kilometrix.ui.theme.KiloMetrixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KiloMetrixTheme {
                AppNavGraph()
            }
        }
    }
}