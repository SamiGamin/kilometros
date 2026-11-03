package co.samidev.kilometrix.presentation.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.domain.model.FuelEfficiencySummary
import co.samidev.kilometrix.ui.theme.*

@Composable
fun HomeFuelEfficiencyCard(
    summary: FuelEfficiencySummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PrimaryContainer.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "⚡ RENDIMIENTO DE COMBUSTIBLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Ver en Gastos",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Ir a Gastos",
                        tint = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val kpg = summary.kmPerGallonAverage
                        ?: summary.averageKmPerGallon.takeIf { it > 0.0 }
                        ?: 0.0
                    Text(
                        if (kpg > 0.0) "${"%.1f".format(kpg)} km/gal" else "⏳ Calibrando",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (kpg > 0.0) Primary else OnSurfaceVariant
                    )
                    Text("Promedio real", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val cpk = if (summary.costPerKmReal > 0) summary.costPerKmReal else summary.costPerKm
                    Text(
                        if (cpk > 0) "$ ${homeCurrencyFmt.format(cpk.toLong())}/km" else "--",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Secondary
                    )
                    Text("Costo por km", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }

                if (summary.totalKmTraveled > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${homeCurrencyFmt.format(summary.totalKmTraveled)} km",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = OnSurface
                        )
                        Text("Total recorrido", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }

            // Sonda de capacidad Reserva -> Lleno si se detectó transición no homóloga (Punto 4)
            if (summary.measuredReserveToFullGallons != null && summary.measuredReserveToFullGallons > 0.0) {
                Text(
                    text = "📐 Capacidad útil medida (Reserva → Lleno): ${"%.2f".format(summary.measuredReserveToFullGallons)} gal",
                    style = MaterialTheme.typography.labelSmall,
                    color = Secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Prompt explícito cuando el ciclo lleva >7 días o >10 gal en parciales sin cerrar (Punto 9)
            if (summary.needsCycleClosurePrompt && !summary.cycleClosurePromptMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SecondaryContainer.copy(alpha = 0.25f),
                    border = BorderStroke(0.5.dp, Secondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = summary.cycleClosurePromptMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
