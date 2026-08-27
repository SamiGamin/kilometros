package co.samidev.kilometrix.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.domain.model.ShiftType
import co.samidev.kilometrix.presentation.util.ThousandSeparatorVisualTransformation
import co.samidev.kilometrix.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartShiftDialog(
    defaultOdometer: Int,
    onDismiss: () -> Unit,
    onConfirm: (odometer: Int, type: ShiftType) -> Unit
) {
    var odText by remember { mutableStateOf(if (defaultOdometer > 0) defaultOdometer.toString() else "") }
    var selectedType by remember { mutableStateOf(ShiftType.WORK) }
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
                text = "🚀 Iniciar Recorrido",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Selecciona el tipo de recorrido e ingresa el odómetro inicial:",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )

            // Selector de Tipo: Trabajo vs Personal
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedType == ShiftType.WORK) PrimaryContainer.copy(alpha = 0.3f) else SurfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (selectedType == ShiftType.WORK) Primary else Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedType = ShiftType.WORK }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🚕", fontSize = 24.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Trabajo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedType == ShiftType.WORK) Primary else OnSurface
                        )
                        Text(
                            text = "Apps / Ganancias",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedType == ShiftType.PERSONAL) PrimaryContainer.copy(alpha = 0.3f) else SurfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (selectedType == ShiftType.PERSONAL) Primary else Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedType = ShiftType.PERSONAL }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🏠", fontSize = 24.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Personal",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedType == ShiftType.PERSONAL) Primary else OnSurface
                        )
                        Text(
                            text = "Particular / Viajes",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = odText,
                onValueChange = { if (it.all { char -> char.isDigit() }) odText = it },
                label = { Text("Odómetro inicial (km)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = ThousandSeparatorVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
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
                        val od = odText.toIntOrNull() ?: 0
                        if (od > 0) {
                            onConfirm(od, selectedType)
                        }
                    },
                    enabled = (odText.toIntOrNull() ?: 0) > 0
                ) {
                    Text("Iniciar Recorrido")
                }
            }
        }
    }
}
