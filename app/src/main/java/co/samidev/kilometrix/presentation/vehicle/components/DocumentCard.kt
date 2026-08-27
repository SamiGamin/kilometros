package co.samidev.kilometrix.presentation.vehicle.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DocumentCard(
    icon: ImageVector,
    title: String,
    expiryDate: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFraction = calculateDateFraction(expiryDate)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceContainerLowest)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isEnabled) PrimaryContainer.copy(alpha = 0.25f) else SurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isEnabled) Primary else OnSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = OnSurface
                )
                Text(
                    text = if (isEnabled && expiryDate.isNotBlank()) "Vence: $expiryDate"
                           else if (isEnabled) "Habilitado"
                           else "Sin registro",
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isEnabled && expiryDate.isNotBlank()) Secondary else OnSurfaceVariant
                )

                // Mini progress bar
                if (isEnabled && expiryDate.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF2A364F))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(Primary)
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = { onToggle(!isEnabled) },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Configurar",
                tint = if (isEnabled) Primary else OnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun calculateDateFraction(expiryDate: String): Float {
    if (expiryDate.isBlank()) return 0.5f
    return try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val date = sdf.parse(expiryDate) ?: return 0.5f
        val now = System.currentTimeMillis()
        val diffDays = (date.time - now) / (1000 * 60 * 60 * 24)
        when {
            diffDays <= 0 -> 0.05f
            diffDays >= 365 -> 1.0f
            else -> (diffDays.toFloat() / 365f).coerceIn(0.1f, 1.0f)
        }
    } catch (_: Exception) {
        0.5f
    }
}
