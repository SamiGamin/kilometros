package co.samidev.kilometrix.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.*

@Composable
fun AnalyticsScreen() {
    var selectedPeriod by remember { mutableIntStateOf(2) } // Default: Month
    val periods = listOf(
        stringResource(R.string.transactions_period_day),
        stringResource(R.string.transactions_period_week),
        stringResource(R.string.transactions_period_month)
    )
    val tabs = listOf(
        stringResource(R.string.analytics_tab_summary),
        stringResource(R.string.analytics_tab_platforms),
        stringResource(R.string.analytics_tab_expenses),
        stringResource(R.string.analytics_tab_history)
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(48.dp))

        Text(stringResource(R.string.analytics_title), style = MaterialTheme.typography.headlineMedium, color = OnSurface)

        // Period selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceContainerLow)
                .padding(4.dp)
        ) {
            periods.forEachIndexed { index, label ->
                val isSelected = index == selectedPeriod
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Secondary else Color.Transparent)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = { selectedPeriod = index }, contentPadding = PaddingValues(0.dp)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) Color.Black else OnSurfaceVariant)
                    }
                }
            }
        }

        // Date nav
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {}) { Text("‹", style = MaterialTheme.typography.headlineMedium, color = OnSurfaceVariant) }
            Text("Este mes", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            IconButton(onClick = {}) { Text("›", style = MaterialTheme.typography.headlineMedium, color = OnSurfaceVariant) }
        }

        // Sub-tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Primary,
            edgePadding = 0.dp,
            divider = { HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f)) }
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            tab,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedTab == index) Primary else OnSurfaceVariant
                        )
                    }
                )
            }
        }

        // Metrics cards
        // Income
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Secondary.copy(alpha = 0.1f))
                .border(1.dp, Secondary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(stringResource(R.string.analytics_income_label), style = MaterialTheme.typography.labelSmall, color = Secondary)
                Text("$ 0", style = MaterialTheme.typography.displayLarge, color = Secondary)
            }
        }

        // Expenses + Net row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                label = stringResource(R.string.analytics_expense_label),
                value = "$ 0",
                color = Error,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = stringResource(R.string.analytics_net_label),
                value = "$ 0",
                color = Primary,
                modifier = Modifier.weight(1f)
            )
        }

        // Expense ratio
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceContainerLow)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.analytics_expense_ratio_label), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("0.0%", style = MaterialTheme.typography.displayLarge, color = Secondary)
                Text(stringResource(R.string.analytics_expense_ratio_detail), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(SurfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0f)
                        .background(Secondary)
                )
            }
            Text(stringResource(R.string.analytics_excellent), style = MaterialTheme.typography.bodySmall, color = Secondary)
        }

        // Bottom stats row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("0", "Transacciones", OnSurface, modifier = Modifier.weight(1f))
            MetricCard("—", "Mejor plataforma", OnSurface, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
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
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
    }
}
