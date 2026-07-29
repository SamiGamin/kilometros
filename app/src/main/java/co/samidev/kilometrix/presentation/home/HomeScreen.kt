package co.samidev.kilometrix.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.R
import co.samidev.kilometrix.domain.model.FuelEfficiencySummary
import co.samidev.kilometrix.domain.model.ShiftStatus
import co.samidev.kilometrix.domain.model.ShiftType
import co.samidev.kilometrix.domain.model.TRANSPORT_APPS
import co.samidev.kilometrix.domain.model.TransportApp
import co.samidev.kilometrix.presentation.util.ThousandSeparatorVisualTransformation
import co.samidev.kilometrix.ui.theme.*
import kotlinx.coroutines.delay
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val currencyFmt: NumberFormat = NumberFormat.getNumberInstance(Locale("es", "CO"))
    .also { (it as? DecimalFormat)?.applyPattern("#,###") }

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02dh %02dm %02ds", hours, minutes, seconds)
}

private fun formatTimeOnly(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale("es", "CO"))
    return sdf.format(Date(timestamp))
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPicoPlaca: () -> Unit,
    onNavigateToGastos: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showStartShiftDialog by remember { mutableStateOf(false) }
    var showEndShiftDialog by remember { mutableStateOf(false) }
    var showAddEarningDialog by remember { mutableStateOf(false) }
    var showNoEarningsConfirm by remember { mutableStateOf(false) }
    var pendingEndShiftAfterEarning by remember { mutableStateOf(false) }

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
                    text = stringResource(R.string.home_greeting, uiState.userName),
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface
                )
                Text(
                    text = uiState.currentDateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }

            val initial = uiState.userName.firstOrNull()?.toString()?.uppercase() ?: "S"
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }

        // ── Active Vehicle Card (card 1) ────────────────────────────────────
        val (c1Alpha, c1Ty) = staggeredEntrance(1)
        val vehicle = uiState.activeVehicle
        if (vehicle != null) {
            val emoji = when (vehicle.type) {
                "MOTO" -> "🏍️"
                "VAN" -> "🚐"
                else -> "🚗"
            }
            val fuelLabel = when (vehicle.fuel) {
                "ELECTRIC" -> "⚡ Eléctrico"
                "GNV" -> "☁️ GNV"
                "DIESEL" -> "🛢️ Diesel"
                "GLP" -> "🔥 GLP"
                else -> "⛽ Gasolina"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(c1Alpha)
                    .offset(y = c1Ty.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceContainerLow)
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = vehicle.nickname.ifBlank { vehicle.brand },
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Secondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "⚡ ACTIVO",
                                style = MaterialTheme.typography.labelSmall,
                                color = Secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${vehicle.brand} ${vehicle.model} · Placa ${vehicle.plate.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
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

        // ── Earnings/Expenses card (card 2) — Visible solo cuando hay recorrido activo ──
        val shiftActive = uiState.activeShift != null

        AnimatedVisibility(
            visible = shiftActive,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val (c2Alpha, c2Ty) = staggeredEntrance(2)
            val isPersonalShift = uiState.activeShift?.type == ShiftType.PERSONAL
            val netProfit = uiState.shiftNetProfit
            val totalEarnings = uiState.shiftTotalEarnings
            val totalExpenses = uiState.shiftTotalExpenses

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(c2Alpha)
                    .offset(y = c2Ty.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF131B2E))
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                        .background(if (isPersonalShift) Primary else if (netProfit < 0) Error else Primary)
                )
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPersonalShift) "RECORRIDO PERSONAL ACTIVO"
                                   else "BALANCE NETO DEL RECORRIDO",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPersonalShift) Primary.copy(alpha = 0.15f)
                                    else if (netProfit >= 0) Secondary.copy(alpha = 0.15f)
                                    else Error.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isPersonalShift) "🏠 Personal"
                                       else if (netProfit >= 0) "🟢 Ganancia"
                                       else "🔴 Pérdida / Gasto",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPersonalShift) Primary else if (netProfit >= 0) Secondary else Error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isPersonalShift) {
                        Text(
                            text = if (totalExpenses > 0) "$ ${currencyFmt.format(totalExpenses.toLong())} en gastos"
                                   else "~${"%.1f".format(uiState.estimatedKmTraveled)} km",
                            style = MaterialTheme.typography.displayLarge,
                            color = OnSurface
                        )
                        Text(
                            text = if (totalExpenses > 0) "Gastos registrados durante este trayecto personal"
                                   else "Trayecto particular sin registro de ganancias",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "$ ${currencyFmt.format(netProfit.toLong())}",
                            style = MaterialTheme.typography.displayLarge,
                            color = if (netProfit >= 0) OnSurface else Error
                        )

                        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("🟢", fontSize = 12.sp)
                                Text("Ingresos:", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                Text(
                                    "$ ${currencyFmt.format(totalEarnings.toLong())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("🔴", fontSize = 12.sp)
                                Text("Gastos:", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                Text(
                                    "$ ${currencyFmt.format(totalExpenses.toLong())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Error
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Work Shift Card (card 3) — Reposo vs Activo ───────────────────────
        val (c3Alpha, c3Ty) = staggeredEntrance(3)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(c3Alpha)
                .offset(y = c3Ty.dp)
        ) {
            val shift = uiState.activeShift
            if (shift == null) {
                // ── Estado REPOSO ──────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainerLow)
                        .border(1.dp, Secondary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🚀", style = MaterialTheme.typography.displayLarge)
                    Text(
                        text = "¿Iniciar un nuevo recorrido?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnSurface
                    )
                    Text(
                        text = "Registra tu odómetro inicial para medir tiempo activo, distancia, combustible y gastos o ganancias.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { showStartShiftDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Iniciar Recorrido",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnSecondary
                        )
                    }
                }
            } else {
                // ── Estado TURNO ACTIVO ────────────────────────────────────────
                val isPaused = shift.status == ShiftStatus.PAUSED

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainerLow)
                        .border(
                            1.dp,
                            if (isPaused) Color(0xFFFF9800).copy(alpha = 0.4f)
                            else if (shift.type == ShiftType.PERSONAL) Primary.copy(alpha = 0.3f)
                            else Secondary.copy(alpha = 0.3f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header de Estado LIVE / PAUSA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(if (shift.type == ShiftType.PERSONAL) "🏠" else "🚕", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "RECORRIDO EN CURSO",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (shift.type == ShiftType.PERSONAL) Primary.copy(alpha = 0.15f) else Secondary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (shift.type == ShiftType.PERSONAL) "Personal" else "Trabajo",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (shift.type == ShiftType.PERSONAL) Primary else Secondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isPaused) Color(0xFFFF9800).copy(alpha = 0.15f) else Secondary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isPaused) Color(0xFFFF9800).copy(alpha = 0.5f) else Secondary.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PulsingDot(color = if (isPaused) Color(0xFFFF9800) else Secondary)
                                Text(
                                    text = if (isPaused) "EN PAUSA" else "LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isPaused) Color(0xFFFF9800) else Secondary
                                )
                            }
                        }
                    }

                    // Cronómetro vivo gigante
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatMs(uiState.shiftElapsedMs),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isPaused) Color(0xFFFFB74D) else OnSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Inicio: ${formatTimeOnly(shift.startTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                            Text(
                                text = "Odómetro: ${currencyFmt.format(shift.initialOdometer)} km",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                    // Métricas de Consumo Estimado
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceContainerHigh)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        ShiftStatItem(
                            label = "Recorrido est.",
                            value = "${"%.1f".format(uiState.estimatedKmTraveled)} km"
                        )
                        ShiftStatItem(
                            label = "Combustible",
                            value = "~${"%.1f".format(uiState.estimatedGallonsConsumed)} gal"
                        )
                        ShiftStatItem(
                            label = "Costo est.",
                            value = "$ ${currencyFmt.format(uiState.estimatedCostConsumed.toLong())}"
                        )
                    }

                    // Ganancias por App (solo para recorridos de Trabajo)
                    if (shift.type == ShiftType.WORK) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "GANANCIAS REGISTRADAS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { showAddEarningDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Registrar ingreso", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            if (shift.earnings.isEmpty()) {
                                Text(
                                    text = "No has registrado ingresos en este recorrido.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant.copy(alpha = 0.7f)
                                )
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    shift.earnings.take(4).forEach { earning ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = SurfaceContainerHigh
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(earning.appEmoji, fontSize = 12.sp)
                                                Text(
                                                    text = "$ ${currencyFmt.format(earning.amount.toLong())}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Banner informativo para Recorrido Personal
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceContainerHigh.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🏠", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Recorrido particular / personal. Midiendo odómetro, tiempo y consumo de combustible.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }

                    // Botones de acción (Pausar/Reanudar y Finalizar)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.togglePauseResumeShift() },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isPaused) Secondary else Color(0xFFFF9800)
                            )
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Stop,
                                contentDescription = null,
                                tint = if (isPaused) Secondary else Color(0xFFFF9800)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isPaused) "Reanudar" else "Pausar",
                                color = if (isPaused) Secondary else Color(0xFFFF9800),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (shift.type == ShiftType.WORK && shift.earnings.isNullOrEmpty()) {
                                    showNoEarningsConfirm = true
                                } else {
                                    showEndShiftDialog = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "🏁 Finalizar",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
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
                    Text("🤖", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.home_ai_analysis_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = TertiaryContainer
                ) {
                    Text(
                        text = "✦ IA",
                        style = MaterialTheme.typography.labelSmall,
                        color = Tertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.home_ai_analysis_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
        }

        // ── Rendimiento de Combustible card ────────────────────────────────
        if (uiState.fuelEfficiencySummary != null) {
            val (cEffAlpha, cEffTy) = staggeredEntrance(4)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(cEffAlpha)
                    .offset(y = cEffTy.dp)
            ) {
                HomeFuelEfficiencyCard(
                    summary = uiState.fuelEfficiencySummary!!,
                    onClick = onNavigateToGastos
                )
            }
        }

        // ── Pico y Placa card (card 5) ────────────────────────────────────
        val (c5Alpha, c5Ty) = staggeredEntrance(5)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(c5Alpha)
                .offset(y = c5Ty.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainerLow)
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                .clickable { onNavigateToPicoPlaca() }
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_pico_placa_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PulsingDot(color = if (uiState.picoPlacaStatus.isRestrictedNow) MaterialTheme.colorScheme.error else Secondary)
                    Text(
                        text = uiState.picoPlacaStatus.statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.picoPlacaStatus.isRestrictedNow) MaterialTheme.colorScheme.error else Secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = uiState.picoPlacaStatus.subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
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

    // ── DIÁLOGOS DE CONTROL DE TURNO ─────────────────────────────────────────

    // 1. Diálogo Iniciar Recorrido
    if (showStartShiftDialog) {
        val defaultOd = uiState.activeVehicle?.odometer ?: 0
        var odText by remember { mutableStateOf(if (defaultOd > 0) defaultOd.toString() else "") }
        var selectedType by remember { mutableStateOf(ShiftType.WORK) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showStartShiftDialog = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "🚀 Iniciar Recorrido",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Selecciona el tipo de recorrido e ingresa el odómetro inicial:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )

                // Selector de Tipo: Trabajo vs Personal
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedType == ShiftType.WORK) PrimaryContainer.copy(alpha = 0.3f) else SurfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (selectedType == ShiftType.WORK) Primary else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedType = ShiftType.WORK }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🚕", fontSize = 24.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Trabajo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (selectedType == ShiftType.WORK) Primary else OnSurface)
                            Text("Apps / Ganancias", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedType == ShiftType.PERSONAL) PrimaryContainer.copy(alpha = 0.3f) else SurfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (selectedType == ShiftType.PERSONAL) Primary else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedType = ShiftType.PERSONAL }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🏠", fontSize = 24.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Personal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (selectedType == ShiftType.PERSONAL) Primary else OnSurface)
                            Text("Particular / Viajes", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                    }
                }

                OutlinedTextField(
                    value = odText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) odText = it },
                    label = { Text("Odómetro inicial (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandSeparatorVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showStartShiftDialog = false }) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val od = odText.toIntOrNull() ?: 0
                            if (od > 0) {
                                viewModel.startShift(od, selectedType)
                                showStartShiftDialog = false
                            }
                        },
                        enabled = (odText.toIntOrNull() ?: 0) > 0
                    ) {
                        Text("Iniciar Recorrido")
                    }
                }
            }
        }
    }

    // 2. Diálogo Registrar Ganancia por App
    if (showAddEarningDialog) {
        co.samidev.kilometrix.presentation.components.AddEarningBottomSheet(
            vehicle = uiState.activeVehicle,
            onDismiss = {
                showAddEarningDialog = false
                if (pendingEndShiftAfterEarning) {
                    pendingEndShiftAfterEarning = false
                    showEndShiftDialog = true
                }
            },
            onSave = { appName, appEmoji, amount, isBonus, date ->
                val finalName = if (isBonus) "$appName (Bono)" else appName
                val finalEmoji = if (isBonus) "🎁" else appEmoji
                viewModel.addEarning(finalName, finalEmoji, amount, date)
                showAddEarningDialog = false
                if (pendingEndShiftAfterEarning) {
                    pendingEndShiftAfterEarning = false
                    showEndShiftDialog = true
                }
            }
        )
    }

    // 3. Advertencia cuando no hay ganancias registradas (se muestra de inmediato al dar clic en Finalizar)
    if (showNoEarningsConfirm) {
        AlertDialog(
            onDismissRequest = {
                showNoEarningsConfirm = false
                pendingEndShiftAfterEarning = false
            },
            icon = { Text("⚠️", fontSize = 32.sp) },
            title = {
                Text(
                    text = "Sin Ganancias Registradas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Este es un recorrido de trabajo pero no has registrado ninguna ganancia.\n\n¿Deseas agregar una ganancia o finalizar el recorrido sin ingresos?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showNoEarningsConfirm = false
                            pendingEndShiftAfterEarning = true
                            showAddEarningDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("➕ Agregar Ganancia", fontWeight = FontWeight.Bold, color = OnPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            showNoEarningsConfirm = false
                            pendingEndShiftAfterEarning = false
                            showEndShiftDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text("Finalizar Sin Ganancias", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }

                    TextButton(
                        onClick = {
                            showNoEarningsConfirm = false
                            pendingEndShiftAfterEarning = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Volver", color = OnSurfaceVariant)
                    }
                }
            },
            dismissButton = null,
            containerColor = Color(0xFF131B2E),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 4. Diálogo Finalizar Turno (Odómetro final obligatorio)
    if (showEndShiftDialog) {
        val shift = uiState.activeShift
        val initOd = shift?.initialOdometer ?: 0
        var finalOdText by remember { mutableStateOf("") }
        val finalOd = finalOdText.toIntOrNull() ?: 0
        val diffKm = finalOd - initOd
        val isExaggerated = diffKm > 1000 || finalOd > 1_500_000
        val isOdValid = finalOd >= initOd && !isExaggerated
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showEndShiftDialog = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "🏁 Finalizar Recorrido",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Para cerrar tu recorrido, ingresa el odómetro final de tu vehículo:",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = finalOdText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) finalOdText = it },
                    label = { Text("Odómetro final (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandSeparatorVisualTransformation(),
                    isError = finalOdText.isNotBlank() && !isOdValid,
                    supportingText = {
                        if (finalOdText.isNotBlank() && finalOd < initOd) {
                            Text("Debe ser mayor o igual al inicial (${currencyFmt.format(initOd)} km)", color = MaterialTheme.colorScheme.error)
                        } else if (finalOdText.isNotBlank() && isExaggerated) {
                            Text("⚠️ Son +${currencyFmt.format(diffKm)} km recorridos. Por favor verifica si escribiste un número de más.", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Odómetro inicial: ${currencyFmt.format(initOd)} km")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isOdValid && finalOd > 0) {
                    val realKm = finalOd - initOd
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SecondaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "RESUMEN DEL RECORRIDO",
                                style = MaterialTheme.typography.labelSmall,
                                color = Secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "· Distancia real: $realKm km",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "· Tiempo activo: ${formatMs(uiState.shiftElapsedMs)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (shift?.type == ShiftType.WORK) {
                                Text(
                                    text = "· Ganancia neta: $ ${currencyFmt.format(uiState.shiftTotalEarnings.toLong())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "· Tipo: 🏠 Personal / Particular",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Primary
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showEndShiftDialog = false }) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isOdValid && finalOd > 0) {
                                viewModel.endShift(finalOd)
                                showEndShiftDialog = false
                            }
                        },
                        enabled = isOdValid && finalOd > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Finalizar Recorrido")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant
        )
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
private fun HomeFuelEfficiencyCard(
    summary: FuelEfficiencySummary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PrimaryContainer.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "⚡ RENDIMIENTO DE COMBUSTIBLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Ver en Gastos",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Ir a Gastos",
                        tint = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
