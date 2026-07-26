package co.samidev.kilometrix.presentation.vehicle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.ui.theme.*

@Composable
fun VehicleScreen() {
    val viewModel: VehicleViewModel = hiltViewModel()
    val vehicles by viewModel.vehicles.collectAsState()
    val activeVehicleId by viewModel.activeVehicleId.collectAsState()
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val picoPlacaState by viewModel.picoPlacaState.collectAsState()
    val hasActiveShift by viewModel.hasActiveShift.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Sync selected vehicle with active vehicle or first item
    LaunchedEffect(activeVehicle, vehicles) {
        if (selectedVehicle == null && activeVehicle != null) {
            selectedVehicle = activeVehicle
        } else if (selectedVehicle != null) {
            selectedVehicle = vehicles.find { it.id == selectedVehicle?.id } ?: activeVehicle ?: vehicles.firstOrNull()
        } else if (vehicles.isNotEmpty()) {
            selectedVehicle = vehicles.first()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Mis vehículos",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnSurface
                )
                Text(
                    text = "${vehicles.size} vehículo(s) registrado(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.seedDemoVehicles() },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerLow),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("🧪 Cargar 4 Demo", style = MaterialTheme.typography.labelSmall, color = Secondary)
                }

                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar vehículo", tint = OnPrimary)
                }
            }
        }

        if (vehicles.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceContainerLow)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🚗", style = MaterialTheme.typography.displayLarge)
                Text("No tienes vehículos registrados", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                Text("Puedes agregar un vehículo manualmente o cargar 4 vehículos de prueba.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Agregar Vehículo", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { viewModel.seedDemoVehicles() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🧪 Cargar 4 Demo", color = Secondary)
                    }
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
                    val isAppActive = vehicle.id == activeVehicleId || (activeVehicleId == null && vehicle.id == vehicles.firstOrNull()?.id)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PrimaryContainer.copy(alpha = 0.2f) else SurfaceContainerLow)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Primary else OutlineVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (hasActiveShift && vehicle.id != activeVehicleId) {
                                    android.widget.Toast.makeText(context, "Debes finalizar el turno actual antes de cambiar de vehículo.", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    selectedVehicle = vehicle
                                    viewModel.setActiveVehicle(vehicle.id)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
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
                            if (isAppActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Secondary.copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        "ACTIVO",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                        color = Secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            selectedVehicle?.let { vehicle ->
                val isAppActive = vehicle.id == activeVehicleId || (activeVehicleId == null && vehicle.id == vehicles.firstOrNull()?.id)

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

                        // Badge / Action for Active Vehicle
                        Box(modifier = Modifier.align(Alignment.TopEnd)) {
                            if (isAppActive) {
                                Surface(shape = RoundedCornerShape(8.dp), color = Secondary.copy(alpha = 0.2f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("⚡", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            "VEHÍCULO ACTIVO",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Secondary
                                        )
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.setActiveVehicle(vehicle.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "⚡ Activar",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Primary
                                    )
                                }
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
                            VehicleStat(String.format("%,d", vehicle.odometer), "Odómetro (km)")
                            VerticalDivider(modifier = Modifier.height(32.dp), color = OutlineVariant)
                            val alertsCount = (if (vehicle.soatEnabled) 1 else 0) + (if (vehicle.tecnomecEnabled) 1 else 0) + (if (vehicle.seguroEnabled) 1 else 0)
                            VehicleStat("$alertsCount", "Documentos")
                        }

                        // Pico y Placa Section
                        val picoPlacaStatus = viewModel.getPicoPlacaStatus(vehicle, userProfile?.city, picoPlacaState)
                        if (picoPlacaStatus.hasData) {
                            val cardBg = if (picoPlacaStatus.isRestrictedNow) {
                                ErrorContainer.copy(alpha = 0.15f)
                            } else {
                                PrimaryContainer.copy(alpha = 0.1f)
                            }
                            val borderCol = if (picoPlacaStatus.isRestrictedNow) {
                                Error.copy(alpha = 0.5f)
                            } else {
                                Primary.copy(alpha = 0.3f)
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(cardBg)
                                    .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = picoPlacaStatus.statusText,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (picoPlacaStatus.isRestrictedNow) Color(0xFFFF5252) else Secondary
                                )
                                Text(
                                    text = picoPlacaStatus.subtext,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SurfaceContainerLowest)
                                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Pico y Placa",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = OnSurface
                                )
                                Text(
                                    text = "Sin información de restricciones de tráfico para la ciudad configurada.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        }

                        // Documents section
                        Text("DOCUMENTOS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = OnSurfaceVariant)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DocumentCard(
                                icon = Icons.Default.Shield,
                                title = "SOAT",
                                expiryDate = vehicle.soatExpiry.orEmpty(),
                                isEnabled = vehicle.soatEnabled,
                                onToggle = { enabled -> viewModel.updateVehicle(vehicle.copy(soatEnabled = enabled)) }
                            )
                            DocumentCard(
                                icon = Icons.Default.Build,
                                title = "Tecno-mecánica",
                                expiryDate = vehicle.tecnomecExpiry.orEmpty(),
                                isEnabled = vehicle.tecnomecEnabled,
                                onToggle = { enabled -> viewModel.updateVehicle(vehicle.copy(tecnomecEnabled = enabled)) }
                            )
                            DocumentCard(
                                icon = Icons.Default.DateRange,
                                title = "Seguro Todo Riesgo",
                                expiryDate = vehicle.seguroExpiry.orEmpty(),
                                isEnabled = vehicle.seguroEnabled,
                                onToggle = { enabled -> viewModel.updateVehicle(vehicle.copy(seguroEnabled = enabled)) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }

    if (showAddDialog) {
        VehicleFormDialog(
            title = "Agregar vehículo",
            vehicle = null,
            onDismiss = { showAddDialog = false },
            onSave = { newVehicle ->
                viewModel.addVehicle(newVehicle)
                showAddDialog = false
            }
        )
    }

    if (showEditDialog && selectedVehicle != null) {
        VehicleFormDialog(
            title = "Editar vehículo",
            vehicle = selectedVehicle,
            onDismiss = { showEditDialog = false },
            onSave = { updatedVehicle ->
                viewModel.updateVehicle(updatedVehicle)
                selectedVehicle = updatedVehicle
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun VehicleStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun DocumentCard(
    icon: ImageVector,
    title: String,
    expiryDate: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLowest)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isEnabled) PrimaryContainer.copy(alpha = 0.2f) else SurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (isEnabled) Primary else OnSurfaceVariant)
            }
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
                Text(
                    if (isEnabled && expiryDate.isNotBlank()) "Vence: $expiryDate"
                    else if (isEnabled) "Habilitado"
                    else "Sin registro",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled) Secondary else OnSurfaceVariant
                )
            }
        }

        IconButton(onClick = { onToggle(!isEnabled) }) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Configurar",
                tint = if (isEnabled) Primary else OnSurfaceVariant
            )
        }
    }
}

private enum class VehicleTypeSelection(val displayName: String, val emoji: String) {
    PARTICULAR("Particular", "🚗"),
    TAXI("Taxi", "🚖"),
    MOTO("Moto", "🏍️"),
    VAN("Camioneta / Van", "🚐")
}

private enum class FuelTypeSelection(val displayName: String) {
    GASOLINE("Gasolina"),
    DIESEL("Diesel"),
    GNV("GNV (Gas)"),
    GLP("GLP"),
    ELECTRIC("Eléctrico")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleFormDialog(
    title: String,
    vehicle: Vehicle?,
    onDismiss: () -> Unit,
    onSave: (Vehicle) -> Unit
) {
    val initialType = when (vehicle?.type) {
        "TAXI" -> VehicleTypeSelection.TAXI
        "MOTO" -> VehicleTypeSelection.MOTO
        "VAN" -> VehicleTypeSelection.VAN
        else -> VehicleTypeSelection.PARTICULAR
    }
    val initialFuel = when (vehicle?.fuel) {
        "DIESEL" -> FuelTypeSelection.DIESEL
        "GNV" -> FuelTypeSelection.GNV
        "GLP" -> FuelTypeSelection.GLP
        "ELECTRIC" -> FuelTypeSelection.ELECTRIC
        else -> FuelTypeSelection.GASOLINE
    }

    var selectedVehicleType by remember { mutableStateOf(initialType) }
    var nickname by remember { mutableStateOf(vehicle?.nickname.orEmpty()) }
    var brand by remember { mutableStateOf(vehicle?.brand.orEmpty()) }
    var model by remember { mutableStateOf(vehicle?.model.orEmpty()) }
    var yearStr by remember { mutableStateOf(vehicle?.year?.toString() ?: "2022") }
    var plate by remember { mutableStateOf(vehicle?.plate.orEmpty()) }
    var selectedFuelType by remember { mutableStateOf(initialFuel) }
    var odometerStr by remember { mutableStateOf(vehicle?.odometer?.toString() ?: "0") }
    var soatExpiry by remember { mutableStateOf(vehicle?.soatExpiry.orEmpty()) }
    var tecnomecExpiry by remember { mutableStateOf(vehicle?.tecnomecExpiry.orEmpty()) }
    var seguroExpiry by remember { mutableStateOf(vehicle?.seguroExpiry.orEmpty()) }

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
            Text(title, color = OnSurface, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Type selection
                Text("Tipo de vehículo", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = OnSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VehicleTypeSelection.values().forEach { type ->
                        val selected = type == selectedVehicleType
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) PrimaryContainer else SurfaceContainerLow)
                                .border(1.dp, if (selected) Primary else OutlineVariant, RoundedCornerShape(8.dp))
                                .clickable { selectedVehicleType = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${type.emoji}\n${type.displayName}", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = if (selected) OnPrimaryContainer else OnSurface)
                        }
                    }
                }

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Apodo del vehículo (ej: Mi Nave)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Marca") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Modelo") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = yearStr,
                        onValueChange = { yearStr = it },
                        label = { Text("Año") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = plate,
                        onValueChange = { plate = it.uppercase() },
                        label = { Text("Placa (ej: QIR098)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Fuel selection
                Text("Tipo de combustible", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = OnSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FuelTypeSelection.values().forEach { fuel ->
                        val selected = fuel == selectedFuelType
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) PrimaryContainer else SurfaceContainerLow)
                                .border(1.dp, if (selected) Primary else OutlineVariant, RoundedCornerShape(8.dp))
                                .clickable { selectedFuelType = fuel }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(fuel.displayName, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = if (selected) OnPrimaryContainer else OnSurface)
                        }
                    }
                }

                OutlinedTextField(
                    value = odometerStr,
                    onValueChange = { odometerStr = it },
                    label = { Text("Odómetro actual (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Vencimientos (opcional)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = OnSurfaceVariant)
                OutlinedTextField(
                    value = soatExpiry,
                    onValueChange = { soatExpiry = it },
                    label = { Text("Vencimiento SOAT (dd/mm/aaaa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tecnomecExpiry,
                    onValueChange = { tecnomecExpiry = it },
                    label = { Text("Vencimiento Tecnomecánica (dd/mm/aaaa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                    onClick = {
                        val year = yearStr.toIntOrNull() ?: 2022
                        val odometer = odometerStr.toIntOrNull() ?: 0
                        val updated = (vehicle ?: Vehicle()).copy(
                            type = selectedVehicleType.name,
                            nickname = nickname.ifEmpty { selectedVehicleType.emoji + " " + brand.ifEmpty { "Mi Vehículo" } },
                            brand = brand,
                            model = model,
                            year = year,
                            plate = plate.uppercase(),
                            fuel = selectedFuelType.name,
                            odometer = odometer,
                            soatExpiry = soatExpiry,
                            tecnomecExpiry = tecnomecExpiry,
                            seguroExpiry = seguroExpiry
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
}
