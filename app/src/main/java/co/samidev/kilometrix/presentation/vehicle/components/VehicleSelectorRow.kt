package co.samidev.kilometrix.presentation.vehicle.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.ui.theme.*

@Composable
fun VehicleSelectorRow(
    vehicles: List<Vehicle>,
    selectedVehicleId: String?,
    activeVehicleId: String?,
    hasActiveShift: Boolean,
    onSelectVehicle: (Vehicle) -> Unit,
    onBlockedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(vehicles) { vehicle ->
            val isSelected = vehicle.id == selectedVehicleId
            val isAppActive = vehicle.id == activeVehicleId || (activeVehicleId == null && vehicle.id == vehicles.firstOrNull()?.id)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Color(0xFF131B2E) else SurfaceContainerLow)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) Primary else CardBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        if (hasActiveShift && vehicle.id != activeVehicleId) {
                            onBlockedChange()
                        } else {
                            onSelectVehicle(vehicle)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val typeEmoji = when (vehicle.type) {
                        "MOTO" -> "🏍️"
                        "VAN" -> "🚐"
                        else -> "🚗"
                    }
                    Text(typeEmoji, fontSize = 16.sp)
                    Text(
                        text = vehicle.nickname,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.White else OnSurfaceVariant
                    )
                    if (isAppActive) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Secondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "ACTIVO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = Secondary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
