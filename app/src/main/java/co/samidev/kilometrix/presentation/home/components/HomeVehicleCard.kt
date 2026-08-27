package co.samidev.kilometrix.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.ui.theme.*

@Composable
fun HomeVehicleCard(
    vehicle: Vehicle,
    modifier: Modifier = Modifier
) {
    val emoji = when (vehicle.type) {
        "MOTO" -> "🏍️"
        "VAN" -> "🚐"
        else -> "🚗"
    }
    val fuelLabel = when (vehicle.fuel) {
        "ELECTRIC" -> "⚡ Eléctrico"
        "GNV" -> "☁️ GNV"
        "DIESEL" -> "🛢️ Diesel"
        "GLP" -> "🔥 GLP"
        else -> "⛽ Gasolina"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerLow)
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SurfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, style = MaterialTheme.typography.titleMedium)
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = vehicle.nickname.ifBlank { vehicle.brand },
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Secondary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "⚡ ACTIVO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Secondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "${vehicle.brand} ${vehicle.model} · Placa ${vehicle.plate.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = fuelLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Primary
            )
            Text(
                text = String.format("%,d km", vehicle.odometer),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant
            )
        }
    }
}
