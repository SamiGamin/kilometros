package co.samidev.kilometrix.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.ui.theme.*
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

private val currencyFmt: NumberFormat = NumberFormat.getNumberInstance(Locale("es", "CO"))
    .also { (it as? DecimalFormat)?.applyPattern("#,###") }

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(48.dp))

        Text("Análisis e Historial", style = MaterialTheme.typography.headlineMedium, color = OnSurface)

        // Vehicle Selector
        if (uiState.vehicles.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.vehicles) { vehicle ->
                    val isSelected = vehicle.id == uiState.selectedVehicle?.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Primary.copy(alpha = 0.2f) else SurfaceContainerLow)
                            .border(1.dp, if (isSelected) Primary else Color.Transparent, RoundedCornerShape(20.dp))
                            .clickable { viewModel.selectVehicle(vehicle.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = vehicle.nickname.ifBlank { vehicle.brand },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) Primary else OnSurfaceVariant
                        )
                    }
                }
            }
        }

        // Period selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceContainerLow)
                .padding(4.dp)
        ) {
            val periods = listOf(AnalyticsPeriod.DAY to "Día", AnalyticsPeriod.WEEK to "Semana", AnalyticsPeriod.MONTH to "Mes")
            periods.forEach { (period, label) ->
                val isSelected = period == uiState.period
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Secondary else Color.Transparent)
                        .clickable { viewModel.setPeriod(period) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) Color.Black else OnSurfaceVariant)
                }
            }
        }

        // Date nav
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.shiftOffset(-1) }) { Text("‹", style = MaterialTheme.typography.headlineMedium, color = OnSurfaceVariant) }
            Text(uiState.periodLabel, style = MaterialTheme.typography.titleMedium, color = OnSurface)
            IconButton(onClick = { viewModel.shiftOffset(1) }, enabled = uiState.dateOffset < 0) { 
                Text("›", style = MaterialTheme.typography.headlineMedium, color = if (uiState.dateOffset < 0) OnSurfaceVariant else Color.Transparent) 
            }
        }

        // Net Profit (Main Card)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Secondary.copy(alpha = 0.1f))
                .border(1.dp, Secondary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Text("Ganancia Neta", style = MaterialTheme.typography.labelSmall, color = Secondary)
                Text("$ ${currencyFmt.format(uiState.netProfit.toLong())}", style = MaterialTheme.typography.displayLarge, color = Secondary)
            }
        }

        // Gross Earnings & Expenses
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                label = "Ingresos Brutos",
                value = "$ ${currencyFmt.format(uiState.totalEarnings.toLong())}",
                color = Primary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Gastos",
                value = "$ ${currencyFmt.format(uiState.totalExpenses.toLong())}",
                color = Error,
                modifier = Modifier.weight(1f)
            )
        }

        // Analytics metrics
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Km Recorridos", "${uiState.kmDriven} km", OnSurface, modifier = Modifier.weight(1f))
            MetricCard("Ganancia / Km", "$ ${currencyFmt.format(uiState.earningsPerKm.toLong())}", OnSurface, modifier = Modifier.weight(1f))
        }

        // Bar Chart
        if (uiState.chartData.isNotEmpty()) {
            Text("Evolución de Ingresos", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            BarChart(
                data = uiState.chartData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceContainerLow)
                    .padding(16.dp)
            )
        }

        // Platform Breakdown
        if (uiState.platformEarnings.isNotEmpty()) {
            Text("Ingresos por Plataforma", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceContainerLow)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.platformEarnings.entries.sortedByDescending { it.value }.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(entry.key, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                        Text("$ ${currencyFmt.format(entry.value.toLong())}", style = MaterialTheme.typography.titleMedium, color = Secondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1)
    }
}

@Composable
fun BarChart(data: List<ChartBarData>, modifier: Modifier = Modifier) {
    val maxVal = data.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { barData ->
            val fillHeight = (barData.value / maxVal).toFloat()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(0.6f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fillHeight)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(Primary)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = barData.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
