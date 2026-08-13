package co.samidev.kilometrix.presentation.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.ui.theme.*
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val currencyFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"))
    .also { (it as? DecimalFormat)?.applyPattern("#,###") }

@Composable
fun AdjustReserveDialog(
    initialReservePercent: Int,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (newPercent: Int) -> Unit
) {
    var reservePercent by remember(initialReservePercent) { 
        mutableFloatStateOf(initialReservePercent.coerceIn(5, 25).toFloat()) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔧", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Ajustar Reserva",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = OnSurface
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Define qué porcentaje de tus ingresos brutos destinarás como fondo preventivo para mantenimiento y repuestos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerHigh.copy(alpha = 0.6f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Porcentaje Destinado",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnSurface
                        )
                        Text(
                            text = "${reservePercent.roundToInt()}%",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Tertiary
                        )
                    }

                    Slider(
                        value = reservePercent,
                        onValueChange = { reservePercent = it },
                        valueRange = 5f..25f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = Tertiary,
                            activeTrackColor = Tertiary,
                            inactiveTrackColor = OutlineVariant.copy(alpha = 0.3f)
                        )
                    )

                    val sampleGross = 100000.0
                    val calculatedReserve = (sampleGross * reservePercent.roundToInt() / 100.0).toLong()
                    val formattedAmount = currencyFormatter.format(calculatedReserve)

                    Text(
                        text = "💡 Ahorras $$formattedAmount COP por cada $100.000 COP brutos generados.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = OnSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(reservePercent.roundToInt()) },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = OnPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Guardar %", color = OnPrimary, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancelar")
            }
        }
    )
}
