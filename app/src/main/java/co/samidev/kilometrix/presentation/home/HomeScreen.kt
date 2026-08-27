package co.samidev.kilometrix.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import co.samidev.kilometrix.domain.model.ShiftType
import co.samidev.kilometrix.presentation.components.AddEarningBottomSheet
import co.samidev.kilometrix.presentation.home.components.*

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
        HomeHeaderCard(
            userName = uiState.userName,
            currentDateText = uiState.currentDateText,
            modifier = Modifier
                .alpha(h0Alpha)
                .offset(y = h0Ty.dp)
        )

        // ── Main Card (card 1): Fused Active Vehicle + Start Shift (or Active Shift Dashboard) ──
        val (c1Alpha, c1Ty) = staggeredEntrance(1)
        val shift = uiState.activeShift
        if (shift == null) {
            // Estado REPOSO: Tarjeta Unificada de Vehículo Activo + Iniciar Recorrido
            HomeMainVehicleShiftCard(
                vehicle = uiState.activeVehicle,
                onStartShiftClick = { showStartShiftDialog = true },
                modifier = Modifier
                    .alpha(c1Alpha)
                    .offset(y = c1Ty.dp)
            )
        } else {
            // Estado TURNO ACTIVO: Balance y Dashboard de Recorrido
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(c1Alpha)
                    .offset(y = c1Ty.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeActiveShiftBalanceCard(uiState = uiState)
                HomeActiveShiftCard(
                    uiState = uiState,
                    onTogglePauseResume = { viewModel.togglePauseResumeShift() },
                    onOpenAddEarning = { showAddEarningDialog = true },
                    onFinishShift = {
                        if (shift.type == ShiftType.WORK && shift.earnings.isNullOrEmpty()) {
                            showNoEarningsConfirm = true
                        } else {
                            showEndShiftDialog = true
                        }
                    }
                )
            }
        }

        // ── Pico y Placa card (card 2) — Justo debajo de la tarjeta principal ──
        val (c2Alpha, c2Ty) = staggeredEntrance(2)
        HomePicoPlacaCard(
            status = uiState.picoPlacaStatus,
            onClick = onNavigateToPicoPlaca,
            modifier = Modifier
                .alpha(c2Alpha)
                .offset(y = c2Ty.dp)
        )

        // ── Rendimiento de Combustible card (card 3) ────────────────────────
        if (uiState.fuelEfficiencySummary != null) {
            val (cEffAlpha, cEffTy) = staggeredEntrance(3)
            HomeFuelEfficiencyCard(
                summary = uiState.fuelEfficiencySummary!!,
                onClick = onNavigateToGastos,
                modifier = Modifier
                    .alpha(cEffAlpha)
                    .offset(y = cEffTy.dp)
            )
        }

        // ── AI analysis card (card 4) ─────────────────────────────────────
        val (c4Alpha, c4Ty) = staggeredEntrance(4)
        HomeAiAnalysisCard(
            modifier = Modifier
                .alpha(c4Alpha)
                .offset(y = c4Ty.dp)
        )

        Spacer(Modifier.height(80.dp))
    }

    // ── DIÁLOGOS Y BOTTOM SHEETS DE CONTROL DE TURNO ────────────────────────

    // 1. Diálogo Iniciar Recorrido
    if (showStartShiftDialog) {
        StartShiftDialog(
            defaultOdometer = uiState.activeVehicle?.odometer ?: 0,
            onDismiss = { showStartShiftDialog = false },
            onConfirm = { od, type ->
                viewModel.startShift(od, type)
                showStartShiftDialog = false
            }
        )
    }

    // 2. Diálogo Registrar Ganancia por App
    if (showAddEarningDialog) {
        AddEarningBottomSheet(
            vehicle = uiState.activeVehicle,
            userPlatforms = uiState.userPlatforms,
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

    // 3. Advertencia cuando no hay ganancias registradas
    if (showNoEarningsConfirm) {
        NoEarningsConfirmDialog(
            onDismiss = {
                showNoEarningsConfirm = false
                pendingEndShiftAfterEarning = false
            },
            onAddEarning = {
                showNoEarningsConfirm = false
                pendingEndShiftAfterEarning = true
                showAddEarningDialog = true
            },
            onEndWithoutEarnings = {
                showNoEarningsConfirm = false
                pendingEndShiftAfterEarning = false
                showEndShiftDialog = true
            }
        )
    }

    // 4. Diálogo Finalizar Turno (Odómetro final obligatorio)
    if (showEndShiftDialog) {
        EndShiftDialog(
            shift = uiState.activeShift,
            elapsedMs = uiState.shiftElapsedMs,
            totalEarnings = uiState.shiftTotalEarnings,
            onDismiss = { showEndShiftDialog = false },
            onConfirm = { finalOd ->
                viewModel.endShift(finalOd)
                showEndShiftDialog = false
            }
        )
    }
}
