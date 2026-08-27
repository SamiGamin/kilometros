package co.samidev.kilometrix.presentation.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.ui.theme.*

@Composable
fun NoEarningsConfirmDialog(
    onDismiss: () -> Unit,
    onAddEarning: () -> Unit,
    onEndWithoutEarnings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("⚠️", fontSize = 32.sp) },
        title = {
            Text(
                text = "Sin Ganancias Registradas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "Este es un recorrido de trabajo pero no has registrado ninguna ganancia.\n\n¿Deseas agregar una ganancia o finalizar el recorrido sin ingresos?",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddEarning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("➕ Agregar Ganancia", fontWeight = FontWeight.Bold, color = OnPrimary)
                }

                OutlinedButton(
                    onClick = onEndWithoutEarnings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("Finalizar Sin Ganancias", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Volver", color = OnSurfaceVariant)
                }
            }
        },
        dismissButton = null,
        containerColor = Color(0xFF131B2E),
        shape = RoundedCornerShape(24.dp)
    )
}
