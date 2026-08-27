package co.samidev.kilometrix.presentation.vehicle.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.ui.theme.*

@Composable
fun Vehicle3DHero(
    vehicle: Vehicle,
    isAppActive: Boolean,
    hasActiveShift: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onBlockedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vehicleFloat")
    val dy by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingY"
    )

    val imageRes = when (vehicle.type) {
        "MOTO" -> R.drawable.moto_sport_render
        "TAXI" -> R.drawable.taxi_render
        "VAN" -> R.drawable.van_render
        else -> R.drawable.car_polo_render
    }

    val fuelLabel = when (vehicle.fuel) {
        "DIESEL" -> "🛢️ Diesel"
        "GLP" -> "🔥 GLP"
        "GNV" -> "☁️ GNV"
        "ELECTRIC" -> "⚡ Eléctrico"
        else -> "⛽ Gasolina"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top status pills row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceContainerHigh.copy(alpha = 0.8f)
            ) {
                Text(
                    text = fuelLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            if (isAppActive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Secondary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Secondary.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("⚡", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = "Vehículo Activo",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Secondary
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        if (hasActiveShift) onBlockedChange()
                        else onActivate()
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "⚡ Activar",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Primary
                    )
                }
            }
        }

        // 3D Vehicle Stage with Radial Lighting & Floating Animation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            // Blue Radial Depth Glow
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Primary.copy(alpha = 0.35f),
                                PrimaryContainer.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Contact shadow
            Box(
                modifier = Modifier
                    .width(170.dp)
                    .height(14.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = (-6).dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x99000000),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // 3D Render Image with Smooth Parallax/Float
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = vehicle.brand + " " + vehicle.model,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(160.dp)
                    .graphicsLayer {
                        translationY = dy
                    }
            )
        }

        // Vehicle info and Edit button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${vehicle.brand} ${vehicle.model} · ${vehicle.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
                Text(
                    text = vehicle.plate.ifEmpty { "SIN PLACA" }.uppercase(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    ),
                    color = OnSurface
                )
                Text(
                    text = String.format("%,d km odómetro", vehicle.odometer),
                    style = MaterialTheme.typography.bodySmall,
                    color = Primary
                )
            }

            Button(
                onClick = onEdit,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer.copy(alpha = 0.25f)),
                border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Editar",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Primary
                )
            }
        }
    }
}
