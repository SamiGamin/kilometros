package co.samidev.kilometrix.presentation.picoyplaca

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.CityData
import co.samidev.kilometrix.domain.model.PicoPlacaResponse
import co.samidev.kilometrix.domain.model.Restriction
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.presentation.vehicle.VehicleViewModel
import co.samidev.kilometrix.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicoYPlacaScreen(
    onNavigateBack: () -> Unit,
    viewModel: PicoYPlacaViewModel = hiltViewModel(),
    vehicleViewModel: VehicleViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val picoPlacaState by viewModel.picoPlacaState.collectAsState()
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var selectedCity by remember { mutableStateOf<CityData?>(null) }
    var currentMonthCalendar by remember { mutableStateOf(Calendar.getInstance()) }

    var showCityDropdown by remember { mutableStateOf(false) }
    var showVehicleDropdown by remember { mutableStateOf(false) }
    var clickedCell by remember { mutableStateOf<CalendarDayCell?>(null) }

    // Sync selected vehicle when list changes
    LaunchedEffect(vehicles) {
        if (vehicles.isNotEmpty() && selectedVehicle == null) {
            selectedVehicle = vehicles.first()
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Calendario Pico y Placa",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = OnSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { paddingValues ->
        when (val state = picoPlacaState) {
            is Resource.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            }
            is Resource.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Error al cargar configuración", color = OnSurfaceVariant)
                        Button(onClick = { viewModel.retry() }) {
                            Text("Reintentar")
                        }
                    }
                }
            }
            is Resource.Success -> {
                val data = state.data
                val cities = data.cities

                // Set initial city
                LaunchedEffect(cities, userProfile) {
                    if (cities.isNotEmpty() && selectedCity == null) {
                        val userCityName = userProfile?.city.orEmpty().trim()
                        val matchedCity = cities.find {
                            it.name.contains(userCityName, ignoreCase = true) ||
                            userCityName.contains(it.name, ignoreCase = true) ||
                            it.id.equals(userCityName, ignoreCase = true)
                        }
                        selectedCity = matchedCity ?: cities.first()
                    }
                }

                selectedCity?.let { city ->
                    val activeVehicle = selectedVehicle
                    val plate = activeVehicle?.plate ?: "ABC123"
                    val vehicleType = activeVehicle?.type ?: "PARTICULAR"
                    val fuelType = activeVehicle?.fuel

                    val activeRestriction = city.restrictions.firstOrNull {
                        it.vehicleType.equals(vehicleType, ignoreCase = true)
                    } ?: if (vehicleType == "MOTO" || vehicleType == "TAXI") {
                        null
                    } else {
                        city.restrictions.firstOrNull {
                            it.vehicleType.equals("PARTICULAR", ignoreCase = true)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))

                        // --- 1. City Selector ---
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SurfaceContainerLow)
                                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                    .clickable { showCityDropdown = true }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryContainer.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Primary
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = city.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = OnSurface
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF00FF9D).copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "Pico y Placa",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = Color(0xFF00FF9D)
                                            )
                                        }
                                    }
                                    Text(
                                        text = city.state,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = OnSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showCityDropdown,
                                onDismissRequest = { showCityDropdown = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .background(SurfaceContainerLow)
                                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                            ) {
                                cities.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c.name, color = OnSurface) },
                                        onClick = {
                                            selectedCity = c
                                            showCityDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // --- 2. Vehicle Selector ---
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceContainerLowest)
                                    .border(1.dp, CardBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .clickable { showVehicleDropdown = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val labelText = if (activeVehicle != null) {
                                    val emoji = when (activeVehicle.type) {
                                        "MOTO" -> "🏍️"
                                        "VAN" -> "🚐"
                                        else -> "🚗"
                                    }
                                    "$emoji ${activeVehicle.nickname} · ${activeVehicle.plate.uppercase()}"
                                } else {
                                    "🚗 Vehículo Predeterminado"
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = labelText,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = OnSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val isElectric = fuelType.equals("ELECTRIC", ignoreCase = true)
                                    if (isElectric) {
                                        Text(
                                            text = "(⚡ Eléctrico)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Secondary
                                        )
                                    } else {
                                        val lastDigit = if (city.id == "medellin" && vehicleType == "MOTO") {
                                            getFirstDigitOfPlate(plate)
                                        } else {
                                            getLastDigitOfPlate(plate)
                                        }
                                        Text(
                                            text = "(termina en $lastDigit)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Primary
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = OnSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showVehicleDropdown,
                                onDismissRequest = { showVehicleDropdown = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .background(SurfaceContainerLow)
                                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                            ) {
                                if (vehicles.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Sin vehículos registrados", color = OnSurfaceVariant) },
                                        onClick = { showVehicleDropdown = false }
                                    )
                                } else {
                                    vehicles.forEach { v ->
                                        val emoji = when (v.type) {
                                            "MOTO" -> "🏍️"
                                            "VAN" -> "🚐"
                                            else -> "🚗"
                                        }
                                        DropdownMenuItem(
                                            text = { Text("$emoji ${v.nickname} (${v.plate.uppercase()})", color = OnSurface) },
                                            onClick = {
                                                selectedVehicle = v
                                                showVehicleDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // --- 3. Control de Mes ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                currentMonthCalendar = (currentMonthCalendar.clone() as Calendar).apply {
                                    add(Calendar.MONTH, -1)
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior", tint = OnSurface)
                            }
                            val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("es-CO"))
                            val monthYearLabel = monthFormat.format(currentMonthCalendar.time).uppercase()
                            Text(
                                text = monthYearLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = OnSurface
                            )
                            IconButton(onClick = {
                                currentMonthCalendar = (currentMonthCalendar.clone() as Calendar).apply {
                                    add(Calendar.MONTH, 1)
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente", tint = OnSurface)
                            }
                        }

                        // --- 4. Calendario Mensual Interactivo ---
                        CalendarGrid(
                            calendar = currentMonthCalendar,
                            plate = plate,
                            vehicleType = vehicleType,
                            fuelType = fuelType,
                            city = city,
                            activeRestriction = activeRestriction,
                            holidays = data.holidays,
                            onCellClick = { cell -> clickedCell = cell }
                        )

                        // --- 5. Estado del Dispositivo en Tiempo Real ---
                        RealTimeStatusCard(
                            plate = plate,
                            vehicleType = vehicleType,
                            fuelType = fuelType,
                            city = city,
                            activeRestriction = activeRestriction,
                            holidays = data.holidays
                        )

                        // --- 6. Leyenda ---
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "LEYENDA",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = OnSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                LegendItem(Color(0xFF00FF9D), "Día libre")
                                LegendItem(Color(0xFFFF5252), "Restringido")
                                LegendItem(Color(0xFFFFD700), "Festivo")
                            }
                        }

                        // --- 7. Advertencia de Movilidad ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceContainerLow.copy(alpha = 0.3f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                Text("⚠️", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Las normas pueden cambiar. Verifica con tu secretaría de movilidad local antes de tomar decisiones operativas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                            }
                            Text(
                                text = city.sourceUrl,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Primary,
                                modifier = Modifier
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(city.sourceUrl))
                                        context.startActivity(intent)
                                    }
                                    .padding(start = 24.dp)
                            )
                        }

                        Spacer(Modifier.height(24.dp))
                    }

                    if (clickedCell != null) {
                        DayDetailBottomSheet(
                            cell = clickedCell!!,
                            plate = selectedVehicle?.plate ?: "ABC123",
                            vehicleType = selectedVehicle?.type ?: "PARTICULAR",
                            fuelType = selectedVehicle?.fuel,
                            city = selectedCity ?: cities.first(),
                            activeRestriction = (selectedCity ?: cities.first()).restrictions.firstOrNull {
                                it.vehicleType.equals(selectedVehicle?.type ?: "PARTICULAR", ignoreCase = true)
                            } ?: if ((selectedVehicle?.type ?: "PARTICULAR") == "MOTO" || (selectedVehicle?.type ?: "PARTICULAR") == "TAXI") {
                                null
                            } else {
                                (selectedCity ?: cities.first()).restrictions.firstOrNull {
                                    it.vehicleType.equals("PARTICULAR", ignoreCase = true)
                                }
                            },
                            holidays = data.holidays,
                            onDismiss = { clickedCell = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun CalendarGrid(
    calendar: Calendar,
    plate: String,
    vehicleType: String,
    fuelType: String?,
    city: CityData,
    activeRestriction: Restriction?,
    holidays: List<String>,
    onCellClick: (CalendarDayCell) -> Unit
) {
    val weekDays = listOf("L", "M", "M", "J", "V", "S", "D")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekDays.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val tempCal = calendar.clone() as Calendar
        tempCal.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeekInMonth = tempCal.get(Calendar.DAY_OF_WEEK)
        val offset = when (firstDayOfWeekInMonth) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

        val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val prevMonthCal = tempCal.clone() as Calendar
        prevMonthCal.add(Calendar.MONTH, -1)
        val prevMonthDays = prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val neededCells = offset + daysInMonth
        val totalCells = if (neededCells <= 35) 35 else 42
        val cells = mutableListOf<CalendarDayCell>()

        for (i in offset - 1 downTo 0) {
            val d = prevMonthDays - i
            val cellCal = prevMonthCal.clone() as Calendar
            cellCal.set(Calendar.DAY_OF_MONTH, d)
            cells.add(CalendarDayCell(d, isCurrentMonth = false, calendar = cellCal))
        }

        for (d in 1..daysInMonth) {
            val cellCal = tempCal.clone() as Calendar
            cellCal.set(Calendar.DAY_OF_MONTH, d)
            cells.add(CalendarDayCell(d, isCurrentMonth = true, calendar = cellCal))
        }

        val nextMonthCal = tempCal.clone() as Calendar
        nextMonthCal.add(Calendar.MONTH, 1)
        var nextMonthDay = 1
        while (cells.size < totalCells) {
            val cellCal = nextMonthCal.clone() as Calendar
            cellCal.set(Calendar.DAY_OF_MONTH, nextMonthDay)
            cells.add(CalendarDayCell(nextMonthDay, isCurrentMonth = false, calendar = cellCal))
            nextMonthDay++
        }

        val chunkedCells = cells.chunked(7)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chunkedCells.forEach { weekRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekRow.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onCellClick(cell) },
                            contentAlignment = Alignment.Center
                        ) {
                            CalendarCellContent(
                                cell = cell,
                                plate = plate,
                                vehicleType = vehicleType,
                                fuelType = fuelType,
                                city = city,
                                activeRestriction = activeRestriction,
                                holidays = holidays
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class CalendarDayCell(
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val calendar: Calendar
)

@Composable
private fun CalendarCellContent(
    cell: CalendarDayCell,
    plate: String,
    vehicleType: String,
    fuelType: String?,
    city: CityData,
    activeRestriction: Restriction?,
    holidays: List<String>
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dateString = sdf.format(cell.calendar.time)
    val isHoliday = holidays.contains(dateString)
    val isElectric = fuelType.equals("ELECTRIC", ignoreCase = true)

    val isRestricted = if (cell.isCurrentMonth && !isHoliday && !isElectric && activeRestriction != null) {
        checkIfRestricted(
            calendar = cell.calendar,
            plate = plate,
            vehicleType = vehicleType,
            fuelType = fuelType,
            city = city,
            restriction = activeRestriction
        )
    } else {
        false
    }

    val todayCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Bogota"))
    val isToday = cell.isCurrentMonth &&
            cell.dayNumber == todayCal.get(Calendar.DAY_OF_MONTH) &&
            cell.calendar.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
            cell.calendar.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)

    val isWeekend = cell.calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            cell.calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

    val borderColor = when {
        isToday -> Color(0xFF00FF9D)
        isHoliday -> Color(0xFFFFD700)
        isRestricted -> Color(0xFFFF5252)
        cell.isCurrentMonth && !isWeekend -> Color(0xFF00FF9D).copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val backgroundColor = when {
        isRestricted -> Color(0xFFFF5252).copy(alpha = 0.08f)
        cell.isCurrentMonth && !isWeekend && !isHoliday -> Color(0xFF00FF9D).copy(alpha = 0.03f)
        isWeekend -> SurfaceContainerLow.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val textColor = when {
        !cell.isCurrentMonth -> OnSurfaceVariant.copy(alpha = 0.25f)
        isHoliday -> Color(0xFFFFD700)
        isRestricted -> Color(0xFFFF5252)
        else -> OnSurface
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = cell.dayNumber.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
        if (isToday) {
            Text(
                "HOY",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.sp, fontWeight = FontWeight.Bold),
                color = Color(0xFF00FF9D)
            )
        } else if (isHoliday) {
            Text(
                "FES",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.sp, fontWeight = FontWeight.Bold),
                color = Color(0xFFFFD700)
            )
        }
    }
}

@Composable
private fun RealTimeStatusCard(
    plate: String,
    vehicleType: String,
    fuelType: String?,
    city: CityData,
    activeRestriction: Restriction?,
    holidays: List<String>
) {
    val isElectric = fuelType.equals("ELECTRIC", ignoreCase = true)
    val todayCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Bogota"))
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("America/Bogota")
    }
    val dateString = sdf.format(todayCal.time)
    val isHoliday = holidays.contains(dateString)

    val dayOfWeek = todayCal.get(Calendar.DAY_OF_WEEK)
    val dayName = when (dayOfWeek) {
        Calendar.MONDAY -> "Lunes"
        Calendar.TUESDAY -> "Martes"
        Calendar.WEDNESDAY -> "Miércoles"
        Calendar.THURSDAY -> "Jueves"
        Calendar.FRIDAY -> "Viernes"
        Calendar.SATURDAY -> "Sábado"
        Calendar.SUNDAY -> "Domingo"
        else -> ""
    }

    val isRestrictedDay = if (!isElectric && !isHoliday && activeRestriction != null && activeRestriction.days.contains(dayName)) {
        checkIfRestricted(
            calendar = todayCal,
            plate = plate,
            vehicleType = vehicleType,
            fuelType = fuelType,
            city = city,
            restriction = activeRestriction
        )
    } else {
        false
    }

    var statusText = ""
    var subtext = ""
    val schedule = activeRestriction?.schedule ?: "No aplica"
    val formattedScheduleDisplay = schedule.split("y").map { convert24hRangeTo12h(it.trim()) }.joinToString(" y ")

    val currentHour = todayCal.get(Calendar.HOUR_OF_DAY)
    val currentMinute = todayCal.get(Calendar.MINUTE)
    val currentMinutesSinceMidnight = currentHour * 60 + currentMinute

    val inRestrictionTime = if (activeRestriction != null) {
        checkRestrictionTime(currentMinutesSinceMidnight, schedule)
    } else {
        false
    }

    if (isElectric) {
        statusText = "🟢 Libre de circular"
        subtext = "Vehículo eléctrico exento de Pico y Placa en ${city.name}."
    } else if (isHoliday) {
        statusText = "🟢 Libre de circular"
        subtext = "Los días festivos no aplica restricción en ${city.name}."
    } else if (dayOfWeek == Calendar.SUNDAY) {
        statusText = "🟢 Libre de circular"
        subtext = "Los domingos no aplica restricción en ${city.name}."
    } else if (isRestrictedDay) {
        if (inRestrictionTime) {
            statusText = "🔴 En horario de restricción"
            val digit = if (city.id == "medellin" && vehicleType == "MOTO") getFirstDigitOfPlate(plate) else getLastDigitOfPlate(plate)
            subtext = "Tu placa termina en $digit y la restricción aplica de $formattedScheduleDisplay hoy en ${city.name}."
        } else {
            statusText = "🟢 Libre de circular (Fuera de horario)"
            subtext = "Hoy aplica de $formattedScheduleDisplay. ¡Puedes circular libremente ahora!"
        }
    } else {
        statusText = "🟢 Libre de circular"
        val endingText = if (city.id == "medellin" && vehicleType == "MOTO") "inicia" else "termina"
        val digit = if (city.id == "medellin" && vehicleType == "MOTO") getFirstDigitOfPlate(plate) else getLastDigitOfPlate(plate)
        subtext = "Tu placa $endingText en $digit y no tiene restricción programada hoy en ${city.name}."
    }

    val isAlert = isRestrictedDay && inRestrictionTime

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isAlert) Color(0xFFFF5252).copy(alpha = 0.12f) else SurfaceContainerLow)
            .border(1.dp, if (isAlert) Color(0xFFFF5252).copy(alpha = 0.4f) else CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isAlert) Color(0xFFFF5252).copy(alpha = 0.2f) else Color(0xFF00FF9D).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isAlert) "🔴" else "🟢", style = androidx.compose.ui.text.TextStyle(fontSize = 20.sp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isAlert) Color(0xFFFF5252) else OnSurface
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
    }
}

private fun checkIfRestricted(
    calendar: Calendar,
    plate: String,
    vehicleType: String,
    fuelType: String? = null,
    city: CityData,
    restriction: Restriction
): Boolean {
    if (fuelType.equals("ELECTRIC", ignoreCase = true)) {
        return false
    }
    if (city.id == "bogota" && vehicleType == "MOTO") {
        return false
    }

    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val calendarDay = calendar.get(Calendar.DAY_OF_MONTH)

    val dayName = when (dayOfWeek) {
        Calendar.MONDAY -> "Lunes"
        Calendar.TUESDAY -> "Martes"
        Calendar.WEDNESDAY -> "Miércoles"
        Calendar.THURSDAY -> "Jueves"
        Calendar.FRIDAY -> "Viernes"
        Calendar.SATURDAY -> "Sábado"
        Calendar.SUNDAY -> "Domingo"
        else -> ""
    }

    if (!restriction.days.contains(dayName)) {
        return false
    }

    return when (restriction.algorithm) {
        "BOGOTA_PARITY" -> {
            val lastDigit = getLastDigitOfPlate(plate)
            val isEvenDay = calendarDay % 2 == 0
            if (isEvenDay) {
                lastDigit in 1..5
            } else {
                lastDigit in 6..9 || lastDigit == 0
            }
        }
        "WEEKDAY_MAP" -> {
            val weekdayKey = when (dayOfWeek) {
                Calendar.MONDAY -> "1"
                Calendar.TUESDAY -> "2"
                Calendar.WEDNESDAY -> "3"
                Calendar.THURSDAY -> "4"
                Calendar.FRIDAY -> "5"
                Calendar.SATURDAY -> "6"
                else -> ""
            }
            val restrictedDigits = restriction.weekdayRules?.get(weekdayKey) ?: emptyList()
            val digitToCheck = if (city.id == "medellin" && vehicleType == "MOTO") {
                getFirstDigitOfPlate(plate)
            } else {
                getLastDigitOfPlate(plate)
            }
            restrictedDigits.contains(digitToCheck)
        }
        else -> false
    }
}

private fun checkRestrictionTime(currentMinutesSinceMidnight: Int, schedule: String): Boolean {
    val parts = schedule.split("y").map { it.trim() }
    for (part in parts) {
        val range = splitRange(part)
        if (range.size == 2) {
            val startMinutes = parseTimeString(range[0])
            val endMinutes = parseTimeString(range[1])
            if (currentMinutesSinceMidnight in startMinutes..endMinutes) {
                return true
            }
        }
    }
    return false
}

private fun splitRange(rangeStr: String): List<String> {
    return when {
        rangeStr.contains("-") -> rangeStr.split("-")
        rangeStr.contains(" a ") -> rangeStr.split(" a ")
        rangeStr.contains(" hasta ") -> rangeStr.split(" hasta ")
        else -> emptyList()
    }.map { it.trim() }.filter { it.isNotEmpty() }
}

private fun parseTimeString(timeStr: String): Int {
    val clean = timeStr.trim().lowercase(Locale.US).replace(".", "")
    val isPm = clean.contains("pm")
    val isAm = clean.contains("am")
    val digitsOnly = clean.replace("am", "").replace("pm", "").trim()
    val parts = digitsOnly.split(":")
    if (parts.isNotEmpty()) {
        var hours = parts[0].trim().toIntOrNull() ?: 0
        val minutes = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: 0 else 0
        if (isPm && hours < 12) {
            hours += 12
        } else if (isAm && hours == 12) {
            hours = 0
        }
        return hours * 60 + minutes
    }
    return 0
}

private fun getLastDigitOfPlate(plate: String): Int {
    val numericPart = plate.filter { it.isDigit() }
    return if (numericPart.isNotEmpty()) numericPart.last().toString().toInt() else 0
}

private fun getFirstDigitOfPlate(plate: String): Int {
    val numericPart = plate.filter { it.isDigit() }
    return if (numericPart.isNotEmpty()) numericPart.first().toString().toInt() else 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailBottomSheet(
    cell: CalendarDayCell,
    plate: String,
    vehicleType: String,
    fuelType: String?,
    city: CityData,
    activeRestriction: Restriction?,
    holidays: List<String>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dateString = sdf.format(cell.calendar.time)
    val isHoliday = holidays.contains(dateString)
    val isElectric = fuelType.equals("ELECTRIC", ignoreCase = true)

    val dayFormatter = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"))
    val formattedDate = dayFormatter.format(cell.calendar.time)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    val todayCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Bogota"))
    val isToday = cell.isCurrentMonth &&
            cell.dayNumber == todayCal.get(Calendar.DAY_OF_MONTH) &&
            cell.calendar.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
            cell.calendar.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)

    val titleText = if (isToday) "$formattedDate · HOY" else formattedDate

    val isRestrictedDay = if (!isElectric && !isHoliday && activeRestriction != null) {
        checkIfRestricted(
            calendar = cell.calendar,
            plate = plate,
            vehicleType = vehicleType,
            fuelType = fuelType,
            city = city,
            restriction = activeRestriction
        )
    } else {
        false
    }

    val schedule = activeRestriction?.schedule ?: "No aplica"

    val currentHour = todayCal.get(Calendar.HOUR_OF_DAY)
    val currentMinute = todayCal.get(Calendar.MINUTE)
    val currentMinutesSinceMidnight = currentHour * 60 + currentMinute
    val inRestrictionTimeNow = isToday && checkRestrictionTime(currentMinutesSinceMidnight, schedule)

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
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = OnSurface
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (badgeColor, badgeText) = when {
                    isElectric -> Pair(Color(0xFF00FF9D), "⚡ Exento · Vehículo Eléctrico")
                    isHoliday -> Pair(Color(0xFFFFD700), "🟠 Festivo · Sin restricción")
                    isRestrictedDay && isToday && inRestrictionTimeNow -> Pair(Color(0xFFFF5252), "🔴 Restringido AHORA (En Horario)")
                    isRestrictedDay && isToday && !inRestrictionTimeNow -> Pair(Color(0xFF00FF9D), "🟢 Libre de circular AHORA (Fuera de Horario)")
                    isRestrictedDay -> Pair(Color(0xFFFF5252), "🔴 Día con restricción")
                    else -> Pair(Color(0xFF00FF9D), "🟢 Día libre")
                }

                Text(
                    badgeText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = badgeColor
                )
            }

            if (isElectric) {
                Text(
                    text = "Los vehículos eléctricos están 100% exentos de Pico y Placa en todas las ciudades del país.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
            } else if (isHoliday) {
                Text(
                    text = "En festivos nacionales no aplica Pico y Placa en ninguna ciudad de Colombia. Puedes circular libremente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
            } else if (isRestrictedDay) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "HORARIOS DE RESTRICCIÓN",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = OnSurfaceVariant
                        )
                        val parts = schedule.split("y").map { it.trim() }
                        parts.forEachIndexed { index, part ->
                            val label = if (parts.size > 1) {
                                if (index == 0) "Pico mañana" else "Pico tarde"
                            } else {
                                "Horario continuo"
                            }
                            Text(
                                text = "• ${convert24hRangeTo12h(part)}  ($label)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurface
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ESTADO ACTUAL",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = OnSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val statusIcon = if (isToday && !inRestrictionTimeNow) "✅" else if (isToday) "🛑" else "ℹ️"
                            val statusDetail = when {
                                isToday && inRestrictionTimeNow -> "Estás en horario de restricción activa. Evita circular."
                                isToday -> "Estás fuera del horario de restricción. ¡Puedes circular libremente ahora!"
                                else -> "Día con restricción para placas terminadas en ${getLastDigitOfPlate(plate)}."
                            }
                            Text(statusIcon, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = statusDetail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurface
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "EXCEPCIONES",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = OnSurfaceVariant
                        )
                        Text("• Vehículos eléctricos / híbridos", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                        Text("• Taxis (placa amarilla)", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                        Text("• Discapacidad (con permiso)", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            } else {
                val digitToCheck = if (city.id == "medellin" && vehicleType == "MOTO") {
                    getFirstDigitOfPlate(plate)
                } else {
                    getLastDigitOfPlate(plate)
                }
                Text(
                    text = "Tu placa ($digitToCheck) no tiene restricción este día. Puedes circular libremente en ${city.name}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerLowest),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cerrar", color = OnSurface, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun convert24hRangeTo12h(range: String): String {
    val parts = splitRange(range)
    if (parts.size == 2) {
        val start12 = formatTo12h(parts[0])
        val end12 = formatTo12h(parts[1])
        return "$start12 - $end12"
    }
    return range
}

private fun formatTo12h(timeStr: String): String {
    val minutesSinceMidnight = parseTimeString(timeStr)
    val hours = minutesSinceMidnight / 60
    val minutes = minutesSinceMidnight % 60
    val amPm = if (hours >= 12) "pm" else "am"
    var hours12 = hours % 12
    if (hours12 == 0) hours12 = 12
    val minutesStr = String.format("%02d", minutes)
    return "$hours12:$minutesStr $amPm"
}
