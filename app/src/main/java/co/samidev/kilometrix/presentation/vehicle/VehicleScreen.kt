package co.samidev.kilometrix.presentation.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.*

@Composable
fun VehicleScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(48.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(stringResource(R.string.vehicle_title), style = MaterialTheme.typography.headlineMedium, color = OnSurface)
                Text("1 vehículo · 0 alertas", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
        }

        // Vehicle card
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
                    Text("🚗", style = MaterialTheme.typography.displayLarge)
                }
                Box(modifier = Modifier.align(Alignment.TopStart)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = SurfaceContainerHigh) {
                        Text("Gasolina", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            // Details
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("Chevrolet Spark · 2020", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    Text("ABC 123", style = MaterialTheme.typography.headlineMedium, color = OnSurface)
                    Text("0 km recorridos", style = MaterialTheme.typography.bodySmall, color = Error)
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
                    VehicleStat("—", stringResource(R.string.vehicle_stat_km))
                    VerticalDivider(modifier = Modifier.height(32.dp), color = OutlineVariant)
                    VehicleStat("$0", stringResource(R.string.vehicle_stat_expense))
                    VerticalDivider(modifier = Modifier.height(32.dp), color = OutlineVariant)
                    VehicleStat("—", stringResource(R.string.vehicle_stat_docs))
                }

                // Pico y Placa badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Secondary.copy(alpha = 0.1f))
                        .border(1.dp, Secondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("✅", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Exento de Pico y Placa · Ciudad sin pico y placa registrado",
                        style = MaterialTheme.typography.bodySmall,
                        color = Secondary
                    )
                }

                // Documents section
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.vehicle_docs_label), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    TextButton(onClick = {}) {
                        Text(stringResource(R.string.vehicle_docs_add), style = MaterialTheme.typography.labelMedium, color = Secondary)
                    }
                }

                DocumentRow("🛡️", stringResource(R.string.setup_docs_soat))
                DocumentRow("🔧", stringResource(R.string.setup_docs_tecnomecanica))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun VehicleStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun DocumentRow(emoji: String, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                Text(stringResource(R.string.vehicle_doc_no_record), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }
        }
        Text("+", style = MaterialTheme.typography.headlineSmall, color = OnSurfaceVariant)
    }
}
