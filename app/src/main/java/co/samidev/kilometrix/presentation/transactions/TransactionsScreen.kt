package co.samidev.kilometrix.presentation.transactions

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelEfficiencySummary
import co.samidev.kilometrix.domain.model.ShiftEarning
import co.samidev.kilometrix.domain.model.ShiftStatus
import co.samidev.kilometrix.domain.model.ShiftType
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.model.WorkShift
import co.samidev.kilometrix.ui.theme.*
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val currencyFmt: NumberFormat = NumberFormat.getNumberInstance(Locale("es", "CO"))
    .also { (it as? DecimalFormat)?.applyPattern("#,###") }

private val monthFmt = SimpleDateFormat("MMMM yyyy", Locale("es", "CO"))
private val dayFmt = SimpleDateFormat("d MMM yyyy", Locale("es", "CO"))
private val timeFmt = SimpleDateFormat("h:mm a", Locale("es", "CO"))

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs / (1000 * 60)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}

private sealed interface DeleteTarget {
    val description: String
    data class Shift(val id: String, override val description: String) : DeleteTarget
    data class Expense(val id: String, override val description: String) : DeleteTarget
    data class Earning(val shiftId: String, val earningId: String, override val description: String) : DeleteTarget
}

private fun expenseColor(type: ExpenseType): Color = when (type) {
    ExpenseType.FUEL -> Color(0xFF4EDEA3)        // Verde
    ExpenseType.MAINTENANCE -> Color(0xFFFFB95F) // Ámbar
    ExpenseType.TOLL -> Color(0xFF60A5FA)        // Azul
    ExpenseType.INSURANCE -> Color(0xFFA78BFA)   // Violeta
    ExpenseType.PARKING -> Color(0xFFF472B6)     // Rosa
    ExpenseType.OTHER -> Color(0xFF94A3B8)       // Gris
}

data class MonthYear(val year: Int, val month: Int, val displayName: String)

enum class DatePeriodFilter(val label: String) {
    CURRENT_MONTH("📅 Este mes"),
    LAST_MONTH("🗓️ Mes anterior"),
    CUSTOM("🔍 Elegir mes...")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val vehicles by viewModel.vehicles.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val shifts by viewModel.shifts.collectAsState()
    val fuelSummary by viewModel.fuelSummary.collectAsState()
    val fuelHistory by viewModel.fuelHistory.collectAsState()  // Historia completa sin filtrar por período
    val hasActiveShift by viewModel.hasActiveShift.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    var periodFilter by remember { mutableStateOf(DatePeriodFilter.CURRENT_MONTH) }
    var selectedCustomMonth by remember { mutableStateOf<MonthYear?>(null) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }

    val filteredShifts = remember(shifts, periodFilter, selectedCustomMonth) {
        val now = System.currentTimeMillis()
        when (periodFilter) {
            DatePeriodFilter.CURRENT_MONTH -> shifts.filter { isSameMonth(it.startTime, now) }
            DatePeriodFilter.LAST_MONTH -> shifts.filter { isPreviousMonth(it.startTime, now) }
            DatePeriodFilter.CUSTOM -> {
                if (selectedCustomMonth != null) {
                    shifts.filter { isTargetMonth(it.startTime, selectedCustomMonth!!.year, selectedCustomMonth!!.month) }
                } else shifts
            }
        }
    }

    val filteredExpenses = remember(expenses, periodFilter, selectedCustomMonth) {
        val now = System.currentTimeMillis()
        when (periodFilter) {
            DatePeriodFilter.CURRENT_MONTH -> expenses.filter { isSameMonth(it.date, now) }
            DatePeriodFilter.LAST_MONTH -> expenses.filter { isPreviousMonth(it.date, now) }
            DatePeriodFilter.CUSTOM -> {
                if (selectedCustomMonth != null) {
                    expenses.filter { isTargetMonth(it.date, selectedCustomMonth!!.year, selectedCustomMonth!!.month) }
                } else expenses
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Spacer(Modifier.height(48.dp))

        // ── Header ─────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Registros",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                if (selectedVehicle != null) {
                    Text(
                        selectedVehicle!!.nickname.ifBlank { "${selectedVehicle!!.brand} ${selectedVehicle!!.model}" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            }

            // Summary badge (depends on tab)
            Column(horizontalAlignment = Alignment.End) {
                when (selectedTabIndex) {
                    0 -> { // Recorridos
                        Text("Total Recorridos", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text(
                            "${filteredShifts.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                    1 -> { // Gastos
                        if (filteredExpenses.isNotEmpty()) {
                            val totalAmount = filteredExpenses.sumOf { it.amount }
                            Text("Gastos (" + periodFilter.label.substringAfter(" ") + ")", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text(
                                "$ ${currencyFmt.format(totalAmount.toLong())}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Error
                            )
                        }
                    }
                    2 -> { // Ganancias
                        val allEarnings = filteredShifts.flatMap { it.earnings }
                        val monthEarnings = allEarnings.sumOf { it.amount }
                        Text("Ganancias (" + periodFilter.label.substringAfter(" ") + ")", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text(
                            "$ ${currencyFmt.format(monthEarnings.toLong())}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Secondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Vehicle selector ────────────────────────────────────────────────────
        if (vehicles.size > 1) {
            VehicleSelector(
                vehicles = vehicles,
                selectedVehicle = selectedVehicle,
                onVehicleSelected = { vehicle ->
                    if (hasActiveShift && vehicle.id != selectedVehicle?.id) {
                        android.widget.Toast.makeText(
                            context,
                            "Debes finalizar el recorrido actual antes de cambiar de vehículo.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        viewModel.selectVehicle(vehicle.id)
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
        }

        // ── Tab Bar ─────────────────────────────────────────────────────────────
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Background,
            contentColor = Primary,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { viewModel.setSelectedTab(0) },
                text = { Text("🚩 Recorridos (${filteredShifts.size})", fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { viewModel.setSelectedTab(1) },
                text = { Text("💸 Gastos (${filteredExpenses.size})", fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTabIndex == 2,
                onClick = { viewModel.setSelectedTab(2) },
                text = {
                    val count = filteredShifts.flatMap { it.earnings }.size
                    Text("💰 Ganancias ($count)", fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal)
                }
            )
        }

        // ── Period Filter Chips (Centrados y balanceados) ────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DatePeriodFilter.values().forEach { filter ->
                val isSelected = periodFilter == filter
                val labelText = if (filter == DatePeriodFilter.CUSTOM && selectedCustomMonth != null) {
                    "🗓️ ${selectedCustomMonth!!.displayName}"
                } else filter.label

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (filter == DatePeriodFilter.CUSTOM) {
                            showMonthPickerDialog = true
                        } else {
                            periodFilter = filter
                        }
                    },
                    label = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                labelText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.2f),
                        selectedLabelColor = Primary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        selectedBorderColor = Primary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (showMonthPickerDialog) {
            MonthPickerDialog(
                onDismiss = { showMonthPickerDialog = false },
                onMonthSelected = { monthYear ->
                    selectedCustomMonth = monthYear
                    periodFilter = DatePeriodFilter.CUSTOM
                    showMonthPickerDialog = false
                }
            )
        }

        var itemToDelete by remember { mutableStateOf<DeleteTarget?>(null) }

        if (itemToDelete != null) {
            val target = itemToDelete!!
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("¿Eliminar registro?", fontWeight = FontWeight.Bold, color = OnSurface) },
                text = {
                    Text(
                        "¿Estás seguro de que deseas eliminar ${target.description}? Esta acción no se puede deshacer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when (target) {
                                is DeleteTarget.Shift -> viewModel.deleteShift(target.id)
                                is DeleteTarget.Expense -> viewModel.deleteExpense(target.id)
                                is DeleteTarget.Earning -> viewModel.deleteEarning(target.shiftId, target.earningId)
                            }
                            itemToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // ── Tab Content ────────────────────────────────────────────────────────
        when (selectedTabIndex) {
            0 -> RecorridosTab(
                shifts = filteredShifts,
                expenses = expenses,
                fuelSummary = fuelSummary,
                onDeleteShift = { id, desc -> itemToDelete = DeleteTarget.Shift(id, desc) }
            )
            1 -> {
                // latestFuelId: último tanqueo real (toda la historia) → ciclo abierto
                val latestFuelId = remember(fuelHistory) {
                    fuelHistory.maxByOrNull { it.date }?.id
                }
                GastosTab(
                    expenses = filteredExpenses,
                    fuelSummary = fuelSummary,
                    latestFuelId = latestFuelId,
                    onDeleteExpense = { id, desc -> itemToDelete = DeleteTarget.Expense(id, desc) }
                )
            }
            2 -> GananciasTab(
                shifts = filteredShifts,
                onDeleteEarning = { shiftId, earningId, desc -> itemToDelete = DeleteTarget.Earning(shiftId, earningId, desc) }
            )
        }
    }
}

// ── TAB 1: RECORRIDOS ──────────────────────────────────────────────────────────

@Composable
private fun RecorridosTab(
    shifts: List<WorkShift>,
    expenses: List<VehicleExpense>,
    fuelSummary: FuelEfficiencySummary?,
    onDeleteShift: (String, String) -> Unit
) {
    if (shifts.isEmpty()) {
        EmptyState(
            icon = "🏁",
            title = "Sin recorridos registrados",
            subtitle = "Toca el botón + en la pantalla principal para iniciar tu primer recorrido."
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RecorridosSummaryCard(
                    shifts = shifts,
                    allExpenses = expenses,
                    fuelSummary = fuelSummary
                )
            }
            items(shifts, key = { it.id }) { shift ->
                ShiftCard(
                    shift = shift,
                    allExpenses = expenses,
                    fuelSummary = fuelSummary,
                    onDeleteShift = onDeleteShift
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun RecorridosSummaryCard(
    shifts: List<WorkShift>,
    allExpenses: List<VehicleExpense>,
    fuelSummary: FuelEfficiencySummary?
) {
    val summaryData = remember(shifts, allExpenses, fuelSummary) {
        var totalMs = 0L
        var totalKm = 0
        var totalNetProfit = 0.0

        shifts.forEach { shift ->
            val isWork = shift.type == ShiftType.WORK
            val endTimeLimit = shift.endTime ?: System.currentTimeMillis()
            val realKm = if (shift.finalOdometer != null && shift.finalOdometer > shift.initialOdometer) {
                shift.finalOdometer - shift.initialOdometer
            } else 0

            val effectiveMs = maxOf(0L, endTimeLimit - shift.startTime - shift.pausedDurationMs)
            totalMs += effectiveMs
            totalKm += realKm

            val nonFuelExpenses = allExpenses.filter { it.date in shift.startTime..endTimeLimit && it.type != ExpenseType.FUEL }
                .sumOf { it.amount }

            val kpg = fuelSummary?.kmPerGallonAverage
                ?: fuelSummary?.averageKmPerGallon.takeIf { it != null && it > 0.0 }
                ?: 35.0

            val pricePerGal = if (fuelSummary != null && fuelSummary.totalGallonsPurchased > 0.0) {
                fuelSummary.totalSpentCash / fuelSummary.totalGallonsPurchased
            } else {
                allExpenses.firstOrNull { it.type == ExpenseType.FUEL }?.fuelDetails?.pricePerGallon ?: 15800.0
            }

            val costPerKm = fuelSummary?.costPerKmReal.takeIf { it != null && it > 0.0 }
                ?: (if (kpg > 0.0) pricePerGal / kpg else 0.0)

            val fuelBurnedExpense = if (costPerKm > 0.0 && realKm > 0) realKm * costPerKm else 0.0
            val shiftExp = if (realKm > 0 && fuelBurnedExpense > 0.0) {
                nonFuelExpenses + fuelBurnedExpense
            } else {
                allExpenses.filter { it.date in shift.startTime..endTimeLimit }.sumOf { it.amount }
            }

            val shiftEarnings = shift.earnings.sumOf { it.amount }
            if (isWork) {
                totalNetProfit += (shiftEarnings - shiftExp)
            }
        }

        val totalHours = totalMs / (1000.0 * 3600.0)
        val avgHourlyRate = if (totalHours > 0.05) totalNetProfit / totalHours else 0.0
        val avgKmRate = if (totalKm > 0) totalNetProfit / totalKm.toDouble() else 0.0

        Triple(Pair(totalMs, totalKm), Pair(totalNetProfit, avgHourlyRate), avgKmRate)
    }

    val totalMs = summaryData.first.first
    val totalKm = summaryData.first.second
    val totalNetProfit = summaryData.second.first
    val avgHourlyRate = summaryData.second.second
    val avgKmRate = summaryData.third
    val durationText = formatDuration(totalMs)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainerLow,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📊 RESUMEN GLOBAL DE RECORRIDOS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Text(
                    "${shifts.size} turnos",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("⏱️ Horas trabajadas", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(durationText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnSurface)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛣️ Total km", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("${currencyFmt.format(totalKm)} km", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("💰 Total Neto", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("$ ${currencyFmt.format(totalNetProfit.toLong())}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Secondary)
                }
            }

            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("⚡ Promedio / Hora:", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(
                        if (avgHourlyRate > 0) "$ ${currencyFmt.format(avgHourlyRate.toLong())}/h" else "—",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Secondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📍 Promedio / Km:", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(
                        if (avgKmRate > 0) "$ ${currencyFmt.format(avgKmRate.toLong())}/km" else "—",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ShiftCard(
    shift: WorkShift,
    allExpenses: List<VehicleExpense>,
    fuelSummary: FuelEfficiencySummary?,
    onDeleteShift: (String, String) -> Unit
) {
    val isWork = shift.type == ShiftType.WORK
    val isLive = shift.status != ShiftStatus.ENDED
    val realKm = if (shift.finalOdometer != null && shift.finalOdometer > shift.initialOdometer) {
        shift.finalOdometer - shift.initialOdometer
    } else 0

    val effectiveMs = remember(shift) {
        val endTimeLimit = shift.endTime ?: System.currentTimeMillis()
        maxOf(0L, endTimeLimit - shift.startTime - shift.pausedDurationMs)
    }
    val effectiveHours = effectiveMs / (1000.0 * 3600.0)
    val durationText = remember(effectiveMs) { formatDuration(effectiveMs) }

    val shiftExpensesResult = remember(shift, allExpenses, fuelSummary) {
        val endTimeLimit = shift.endTime ?: System.currentTimeMillis()
        val nonFuelExpenses = allExpenses.filter { it.date in shift.startTime..endTimeLimit && it.type != ExpenseType.FUEL }
            .sumOf { it.amount }

        val kpg = fuelSummary?.kmPerGallonAverage
            ?: fuelSummary?.averageKmPerGallon.takeIf { it != null && it > 0.0 }
            ?: 35.0

        val pricePerGal = if (fuelSummary != null && fuelSummary.totalGallonsPurchased > 0.0) {
            fuelSummary.totalSpentCash / fuelSummary.totalGallonsPurchased
        } else {
            allExpenses.firstOrNull { it.type == ExpenseType.FUEL }?.fuelDetails?.pricePerGallon ?: 15800.0
        }

        val costPerKm = fuelSummary?.costPerKmReal.takeIf { it != null && it > 0.0 }
            ?: (if (kpg > 0.0) pricePerGal / kpg else 0.0)

        val gallonsBurned = if (kpg > 0.0 && realKm > 0) realKm.toDouble() / kpg else 0.0
        val fuelBurnedExpense = if (costPerKm > 0.0 && realKm > 0) realKm * costPerKm else 0.0

        if (realKm > 0 && fuelBurnedExpense > 0.0) {
            Triple(nonFuelExpenses + fuelBurnedExpense, gallonsBurned, true)
        } else {
            val rawExpenses = allExpenses.filter { it.date in shift.startTime..endTimeLimit }.sumOf { it.amount }
            Triple(rawExpenses, 0.0, false)
        }
    }

    val shiftExpenses = shiftExpensesResult.first
    val gallonsBurnedInShift = shiftExpensesResult.second
    val isOperationalCostUsed = shiftExpensesResult.third

    val totalEarnings = shift.earnings.sumOf { it.amount }
    val netProfit = totalEarnings - shiftExpenses

    val hourlyRate = if (effectiveHours > 0.05 && netProfit > 0) netProfit / effectiveHours else 0.0
    val perKmRate = if (realKm > 0 && netProfit > 0) netProfit / realKm.toDouble() else 0.0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (isLive) Secondary.copy(alpha = 0.6f) else OutlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isWork) Secondary.copy(alpha = 0.15f) else Primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isWork) "🚕 Trabajo" else "🏠 Personal",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isWork) Secondary else Primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Text(
                        text = dayFmt.format(Date(shift.startTime)),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isLive) Color(0xFF4CAF50).copy(alpha = 0.2f) else SurfaceContainerHigh
                    ) {
                        Text(
                            text = if (isLive) "● LIVE" else "Finalizado",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isLive) Color(0xFF4CAF50) else OnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            onDeleteShift(
                                shift.id,
                                "el recorrido del ${dayFmt.format(Date(shift.startTime))} (${if (realKm > 0) "$realKm km" else "en curso"})"
                            )
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar recorrido",
                            tint = OnSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Time, Duration & Distance Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceContainerHigh)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Horario", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    val startStr = timeFmt.format(Date(shift.startTime))
                    val endStr = if (shift.endTime != null) timeFmt.format(Date(shift.endTime)) else "En curso"
                    Text("$startStr - $endStr", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = OnSurface)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Duración", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Distancia", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(
                        text = if (realKm > 0) "$realKm km" else "En trayecto",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }

            // Financial Summary & Rates
            if (isWork) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Ingresos: $ ${currencyFmt.format(totalEarnings.toLong())}", style = MaterialTheme.typography.labelSmall, color = Secondary)
                        if (shiftExpenses > 0) {
                            Text("Gastos: - $ ${currencyFmt.format(shiftExpenses.toLong())}", style = MaterialTheme.typography.labelSmall, color = Error)
                            if (isOperationalCostUsed && gallonsBurnedInShift > 0.0) {
                                Text(
                                    "🔥 ${"%.1f".format(gallonsBurnedInShift)} gal quemados",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Balance Neto", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text(
                            text = "$ ${currencyFmt.format(netProfit.toLong())}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (netProfit >= 0) Secondary else Error
                        )
                    }
                }

                // Rates pills (Ganancia/hora & Ganancia/km)
                if (hourlyRate > 0 || perKmRate > 0) {
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hourlyRate > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("⚡ Ganancia / Hora:", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Text("$ ${currencyFmt.format(hourlyRate.toLong())}/h", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Secondary)
                            }
                        }
                        if (perKmRate > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("📍 Ganancia / Km:", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Text("$ ${currencyFmt.format(perKmRate.toLong())}/km", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary)
                            }
                        }
                    }
                }
            } else if (shiftExpenses > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gastos del trayecto:", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(
                        "- $ ${currencyFmt.format(shiftExpenses.toLong())}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Error
                    )
                }
            }

            // Earnings per App list
            if (isWork && shift.earnings.isNotEmpty()) {
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    shift.earnings.forEach { earning ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(earning.appEmoji, fontSize = 12.sp)
                                Text(
                                    text = "$ ${currencyFmt.format(earning.amount.toLong())}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── TAB 2: GASTOS ──────────────────────────────────────────────────────────────

@Composable
private fun GastosTab(
    expenses: List<VehicleExpense>,
    fuelSummary: FuelEfficiencySummary?,
    latestFuelId: String?,
    onDeleteExpense: (String, String) -> Unit
) {
    if (expenses.isEmpty() && fuelSummary == null) {
        EmptyState(
            icon = "📬",
            title = "Sin gastos registrados",
            subtitle = "Toca el botón + para registrar tu primer gasto o llenado de combustible."
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (fuelSummary != null) {
                item { FuelEfficiencyCard(summary = fuelSummary) }
                item { Spacer(Modifier.height(4.dp)) }
            }

            val grouped = expenses.groupBy { monthFmt.format(Date(it.date)).replaceFirstChar { c -> c.uppercase() } }
            grouped.forEach { (month, monthExpenses) ->
                item {
                    MonthHeader(
                        month = month,
                        total = monthExpenses.sumOf { it.amount }
                    )
                }
                items(monthExpenses, key = { it.id }) { expense ->
                    ExpenseCard(
                        expense = expense,
                        isLatestFuel = expense.type == ExpenseType.FUEL && expense.id == latestFuelId,
                        onDeleteExpense = onDeleteExpense
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ── TAB 3: GANANCIAS ───────────────────────────────────────────────────────────

@Composable
private fun GananciasTab(
    shifts: List<WorkShift>,
    onDeleteEarning: (String, String, String) -> Unit
) {
    val allEarnings = remember(shifts) {
        shifts.flatMap { it.earnings }
            .sortedByDescending { it.registeredAt }
    }

    val earningToShiftMap = remember(shifts) {
        buildMap {
            shifts.forEach { shift ->
                shift.earnings.forEach { earning ->
                    put(earning.id, shift)
                }
            }
        }
    }

    if (allEarnings.isEmpty()) {
        EmptyState(
            icon = "💰",
            title = "Sin ganancias registradas",
            subtitle = "Registra ganancias en tus turnos o desde el botón + para ver tu historial."
        )
    } else {
        val grouped = remember(allEarnings) {
            allEarnings.groupBy { monthFmt.format(Date(it.registeredAt)).replaceFirstChar { c -> c.uppercase() } }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            grouped.forEach { (month, monthEarnings) ->
                item {
                    MonthHeader(
                        month = month,
                        total = monthEarnings.sumOf { it.amount },
                        isEarnings = true
                    )
                }
                items(monthEarnings, key = { it.id }) { earning ->
                    val shift = earningToShiftMap[earning.id]
                    EarningCard(
                        earning = earning,
                        onDelete = {
                            if (shift != null) {
                                onDeleteEarning(
                                    shift.id,
                                    earning.id,
                                    "la ganancia de ${earning.appName} ($ ${currencyFmt.format(earning.amount.toLong())})"
                                )
                            }
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun EarningCard(
    earning: ShiftEarning,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceContainerLow,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SecondaryContainer.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(earning.appEmoji, fontSize = 22.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        earning.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    Text(
                        "${dayFmt.format(Date(earning.registeredAt))} · ${timeFmt.format(Date(earning.registeredAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "+ $ ${currencyFmt.format(earning.amount.toLong())}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Secondary
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar ganancia",
                        tint = OnSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ── Shared components ──────────────────────────────────────────────────────────

@Composable
private fun VehicleSelector(
    vehicles: List<Vehicle>,
    selectedVehicle: Vehicle?,
    onVehicleSelected: (Vehicle) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(vehicles) { vehicle ->
            val isSelected = vehicle.id == selectedVehicle?.id
            val fuelEmoji = when (vehicle.fuel.uppercase()) {
                "ELECTRIC" -> "⚡"
                "GNV" -> "☁️"
                "DIESEL" -> "🛢️"
                "GLP" -> "🔥"
                else -> "⛽"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) PrimaryContainer.copy(alpha = 0.25f)
                        else SurfaceContainerLow
                    )
                    .border(
                        1.dp,
                        if (isSelected) Primary else CardBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onVehicleSelected(vehicle) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(vehicleEmoji(vehicle.type), fontSize = 16.sp)
                    Column {
                        Text(
                            vehicle.nickname.ifBlank { vehicle.model },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Primary else OnSurface
                        )
                        Text(
                            "${vehicle.plate.uppercase()} · $fuelEmoji",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: String, total: Double, isEarnings: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            month,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant
        )
        Text(
            text = (if (isEarnings) "+ $ " else "Total: $ ") + currencyFmt.format(total.toLong()),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isEarnings) Secondary else OnSurfaceVariant
        )
    }
}

@Composable
private fun ExpenseCard(
    expense: VehicleExpense,
    isLatestFuel: Boolean = false,
    onDeleteExpense: (String, String) -> Unit
) {
    val color = expenseColor(expense.type)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceContainerLow,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Emoji + Title + Amount + Delete Icon (esquina superior derecha)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(expense.type.emoji, fontSize = 18.sp)
                    }

                    Text(
                        expense.type.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "- $ ${currencyFmt.format(expense.amount.toLong())}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = color
                    )

                    IconButton(
                        onClick = {
                            onDeleteExpense(
                                expense.id,
                                "el gasto de ${expense.type.label} por $ ${currencyFmt.format(expense.amount.toLong())}"
                            )
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar gasto",
                            tint = OnSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Body Row: Fuel details / Notes + Chips + Odometer + Date
            val fd = expense.fuelDetails
            if (expense.type == ExpenseType.FUEL && fd != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (fd.gallons > 0.0) {
                        FuelChip("${"%.2f".format(fd.gallons)} gal", color)
                    }
                    if (fd.pricePerGallon > 0.0) {
                        FuelChip("$ ${currencyFmt.format(fd.pricePerGallon.toLong())}/gal", color.copy(alpha = 0.7f))
                    }
                    when {
                        isLatestFuel ->
                            FuelChip("⏳ Ciclo abierto", OnSurfaceVariant.copy(alpha = 0.6f))
                        fd.kmPerGallon > 0.0 ->
                            FuelChip("${"%.1f".format(fd.kmPerGallon)} km/gal", Secondary)
                    }
                }

                val kmStr = if (!isLatestFuel && fd.kmTraveled > 0) "🛣 ${currencyFmt.format(fd.kmTraveled)} km" else ""
                val odStr = if (fd.odometerAtRefuel > 0) "Odóm: ${currencyFmt.format(fd.odometerAtRefuel)} km" else ""
                val tankTypeStr = when {
                    fd.isFullTank -> "⛽ Tanque Lleno"
                    fd.isReserve -> "⚠️ Reserva"
                    fd.isPartial -> "💧 Parcial"
                    else -> ""
                }
                val subInfo = listOf(kmStr, odStr, tankTypeStr).filter { it.isNotBlank() }.joinToString(" · ")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (subInfo.isNotBlank()) {
                        Text(
                            text = subInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "${dayFmt.format(Date(expense.date))} · ${timeFmt.format(Date(expense.date))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (expense.notes.isNotBlank()) {
                        Text(
                            expense.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        "${dayFmt.format(Date(expense.date))} · ${timeFmt.format(Date(expense.date))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FuelChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun FuelEfficiencyCard(summary: FuelEfficiencySummary) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PrimaryContainer.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⚡ RENDIMIENTO DE COMBUSTIBLE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Text(
                    "${summary.fillUpsCount} tanqueos procesados",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val kpg = summary.kmPerGallonAverage
                        ?: summary.averageKmPerGallon.takeIf { it > 0.0 }
                        ?: (if (summary.totalGallonsPurchased > 0 && summary.totalKmTraveled > 0)
                            summary.totalKmTraveled.toDouble() / summary.totalGallonsPurchased else 0.0)
                    Text(
                        if (kpg > 0.0) "${"%.1f".format(kpg)} km/gal" else "⏳ Acumulando",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (kpg > 0.0) Primary else OnSurfaceVariant
                    )
                    Text("Promedio real", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val cpk = if (summary.costPerKmReal > 0) summary.costPerKmReal else summary.costPerKm
                    Text(
                        if (cpk > 0) "$ ${currencyFmt.format(cpk.toLong())}/km" else "--",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Secondary
                    )
                    Text("Costo por km", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }

                if (summary.totalKmTraveled > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${currencyFmt.format(summary.totalKmTraveled)} km",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = OnSurface
                        )
                        Text("Total recorrido", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: String,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = OnSurface,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MonthPickerDialog(
    onDismiss: () -> Unit,
    onMonthSelected: (MonthYear) -> Unit
) {
    val months = remember {
        val list = mutableListOf<MonthYear>()
        val cal = java.util.Calendar.getInstance()
        val fmt = SimpleDateFormat("MMMM yyyy", Locale("es", "CO"))
        for (i in 0..11) {
            val y = cal.get(java.util.Calendar.YEAR)
            val m = cal.get(java.util.Calendar.MONTH)
            val name = fmt.format(cal.time).replaceFirstChar { it.uppercase() }
            list.add(MonthYear(y, m, name))
            cal.add(java.util.Calendar.MONTH, -1)
        }
        list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🗓️ Selecciona un Mes", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(months) { monthYear ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onMonthSelected(monthYear)
                            }
                    ) {
                        Text(
                            text = monthYear.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
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

private fun isSameMonth(ts1: Long, ts2: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().also { it.timeInMillis = ts1 }
    val cal2 = java.util.Calendar.getInstance().also { it.timeInMillis = ts2 }
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
            cal1.get(java.util.Calendar.MONTH) == cal2.get(java.util.Calendar.MONTH)
}

private fun isPreviousMonth(ts1: Long, ts2: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().also { it.timeInMillis = ts1 }
    val cal2 = java.util.Calendar.getInstance().also { it.timeInMillis = ts2 }
    cal2.add(java.util.Calendar.MONTH, -1)
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
            cal1.get(java.util.Calendar.MONTH) == cal2.get(java.util.Calendar.MONTH)
}

private fun isTargetMonth(ts: Long, year: Int, month: Int): Boolean {
    val cal = java.util.Calendar.getInstance().also { it.timeInMillis = ts }
    return cal.get(java.util.Calendar.YEAR) == year && cal.get(java.util.Calendar.MONTH) == month
}

private fun vehicleEmoji(type: String): String = when (type.uppercase()) {
    "MOTO" -> "🏍️"
    "VAN" -> "🚐"
    else -> "🚗"
}
