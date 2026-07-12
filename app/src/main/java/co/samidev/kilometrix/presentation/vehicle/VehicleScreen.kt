package co.samidev.kilometrix.presentation.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.R
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private enum class VehicleType(val labelRes: Int, val emoji: String) {
    CAR(R.string.setup_vehicle_type_car, "🚗"),
    MOTO(R.string.setup_vehicle_type_moto, "🏍️"),
    VAN(R.string.setup_vehicle_type_van, "🚐")
}

private enum class FuelType(val labelRes: Int, val emoji: String) {
    GASOLINE(R.string.setup_vehicle_fuel_gasoline, "⛽"),
    DIESEL(R.string.setup_vehicle_fuel_diesel, "🛢️"),
    GLP(R.string.setup_vehicle_fuel_glp, "🔥"),
    GNV(R.string.setup_vehicle_fuel_gnv, "☁️"),
    ELECTRIC(R.string.setup_vehicle_fuel_electric, "⚡")
}

@Composable
fun VehicleScreen() {
    val viewModel: VehicleViewModel = hiltViewModel()
    val vehicles by viewModel.vehicles.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Sync selected vehicle when list changes
    LaunchedEffect(vehicles) {
        if (selectedVehicle == null && vehicles.isNotEmpty()) {
            selectedVehicle = vehicles.first()
        } else if (selectedVehicle != null) {
            // Update selected vehicle instance with latest data
            selectedVehicle = vehicles.find { it.id == selectedVehicle?.id } ?: vehicles.firstOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(48.dp))

        // Title and "+" button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.vehicle_title), style = MaterialTheme.typography.headlineMedium, color = OnSurface)
                val countText = if (vehicles.isEmpty()) "Sin vehículos" else "${vehicles.size} vehículo(s) registrado(s)"
                Text(countText, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .background(PrimaryContainer.copy(alpha = 0.2f), CircleShape)
                    .size(40.dp)
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge, color = Primary)
            }
        }

        if (vehicles.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceContainerLow)
                    .clickable { showAddDialog = true }
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🚗", style = MaterialTheme.typography.displayLarge)
                    Text("No tienes vehículos registrados", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text("Toca aquí para registrar tu primer vehículo y empezar a trackear.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            // Horizontal switch for multiple vehicles
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(vehicles) { vehicle ->
                    val isSelected = vehicle.id == selectedVehicle?.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PrimaryContainer.copy(alpha = 0.2f) else SurfaceContainerLow)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Primary else OutlineVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedVehicle = vehicle }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val typeEmoji = when (vehicle.type) {
                                "MOTO" -> "🏍️"
                                "VAN" -> "🚐"
                                else -> "🚗"
                            }
                            Text(typeEmoji)
                            Text(
                                text = vehicle.nickname,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Primary else OnSurface
                            )
                        }
                    }
                }
            }

            selectedVehicle?.let { vehicle ->
                // Selected vehicle details card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainerLow)
                        .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                ) {
                    // Top hero area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PrimaryContainer.copy(alpha = 0.1f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainer.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val emoji = when (vehicle.type) {
                                "MOTO" -> "🏍️"
                                "VAN" -> "🚐"
                                else -> "🚗"
                            }
                            Text(emoji, style = MaterialTheme.typography.displayLarge)
                        }
                        Box(modifier = Modifier.align(Alignment.TopStart)) {
                            Surface(shape = RoundedCornerShape(6.dp), color = SurfaceContainerHigh) {
                                val fuelLabel = when (vehicle.fuel) {
                                    "DIESEL" -> "Diesel"
                                    "GLP" -> "GLP"
                                    "GNV" -> "GNV"
                                    "ELECTRIC" -> "Eléctrico"
                                    else -> "Gasolina"
                                }
                                Text(fuelLabel, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }

                    // Details
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${vehicle.brand} ${vehicle.model} · ${vehicle.year}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                Text(vehicle.plate.ifEmpty { "SIN PLACA" }.uppercase(), style = MaterialTheme.typography.headlineMedium, color = OnSurface)
                                Text(String.format("%,d km odómetro", vehicle.odometer), style = MaterialTheme.typography.bodySmall, color = Primary)
                            }
                            Button(
                                onClick = { showEditDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Editar", style = MaterialTheme.typography.labelMedium, color = Primary)
                            }
                        }

                        // Stats grid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Background)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            VehicleStat(String.format("%,d", vehicle.odometer), "Odometer (km)")
                            VerticalDivider(modifier = Modifier.height(32.dp), color = OutlineVariant)
                            val alertsCount = (if (vehicle.soatEnabled) 1 else 0) + (if (vehicle.tecnomecEnabled) 1 else 0) + (if (vehicle.seguroEnabled) 1 else 0)
                            VehicleStat("$alertsCount", "Documentos")
                        }

                        // Documents section header
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.vehicle_docs_label), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }

                        DocumentRow(
                            emoji = "🛡️",
                            title = stringResource(R.string.setup_docs_soat),
                            enabled = vehicle.soatEnabled,
                            expiryDate = vehicle.soatExpiry,
                            onClick = { showEditDialog = true }
                        )
                        DocumentRow(
                            emoji = "🔧",
                            title = stringResource(R.string.setup_docs_tecnomecanica),
                            enabled = vehicle.tecnomecEnabled,
                            expiryDate = vehicle.tecnomecExpiry,
                            onClick = { showEditDialog = true }
                        )
                        DocumentRow(
                            emoji = "📋",
                            title = stringResource(R.string.setup_docs_seguro),
                            enabled = vehicle.seguroEnabled,
                            expiryDate = vehicle.seguroExpiry,
                            onClick = { showEditDialog = true }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    // Modal bottom sheet to Add Vehicle
    if (showAddDialog) {
        VehicleAddEditBottomSheet(
            isEdit = false,
            onDismiss = { showAddDialog = false },
            onConfirm = { newVehicle ->
                viewModel.addVehicle(newVehicle)
                showAddDialog = false
            }
        )
    }

    // Modal bottom sheet to Edit Vehicle Documents & Odometer
    if (showEditDialog) {
        selectedVehicle?.let { vehicle ->
            VehicleAddEditBottomSheet(
                isEdit = true,
                initialVehicle = vehicle,
                onDismiss = { showEditDialog = false },
                onConfirm = { updatedVehicle ->
                    viewModel.updateVehicle(updatedVehicle)
                    showEditDialog = false
                }
            )
        }
    }
}

@Composable
private fun VehicleStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = OnSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun DocumentRow(
    emoji: String,
    title: String,
    enabled: Boolean,
    expiryDate: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, style = MaterialTheme.typography.bodyLarge)
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = OnSurface)
                val statusText = if (enabled) {
                    if (!expiryDate.isNullOrEmpty()) "Vence: $expiryDate" else "Habilitado"
                } else {
                    "Sin registro"
                }
                Text(statusText, style = MaterialTheme.typography.labelSmall, color = if (enabled) Secondary else OnSurfaceVariant)
            }
        }
        Text("⚙️", style = MaterialTheme.typography.bodyLarge, color = OnSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleAddEditBottomSheet(
    isEdit: Boolean,
    initialVehicle: Vehicle? = null,
    onDismiss: () -> Unit,
    onConfirm: (Vehicle) -> Unit
) {
    var selectedVehicleType by remember { mutableStateOf(if (isEdit && initialVehicle != null) VehicleType.valueOf(initialVehicle.type) else VehicleType.CAR) }
    var nickname by remember { mutableStateOf(initialVehicle?.nickname ?: "") }
    var brand by remember { mutableStateOf(initialVehicle?.brand ?: "") }
    var model by remember { mutableStateOf(initialVehicle?.model ?: "") }
    var year by remember { mutableStateOf(initialVehicle?.year?.toString() ?: "") }
    var plate by remember { mutableStateOf(initialVehicle?.plate ?: "") }
    var odometer by remember { mutableStateOf(initialVehicle?.odometer?.toString() ?: "") }
    var selectedFuel by remember { mutableStateOf(if (isEdit && initialVehicle != null) FuelType.valueOf(initialVehicle.fuel) else FuelType.GASOLINE) }

    var soatEnabled by remember { mutableStateOf(initialVehicle?.soatEnabled ?: true) }
    var soatExpiryDate by remember { mutableStateOf(initialVehicle?.soatExpiry ?: "") }

    var tecnomecEnabled by remember { mutableStateOf(initialVehicle?.tecnomecEnabled ?: true) }
    var tecnomecExpiryDate by remember { mutableStateOf(initialVehicle?.tecnomecExpiry ?: "") }

    var seguroEnabled by remember { mutableStateOf(initialVehicle?.seguroEnabled ?: false) }
    var seguroExpiryDate by remember { mutableStateOf(initialVehicle?.seguroExpiry ?: "") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isEdit) "Editar Vehículo" else "Agregar Vehículo",
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // Vehicle Type selector
            Text("Tipo de vehículo", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleType.entries.forEach { type ->
                    val selected = type == selectedVehicleType
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) PrimaryContainer.copy(alpha = 0.2f) else SurfaceContainerLowest)
                            .border(1.5.dp, if (selected) Primary else OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable { selectedVehicleType = type }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(type.emoji, style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(type.labelRes), style = MaterialTheme.typography.bodySmall, color = if (selected) Primary else OnSurfaceVariant)
                        }
                    }
                }
            }

            // General Form
            DialogInputField("Apodo", nickname, { nickname = it }, "Mi Spark, La Nave...")
            DialogInputField("Marca", brand, { brand = it }, "Chevrolet, Kia, BYD...")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { DialogInputField("Modelo", model, { model = it }, "Spark...") }
                Box(Modifier.weight(1f)) { DialogInputField("Año", year, { year = it }, "2020", KeyboardType.Number) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { DialogInputField("Placa", plate, { plate = it }, "ABC 123") }
                Box(Modifier.weight(1f)) { DialogInputField("Odómetro (km)", odometer, { odometer = it }, "Ej. 45000", KeyboardType.Number) }
            }

            // Fuel selector
            Text("Combustible", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FuelType.entries.forEach { fuel ->
                    val selected = fuel == selectedFuel
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) PrimaryContainer.copy(alpha = 0.2f) else SurfaceContainerLowest)
                            .border(1.dp, if (selected) Primary else OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable { selectedFuel = fuel }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("${fuel.emoji} ${stringResource(fuel.labelRes)}", style = MaterialTheme.typography.bodySmall, color = if (selected) Primary else OnSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))

            // Documents toggles & Date Pickers
            Text("Documentos", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            
            DialogDocumentCard("🛡️ SOAT", soatEnabled, { soatEnabled = it }, soatExpiryDate, { soatExpiryDate = it })
            DialogDocumentCard("🔧 Tecnomecánica", tecnomecEnabled, { tecnomecEnabled = it }, tecnomecExpiryDate, { tecnomecExpiryDate = it })
            DialogDocumentCard("📋 Seguro Todo Riesgo", seguroEnabled, { seguroEnabled = it }, seguroExpiryDate, { seguroExpiryDate = it })

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar", color = OnSurfaceVariant)
                }
                Button(
                    onClick = {
                        val vehicleToSave = Vehicle(
                            id = initialVehicle?.id ?: "",
                            type = selectedVehicleType.name,
                            nickname = nickname.ifEmpty { selectedVehicleType.emoji + " " + brand.ifEmpty { "Mi Vehículo" } },
                            brand = brand,
                            model = model,
                            year = year.toIntOrNull() ?: 0,
                            plate = plate,
                            fuel = selectedFuel.name,
                            odometer = odometer.toIntOrNull() ?: 0,
                            soatExpiry = if (soatEnabled) soatExpiryDate else null,
                            tecnomecExpiry = if (tecnomecEnabled) tecnomecExpiryDate else null,
                            seguroExpiry = if (seguroEnabled) seguroExpiryDate else null,
                            soatEnabled = soatEnabled,
                            tecnomecEnabled = tecnomecEnabled,
                            seguroEnabled = seguroEnabled
                        )
                        onConfirm(vehicleToSave)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    enabled = brand.isNotEmpty() && model.isNotEmpty() && year.isNotEmpty() && odometer.isNotEmpty()
                ) {
                    Text("Guardar", color = Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DialogInputField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, keyboardType: KeyboardType = KeyboardType.Text) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant.copy(alpha = 0.4f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary, unfocusedBorderColor = OutlineVariant,
                focusedContainerColor = SurfaceContainerLowest, unfocusedContainerColor = SurfaceContainerLowest,
                focusedTextColor = OnSurface, unfocusedTextColor = OnSurface, cursorColor = Primary
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DialogDocumentCard(
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    expiryDate: String,
    onExpiryChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLowest)
            .border(1.dp, OutlineVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Secondary, checkedTrackColor = Secondary.copy(alpha = 0.5f))
            )
        }
        if (enabled) {
            DialogDateSelectionField("Fecha de vencimiento", expiryDate, onExpiryChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogDateSelectionField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                            calendar.timeInMillis = selectedMillis
                            val day = calendar.get(Calendar.DAY_OF_MONTH)
                            val month = calendar.get(Calendar.MONTH) + 1
                            val year = calendar.get(Calendar.YEAR)
                            val formattedDate = String.format("%02d/%02d/%d", day, month, year)
                            onValueChange(formattedDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContainerLow)
            .clickable { showDatePicker = true }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text(
                    text = value.ifEmpty { "Seleccionar fecha..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value.isNotEmpty()) OnSurface else OnSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Text("📅", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
