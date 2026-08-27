package co.samidev.kilometrix.presentation.home.components

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
    onDismiss: () -> Unit,
    onConfirm: (finalOdometer: Int) -> Unit
) {
    val initOd = shift?.initialOdometer ?: 0
    var finalOdText by remember { mutableStateOf("") }
    val finalOd = finalOdText.toIntOrNull() ?: 0
    val diffKm = finalOd - initOd
    val isExaggerated = diffKm > 1000 || finalOd > 1_500_000
    val isOdValid = finalOd >= initOd && !isExaggerated
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
                text = "Para cerrar tu recorrido, ingresa el odómetro final de tu vehículo:",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = finalOdText,
                onValueChange = { if (it.all { c -> c.isDigit() }) finalOdText = it },
                label = { Text("Odómetro final (km)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = ThousandSeparatorVisualTransformation(),
                isError = finalOdText.isNotBlank() && !isOdValid,
                supportingText = {
                    if (finalOdText.isNotBlank() && finalOd < initOd) {
                        Text("Debe ser mayor o igual al inicial (${homeCurrencyFmt.format(initOd)} km)", color = MaterialTheme.colorScheme.error)
                    } else if (finalOdText.isNotBlank() && isExaggerated) {
                        Text("⚠️ Son +${homeCurrencyFmt.format(diffKm)} km recorridos. Por favor verifica si escribiste un número de más.", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Odómetro inicial: ${homeCurrencyFmt.format(initOd)} km")
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (isOdValid && finalOd > 0) {
                val realKm = finalOd - initOd
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
                            text = "· Distancia real: $realKm km",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (isOdValid && finalOd > 0) {
                            onConfirm(finalOd)
                        }
                    },
                    enabled = isOdValid && finalOd > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Finalizar Recorrido")
                }
            }
        }
    }
}
