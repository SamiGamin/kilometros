package co.samidev.kilometrix.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.*
import kotlinx.coroutines.delay

@Composable
private fun staggeredEntrance(index: Int): Pair<Float, Float> {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "cardAlpha"
    )
    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardTranslation"
    )
    return alpha to translationY
}

@Composable
fun HomeScreen(
    onNavigateToPicoPlaca: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(48.dp))

        // ── Header (card 0) ─────────────────────────────────────────────────
        val (h0Alpha, h0Ty) = staggeredEntrance(0)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(h0Alpha)
                .offset(y = h0Ty.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_greeting, uiState.userName.ifEmpty { "Conductor" }),
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface
                )
                Text(
                    text = uiState.currentDateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val initialText = if (uiState.userName.isNotEmpty()) uiState.userName.first().toString() else "C"
                Text(
                    text = initialText,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }

        // ── Active Vehicle Card (card 1) ────────────────────────────────────
        val (c1Alpha, c1Ty) = staggeredEntrance(1)
        uiState.activeVehicle?.let { vehicle ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(c1Alpha)
                    .offset(y = c1Ty.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceContainerLow)
                    .border(1.dp, Secondary.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val emoji = when (vehicle.type) {
                            "MOTO" -> "🏍️"
                            "VAN" -> "🚐"
                            else -> "🚗"
                        }
                        Text(emoji, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = vehicle.nickname,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = OnSurface
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Secondary.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "⚡ ACTIVO",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = Secondary
                                )
                            }
                        }
                        Text(
                            text = "${vehicle.brand} ${vehicle.model} · Placa ${vehicle.plate.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val fuelLabel = when (vehicle.fuel) {
                        "DIESEL" -> "⛽ Diesel"
                        "GLP" -> "⛽ GLP"
                        "GNV" -> "⛽ GNV"
                        "ELECTRIC" -> "⚡ Eléctrico"
                        else -> "⛽ Gasolina"
                    }
                    Text(
                        text = fuelLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Primary
                    )
                    Text(
                        text = String.format("%,d km", vehicle.odometer),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        }

        // ── Earnings card (card 2) ────────────────────────────────────────
        val (c2Alpha, c2Ty) = staggeredEntrance(2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(c2Alpha)
                .offset(y = c2Ty.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainerLow)
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .matchParentSize()
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(PrimaryContainer)
            )
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_earnings_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "$ 0",
                    style = MaterialTheme.typography.displayLarge,
                    color = OnSurface
                )
                Text(
                    text = stringResource(R.string.home_earnings_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }

        // ── Start day card (card 3) ───────────────────────────────────────
        val (c3Alpha, c3Ty) = staggeredEntrance(3)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(c3Alpha)
                .offset(y = c3Ty.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainerLow)
                .border(1.dp, Secondary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🚀", style = MaterialTheme.typography.displayLarge)
            Text(
                text = stringResource(R.string.home_start_day_title),
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface
            )
            Text(
                text = stringResource(R.string.home_start_day_body),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
            StartDayButton()
        }

        // ── AI analysis card (card 4) ─────────────────────────────────────
        val (c4Alpha, c4Ty) = staggeredEntrance(4)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(c4Alpha)
                .offset(y = c4Ty.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainerLow)
                .border(1.dp, Tertiary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🤖", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(R.string.home_ai_analysis_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface
                    )
                }
                Surface(shape = RoundedCornerShape(50), color = Tertiary.copy(alpha = 0.15f)) {
                    Text(
                        text = "✨ IA",
                        style = MaterialTheme.typography.labelSmall,
                        color = Tertiary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.home_ai_analysis_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
        }

        // ── Pico y placa card (card 5) ────────────────────────────────────
        val (c5Alpha, c5Ty) = staggeredEntrance(5)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(c5Alpha)
                .offset(y = c5Ty.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceContainerLow)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .clickable { onNavigateToPicoPlaca() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                val statusColor = if (uiState.picoPlacaStatus.isRestrictedNow) Color(0xFFEF4444) else Secondary
                PulsingDot(color = statusColor)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Pico y Placa",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = OnSurface
                    )
                    Text(
                        text = uiState.picoPlacaStatus.statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                    Text(
                        text = uiState.picoPlacaStatus.subtext,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Ver más",
                    tint = Color.White
                )
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun StartDayButton() {
    Button(
        onClick = { /* Start day action */ },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Secondary),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = stringResource(R.string.home_start_day_button),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = OnSecondary
        )
    }
}
