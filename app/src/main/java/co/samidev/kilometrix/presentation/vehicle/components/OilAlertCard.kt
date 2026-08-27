package co.samidev.kilometrix.presentation.vehicle.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.domain.model.OilStatus
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.ui.theme.*

@Composable
fun OilAlertCard(
    vehicle: Vehicle,
    onRegisterOilChangeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lastKm = vehicle.lastOilChangeKm
    val remaining = vehicle.remainingOilKm
    val nextKm = vehicle.nextOilChangeKm
    val status = vehicle.oilStatus

    val isWarning = status == OilStatus.SOON
    val isExpired = status == OilStatus.EXPIRED

    val borderBrush = when {
        isExpired -> Brush.linearGradient(listOf(Color(0xFFFF5252), Color(0xFFFF7A00)))
        isWarning -> Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFFFC107)))
        else -> Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF00B0FF)))
    }

    val cardBg = when {
        isExpired -> Color(0xFF2A141A)
        isWarning -> Color(0xFF2E2213)
        else -> Color(0xFF131B2E)
    }

    val titleText = when {
        lastKm == null -> "Control de Aceite"
        isExpired -> "¡Cambio de aceite vencido!"
        isWarning -> "¡Cambio de aceite próximo!"
        else -> "Cambio de aceite al día"
    }

    val fractionUsed = if (vehicle.oilIntervalKm > 0 && lastKm != null) {
        (1f - (remaining.toFloat() / vehicle.oilIntervalKm.toFloat())).coerceIn(0.05f, 1f)
    } else 0.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.5.dp, borderBrush, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        // NEW badge
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isExpired) Error else Color(0xFFFF9800),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = "NEW",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = titleText,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isExpired) Color(0xFFFF5252) else if (isWarning) Color(0xFFFFB74D) else Color(0xFF00E676)
                )

                Text(
                    text = if (lastKm != null) "Próximo cambio a los ${String.format("%,d", nextKm)} km."
                           else "Sin registro previo.",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = OnSurfaceVariant
                )
            }

            // Segmented / Gradient Progress Bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF202A3F))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fractionUsed)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF00E676),
                                        Color(0xFFFFC107),
                                        Color(0xFFFF5252)
                                    )
                                )
                            )
                    )
                }

                Text(
                    text = if (lastKm != null) {
                        if (remaining >= 0) "Te restan solo ${String.format("%,d", remaining)} km."
                        else "Excedido por ${String.format("%,d", -remaining)} km."
                    } else "Registra odómetro.",
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                    color = OnSurfaceVariant
                )
            }

            // Big Bold Action Button
            Button(
                onClick = onRegisterOilChangeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isExpired) Color(0xFFFF5252) else Color(0xFFFFB300)
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Registrar cambio",
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.Black
                )
            }
        }
    }
}
