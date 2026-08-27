package co.samidev.kilometrix.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.ui.theme.*

@Composable
fun HomeStartShiftCard(
    onStartShiftClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerLow)
            .border(1.dp, Secondary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🚀", style = MaterialTheme.typography.displayLarge)
        Text(
            text = "¿Iniciar un nuevo recorrido?",
            style = MaterialTheme.typography.headlineSmall,
            color = OnSurface
        )
        Text(
            text = "Registra tu odómetro inicial para medir tiempo activo, distancia, combustible y gastos o ganancias.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onStartShiftClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Secondary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "Iniciar Recorrido",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = OnSecondary
            )
        }
    }
}
