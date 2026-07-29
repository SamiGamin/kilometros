package co.samidev.kilometrix.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.domain.model.TRANSPORT_APPS
import co.samidev.kilometrix.domain.model.TransportApp
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.presentation.util.ThousandSeparatorVisualTransformation
import co.samidev.kilometrix.ui.theme.OnSurface
import co.samidev.kilometrix.ui.theme.OnSurfaceVariant
import co.samidev.kilometrix.ui.theme.Primary
import co.samidev.kilometrix.ui.theme.PrimaryContainer
import co.samidev.kilometrix.ui.theme.SurfaceContainerHigh
import co.samidev.kilometrix.ui.theme.SurfaceContainerLow
import java.text.SimpleDateFormat
import java.util.*

/** Devuelve medianoche del día de HOY en zona horaria Bogotá, expresado como UTC midnight para el DatePicker. */
private fun todayAsBogotaDatePickerMs(): Long {
    val bogotaTz = TimeZone.getTimeZone("America/Bogota")
    val bogotaCal = Calendar.getInstance(bogotaTz)
    // Construimos medianoche UTC del mismo día calendario que es hoy en Bogotá
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utcCal.set(
        bogotaCal.get(Calendar.YEAR),
        bogotaCal.get(Calendar.MONTH),
        bogotaCal.get(Calendar.DAY_OF_MONTH),
        0, 0, 0
    )
    utcCal.set(Calendar.MILLISECOND, 0)
    return utcCal.timeInMillis
}

/**
 * Contenido reutilizable del formulario de registro de ganancias.
 * Puede usarse dentro de un ModalBottomSheet o dentro de un TabRow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEarningSheetContent(
    vehicle: Vehicle?,
    onSave: (appName: String, appEmoji: String, amount: Double, isBonus: Boolean, date: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedApp by remember { mutableStateOf<TransportApp?>(TRANSPORT_APPS.first()) }
    var amountText by remember { mutableStateOf("") }
    var isBonus by remember { mutableStateOf(false) }
    // Para el guardado usamos tiempo real en Colombia (System.currentTimeMillis es UTC-agnostic)
    var selectedDateMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val bogotaTz = remember { TimeZone.getTimeZone("America/Bogota") }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale("es", "CO")).apply { timeZone = bogotaTz } }
    val isToday = remember(selectedDateMs) {
        val cal1 = Calendar.getInstance(bogotaTz)
        val cal2 = Calendar.getInstance(bogotaTz).apply { timeInMillis = selectedDateMs }
        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    if (showDatePicker) {
        // initialSelectedDateMillis debe ser medianoche UTC del día actual en Bogotá
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = todayAsBogotaDatePickerMs()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMidnight ->
                        // Convertimos medianoche UTC a medianoche Bogotá sumando el offset negativo
                        val bogotaOffsetMs = bogotaTz.getOffset(utcMidnight)
                        selectedDateMs = utcMidnight - bogotaOffsetMs
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Vehículo activo (UI Unificada) ───────────────────────────────────
        if (vehicle != null) {
            val vehicleEmoji = when (vehicle.type) {
                "MOTO" -> "🏍️"
                "VAN" -> "🚐"
                else -> "🚗"
            }
            val (fuelEmoji, fuelTypeName) = when (vehicle.fuel.uppercase()) {
                "ELECTRIC" -> Pair("⚡", "Eléctrico")
                "GNV" -> Pair("☁️", "GNV")
                "DIESEL" -> Pair("🛢️", "Diesel")
                "GLP" -> Pair("🔥", "GLP")
                else -> Pair("⛽", "Gasolina")
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                PrimaryContainer.copy(alpha = 0.25f),
                                SurfaceContainerHigh
                            )
                        )
                    )
                    .border(1.5.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainer.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(vehicleEmoji, style = MaterialTheme.typography.titleLarge)
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "VEHÍCULO ACTIVO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "· $fuelEmoji $fuelTypeName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = vehicle.nickname.ifBlank { "${vehicle.brand} ${vehicle.model}" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                            Text(
                                text = "${vehicle.brand} ${vehicle.model} · ${vehicle.year}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    // License plate badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Primary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = vehicle.plate.ifBlank { "SIN PLACA" }.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠️ No hay vehículo activo seleccionado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // ── Selector de fecha ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📅 Fecha de registro:", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
            Text(
                text = if (isToday) "Hoy y Ahora" else dateFmt.format(Date(selectedDateMs)),
                style = MaterialTheme.typography.titleSmall,
                color = Primary
            )
        }

        Text(
            text = "Selecciona la aplicación o medio de pago:",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant
        )

        // ── Grid de Apps de Transporte ───────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TRANSPORT_APPS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { app ->
                        val isSelected = selectedApp?.name == app.name
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) PrimaryContainer.copy(alpha = 0.3f) else SurfaceContainerHigh,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) Primary else Color.Transparent
                            ),
                            modifier = Modifier.weight(1f).clickable { selectedApp = app }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.drawableRes != null) {
                                    Image(
                                        painter = painterResource(id = app.drawableRes),
                                        contentDescription = app.name,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Text(app.emoji, fontSize = 20.sp)
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = app.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Primary else OnSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Monto de la carrera / ganancia") },
            prefix = { Text("$ ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = ThousandSeparatorVisualTransformation(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = isBonus, onCheckedChange = { isBonus = it })
            Spacer(Modifier.width(8.dp))
            Text("Es un bono o meta semanal (Ej: Reto dominical)", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val amount = amountText.replace(Regex("[^\\d]"), "").toDoubleOrNull() ?: 0.0
                    val app = selectedApp
                    if (amount > 0.0 && app != null) {
                        onSave(app.name, app.emoji, amount, isBonus, selectedDateMs)
                    }
                },
                enabled = (amountText.replace(Regex("[^\\d]"), "").toDoubleOrNull() ?: 0.0) > 0.0 && vehicle != null
            ) {
                Text("Guardar")
            }
        }
    }
}

/**
 * Wrapper standalone (para usarlo desde HomeScreen con turno activo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEarningBottomSheet(
    vehicle: Vehicle? = null,
    onDismiss: () -> Unit,
    onSave: (appName: String, appEmoji: String, amount: Double, isBonus: Boolean, date: Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        AddEarningSheetContent(vehicle = vehicle, onSave = onSave, onDismiss = onDismiss)
    }
}
