package co.samidev.kilometrix.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.domain.model.ShiftStatus
import co.samidev.kilometrix.domain.model.ShiftType
import co.samidev.kilometrix.presentation.home.HomeUiState
import co.samidev.kilometrix.ui.theme.*

@Composable
fun HomeActiveShiftCard(
    uiState: HomeUiState,
    onTogglePauseResume: () -> Unit,
    onOpenAddEarning: () -> Unit,
    onFinishShift: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shift = uiState.activeShift ?: return
    val isPaused = shift.status == ShiftStatus.PAUSED

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerLow)
            .border(
                1.dp,
                if (isPaused) Color(0xFFFF9800).copy(alpha = 0.4f)
                else if (shift.type == ShiftType.PERSONAL) Primary.copy(alpha = 0.3f)
                else Secondary.copy(alpha = 0.3f),
                RoundedCornerShape(20.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header de Estado LIVE / PAUSA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(if (shift.type == ShiftType.PERSONAL) "🏠" else "🚕", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "RECORRIDO EN CURSO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (shift.type == ShiftType.PERSONAL) Primary.copy(alpha = 0.15f) else Secondary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (shift.type == ShiftType.PERSONAL) "Personal" else "Trabajo",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (shift.type == ShiftType.PERSONAL) Primary else Secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isPaused) Color(0xFFFF9800).copy(alpha = 0.15f) else Secondary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isPaused) Color(0xFFFF9800).copy(alpha = 0.5f) else Secondary.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PulsingDot(color = if (isPaused) Color(0xFFFF9800) else Secondary)
                    Text(
                        text = if (isPaused) "EN PAUSA" else "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isPaused) Color(0xFFFF9800) else Secondary
                    )
                }
            }
        }

        // Cronómetro vivo gigante
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatShiftMs(uiState.shiftElapsedMs),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isPaused) Color(0xFFFFB74D) else OnSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Inicio: ${formatShiftTimeOnly(shift.startTime)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "Odómetro: ${homeCurrencyFmt.format(shift.initialOdometer)} km",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

        // Métricas de Consumo Estimado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceContainerHigh)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ShiftStatItem(
                label = "Recorrido est.",
                value = "${"%.1f".format(uiState.estimatedKmTraveled)} km"
            )
            ShiftStatItem(
                label = "Combustible",
                value = "~${"%.1f".format(uiState.estimatedGallonsConsumed)} gal"
            )
            ShiftStatItem(
                label = "Costo est.",
                value = "$ ${homeCurrencyFmt.format(uiState.estimatedCostConsumed.toLong())}"
            )
        }

        // Ganancias por App (solo para recorridos de Trabajo)
        if (shift.type == ShiftType.WORK) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GANANCIAS REGISTRADAS",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onOpenAddEarning) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Registrar ingreso", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (shift.earnings.isEmpty()) {
                    Text(
                        text = "No has registrado ingresos en este recorrido.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        shift.earnings.take(4).forEach { earning ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(earning.appEmoji, fontSize = 12.sp)
                                    Text(
                                        text = "$ ${homeCurrencyFmt.format(earning.amount.toLong())}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Banner informativo para Recorrido Personal
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceContainerHigh.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🏠", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Recorrido particular / personal. Midiendo odómetro, tiempo y consumo de combustible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        }

        // Botones de acción (Pausar/Reanudar y Finalizar)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onTogglePauseResume,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isPaused) Secondary else Color(0xFFFF9800)
                )
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Stop,
                    contentDescription = null,
                    tint = if (isPaused) Secondary else Color(0xFFFF9800)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isPaused) "Reanudar" else "Pausar",
                    color = if (isPaused) Secondary else Color(0xFFFF9800),
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onFinishShift,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "🏁 Finalizar",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ShiftStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant
        )
    }
}
