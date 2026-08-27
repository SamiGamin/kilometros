package co.samidev.kilometrix.presentation.vehicle.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.domain.model.OilStatus
import co.samidev.kilometrix.domain.model.PicoPlacaStatus
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.ui.theme.*

@Composable
fun VehicleMetricsGrid(
    vehicle: Vehicle,
    picoPlacaStatus: PicoPlacaStatus,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Fila 1: Odómetro Actual & Mantenimiento Aceite
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Odómetro Actual
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF131B2E))
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Odómetro actual",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = String.format("%,d km", vehicle.odometer),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = OnSurface,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )

                    OdometerWaveCanvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )
                }
            }

            // Card 2: Mantenimiento Aceite
            val remainingKm = vehicle.remainingOilKm
            val oilFraction = if (vehicle.oilIntervalKm > 0) {
                (remainingKm.toFloat() / vehicle.oilIntervalKm.toFloat()).coerceIn(0f, 1f)
            } else 0.5f

            val gaugeColor = when (vehicle.oilStatus) {
                OilStatus.EXPIRED -> Error
                OilStatus.SOON -> Color(0xFFFF9800)
                OilStatus.OK -> Secondary
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF131B2E))
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                // NEW Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Secondary.copy(alpha = 0.2f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "NEW",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                        color = Secondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OilArcGaugeCanvas(
                            fraction = oilFraction,
                            color = gaugeColor,
                            modifier = Modifier.fillMaxSize()
                        )
                        Text("🛢️", fontSize = 14.sp)
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Mantenimiento Aceite",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = OnSurface,
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        if (vehicle.lastOilChangeKm != null) {
                            Text(
                                text = "Próximo: ${String.format("%,d", vehicle.nextOilChangeKm)} km",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = OnSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            Text(
                                text = if (remainingKm >= 0) "Te restan ${String.format("%,d", remainingKm)} km"
                                       else "Excedido por ${String.format("%,d", -remainingKm)} km",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (remainingKm < 0) Error else Secondary,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        } else {
                            Text(
                                text = "Sin registro previo",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = OnSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                }
            }
        }

        // Fila 2: Restricción (Pico y Placa) & Seguridad (Extintor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 3: Restricción (Pico y Placa)
            val isRestricted = picoPlacaStatus.isRestrictedNow
            val ppBorder = if (isRestricted) Error.copy(alpha = 0.6f) else Primary.copy(alpha = 0.5f)
            val ppBg = if (isRestricted) ErrorContainer.copy(alpha = 0.15f) else Color(0xFF131B2E)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ppBg)
                    .border(1.5.dp, ppBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (isRestricted) Error else Primary).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isRestricted) Error else Primary
                            ),
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Text(
                            text = "Restricción",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isRestricted) Color(0xFFFF5252) else Primary,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = if (picoPlacaStatus.hasData) picoPlacaStatus.statusText
                               else "Placa ${vehicle.plate.uppercase()}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = OnSurface,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = if (picoPlacaStatus.hasData) picoPlacaStatus.subtext
                               else "Sin información de tráfico",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = OnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Card 4: Seguridad (Kit de carretera / Extintor)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF131B2E))
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                // NEW Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Secondary.copy(alpha = 0.2f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "NEW",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                        color = Secondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🧯", fontSize = 20.sp)
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Seguridad",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = OnSurface,
                                fontSize = 11.sp,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = "Kit de carretera",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = OnSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = if (!vehicle.extinguisherExpiry.isNullOrBlank()) "Vence: ${vehicle.extinguisherExpiry}"
                                   else "Sin fecha extintor",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (!vehicle.extinguisherExpiry.isNullOrBlank()) Secondary else OnSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
            }
        }
    }
}
