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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.ui.theme.*

@Composable
fun HomeMainVehicleShiftCard(
    vehicle: Vehicle?,
    onStartShiftClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerLow)
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (vehicle != null) {
            val fuelLabel = when (vehicle.fuel) {
                "ELECTRIC" -> "Eléctrico"
                "GNV" -> "GNV"
                "DIESEL" -> "Diesel"
                "GLP" -> "GLP"
                else -> "Gasolina"
            }

            // Top row: Tu Vehículo: <Nickname> + ACTIVO badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tu Vehículo: ${vehicle.nickname.ifBlank { vehicle.brand }}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnSurface
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Secondary.copy(alpha = 0.15f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("⚡", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = "ACTIVO",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Secondary
                        )
                    }
                }
            }

            // Subtitle: Brand Model • Placa QIR098
            Text(
                text = "${vehicle.brand} ${vehicle.model} • Placa ${vehicle.plate.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )

            // Two stats: Combustible & Odómetro
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("⛽", fontSize = 16.sp)
                    Text(
                        text = fuelLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = OnSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("⏱️", fontSize = 16.sp)
                    Column {
                        Text(
                            text = "Odómetro",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = OnSurfaceVariant
                        )
                        Text(
                            text = String.format("%,d km", vehicle.odometer),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnSurface
                        )
                    }
                }
            }

            HorizontalDivider(
                color = OutlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        // ¿Iniciar un nuevo recorrido? section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "¿Iniciar un nuevo recorrido?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = OnSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Registra tu odómetro inicial para medir tiempo activo, distancia, combustible y gastos o ganancias.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Green CTA button: Iniciar Recorrido 🚀
        Button(
            onClick = onStartShiftClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Secondary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Iniciar Recorrido",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0D1B1E)
                )
                Text("🚀", fontSize = 16.sp)
            }
        }
    }
}
