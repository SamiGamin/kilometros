package co.samidev.kilometrix.presentation.vehicle.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.presentation.util.ThousandSeparatorVisualTransformation
import co.samidev.kilometrix.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class VehicleTypeSelection(val displayName: String, val emoji: String, val defaultInterval: Int, val presets: List<Int>) {
    PARTICULAR("Particular", "🚗", 10000, listOf(5000, 7500, 10000)),
    TAXI("Taxi", "🚖", 5000, listOf(5000, 6000, 7500)),
    MOTO("Moto", "🏍️", 3000, listOf(2500, 3000, 5000)),
    VAN("Camioneta / Van", "🚐", 7500, listOf(5000, 7500, 10000))
}

enum class FuelTypeSelection(val displayName: String) {
    GASOLINE("Gasolina"),
    DIESEL("Diesel"),
    GNV("GNV (Gas)"),
    GLP("GLP"),
    ELECTRIC("Eléctrico")
}

private enum class ExpiryDateField {
    SOAT, TECNOMEC, SEGURO, EXTINGUISHER
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleFormDialog(
    title: String,
    vehicle: Vehicle?,
    onDismiss: () -> Unit,
    onSave: (Vehicle) -> Unit
) {
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    var selectedVehicleType by remember(vehicle) {
        mutableStateOf(
            VehicleTypeSelection.entries.find { it.name == vehicle?.type } ?: VehicleTypeSelection.PARTICULAR
        )
    }
    var selectedFuelType by remember(vehicle) {
        mutableStateOf(
            FuelTypeSelection.entries.find { it.name == vehicle?.fuel } ?: FuelTypeSelection.GASOLINE
        )
    }
    var nickname by remember(vehicle) { mutableStateOf(vehicle?.nickname.orEmpty()) }
    var brand by remember(vehicle) { mutableStateOf(vehicle?.brand.orEmpty()) }
    var model by remember(vehicle) { mutableStateOf(vehicle?.model.orEmpty()) }
    var yearStr by remember(vehicle) { mutableStateOf(vehicle?.year?.toString() ?: currentYear.toString()) }
    var plate by remember(vehicle) { mutableStateOf(vehicle?.plate.orEmpty()) }
    var odometerStr by remember(vehicle) { mutableStateOf(vehicle?.odometer?.toString() ?: "0") }
    var lastOilChangeKmStr by remember(vehicle) { mutableStateOf(vehicle?.lastOilChangeKm?.toString() ?: "") }
    var oilIntervalKmStr by remember(vehicle) {
        mutableStateOf(vehicle?.oilIntervalKm?.toString() ?: selectedVehicleType.defaultInterval.toString())
    }
    var soatExpiry by remember(vehicle) { mutableStateOf(vehicle?.soatExpiry.orEmpty()) }
    var tecnomecExpiry by remember(vehicle) { mutableStateOf(vehicle?.tecnomecExpiry.orEmpty()) }
    var seguroExpiry by remember(vehicle) { mutableStateOf(vehicle?.seguroExpiry.orEmpty()) }
    var extinguisherExpiry by remember(vehicle) { mutableStateOf(vehicle?.extinguisherExpiry.orEmpty()) }

    // Estados para los pickers
    var showYearPicker by remember { mutableStateOf(false) }
    var activeDateField by remember { mutableStateOf<ExpiryDateField?>(null) }
    val datePickerState = rememberDatePickerState()

    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    val isFormValid = plate.isNotBlank() && brand.isNotBlank() && yearStr.toIntOrNull() != null
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
                text = title,
                color = OnSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Selector de Tipo de Vehículo
                Text(
                    text = "Tipo de vehículo",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VehicleTypeSelection.entries.forEach { type ->
                        FilterChip(
                            selected = type == selectedVehicleType,
                            onClick = {
                                selectedVehicleType = type
                                if (vehicle == null) {
                                    oilIntervalKmStr = type.defaultInterval.toString()
                                }
                            },
                            label = { Text("${type.emoji} ${type.displayName}") }
                        )
                    }
                }

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Apodo del vehículo (opcional)") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Marca *") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Modelo / Línea") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Campo Año con Selector Modal
                    OutlinedTextField(
                        value = yearStr,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Año *") },
                        trailingIcon = {
                            IconButton(onClick = { showYearPicker = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Seleccionar año")
                            }
                        },
                        interactionSource = remember { MutableInteractionSource() }.also { interactionSource ->
                            LaunchedEffect(interactionSource) {
                                interactionSource.interactions.collect {
                                    if (it is PressInteraction.Release) showYearPicker = true
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = plate,
                        onValueChange = { if (it.length <= 6) plate = it.uppercase().trim() },
                        label = { Text("Placa *") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Selector de Combustible
                Text(
                    text = "Tipo de combustible",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FuelTypeSelection.entries.forEach { fuel ->
                        FilterChip(
                            selected = fuel == selectedFuelType,
                            onClick = { selectedFuelType = fuel },
                            label = { Text(fuel.displayName) }
                        )
                    }
                }

                OutlinedTextField(
                    value = odometerStr,
                    onValueChange = { if (it.all { c -> c.isDigit() }) odometerStr = it },
                    label = { Text("Odómetro actual (km)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    visualTransformation = ThousandSeparatorVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ── SECCIÓN MANTENIMIENTO DE ACEITE ──
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = OutlineVariant.copy(alpha = 0.4f))
                Text(
                    text = "🛢️ Control de cambio de aceite (opcional)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnSurfaceVariant
                )

                OutlinedTextField(
                    value = lastOilChangeKmStr,
                    onValueChange = { if (it.all { c -> c.isDigit() }) lastOilChangeKmStr = it },
                    label = { Text("Último cambio realizado (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandSeparatorVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Frecuencia / Intervalo de cambio",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedVehicleType.presets.forEach { preset ->
                        val isPresetSelected = oilIntervalKmStr == preset.toString()
                        FilterChip(
                            selected = isPresetSelected,
                            onClick = { oilIntervalKmStr = preset.toString() },
                            label = { Text("${String.format("%,d", preset)} km") }
                        )
                    }
                }

                OutlinedTextField(
                    value = oilIntervalKmStr,
                    onValueChange = { if (it.all { c -> c.isDigit() }) oilIntervalKmStr = it },
                    label = { Text("Intervalo personalizado (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandSeparatorVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val lastOilKm = lastOilChangeKmStr.toIntOrNull()
                val intervalKm = oilIntervalKmStr.toIntOrNull() ?: selectedVehicleType.defaultInterval
                val currentOd = odometerStr.toIntOrNull() ?: 0
                if (lastOilKm != null && lastOilKm > 0) {
                    val nextKm = lastOilKm + intervalKm
                    val remKm = nextKm - currentOd
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (remKm < 0) ErrorContainer.copy(alpha = 0.2f) else PrimaryContainer.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (remKm >= 0) {
                                "💡 Próximo cambio programado a los ${String.format("%,d", nextKm)} km (te restan ${String.format("%,d", remKm)} km)"
                            } else {
                                "⚠️ Próximo cambio debió ser a los ${String.format("%,d", nextKm)} km (excedido por ${String.format("%,d", -remKm)} km)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (remKm < 0) MaterialTheme.colorScheme.error else Primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // ── SECCIÓN VENCIMIENTOS DE DOCUMENTOS Y SEGURIDAD ──
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = OutlineVariant.copy(alpha = 0.4f))
                Text(
                    text = "Vencimientos de documentos y seguridad (opcional)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnSurfaceVariant
                )

                // Vencimiento SOAT
                DatePickerField(
                    label = "Vencimiento SOAT",
                    value = soatExpiry,
                    onClear = { soatExpiry = "" },
                    onClick = { activeDateField = ExpiryDateField.SOAT }
                )

                // Vencimiento Tecnomecánica
                DatePickerField(
                    label = "Vencimiento Tecnomecánica",
                    value = tecnomecExpiry,
                    onClear = { tecnomecExpiry = "" },
                    onClick = { activeDateField = ExpiryDateField.TECNOMEC }
                )

                // Vencimiento Seguro Todo Riesgo
                DatePickerField(
                    label = "Vencimiento Seguro Todo Riesgo",
                    value = seguroExpiry,
                    onClear = { seguroExpiry = "" },
                    onClick = { activeDateField = ExpiryDateField.SEGURO }
                )

                // Vencimiento Extintor / Kit de carretera
                DatePickerField(
                    label = "Vencimiento Extintor / Kit de carretera",
                    value = extinguisherExpiry,
                    onClear = { extinguisherExpiry = "" },
                    onClick = { activeDateField = ExpiryDateField.EXTINGUISHER }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = OnSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = isFormValid,
                    onClick = {
                        val year = yearStr.toIntOrNull() ?: currentYear
                        val odometer = odometerStr.toIntOrNull() ?: 0
                        val finalNickname = nickname.trim().ifEmpty {
                            "${selectedVehicleType.emoji} ${brand.trim().ifEmpty { "Mi Vehículo" }}"
                        }
                        val lastOil = lastOilChangeKmStr.toIntOrNull()
                        val oilInterval = oilIntervalKmStr.toIntOrNull() ?: selectedVehicleType.defaultInterval

                        val updated = (vehicle ?: Vehicle()).copy(
                            type = selectedVehicleType.name,
                            nickname = finalNickname,
                            brand = brand.trim(),
                            model = model.trim(),
                            year = year,
                            plate = plate.uppercase().trim(),
                            fuel = selectedFuelType.name,
                            odometer = odometer,
                            lastOilChangeKm = lastOil,
                            oilIntervalKm = oilInterval,
                            soatExpiry = soatExpiry.trim(),
                            tecnomecExpiry = tecnomecExpiry.trim(),
                            seguroExpiry = seguroExpiry.trim(),
                            extinguisherExpiry = extinguisherExpiry.trim()
                        )
                        onSave(updated)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Guardar", color = OnPrimary)
                }
            }
        }
    }

    // Diálogo Selector de Años
    if (showYearPicker) {
        YearPickerDialog(
            selectedYear = yearStr.toIntOrNull() ?: currentYear,
            onYearSelected = { year ->
                yearStr = year.toString()
                showYearPicker = false
            },
            onDismiss = { showYearPicker = false }
        )
    }

    // Diálogo Calendario DatePicker Material 3
    if (activeDateField != null) {
        DatePickerDialog(
            onDismissRequest = { activeDateField = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formattedDate = dateFormatter.format(Date(millis))
                            when (activeDateField) {
                                ExpiryDateField.SOAT -> soatExpiry = formattedDate
                                ExpiryDateField.TECNOMEC -> tecnomecExpiry = formattedDate
                                ExpiryDateField.SEGURO -> seguroExpiry = formattedDate
                                ExpiryDateField.EXTINGUISHER -> extinguisherExpiry = formattedDate
                                null -> Unit
                            }
                        }
                        activeDateField = null
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeDateField = null }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun DatePickerField(
    label: String,
    value: String,
    onClear: () -> Unit,
    onClick: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("dd/mm/aaaa") },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Borrar fecha")
                }
            } else {
                IconButton(onClick = onClick) {
                    Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha")
                }
            }
        },
        interactionSource = remember { MutableInteractionSource() }.also { interactionSource ->
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect {
                    if (it is PressInteraction.Release) onClick()
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun YearPickerDialog(
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    minYear: Int = 1980,
    maxYear: Int = Calendar.getInstance().get(Calendar.YEAR) + 1
) {
    val years = remember(minYear, maxYear) { (maxYear downTo minYear).toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecciona el año del vehículo") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                items(years) { year ->
                    val isSelected = year == selectedYear
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PrimaryContainer else SurfaceContainerLow)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Primary else OutlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onYearSelected(year) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) OnPrimaryContainer else OnSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}