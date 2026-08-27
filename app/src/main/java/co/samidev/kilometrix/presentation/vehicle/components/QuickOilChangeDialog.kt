package co.samidev.kilometrix.presentation.vehicle.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.presentation.util.ThousandSeparatorVisualTransformation
import co.samidev.kilometrix.ui.theme.*

@Composable
fun QuickOilChangeDialog(
    vehicle: Vehicle,
    onDismiss: () -> Unit,
    onConfirm: (km: Int) -> Unit
) {
    var kmText by remember { mutableStateOf(vehicle.odometer.takeIf { it > 0 }?.toString() ?: "") }
    val km = kmText.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("🛢️", fontSize = 32.sp) },
        title = {
            Text(
                text = "Registrar Cambio de Aceite",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Ingresa el kilometraje actual en el que realizaste el cambio de aceite para reiniciar el ciclo de alertas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )

                OutlinedTextField(
                    value = kmText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) kmText = it },
                    label = { Text("Kilometraje del cambio") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandSeparatorVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (km > 0) {
                    val nextKm = km + vehicle.oilIntervalKm
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PrimaryContainer.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 Próximo cambio programado a los ${String.format("%,d", nextKm)} km (cada ${String.format("%,d", vehicle.oilIntervalKm)} km)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Primary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (km > 0) onConfirm(km) },
                enabled = km > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Confirmar Cambio", color = OnPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = OnSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = SurfaceContainerLow
    )
}
