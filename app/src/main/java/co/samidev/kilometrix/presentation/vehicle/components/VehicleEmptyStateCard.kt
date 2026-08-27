package co.samidev.kilometrix.presentation.vehicle.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.ui.theme.OnSurface
import co.samidev.kilometrix.ui.theme.OnSurfaceVariant
import co.samidev.kilometrix.ui.theme.PrimaryContainer
import co.samidev.kilometrix.ui.theme.SurfaceContainerLow

@Composable
fun VehicleEmptyStateCard(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerLow)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🚗", style = MaterialTheme.typography.displayLarge)
        Text(
            text = "No tienes vehículos registrados",
            style = MaterialTheme.typography.titleMedium,
            color = OnSurface
        )
        Text(
            text = "Agrega tu vehículo para comenzar a monitorear recorridos y gastos.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Agregar Vehículo", color = Color.White)
        }
    }
}
