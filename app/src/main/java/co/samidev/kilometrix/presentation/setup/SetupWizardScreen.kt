package co.samidev.kilometrix.presentation.setup

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.R
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.ui.theme.*
import kotlinx.coroutines.launch
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

private data class Platform(val nameRes: Int, val iconRes: Int, val color: Color)

private val platforms = listOf(
    Platform(R.string.platform_uber, R.drawable.uber, Color(0xFFFBBF24)),
    Platform(R.string.platform_didi, R.drawable.didi, Color(0xFFF97316)),
    Platform(R.string.platform_indrive, R.drawable.indrive, Color(0xFF22C55E)),
    Platform(R.string.platform_cabify, R.drawable.cabify, Color(0xFF9333EA)),
    Platform(R.string.platform_rappi, R.drawable.rappi, Color(0xFFFB923C)),
    Platform(R.string.platform_yango, R.drawable.yangopro, Color(0xFF3B82F6))
)

private val platformIds = listOf("Uber", "Didi", "InDrive", "Cabify", "Rappi", "Yango Pro")

@Composable
fun SetupWizardScreen(onSetupComplete: () -> Unit) {
    val viewModel: SetupViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 4

    // Step 1 state
    var cityQuery by remember { mutableStateOf("") }

    // Step 2 state
    var selectedVehicleType by remember { mutableStateOf(VehicleType.CAR) }
    var nickname by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }
    var selectedFuel by remember { mutableStateOf(FuelType.GASOLINE) }

    // Step 3 state — doc toggles & dates
    var soatEnabled by remember { mutableStateOf(true) }
    var soatPurchaseDate by remember { mutableStateOf("") }
    var soatExpiryDate by remember { mutableStateOf("") }

    var tecnomecEnabled by remember { mutableStateOf(true) }
    var tecnomecPurchaseDate by remember { mutableStateOf("") }
    var tecnomecExpiryDate by remember { mutableStateOf("") }

    var seguroEnabled by remember { mutableStateOf(false) }
    var seguroPurchaseDate by remember { mutableStateOf("") }
    var seguroExpiryDate by remember { mutableStateOf("") }

    // Step 4 state
    val selectedPlatforms = remember { androidx.compose.runtime.mutableStateListOf<Int>() }

    // Error handling snackbar
    LaunchedEffect(uiState) {
        if (uiState is SetupUiState.Error) {
            val errorState = uiState as SetupUiState.Error
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorState.message,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 24.dp)
            ) {
                // ── Header / Progress ──────────────────────────────────────────
                Text(
                    text = stringResource(R.string.setup_step_label, currentStep, totalSteps),
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(totalSteps) { index ->
                        val filled = index < currentStep
                        val barColor by animateColorAsState(
                            targetValue = if (filled) PrimaryContainer else SurfaceContainerHigh,
                            animationSpec = tween(300),
                            label = "progressBar"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(barColor)
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))

                // ── Step content with directional slide transition ─────────────
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        slideInHorizontally(
                            initialOffsetX = { it * direction },
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(200)) togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { -it * direction },
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(160))
                    },
                    label = "wizardStep",
                    modifier = Modifier.weight(1f)
                ) { step ->
                    when (step) {
                        1 -> Step1City(cityQuery, onQueryChange = { cityQuery = it })
                        2 -> Step2Vehicle(
                            selectedType = selectedVehicleType,
                            onTypeSelected = { selectedVehicleType = it },
                            nickname = nickname, onNicknameChange = { nickname = it },
                            brand = brand, onBrandChange = { brand = it },
                            model = model, onModelChange = { model = it },
                            year = year, onYearChange = { year = it },
                            plate = plate, onPlateChange = { plate = it },
                            odometer = odometer, onOdometerChange = { odometer = it },
                            selectedFuel = selectedFuel, onFuelSelected = { selectedFuel = it }
                        )
                        3 -> Step3Documents(
                            soatEnabled = soatEnabled, onSoatToggle = { soatEnabled = it },
                            soatPurchaseDate = soatPurchaseDate, onSoatPurchaseChange = { soatPurchaseDate = it },
                            soatExpiryDate = soatExpiryDate, onSoatExpiryChange = { soatExpiryDate = it },
                            tecnomecEnabled = tecnomecEnabled, onTecnomecToggle = { tecnomecEnabled = it },
                            tecnomecPurchaseDate = tecnomecPurchaseDate, onTecnomecPurchaseChange = { tecnomecPurchaseDate = it },
                            tecnomecExpiryDate = tecnomecExpiryDate, onTecnomecExpiryChange = { tecnomecExpiryDate = it },
                            seguroEnabled = seguroEnabled, onSeguroToggle = { seguroEnabled = it },
                            seguroPurchaseDate = seguroPurchaseDate, onSeguroPurchaseChange = { seguroPurchaseDate = it },
                            seguroExpiryDate = seguroExpiryDate, onSeguroExpiryChange = { seguroExpiryDate = it }
                        )
                        4 -> Step4Platforms(
                            selectedPlatforms = selectedPlatforms,
                            onTogglePlatform = { index ->
                                if (selectedPlatforms.contains(index)) selectedPlatforms.remove(index)
                                else selectedPlatforms.add(index)
                            }
                        )
                    }
                }

                // ── Footer ─────────────────────────────────────────────────────
                if (currentStep >= 3) {
                    TextButton(
                        onClick = {
                            if (currentStep < totalSteps) {
                                currentStep++
                            } else {
                                // Save to Firebase on finish
                                val initialVehicle = Vehicle(
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
                                viewModel.saveSetupData(
                                    city = cityQuery,
                                    platforms = selectedPlatforms.map { platformIds[it] },
                                    vehicle = initialVehicle,
                                    onComplete = onSetupComplete
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState !is SetupUiState.Loading
                    ) {
                        Text(
                            text = stringResource(R.string.setup_skip),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = {
                        if (currentStep < totalSteps) {
                            currentStep++
                        } else {
                            // Save to Firebase on finish
                            val initialVehicle = Vehicle(
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
                            viewModel.saveSetupData(
                                city = cityQuery.ifEmpty { "Bogota" },
                                platforms = selectedPlatforms.map { platformIds[it] },
                                vehicle = initialVehicle,
                                onComplete = onSetupComplete
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    enabled = uiState !is SetupUiState.Loading
                ) {
                    if (uiState is SetupUiState.Loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = if (currentStep == totalSteps)
                                stringResource(R.string.setup_finish)
                            else
                                stringResource(R.string.setup_next),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ── Step 1 — City ─────────────────────────────────────────────────────────────
@Composable
private fun Step1City(query: String, onQueryChange: (String) -> Unit) {
    Column {
        Text(stringResource(R.string.setup_city_title), style = MaterialTheme.typography.headlineMedium, color = OnSurface)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_city_subtitle), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.setup_city_search_placeholder), color = OnSurfaceVariant.copy(alpha = 0.5f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary, unfocusedBorderColor = OutlineVariant,
                focusedContainerColor = SurfaceContainerLow, unfocusedContainerColor = SurfaceContainerLow,
                focusedTextColor = OnSurface, unfocusedTextColor = OnSurface, cursorColor = Primary
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_city_search_hint), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant.copy(alpha = 0.6f))
    }
}

// ── Step 2 — Vehicle ──────────────────────────────────────────────────────────
@Composable
private fun Step2Vehicle(
    selectedType: VehicleType, onTypeSelected: (VehicleType) -> Unit,
    nickname: String, onNicknameChange: (String) -> Unit,
    brand: String, onBrandChange: (String) -> Unit,
    model: String, onModelChange: (String) -> Unit,
    year: String, onYearChange: (String) -> Unit,
    plate: String, onPlateChange: (String) -> Unit,
    odometer: String, onOdometerChange: (String) -> Unit,
    selectedFuel: FuelType, onFuelSelected: (FuelType) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.setup_vehicle_title), style = MaterialTheme.typography.headlineMedium, color = OnSurface)
        Text(stringResource(R.string.setup_vehicle_subtitle), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)

        // Vehicle type selector
        Text(stringResource(R.string.setup_vehicle_type_label), style = MaterialTheme.typography.titleMedium, color = OnSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VehicleType.entries.forEach { type ->
                val selected = type == selectedType
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) PrimaryContainer.copy(alpha = 0.2f) else SurfaceContainerLow)
                        .border(2.dp, if (selected) PrimaryContainer else OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .clickable { onTypeSelected(type) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(type.emoji, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(type.labelRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) Primary else OnSurfaceVariant
                        )
                    }
                }
            }
        }

        // Text fields
        SetupInputField(stringResource(R.string.setup_vehicle_nickname_label), nickname, onNicknameChange, stringResource(R.string.setup_vehicle_nickname_placeholder))
        SetupInputField(stringResource(R.string.setup_vehicle_brand_label), brand, onBrandChange, stringResource(R.string.setup_vehicle_brand_placeholder))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { SetupInputField(stringResource(R.string.setup_vehicle_model_label), model, onModelChange, stringResource(R.string.setup_vehicle_model_placeholder)) }
            Box(Modifier.weight(1f)) { SetupInputField(stringResource(R.string.setup_vehicle_year_label), year, onYearChange, stringResource(R.string.setup_vehicle_year_placeholder), KeyboardType.Number) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { SetupInputField(stringResource(R.string.setup_vehicle_plate_label), plate, onPlateChange, stringResource(R.string.setup_vehicle_plate_placeholder)) }
            Box(Modifier.weight(1f)) { SetupInputField(stringResource(R.string.setup_vehicle_odometer_label), odometer, onOdometerChange, stringResource(R.string.setup_vehicle_odometer_placeholder), KeyboardType.Number) }
        }

        // Fuel selector
        Text(stringResource(R.string.setup_vehicle_fuel_label), style = MaterialTheme.typography.titleMedium, color = OnSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FuelType.entries.forEach { fuel ->
                val selected = fuel == selectedFuel
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) PrimaryContainer.copy(alpha = 0.2f) else SurfaceContainerLow)
                        .border(1.dp, if (selected) PrimaryContainer else OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { onFuelSelected(fuel) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("${fuel.emoji} ${stringResource(fuel.labelRes)}", style = MaterialTheme.typography.bodySmall, color = if (selected) Primary else OnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SetupInputField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, keyboardType: KeyboardType = KeyboardType.Text) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant.copy(alpha = 0.4f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary, unfocusedBorderColor = OutlineVariant,
                focusedContainerColor = SurfaceContainerLow, unfocusedContainerColor = SurfaceContainerLow,
                focusedTextColor = OnSurface, unfocusedTextColor = OnSurface, cursorColor = Primary
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Step 3 — Documents ────────────────────────────────────────────────────────
@Composable
private fun Step3Documents(
    soatEnabled: Boolean, onSoatToggle: (Boolean) -> Unit,
    soatPurchaseDate: String, onSoatPurchaseChange: (String) -> Unit,
    soatExpiryDate: String, onSoatExpiryChange: (String) -> Unit,
    tecnomecEnabled: Boolean, onTecnomecToggle: (Boolean) -> Unit,
    tecnomecPurchaseDate: String, onTecnomecPurchaseChange: (String) -> Unit,
    tecnomecExpiryDate: String, onTecnomecExpiryChange: (String) -> Unit,
    seguroEnabled: Boolean, onSeguroToggle: (Boolean) -> Unit,
    seguroPurchaseDate: String, onSeguroPurchaseChange: (String) -> Unit,
    seguroExpiryDate: String, onSeguroExpiryChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.setup_docs_title), style = MaterialTheme.typography.headlineMedium, color = OnSurface)
        Text(stringResource(R.string.setup_docs_subtitle), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)

        DocumentCard(
            emoji = "🛡️",
            title = stringResource(R.string.setup_docs_soat),
            enabled = soatEnabled,
            onToggle = onSoatToggle,
            purchaseDate = soatPurchaseDate,
            onPurchaseChange = onSoatPurchaseChange,
            expiryDate = soatExpiryDate,
            onExpiryChange = onSoatExpiryChange
        )

        DocumentCard(
            emoji = "🔧",
            title = stringResource(R.string.setup_docs_tecnomecanica),
            enabled = tecnomecEnabled,
            onToggle = onTecnomecToggle,
            purchaseDate = tecnomecPurchaseDate,
            onPurchaseChange = onTecnomecPurchaseChange,
            expiryDate = tecnomecExpiryDate,
            onExpiryChange = onTecnomecExpiryChange
        )

        DocumentCard(
            emoji = "📋",
            title = stringResource(R.string.setup_docs_seguro),
            enabled = seguroEnabled,
            onToggle = onSeguroToggle,
            purchaseDate = seguroPurchaseDate,
            onPurchaseChange = onSeguroPurchaseChange,
            expiryDate = seguroExpiryDate,
            onExpiryChange = onSeguroExpiryChange
        )
    }
}

@Composable
private fun DocumentCard(
    emoji: String,
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    purchaseDate: String,
    onPurchaseChange: (String) -> Unit,
    expiryDate: String,
    onExpiryChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow)
            .border(1.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                Text(title, style = MaterialTheme.typography.titleMedium, color = OnSurface)
            }
            Checkbox(
                checked = enabled, onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(checkedColor = Secondary, uncheckedColor = OutlineVariant, checkmarkColor = Color.Black)
            )
        }
        if (enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateSelectionField(stringResource(R.string.setup_docs_purchase_date), purchaseDate, onPurchaseChange, Modifier.weight(1f))
                DateSelectionField(stringResource(R.string.setup_docs_expiry_date), expiryDate, onExpiryChange, Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            // Convert standard UTC date millis to Local Timezone Calendar to prevent offset shift
                            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                            calendar.timeInMillis = selectedMillis
                            // Construct simple display date format DD/MM/AAAA
                            val day = calendar.get(Calendar.DAY_OF_MONTH)
                            val month = calendar.get(Calendar.MONTH) + 1
                            val year = calendar.get(Calendar.YEAR)
                            val formattedDate = String.format("%02d/%02d/%d", day, month, year)
                            onValueChange(formattedDate)
                        }
                        showDialog = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceContainerLowest)
            .clickable { showDialog = true }
            .padding(10.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value.ifEmpty { stringResource(R.string.setup_docs_date_placeholder) },
            style = MaterialTheme.typography.bodySmall,
            color = if (value.isNotEmpty()) OnSurface else OnSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

// ── Step 4 — Platforms ────────────────────────────────────────────────────────
@Composable
private fun Step4Platforms(selectedPlatforms: List<Int>, onTogglePlatform: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.setup_platforms_title), style = MaterialTheme.typography.headlineMedium, color = OnSurface)
        Text(stringResource(R.string.setup_platforms_subtitle), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)

        @Suppress("UNUSED_EXPRESSION")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            platforms.chunked(2).forEachIndexed { rowIndex, rowPlatforms ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowPlatforms.forEachIndexed { colIndex, platform ->
                        val globalIndex = rowIndex * 2 + colIndex
                        val isSelected = selectedPlatforms.contains(globalIndex)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (isSelected) platform.color.copy(alpha = 0.15f) else SurfaceContainerLow)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) platform.color else OutlineVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable { onTogglePlatform(globalIndex) }
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(id = platform.iconRes),
                                        contentDescription = stringResource(platform.nameRes),
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                                Text(stringResource(platform.nameRes), style = MaterialTheme.typography.titleMedium, color = OnSurface, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    // Fill empty slot if odd number
                    if (rowPlatforms.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}


