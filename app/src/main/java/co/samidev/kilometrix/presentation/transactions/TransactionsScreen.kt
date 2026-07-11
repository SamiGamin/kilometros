package co.samidev.kilometrix.presentation.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.*

@Composable
fun TransactionsScreen() {
    var selectedPeriod by remember { mutableIntStateOf(0) }
    val periods = listOf(
        stringResource(R.string.transactions_period_day),
        stringResource(R.string.transactions_period_week),
        stringResource(R.string.transactions_period_month)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            text = stringResource(R.string.transactions_title),
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface
        )

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
                        .let {
                            if (!isSelected) it else it
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = { selectedPeriod = index },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) Color.Black else OnSurfaceVariant
                        )
                    }
                }
            }
        }

        // Date navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { }) {
                Text("‹", style = MaterialTheme.typography.headlineMedium, color = OnSurfaceVariant)
            }
            Text(
                text = stringResource(R.string.transactions_today),
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface
            )
            IconButton(onClick = { }) {
                Text("›", style = MaterialTheme.typography.headlineMedium, color = OnSurfaceVariant)
            }
        }

        // Empty state
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📬", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.transactions_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.transactions_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { /* TODO */ },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "+ ${stringResource(R.string.transactions_register_button)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
    }
}
