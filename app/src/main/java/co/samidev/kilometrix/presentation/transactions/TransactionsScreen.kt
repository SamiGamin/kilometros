package co.samidev.kilometrix.presentation.transactions

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelEfficiencySummary
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.ui.theme.*
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val currencyFmt: NumberFormat = NumberFormat.getNumberInstance(Locale("es", "CO"))
    .also { (it as? DecimalFormat)?.applyPattern("#,###") }

private val monthFmt = SimpleDateFormat("MMMM yyyy", Locale("es", "CO"))
private val dayFmt = SimpleDateFormat("d MMM", Locale("es", "CO"))
private val timeFmt = SimpleDateFormat("h:mm a", Locale("es", "CO"))

// Colores por tipo de gasto
private fun expenseColor(type: ExpenseType): Color = when (type) {
    ExpenseType.FUEL -> Color(0xFF4EDEA3)        // Verde (Secondary)
    ExpenseType.MAINTENANCE -> Color(0xFFFFB95F) // Ámbar
    ExpenseType.TOLL -> Color(0xFF60A5FA)        // Azul claro
    ExpenseType.INSURANCE -> Color(0xFFA78BFA)   // Violeta
    ExpenseType.PARKING -> Color(0xFFF472B6)     // Rosa
    ExpenseType.OTHER -> Color(0xFF94A3B8)       // Gris
}

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val vehicles by viewModel.vehicles.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val fuelSummary by viewModel.fuelSummary.collectAsState()

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
                    "Gastos",
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
            // Total badge
            if (expenses.isNotEmpty()) {
                val totalMonth = expenses
                    .filter { isSameMonth(it.date, System.currentTimeMillis()) }
                    .sumOf { it.amount }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Este mes", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(
                        "$ ${currencyFmt.format(totalMonth.toLong())}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Error
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Vehicle selector ────────────────────────────────────────────────────
        if (vehicles.size > 1) {
            VehicleSelector(
                vehicles = vehicles,
                selectedVehicle = selectedVehicle,
                onVehicleSelected = { viewModel.selectVehicle(it.id) }
            )
            Spacer(Modifier.height(16.dp))
        }

        // ── Content ─────────────────────────────────────────────────────────────
        if (expenses.isEmpty() && fuelSummary == null) {
            EmptyExpensesState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Fuel efficiency card
                if (fuelSummary != null) {
                    item { FuelEfficiencyCard(summary = fuelSummary!!) }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                // Group expenses by month
                val grouped = expenses.groupBy { monthFmt.format(Date(it.date)).replaceFirstChar { c -> c.uppercase() } }
                grouped.forEach { (month, monthExpenses) ->
                    item {
                        MonthHeader(
                            month = month,
                            total = monthExpenses.sumOf { it.amount }
                        )
                    }
                    items(monthExpenses, key = { it.id }) { expense ->
                        ExpenseCard(expense = expense)
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ── Vehicle Selector ───────────────────────────────────────────────────────────

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
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Primary else CardBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onVehicleSelected(vehicle) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = vehicleEmoji(vehicle.type), style = MaterialTheme.typography.titleMedium)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                vehicle.nickname.ifBlank { vehicle.brand },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Primary else OnSurface
                            )
                            if (isSelected) {
                                Text("✓", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text(
                            text = "${vehicle.plate.uppercase()} · $fuelEmoji",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) Primary.copy(alpha = 0.8f) else OnSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ── Fuel Efficiency Card ───────────────────────────────────────────────────────

@Composable
fun FuelEfficiencyCard(summary: FuelEfficiencySummary) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF003824),
                        Color(0xFF005236),
                        Color(0xFF003824)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        "⛽ Rendimiento de Combustible",
                        style = MaterialTheme.typography.labelMedium,
                        color = Secondary.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${"%.1f".format(summary.averageKmPerGallon)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Secondary
                        )
                        Text(
                            "km/gal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Secondary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        "${"%.1f".format(summary.averageKmPerLiter)} km/L",
                        style = MaterialTheme.typography.bodySmall,
                        color = Secondary.copy(alpha = 0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${summary.fillUpsCount} llenadas",
                        style = MaterialTheme.typography.labelSmall,
                        color = Secondary.copy(alpha = 0.7f)
                    )
                    Text(
                        "${currencyFmt.format(summary.totalKmFueled)} km",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Secondary
                    )
                }
            }

            HorizontalDivider(color = Secondary.copy(alpha = 0.2f), thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FuelStat(
                    label = "Total galones",
                    value = "${"%.2f".format(summary.totalGallons)} gal"
                )
                FuelStat(
                    label = "Gasto total",
                    value = "$ ${currencyFmt.format(summary.totalFuelCost.toLong())}"
                )
                FuelStat(
                    label = "Costo/km",
                    value = "$ ${"%.0f".format(summary.costPerKm)}"
                )
            }

            // Best / worst bar
            if (summary.bestKmPerGallon > 0.0 && summary.worstKmPerGallon < summary.bestKmPerGallon) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FuelStat(label = "Mejor", value = "${"%.1f".format(summary.bestKmPerGallon)} km/gal")
                    FuelStat(label = "Peor", value = "${"%.1f".format(summary.worstKmPerGallon)} km/gal")
                }
            }
        }
    }
}

@Composable
private fun FuelStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Secondary.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Secondary)
    }
}

// ── Month header ───────────────────────────────────────────────────────────────

@Composable
private fun MonthHeader(month: String, total: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            month,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface
        )
        Text(
            "- $ ${currencyFmt.format(total.toLong())}",
            style = MaterialTheme.typography.bodyMedium,
            color = Error
        )
    }
}

// ── Expense Card ───────────────────────────────────────────────────────────────

@Composable
private fun ExpenseCard(expense: VehicleExpense) {
    val color = expenseColor(expense.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(expense.type.emoji, style = MaterialTheme.typography.titleMedium)
        }

        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                expense.type.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
            // Fuel: show km/gal
            val fd = expense.fuelDetails
            if (expense.type == ExpenseType.FUEL && fd != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (fd.kmPerGallon > 0.0) {
                        FuelChip("${"%.1f".format(fd.kmPerGallon)} km/gal", color)
                        FuelChip("${"%.1f".format(fd.kmPerLiter)} km/L", color.copy(alpha = 0.6f))
                    }
                    if (fd.gallons > 0.0) {
                        FuelChip("${"%.2f".format(fd.gallons)} gal", color.copy(alpha = 0.5f))
                    }
                }
            } else if (expense.notes.isNotBlank()) {
                Text(
                    expense.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${dayFmt.format(Date(expense.date))} · ${timeFmt.format(Date(expense.date))}",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant
            )
        }

        // Amount
        Text(
            "- $ ${currencyFmt.format(expense.amount.toLong())}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
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

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyExpensesState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📬", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(20.dp))
        Text(
            "Sin gastos registrados",
            style = MaterialTheme.typography.headlineSmall,
            color = OnSurface,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Toca el botón + para registrar\ntu primer gasto o llenado de combustible.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun isSameMonth(ts1: Long, ts2: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().also { it.timeInMillis = ts1 }
    val cal2 = java.util.Calendar.getInstance().also { it.timeInMillis = ts2 }
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
            cal1.get(java.util.Calendar.MONTH) == cal2.get(java.util.Calendar.MONTH)
}

private fun vehicleEmoji(type: String): String = when (type.uppercase()) {
    "MOTO" -> "🏍️"
    "VAN" -> "🚐"
    else -> "🚗"
}
