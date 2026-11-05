package co.samidev.kilometrix.presentation.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.domain.model.ShiftType
import co.samidev.kilometrix.domain.model.WorkShift
import co.samidev.kilometrix.presentation.util.ThousandSeparatorVisualTransformation
import co.samidev.kilometrix.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndShiftDialog(
    shift: WorkShift?,
    elapsedMs: Long,
    totalEarnings: Double,
    suggestedFinalOdometer: Int = 0,
    suggestedFinalOdometerPrecise: Double = 0.0,
    obdEstimatedDistanceKm: Double = 0.0,
    onDismiss: () -> Unit,
    onConfirm: (finalOdometer: Int, finalOdometerPrecise: Double?) -> Unit
) {
    val initOd = shift?.initialOdometer ?: 0
    val initOdPrecise = shift?.initialOdometerPrecise ?: (shift?.initialOdometer?.toDouble() ?: 0.0)
    val initialText = when {
        suggestedFinalOdometerPrecise > (shift?.initialOdometerPrecise ?: (shift?.initialOdometer?.toDouble() ?: 0.0)) + 0.5 ->
            String.format(java.util.Locale.US, "%.2f", suggestedFinalOdometerPrecise)
        else -> ""
    }
    var finalOdText by remember(suggestedFinalOdometerPrecise, initOdPrecise) { mutableStateOf(initialText) }
    val parsedPrecise = finalOdText.replace(',', '.').toDoubleOrNull()
    val parsedInt = parsedPrecise?.toInt() ?: 0
    val diffKm = (parsedPrecise ?: 0.0) - initOdPrecise
    val isExaggerated = diffKm > 1000.0 || (parsedPrecise ?: 0.0) > 1_500_000.0
    val isOdValid = parsedPrecise != null && parsedPrecise >= initOdPrecise && !isExaggerated
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🏁 Finalizar Recorrido",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Confirma el odómetro final del tablero de tu vehículo (pre-calculado con los km medidos por OBD-II para calibración cruzada):",
                style = MaterialTheme.typography.bodyMedium
            )

            val isShortTrip = obdEstimatedDistanceKm > 0.0 && obdEstimatedDistanceKm < 1.0
            if (isShortTrip) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SecondaryContainer.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, Secondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🛣️ Recorrido muy corto", fontWeight = FontWeight.Bold, color = Secondary)
                        Text(
                            "Solo ${String.format("%.2f", obdEstimatedDistanceKm)} km. " +
                            "¿Continuar conduciendo o finalizar?",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            OutlinedTextField(
                value = finalOdText,
                onValueChange = { input ->
                    val normalized = input.replace(',', '.')
                    if (normalized.count { it == '.' } <= 1 && normalized.all { it.isDigit() || it == '.' }) {
                        finalOdText = normalized
                    }
                },
                label = { Text("Odómetro final (km)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = finalOdText.isNotBlank() && !isOdValid,
                supportingText = {
                    when {
                        finalOdText.isNotBlank() && (parsedPrecise ?: 0.0) < initOdPrecise -> {
                            val initDisplay = if (shift?.initialOdometerPrecise != null) {
                                String.format(java.util.Locale.US, "%.2f", shift.initialOdometerPrecise)
                            } else {
                                homeCurrencyFmt.format(initOd)
                            }
                            Text(
                                "Debe ser mayor o igual al inicial ($initDisplay km)",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        finalOdText.isNotBlank() && isExaggerated -> {
                            Text(
                                "⚠️ Son +${String.format(java.util.Locale.US, "%.1f", diffKm)} km. Verifica si escribiste un número de más.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        obdEstimatedDistanceKm > 0.01 -> {
                            // Mostrar distancia OBD precisa cuando está disponible
                            val preciseOdometer = suggestedFinalOdometerPrecise
                            Text(
                                "📡 OBD midió ${String.format(java.util.Locale.US, "%.2f", obdEstimatedDistanceKm)} km " +
                                "(odómetro ~${String.format(java.util.Locale.US, "%.2f", preciseOdometer)} km)",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        else -> {
                            val initDisplay = if (shift?.initialOdometerPrecise != null) {
                                String.format(java.util.Locale.US, "%.2f", shift.initialOdometerPrecise)
                            } else {
                                homeCurrencyFmt.format(initOd)
                            }
                            Text("Odómetro inicial: $initDisplay km")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (isOdValid && parsedInt > 0) {
                val realKm = (parsedPrecise ?: 0.0) - initOdPrecise
                val displayKm = if (obdEstimatedDistanceKm > 0.01) {
                    String.format(java.util.Locale.US, "%.2f km", obdEstimatedDistanceKm)
                } else {
                    String.format(java.util.Locale.US, "%.2f km", realKm)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SecondaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "RESUMEN DEL RECORRIDO",
                            style = MaterialTheme.typography.labelSmall,
                            color = Secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "· Distancia real: $displayKm",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "· Tiempo activo: ${formatShiftMs(elapsedMs)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (shift?.type == ShiftType.WORK) {
                            Text(
                                text = "· Ganancia neta: $ ${homeCurrencyFmt.format(totalEarnings.toLong())}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "· Tipo: 🏠 Personal / Particular",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(if (isShortTrip) "Continuar conduciendo" else "Cancelar")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val pPrecise = finalOdText.replace(',', '.').toDoubleOrNull()
                        val pInt = pPrecise?.toInt() ?: 0
                        if (isOdValid && pInt > 0) {
                            onConfirm(pInt, pPrecise)
                        }
                    },
                    enabled = isOdValid && parsedInt > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isShortTrip) "Finalizar de todas formas" else "Finalizar Recorrido")
                }
            }
        }
    }
}
