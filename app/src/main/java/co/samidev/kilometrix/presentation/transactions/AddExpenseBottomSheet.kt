package co.samidev.kilometrix.presentation.transactions

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelUnit
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.ui.theme.*
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

private val currencyFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("es", "CO"))
    .also { (it as? DecimalFormat)?.applyPattern("#,###") }

data class FuelConfig(
    val emoji: String,
    val typeName: String,
    val defaultUnitLabel: String,
    val unitShort: String,
    val priceLabel: String,
    val efficiencyUnit: String,
    val isElectricOrGnv: Boolean = false,
    val showToggle: Boolean = true
)

private fun getFuelConfig(fuelType: String?): FuelConfig {
    return when (fuelType?.uppercase()) {
        "ELECTRIC" -> FuelConfig(
            emoji = "⚡",
            typeName = "Eléctrico",
            defaultUnitLabel = "Kilovatios (kWh)",
            unitShort = "kWh",
            priceLabel = "Precio / kWh",
            efficiencyUnit = "km/kWh",
            isElectricOrGnv = true,
            showToggle = false
        )
        "GNV" -> FuelConfig(
            emoji = "☁️",
            typeName = "GNV",
            defaultUnitLabel = "Metros cúbicos (m³)",
            unitShort = "m³",
            priceLabel = "Precio / m³",
            efficiencyUnit = "km/m³",
            isElectricOrGnv = true,
            showToggle = false
        )
        "DIESEL" -> FuelConfig(
            emoji = "🛢️",
            typeName = "Diesel / ACPM",
            defaultUnitLabel = "Galones",
            unitShort = "gal",
            priceLabel = "Precio / gal",
            efficiencyUnit = "km/gal"
        )
        "GLP" -> FuelConfig(
            emoji = "🔥",
            typeName = "GLP",
            defaultUnitLabel = "Galones",
            unitShort = "gal",
            priceLabel = "Precio / gal",
            efficiencyUnit = "km/gal"
        )
        else -> FuelConfig(
            emoji = "⛽",
            typeName = "Gasolina",
            defaultUnitLabel = "Galones",
            unitShort = "gal",
            priceLabel = "Precio / gal",
            efficiencyUnit = "km/gal"
        )
    }
}

/**
 * Tipo de tanqueo para el Modelo Multifase.
 * Determina si el registro actúa como hito de calibración del Tanque Virtual.
 */
enum class RefuelCycleType(
    val label: String,
    val emoji: String,
    val description: String,
    val isReserve: Boolean,
    val isFullTank: Boolean,
    val isPartial: Boolean
) {
    RESERVE(
        label = "Reserva",
        emoji = "🪫",
        description = "Testigo encendido",
        isReserve = true,
        isFullTank = false,
        isPartial = false
    ),
    FULL_TANK(
        label = "Lleno",
        emoji = "✅",
        description = "Tanque completo",
        isReserve = false,
        isFullTank = true,
        isPartial = false
    ),
    PARTIAL(
        label = "Parcial",
        emoji = "⛽",
        description = "Tanqueo normal",
        isReserve = false,
        isFullTank = false,
        isPartial = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseBottomSheet(
    vehicle: Vehicle?,
    previousOdometer: Int,
    onDismiss: () -> Unit,
    onSave: (VehicleExpense, FuelUnit, Double, Double, Int, RefuelCycleType) -> Unit,
    viewModel: TransactionsViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        AddExpenseSheetContent(
            vehicle = vehicle,
            previousOdometer = previousOdometer,
            onSave = onSave
        )
    }
}

@Composable
private fun AddExpenseSheetContent(
    vehicle: Vehicle?,
    previousOdometer: Int,
    onSave: (VehicleExpense, FuelUnit, Double, Double, Int, RefuelCycleType) -> Unit
) {
    var selectedType by remember { mutableStateOf(ExpenseType.FUEL) }
    var notesText by remember { mutableStateOf("") }

    // Combustible
    var fuelUnit by remember { mutableStateOf(FuelUnit.GALLON) }
    var quantityText by remember { mutableStateOf("") }
    var pricePerUnitText by remember { mutableStateOf("") }
    var odometerText by remember { mutableStateOf("") }

    // 🆕 Tipo de ciclo del tanqueo
    var refuelCycleType by remember { mutableStateOf(RefuelCycleType.PARTIAL) }

    // Otros gastos
    var amountText by remember { mutableStateOf("") }

    val fuelConfig = remember(vehicle?.fuel) { getFuelConfig(vehicle?.fuel) }

    // ── Derived calculations ────────────────────────────────────────────────────
    val quantity = quantityText.toDoubleOrNull() ?: 0.0
    val pricePerUnit = pricePerUnitText.toDoubleOrNull() ?: 0.0
    val odometerNow = odometerText.toIntOrNull() ?: 0
    val totalFuel = quantity * pricePerUnit
    val kmTraveled = maxOf(0, odometerNow - previousOdometer)
    val litersPerGallon = 3.78541
    val gallons = if (fuelUnit == FuelUnit.GALLON) quantity else quantity / litersPerGallon
    val kmPerGallon = if (gallons > 0.0 && kmTraveled > 0) kmTraveled / gallons else 0.0
    val kmPerLiter = kmPerGallon / litersPerGallon

    // Validation
    val odometerEntered = odometerText.isNotBlank()
    val odometerError: String? = when {
        odometerEntered && odometerNow <= 0 ->
            "El odómetro debe ser mayor a 0"
        odometerEntered && previousOdometer > 0 && odometerNow < previousOdometer ->
            "Debe ser mayor al anterior: ${currencyFormat.format(previousOdometer)} km"
        odometerEntered && previousOdometer > 0 && odometerNow == previousOdometer ->
            "Debe ser mayor al anterior (sin km recorridos)"
        else -> null
    }

    val odometerWarning: String? = if (
        odometerError == null &&
        odometerEntered &&
        previousOdometer > 0 &&
        kmTraveled > 1000
    ) {
        "¡Eso es mucho! Llevas ${currencyFormat.format(kmTraveled)} km desde el último llenado. ¿Estás seguro?"
    } else null

    val odometerIsValid = !odometerEntered || odometerError == null

    val isFuel = selectedType == ExpenseType.FUEL
    val canSave = if (isFuel) {
        quantity > 0.0 && pricePerUnit > 0.0 && vehicle != null && odometerIsValid
    } else {
        (amountText.toDoubleOrNull() ?: 0.0) > 0.0 && vehicle != null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Header Title ────────────────────────────────────────────────────────
        Text(
            text = "Registrar Gasto",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            modifier = Modifier.padding(top = 4.dp)
        )

        // ── HIGH-PROMINENCE ACTIVE VEHICLE BANNER ────────────────────────────────
        if (vehicle != null) {
            val vehicleEmoji = when (vehicle.type) {
                "MOTO" -> "🏍️"
                "VAN" -> "🚐"
                else -> "🚗"
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
                                    text = "· ${fuelConfig.emoji} ${fuelConfig.typeName}",
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

                    // High-contrast plate chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Primary)
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
        }

        // ── Expense type selector ───────────────────────────────────────────────
        Text(
            text = "TIPO DE GASTO",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        ExpenseTypeGrid(
            selected = selectedType,
            fuelConfig = fuelConfig,
            onSelect = { selectedType = it }
        )

        // ── Fuel/Energy specific fields ─────────────────────────────────────────
        AnimatedVisibility(
            visible = isFuel,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Unit toggle only if fuel type supports toggle (Gasolina, Diesel, GLP)
                if (fuelConfig.showToggle) {
                    FuelUnitToggle(selected = fuelUnit, onSelect = { fuelUnit = it })
                }

                val quantityLabel = when {
                    fuelConfig.isElectricOrGnv -> fuelConfig.defaultUnitLabel
                    fuelUnit == FuelUnit.GALLON -> "Galones"
                    else -> "Litros"
                }

                val priceLabel = when {
                    fuelConfig.isElectricOrGnv -> fuelConfig.priceLabel
                    fuelUnit == FuelUnit.GALLON -> "Precio / gal"
                    else -> "Precio / L"
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Quantity / Amount of fuel/energy
                    ExpenseTextField(
                        modifier = Modifier.weight(1f),
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = quantityLabel,
                        prefix = null,
                        keyboardType = KeyboardType.Decimal
                    )
                    // Price per unit
                    ExpenseTextField(
                        modifier = Modifier.weight(1f),
                        value = pricePerUnitText,
                        onValueChange = { pricePerUnitText = it },
                        label = priceLabel,
                        prefix = "$",
                        keyboardType = KeyboardType.Decimal,
                        visualTransformation = co.samidev.kilometrix.presentation.util.ThousandSeparatorVisualTransformation()
                    )
                }

                // Odometer
                ExpenseTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = odometerText,
                    onValueChange = { new ->
                        if (new.all { it.isDigit() }) odometerText = new
                    },
                    label = "Odómetro actual (km)",
                    prefix = null,
                    keyboardType = KeyboardType.Number,
                    visualTransformation = co.samidev.kilometrix.presentation.util.ThousandSeparatorVisualTransformation(),
                    isError = odometerError != null,
                    supportingText = odometerError
                        ?: if (previousOdometer > 0)
                            "Odómetro anterior: ${currencyFormat.format(previousOdometer)} km"
                        else "Registra el odómetro para calcular el rendimiento",
                    supportingTextIsError = odometerError != null
                )

                // Warning banner
                AnimatedVisibility(
                    visible = odometerWarning != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Tertiary.copy(alpha = 0.12f))
                            .border(1.dp, Tertiary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("⚠️", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = odometerWarning ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Tertiary
                        )
                    }
                }

                // 🆕 Selector de tipo de tanqueo (Reserva / Lleno / Parcial)
                // Solo se muestra para combustibles fósiles/GNV (no eléctrico puro)
                if (!fuelConfig.isElectricOrGnv || fuelConfig.typeName == "GNV") {
                    RefuelCycleSelector(
                        selected = refuelCycleType,
                        onSelect = { refuelCycleType = it }
                    )
                }

                // Live efficiency preview
                AnimatedVisibility(visible = kmTraveled > 0 && quantity > 0.0) {
                    FuelPreviewCard(
                        totalAmount = totalFuel,
                        kmTraveled = kmTraveled,
                        kmPerGallon = kmPerGallon,
                        kmPerLiter = kmPerLiter,
                        fuelConfig = fuelConfig,
                        fuelUnit = fuelUnit
                    )
                }

                // Total preview when no odometer
                AnimatedVisibility(visible = totalFuel > 0.0 && kmTraveled == 0) {
                    TotalAmountRow(total = totalFuel)
                }
            }
        }

        // ── Non-fuel amount ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = !isFuel,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ExpenseTextField(
                modifier = Modifier.fillMaxWidth(),
                value = amountText,
                onValueChange = { amountText = it },
                label = "Monto (COP)",
                prefix = "$",
                keyboardType = KeyboardType.Decimal,
                visualTransformation = co.samidev.kilometrix.presentation.util.ThousandSeparatorVisualTransformation()
            )
        }

        // ── Notes ───────────────────────────────────────────────────────────────
        ExpenseTextField(
            modifier = Modifier.fillMaxWidth(),
            value = notesText,
            onValueChange = { notesText = it },
            label = "Notas (opcional)",
            prefix = null,
            keyboardType = KeyboardType.Text,
            maxLines = 3
        )

        // ── Save button ─────────────────────────────────────────────────────────
        Button(
            onClick = {
                if (!canSave || vehicle == null) return@Button
                val finalAmount = if (isFuel) totalFuel else (amountText.toDoubleOrNull() ?: 0.0)
                val expense = VehicleExpense(
                    vehicleId = vehicle.id,
                    type = selectedType,
                    amount = finalAmount,
                    date = System.currentTimeMillis(),
                    notes = notesText.trim()
                )
                onSave(
                    expense,
                    fuelUnit,
                    quantity,
                    pricePerUnit,
                    odometerNow,
                    refuelCycleType   // 🆕 Tipo de ciclo
                )
            },
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "Guardar Gasto",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🆕 Selector de Tipo de Tanqueo (Modelo Multifase)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RefuelCycleSelector(
    selected: RefuelCycleType,
    onSelect: (RefuelCycleType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header con info del tanque virtual
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "TIPO DE TANQUEO",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            // Badge informativo
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Secondary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "Tanque Virtual",
                    style = MaterialTheme.typography.labelSmall,
                    color = Secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Chips de selección
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RefuelCycleType.values().forEach { cycleType ->
                val isSelected = cycleType == selected
                val chipColor = when (cycleType) {
                    RefuelCycleType.RESERVE -> Color(0xFFFF6B35)   // naranja reserva
                    RefuelCycleType.FULL_TANK -> Color(0xFF4CAF50)  // verde lleno
                    RefuelCycleType.PARTIAL -> Secondary            // azul parcial
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) chipColor.copy(alpha = 0.18f)
                            else SurfaceContainerHigh
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 0.dp,
                            color = if (isSelected) chipColor else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelect(cycleType) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = cycleType.emoji,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = cycleType.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) chipColor else OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = cycleType.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) chipColor.copy(alpha = 0.8f) else OnSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Banner contextual según selección
        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "cycle_info"
        ) { cycleSelected ->
            val (color, text) = when (cycleSelected) {
                RefuelCycleType.RESERVE ->
                    Color(0xFFFF6B35) to "🪫 El testigo de reserva como hito A activa la calibración del Tanque Virtual"
                RefuelCycleType.FULL_TANK ->
                    Color(0xFF4CAF50) to "✅ Llenado completo: cierra el ciclo y calcula el rendimiento real km/gal"
                RefuelCycleType.PARTIAL ->
                    Secondary to "⛽ Tanqueo normal: se acumula en el flujo de caja sin calibrar el rendimiento"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.08f))
                    .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.9f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Componentes existentes (sin cambios)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpenseTypeGrid(
    selected: ExpenseType,
    fuelConfig: FuelConfig,
    onSelect: (ExpenseType) -> Unit
) {
    val types = ExpenseType.values()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        types.toList().chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { type ->
                    val isSelected = type == selected
                    val displayEmoji = if (type == ExpenseType.FUEL) fuelConfig.emoji else type.emoji
                    val displayLabel = if (type == ExpenseType.FUEL) fuelConfig.typeName else type.label

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) PrimaryContainer.copy(alpha = 0.2f)
                                else SurfaceContainerHigh
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) PrimaryContainer else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelect(type) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = displayEmoji,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = displayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Primary else OnSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FuelUnitToggle(
    selected: FuelUnit,
    onSelect: (FuelUnit) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerHigh)
            .padding(4.dp)
    ) {
        FuelUnit.values().forEach { unit ->
            val isSelected = unit == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) Secondary else Color.Transparent)
                    .clickable { onSelect(unit) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${unit.label} (${unit.shortLabel})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF003824) else OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExpenseTextField(
    modifier: Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    prefix: String?,
    keyboardType: KeyboardType,
    supportingText: String? = null,
    supportingTextIsError: Boolean = false,
    isError: Boolean = false,
    maxLines: Int = 1,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        prefix = if (prefix != null) ({ Text(prefix) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        maxLines = maxLines,
        shape = RoundedCornerShape(12.dp),
        isError = isError,
        visualTransformation = visualTransformation,
        supportingText = if (supportingText != null) ({
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = if (supportingTextIsError) MaterialTheme.colorScheme.error
                        else OnSurfaceVariant
            )
        }) else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isError) MaterialTheme.colorScheme.error else Primary,
            unfocusedBorderColor = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else OutlineVariant,
            focusedLabelColor = if (isError) MaterialTheme.colorScheme.error else Primary,
            unfocusedLabelColor = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else OnSurfaceVariant,
            focusedTextColor = OnSurface,
            unfocusedTextColor = OnSurface,
            cursorColor = if (isError) MaterialTheme.colorScheme.error else Primary,
            errorBorderColor = MaterialTheme.colorScheme.error,
            errorLabelColor = MaterialTheme.colorScheme.error,
            errorCursorColor = MaterialTheme.colorScheme.error
        )
    )
}

@Composable
private fun FuelPreviewCard(
    totalAmount: Double,
    kmTraveled: Int,
    kmPerGallon: Double,
    kmPerLiter: Double,
    fuelConfig: FuelConfig,
    fuelUnit: FuelUnit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SecondaryContainer.copy(alpha = 0.15f))
            .border(1.dp, Secondary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PreviewStat(label = "Total", value = "$ ${currencyFormat.format(totalAmount.toLong())}")
        PreviewStat(label = "km recorridos", value = "${currencyFormat.format(kmTraveled)} km")

        val effVal = if (fuelConfig.isElectricOrGnv) kmPerGallon else if (fuelUnit == FuelUnit.GALLON) kmPerGallon else kmPerLiter
        val effUnit = if (fuelConfig.isElectricOrGnv) fuelConfig.efficiencyUnit else if (fuelUnit == FuelUnit.GALLON) "km/gal" else "km/L"

        PreviewStat(label = "Rendimiento", value = "${"%.1f".format(effVal)} $effUnit")
        if (!fuelConfig.isElectricOrGnv && fuelUnit == FuelUnit.GALLON) {
            PreviewStat(label = "", value = "${"%.1f".format(kmPerLiter)} km/L")
        }
    }
}

@Composable
private fun TotalAmountRow(total: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerHigh)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Total a pagar", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        Text(
            "$ ${currencyFormat.format(total.toLong())}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Secondary
        )
    }
}

@Composable
private fun PreviewStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Secondary)
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        }
    }
}
