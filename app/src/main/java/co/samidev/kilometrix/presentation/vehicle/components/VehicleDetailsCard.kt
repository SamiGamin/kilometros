package co.samidev.kilometrix.presentation.vehicle.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.domain.model.PicoPlacaStatus
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.ui.theme.*

@Composable
fun VehicleDetailsCard(
    vehicle: Vehicle,
    isAppActive: Boolean,
    picoPlacaStatus: PicoPlacaStatus,
    hasActiveShift: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onRegisterOilChange: () -> Unit,
    onUpdateVehicle: (Vehicle) -> Unit,
    onBlockedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceContainerLow)
            .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
    ) {
        // 1. 3D Vehicle Hero Section
        Vehicle3DHero(
            vehicle = vehicle,
            isAppActive = isAppActive,
            hasActiveShift = hasActiveShift,
            onActivate = onActivate,
            onEdit = onEdit,
            onBlockedChange = onBlockedChange
        )

        // 2. 2x2 Modernized Metrics Grid
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            VehicleMetricsGrid(
                vehicle = vehicle,
                picoPlacaStatus = picoPlacaStatus
            )
        }

        Spacer(Modifier.height(16.dp))

        // 3. Documentos y Alerta Inteligente
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "DOCUMENTOS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = OnSurfaceVariant
            )

            // Split section: Left documents + Right Oil Alert with exact Intrinsic Max height
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left Column: SOAT & Tecnomecánica (equal heights)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DocumentCard(
                        icon = Icons.Default.Shield,
                        title = "SOAT",
                        expiryDate = vehicle.soatExpiry.orEmpty(),
                        isEnabled = vehicle.soatEnabled,
                        onToggle = { enabled -> onUpdateVehicle(vehicle.copy(soatEnabled = enabled)) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    DocumentCard(
                        icon = Icons.Default.Build,
                        title = "Tecno-mecánica",
                        expiryDate = vehicle.tecnomecExpiry.orEmpty(),
                        isEnabled = vehicle.tecnomecEnabled,
                        onToggle = { enabled -> onUpdateVehicle(vehicle.copy(tecnomecEnabled = enabled)) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                // Right Column: Smart Oil Alert Card
                Box(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight()
                ) {
                    OilAlertCard(
                        vehicle = vehicle,
                        onRegisterOilChangeClick = onRegisterOilChange,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }

            // Bottom Full Width: Seguro Todo Riesgo
            DocumentCard(
                icon = Icons.Default.DateRange,
                title = "Seguro Todo Riesgo",
                expiryDate = vehicle.seguroExpiry.orEmpty(),
                isEnabled = vehicle.seguroEnabled,
                onToggle = { enabled -> onUpdateVehicle(vehicle.copy(seguroEnabled = enabled)) }
            )
        }
    }
}
