package co.samidev.kilometrix.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import co.samidev.kilometrix.domain.model.ShiftStatus
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
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showStartShiftDialog by remember { mutableStateOf(false) }
    var showEndShiftDialog by remember { mutableStateOf(false) }
    var showAddEarningDialog by remember { mutableStateOf(false) }

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
                            color = SecondaryContainer
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

        // ── Earnings card (card 2) ────────────────────────────────────────
        val (c2Alpha, c2Ty) = staggeredEntrance(2)
        val shiftActive = uiState.activeShift != null
        val displayEarnings = if (shiftActive) uiState.shiftTotalEarnings else 0.0

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
                    text = if (shiftActive) "GANANCIA DEL TURNO ACTIVO" else stringResource(R.string.home_earnings_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$ ${currencyFmt.format(displayEarnings.toLong())}",
                    style = MaterialTheme.typography.displayLarge,
                    color = OnSurface
                )
                Text(
                    text = if (shiftActive) "${uiState.activeShift?.earnings?.size ?: 0} ingresos registrados en este turno"
                           else stringResource(R.string.home_earnings_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
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
                        text = "¿Empezar tu turno de hoy?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnSurface
                    )
                    Text(
                        text = "Registra tu odómetro inicial para medir tiempo activo, ganancias por app y costo de combustible.",
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
                            text = "Iniciar Turno",
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
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    if (isPaused) Color(0xFF2D1B00) else Color(0xFF0D2818),
                                    SurfaceContainerLow
                                )
                            )
                        )
                        .border(
                            1.5.dp,
                            if (isPaused) Color(0xFFFF9800).copy(alpha = 0.6f) else Secondary.copy(alpha = 0.6f),
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
                            Text("🚗", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "TURNO EN CURSO",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isPaused) Color(0xFFFF9800).copy(alpha = 0.2f) else Color(0xFF4CAF50).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isPaused) Color(0xFFFF9800) else Color(0xFF4CAF50)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PulsingDot(color = if (isPaused) Color(0xFFFF9800) else Color(0xFF4CAF50))
                                Text(
                                    text = if (isPaused) "EN PAUSA" else "LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isPaused) Color(0xFFFF9800) else Color(0xFF4CAF50)
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
                            color = if (isPaused) Color(0xFFFFB74D) else Secondary
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

                    // Ganancias por App
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
                                text = "No has registrado ingresos en este turno.",
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
                            onClick = { showEndShiftDialog = true },
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

    // 1. Diálogo Iniciar Turno
    if (showStartShiftDialog) {
        val defaultOd = uiState.activeVehicle?.odometer ?: 0
        var odText by remember { mutableStateOf(if (defaultOd > 0) defaultOd.toString() else "") }
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
                    text = "🚀 Iniciar Turno de Conducción",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ingresa el odómetro actual con el que comienzas tu jornada de trabajo:",
                    style = MaterialTheme.typography.bodyMedium
                )
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
                                viewModel.startShift(od)
                                showStartShiftDialog = false
                            }
                        },
                        enabled = (odText.toIntOrNull() ?: 0) > 0
                    ) {
                        Text("Iniciar")
                    }
                }
            }
        }
    }

    // 2. Diálogo Registrar Ganancia por App
    if (showAddEarningDialog) {
        var selectedApp by remember { mutableStateOf<TransportApp?>(TRANSPORT_APPS.first()) }
        var amountText by remember { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showAddEarningDialog = false },
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
                    text = "💰 Registrar Ganancia",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Selecciona la aplicación o medio de pago:",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )

                // Grid de Apps de Transporte
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TRANSPORT_APPS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { app ->
                                val isSelected = selectedApp?.name == app.name
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) PrimaryContainer.copy(alpha = 0.3f) else SurfaceContainerHigh,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) Primary else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedApp = app }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (app.drawableRes != null) {
                                            Image(
                                                painter = painterResource(id = app.drawableRes),
                                                contentDescription = app.name,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            Text(app.emoji, fontSize = 20.sp)
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = app.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Primary else OnSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monto de la carrera / ganancia") },
                    prefix = { Text("$ ") },
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
                    TextButton(onClick = { showAddEarningDialog = false }) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = amountText.replace(Regex("[^\\d]"), "").toDoubleOrNull() ?: 0.0
                            val app = selectedApp
                            if (amount > 0.0 && app != null) {
                                viewModel.addEarning(app.name, app.emoji, amount)
                                showAddEarningDialog = false
                            }
                        },
                        enabled = (amountText.replace(Regex("[^\\d]"), "").toDoubleOrNull() ?: 0.0) > 0.0
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }

    // 3. Diálogo Finalizar Turno (Odómetro final obligatorio)
    if (showEndShiftDialog) {
        val shift = uiState.activeShift
        val initOd = shift?.initialOdometer ?: 0
        var finalOdText by remember { mutableStateOf("") }
        val finalOd = finalOdText.toIntOrNull() ?: 0
        val isOdValid = finalOd >= initOd
        var showNoEarningsConfirm by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        if (showNoEarningsConfirm) {
            AlertDialog(
                onDismissRequest = { showNoEarningsConfirm = false },
                title = { Text("⚠️ Sin Ganancias") },
                text = { Text("No has registrado ninguna ganancia en este turno.\n\n¿Estás seguro que deseas finalizarlo de todos modos?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.endShift(finalOd)
                            showNoEarningsConfirm = false
                            showEndShiftDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Cerrar sin ganancias") }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showNoEarningsConfirm = false 
                    }) { Text("Atrás") }
                }
            )
        } else {
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
                        text = "🏁 Finalizar Turno",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Para cerrar tu turno de trabajo, ingresa el odómetro final de tu vehículo:",
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
                            if (finalOdText.isNotBlank() && !isOdValid) {
                                Text("Debe ser mayor o igual al inicial (${currencyFmt.format(initOd)} km)")
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
                                    text = "RESUMEN DEL TURNO",
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
                                Text(
                                    text = "· Ganancia neta: $ ${currencyFmt.format(uiState.shiftTotalEarnings.toLong())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
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
                                    if (shift?.earnings.isNullOrEmpty()) {
                                        showNoEarningsConfirm = true
                                    } else {
                                        viewModel.endShift(finalOd)
                                        showEndShiftDialog = false
                                    }
                                }
                            },
                            enabled = isOdValid && finalOd > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cerrar Turno")
                        }
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
