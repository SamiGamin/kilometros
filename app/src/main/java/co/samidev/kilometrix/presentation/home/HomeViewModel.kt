package co.samidev.kilometrix.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelEfficiencySummary
import co.samidev.kilometrix.domain.model.PicoPlacaResponse
import co.samidev.kilometrix.domain.model.PicoPlacaStatus
import co.samidev.kilometrix.domain.model.ShiftEarning
import co.samidev.kilometrix.domain.model.ShiftStatus
import co.samidev.kilometrix.domain.model.UserProfile
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.model.WorkShift
import co.samidev.kilometrix.domain.repository.ActiveVehicleRepository
import co.samidev.kilometrix.domain.repository.ExpenseRepository
import co.samidev.kilometrix.domain.repository.PicoYPlacaRepository
import co.samidev.kilometrix.domain.repository.UserRepository
import co.samidev.kilometrix.domain.repository.VehicleRepository
import co.samidev.kilometrix.domain.repository.WorkShiftRepository
import co.samidev.kilometrix.domain.usecase.CalculateFuelEfficiencyUseCase
import co.samidev.kilometrix.domain.usecase.CalculateObdCalibrationUseCase
import co.samidev.kilometrix.domain.usecase.CalculatePicoYPlacaUseCase
import co.samidev.kilometrix.domain.usecase.ObdCalibrationFactors
import co.samidev.kilometrix.domain.usecase.RecalculateFuelChainUseCase
import co.samidev.kilometrix.domain.util.FuelUnits
import co.samidev.kilometrix.obd.data.ObdRepository
import co.samidev.kilometrix.obd.domain.ConnectionState
import co.samidev.kilometrix.obd.domain.EcuDiagnosticInfo
import co.samidev.kilometrix.obd.domain.ObdDevice
import co.samidev.kilometrix.obd.domain.ObdFaultType
import co.samidev.kilometrix.obd.domain.ShiftTelemetryState
import co.samidev.kilometrix.obd.service.ObdServiceController
import co.samidev.kilometrix.obd.service.ObdSessionSnapshot
import co.samidev.kilometrix.obd.service.ObdSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class HomeUiState(
    val userName: String = "",
    val currentDateText: String = "",
    val activeVehicle: Vehicle? = null,
    val userPlatforms: List<String> = emptyList(),
    val picoPlacaStatus: PicoPlacaStatus = PicoPlacaStatus("Cargando...", "Verificando restricciones", false, false),
    val activeShift: WorkShift? = null,
    val shiftElapsedMs: Long = 0L,
    val shiftTotalEarnings: Double = 0.0,
    val shiftTotalExpenses: Double = 0.0,
    val shiftNetProfit: Double = 0.0,
    val estimatedKmTraveled: Double = 0.0,
    val estimatedGallonsConsumed: Double = 0.0,
    val estimatedCostConsumed: Double = 0.0,
    val fuelEfficiencySummary: FuelEfficiencySummary? = null,
    val actionLoading: Boolean = false,
    val errorMessage: String? = null,
    // ── Telemetría y Estado OBD-II ────────────────────────────────────────────
    val obdConnectionState: ConnectionState = ConnectionState.Disconnected,
    val obdDiagnosticInfo: EcuDiagnosticInfo = EcuDiagnosticInfo(),
    val isObdConnected: Boolean = false,
    val obdLiveRpm: Int? = null,
    val obdLiveSpeedKph: Int? = null,
    val obdInstantLitersPerHour: Double? = null,
    val obdLiveFuelLevelPct: Double? = null,
    val obdCoveragePct: Double = 100.0,
    val obdIsFuelCutOffActive: Boolean = false,
    val obdIsIdleActive: Boolean = false,
    val obdFaultType: ObdFaultType = ObdFaultType.NONE,
    val shiftTelemetryState: ShiftTelemetryState = ShiftTelemetryState.AWAITING_CONNECTION,
    val obdDisconnectionAlert: Boolean = false,
    val obdAlertMessage: String? = null,
    val obdCalibrationFactors: ObdCalibrationFactors = ObdCalibrationFactors(),
    val fuelSourceLabel: String = "⏳ Calibrando 1er ciclo",
    val suggestedFinalOdometer: Int = 0
)

private data class HomeCoreData(
    val profile: UserProfile?,
    val vehicle: Vehicle?,
    val picoResource: Resource<PicoPlacaResponse>,
    val shift: WorkShift?,
    val fuelExpenses: List<VehicleExpense>,
    val allExpenses: List<VehicleExpense>,
    val allShifts: List<WorkShift>
)

private data class ObdLiveBundle(
    val connectionState: ConnectionState,
    val diagnosticInfo: EcuDiagnosticInfo,
    val sessionSnapshot: ObdSessionSnapshot,
    val calibrationFactors: ObdCalibrationFactors
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val vehicleRepository: VehicleRepository,
    private val activeVehicleRepository: ActiveVehicleRepository,
    private val picoYPlacaRepository: PicoYPlacaRepository,
    private val calculatePicoYPlacaUseCase: CalculatePicoYPlacaUseCase,
    private val workShiftRepository: WorkShiftRepository,
    private val expenseRepository: ExpenseRepository,
    private val calculateFuelEfficiencyUseCase: CalculateFuelEfficiencyUseCase,
    private val calculateObdCalibrationUseCase: CalculateObdCalibrationUseCase,
    private val recalculateFuelChainUseCase: RecalculateFuelChainUseCase,
    private val obdRepository: ObdRepository,
    private val obdSessionState: ObdSessionState,
    private val obdServiceController: ObdServiceController
) : ViewModel() {

    private val actionLoading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val _scannedDevices = MutableStateFlow<List<ObdDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ObdDevice>> = _scannedDevices.asStateFlow()

    private val _isScanningObd = MutableStateFlow(false)
    val isScanningObd: StateFlow<Boolean> = _isScanningObd.asStateFlow()

    private var scanJob: Job? = null

    private val timeTicker = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(1000)
        }
    }

    private val activeVehicle: Flow<Vehicle?> = combine(
        vehicleRepository.getVehiclesRealtime(),
        activeVehicleRepository.activeVehicleId
    ) { vehicles, activeId ->
        if (activeId != null) vehicles.find { it.id == activeId } ?: vehicles.firstOrNull()
        else vehicles.firstOrNull()
    }

    private val activeShift: Flow<WorkShift?> = activeVehicle.flatMapLatest { vehicle ->
        if (vehicle != null) workShiftRepository.getActiveShift(vehicle.id)
        else flowOf(null)
    }

    private val vehicleDataTriple: Flow<Triple<List<VehicleExpense>, List<VehicleExpense>, List<WorkShift>>> =
        activeVehicle.flatMapLatest { vehicle ->
            if (vehicle != null) {
                combine(
                    expenseRepository.getFuelHistory(vehicle.id),
                    expenseRepository.getExpensesRealtime(vehicle.id),
                    workShiftRepository.getShiftsForVehicle(vehicle.id)
                ) { fuel, all, shifts ->
                    Triple(recalculateFuelChainUseCase(fuel), recalculateFuelChainUseCase(all), shifts)
                }
            } else flowOf(Triple(emptyList(), emptyList(), emptyList()))
        }

    private val calibrationFlow: Flow<ObdCalibrationFactors> = vehicleDataTriple
        .map { (fuel, _, shifts) -> calculateObdCalibrationUseCase(fuel, shifts) }
        .distinctUntilChanged()

    private val fuelSummaryFlow: Flow<FuelEfficiencySummary?> = combine(
        vehicleDataTriple,
        activeVehicle
    ) { (fuel, _, _), vehicle ->
        val summary = calculateFuelEfficiencyUseCase(
            fuelExpenses = fuel,
            currentVehicleOdometer = vehicle?.odometer ?: 0
        )
        summary.takeIf { it.fillUpsCount > 0 || it.totalGallonsPurchased > 0 }
    }.distinctUntilChanged()

    private val coreDataFlow: Flow<HomeCoreData> = combine(
        userRepository.getUserProfile(),
        activeVehicle,
        picoYPlacaRepository.getPicoYPlacaData(),
        activeShift,
        vehicleDataTriple
    ) { profile, vehicle, picoResource, shift, triple ->
        HomeCoreData(profile, vehicle, picoResource, shift, triple.first, triple.second, triple.third)
    }

    private val obdLiveFlow: Flow<ObdLiveBundle> = combine(
        obdRepository.connectionState,
        obdRepository.diagnosticInfo,
        obdSessionState.snapshot,
        calibrationFlow
    ) { conn, diag, session, cal ->
        ObdLiveBundle(conn, diag, session, cal)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        coreDataFlow,
        obdLiveFlow,
        fuelSummaryFlow,
        timeTicker,
        combine(actionLoading, errorMessage, ::Pair)
    ) { core, obd, globalFuelSummary, now, (isLoading, errorMsg) ->
        val profile = core.profile
        val vehicle = core.vehicle
        val picoResource = core.picoResource
        val shift = core.shift
        val fuelExpenses = core.fuelExpenses

        val userName = profile?.name?.substringBefore(" ") ?: "Conductor"

        val calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Bogota"))
        val dayName = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.forLanguageTag("es-CO"))
            ?.replaceFirstChar { it.uppercase() } ?: ""
        val dayNumber = calendar.get(Calendar.DAY_OF_MONTH)
        val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.forLanguageTag("es-CO"))
        val dateText = "$dayName, $dayNumber de $monthName"

        val status = calculatePicoYPlacaUseCase(profile?.city, vehicle, picoResource)

        val obdCalibration = obd.calibrationFactors.let { cal ->
            if (cal.isFuelCalibrated) cal
            else cal.copy(
                kFuel = vehicle?.obdFuelFactorEwma ?: 1.0,
                obdFuelFactorStdev = vehicle?.obdFuelFactorStdev ?: 0.0,
                kDist = vehicle?.obdDistFactorMedian ?: 1.0
            )
        }

        val isObdConnected = obd.connectionState is ConnectionState.Connected && obd.diagnosticInfo.ecuVerified
        val rPromCalibrated = globalFuelSummary?.kmPerGallonAverage

        val lastRefuelPricePerGal = fuelExpenses.maxByOrNull { it.date }
            ?.fuelDetails?.pricePerGallon?.takeIf { it > 0.0 }
            ?: (if (globalFuelSummary != null && globalFuelSummary.totalGallonsPurchased > 0)
                globalFuelSummary.totalSpentCash / globalFuelSummary.totalGallonsPurchased else 0.0)
        // Precio canónico en COP/L para las prioridades OBD-II (Punto 5)
        val lastRefuelPricePerLiter = FuelUnits.pricePerGallonToPricePerLiter(lastRefuelPricePerGal)

        // ── Cálculos del turno activo (Jerarquía de 4 Niveles sin números inventados) ──
        var elapsedMs = 0L
        var shiftEarningsTotal = 0.0
        var shiftExpensesTotal = 0.0
        var estKm = 0.0
        var estGallons = 0.0
        var estCost = 0.0

        val obdMetrics = obd.sessionSnapshot.metrics
        val hasLiveObdMetrics = obdMetrics.distanceKilometers > 0.0 ||
            obdMetrics.fuelConsumedLiters > 0.0 ||
            isObdConnected
        val hasPumpCycleCalibration = rPromCalibrated != null && rPromCalibrated > 0.0
        var fuelSourceLabel = obdCalibration.formatFuelSourceBadge(
            hasLiveObdFuel = hasLiveObdMetrics,
            hasPumpCycleCalibration = hasPumpCycleCalibration,
            isIatDegraded = obd.diagnosticInfo.isIatDegraded
        )

        if (shift != null) {
            shiftEarningsTotal = shift.earnings.sumOf { it.amount }
            val nonFuelExpenses = core.allExpenses
                .filter { it.date >= shift.startTime && it.type != ExpenseType.FUEL }
                .sumOf { it.amount }

            elapsedMs = when (shift.status) {
                ShiftStatus.ACTIVE -> maxOf(0L, now - shift.startTime - shift.pausedDurationMs)
                ShiftStatus.PAUSED -> {
                    val pauseStart = shift.pauseStartTime ?: now
                    maxOf(0L, pauseStart - shift.startTime - shift.pausedDurationMs)
                }
                ShiftStatus.ENDED -> 0L
            }

            if (hasLiveObdMetrics) {
                // Nivel 1 y 2: Medición directa por OBD-II en Litros canónicos (aplicando k_dist y k_fuel EWMA)
                estKm = obdMetrics.distanceKilometers * obdCalibration.kDist
                val calibratedLiters = obdMetrics.fuelConsumedLiters * obdCalibration.kFuel
                estGallons = FuelUnits.litersToGallons(calibratedLiters)
                if (lastRefuelPricePerLiter > 0.0) {
                    estCost = calibratedLiters * lastRefuelPricePerLiter
                }
            }

            shiftExpensesTotal = nonFuelExpenses + estCost
        }

        // Distinción entre BLE caído (adaptador) vs ECU muda (NO DATA) (Punto 2)
        val faultType = when {
            shift == null -> ObdFaultType.NONE
            obd.sessionSnapshot.faultType != ObdFaultType.NONE -> obd.sessionSnapshot.faultType
            !isObdConnected && obd.connectionState is ConnectionState.Connected -> ObdFaultType.ECU_SILENT
            !isObdConnected -> ObdFaultType.BLE_DISCONNECTED
            else -> ObdFaultType.NONE
        }
        val disconnectionAlert = shift != null && (!isObdConnected || obd.sessionSnapshot.disconnectionAlert)
        val alertMsg = when {
            shift != null && !obd.sessionSnapshot.alertMessage.isNullOrBlank() -> obd.sessionSnapshot.alertMessage
            shift != null && faultType == ObdFaultType.ECU_SILENT ->
                "🚗 Adaptador BLE conectado, pero la ECU está muda (NO DATA). Verifica que el contacto esté encendido."
            shift != null && !isObdConnected ->
                "🔌 Adaptador BLE desconectado. Reconéctalo al puerto OBD-II para continuar midiendo el turno."
            else -> null
        }

        val suggestedFinalOd = if (shift != null && estKm > 0.0) {
            shift.initialOdometer + estKm.roundToInt()
        } else {
            shift?.initialOdometer ?: 0
        }

        HomeUiState(
            userName = userName,
            currentDateText = dateText,
            activeVehicle = vehicle,
            userPlatforms = profile?.platforms ?: emptyList(),
            picoPlacaStatus = status,
            activeShift = shift,
            shiftElapsedMs = elapsedMs,
            shiftTotalEarnings = shiftEarningsTotal,
            shiftTotalExpenses = shiftExpensesTotal,
            shiftNetProfit = shiftEarningsTotal - shiftExpensesTotal,
            estimatedKmTraveled = estKm,
            estimatedGallonsConsumed = estGallons,
            estimatedCostConsumed = estCost,
            fuelEfficiencySummary = globalFuelSummary,
            actionLoading = isLoading,
            errorMessage = errorMsg,
            obdConnectionState = obd.connectionState,
            obdDiagnosticInfo = obd.diagnosticInfo,
            isObdConnected = isObdConnected,
            obdLiveRpm = obd.diagnosticInfo.liveRpm,
            obdLiveSpeedKph = obd.diagnosticInfo.liveSpeedKph,
            obdInstantLitersPerHour = obdMetrics.instantaneousFuelFlowLitersPerHour,
            obdLiveFuelLevelPct = obd.diagnosticInfo.liveFuelLevelPct,
            obdCoveragePct = obdMetrics.coveragePct,
            obdIsFuelCutOffActive = obdMetrics.isFuelCutOffActive,
            obdIsIdleActive = obdMetrics.isIdleActive,
            obdFaultType = faultType,
            shiftTelemetryState = obd.sessionSnapshot.telemetryState,
            obdDisconnectionAlert = disconnectionAlert,
            obdAlertMessage = alertMsg,
            obdCalibrationFactors = obdCalibration,
            fuelSourceLabel = fuelSourceLabel,
            suggestedFinalOdometer = suggestedFinalOd
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    // ── Funciones de Conexión BLE OBD-II ──────────────────────────────────────

    fun startObdScan() {
        scanJob?.cancel()
        _scannedDevices.value = emptyList()
        _isScanningObd.value = true
        scanJob = viewModelScope.launch {
            try {
                obdRepository.scanDevices().collect { device ->
                    _scannedDevices.update { current ->
                        val filtered = current.filterNot { it.address == device.address }
                        (filtered + device).sortedByDescending { it.rssi }
                    }
                }
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Error al buscar adaptador OBD-II"
            } finally {
                _isScanningObd.value = false
            }
        }
    }

    fun stopObdScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanningObd.value = false
    }

    fun connectObdDevice(device: ObdDevice) {
        stopObdScan()
        viewModelScope.launch {
            val connected = obdRepository.connectAndVerify(device)
            if (connected) {
                val activeShift = uiState.value.activeShift
                if (activeShift != null) {
                    val pricePerLiter = resolvePricePerLiterCop()
                    obdServiceController.startShiftTelemetry(
                        shiftId = activeShift.id,
                        initialOdometerKm = activeShift.initialOdometer,
                        pricePerLiterCop = pricePerLiter
                    )
                } else {
                    obdServiceController.startMonitoring()
                }
            }
        }
    }

    fun disconnectObd() {
        stopObdScan()
        obdRepository.disconnect()
    }

    private fun resolvePricePerLiterCop(): Double {
        val summary = uiState.value.fuelEfficiencySummary
        return if (summary != null && summary.totalLiters > 0.0) {
            summary.totalSpentCash / summary.totalLiters
        } else 0.0
    }

    // ── Funciones de interacción del turno ────────────────────────────────────

    fun startShift(
        initialOdometer: Int,
        type: co.samidev.kilometrix.domain.model.ShiftType = co.samidev.kilometrix.domain.model.ShiftType.WORK
    ) {
        val vehicle = uiState.value.activeVehicle ?: return
        if (!uiState.value.isObdConnected) {
            errorMessage.value = "Debes conectar el adaptador OBD-II a la ECU antes de iniciar el recorrido."
            return
        }
        viewModelScope.launch {
            actionLoading.value = true
            errorMessage.value = null
            val result = workShiftRepository.startShift(vehicle.id, initialOdometer, type)
            actionLoading.value = false
            result.onSuccess { shiftId ->
                obdServiceController.startShiftTelemetry(
                    shiftId = shiftId,
                    initialOdometerKm = initialOdometer,
                    pricePerLiterCop = resolvePricePerLiterCop()
                )
            }.onFailure { err ->
                errorMessage.value = err.message ?: "Error al iniciar recorrido"
            }
        }
    }

    fun togglePauseResumeShift() {
        val shift = uiState.value.activeShift ?: return
        viewModelScope.launch {
            actionLoading.value = true
            errorMessage.value = null
            val result = if (shift.status == ShiftStatus.ACTIVE) {
                workShiftRepository.pauseShift(shift.id)
            } else {
                workShiftRepository.resumeShift(shift.id)
            }
            actionLoading.value = false
            if (result.isFailure) {
                errorMessage.value = result.exceptionOrNull()?.message ?: "Error al cambiar estado del recorrido"
            }
        }
    }

    fun addEarning(appName: String, appEmoji: String, amount: Double, date: Long = System.currentTimeMillis()) {
        val shift = uiState.value.activeShift ?: return
        if (amount <= 0.0) return
        viewModelScope.launch {
            actionLoading.value = true
            errorMessage.value = null
            val earning = ShiftEarning(
                appName = appName,
                appEmoji = appEmoji,
                amount = amount,
                registeredAt = date
            )
            val result = workShiftRepository.addEarning(shift.id, earning)
            actionLoading.value = false
            if (result.isFailure) {
                errorMessage.value = result.exceptionOrNull()?.message ?: "Error al guardar ganancia"
            }
        }
    }

    fun endShift(finalOdometer: Int) {
        val shift = uiState.value.activeShift ?: return
        val obdMetrics = obdSessionState.snapshot.value.metrics
        val isConnectedAtEnd = uiState.value.isObdConnected
        val finalMetricsForEval = obdMetrics.copy(connectedAtEnd = isConnectedAtEnd)
        val calFactors = uiState.value.obdCalibrationFactors

        viewModelScope.launch {
            actionLoading.value = true
            errorMessage.value = null
            val result = workShiftRepository.endShift(
                shiftId = shift.id,
                finalOdometer = finalOdometer,
                obdDistanceKm = obdMetrics.distanceKilometers,
                obdFuelLitersRaw = obdMetrics.fuelConsumedLiters,
                obdCoveragePct = obdMetrics.coveragePct,
                obdMaxGapSec = obdMetrics.maxGapSec,
                obdGapCount = obdMetrics.gapCount,
                obdValid = finalMetricsForEval.isValidForCalibration(),
                obdConnectedAtStart = obdMetrics.connectedAtStart,
                obdConnectedAtEnd = isConnectedAtEnd,
                obdSampleCount = obdMetrics.sampleCount,
                obdMeanDtMs = obdMetrics.meanDtMs,
                obdIdleSec = obdMetrics.idleSec,
                obdCutoffSec = obdMetrics.cutoffSec,
                obdFuelFactorEwma = calFactors.kFuel,
                obdFuelFactorStdev = calFactors.obdFuelFactorStdev,
                obdDistFactorMedian = calFactors.kDist
            )
            actionLoading.value = false
            if (result.isSuccess) {
                obdServiceController.stopShiftTelemetry()
            } else {
                errorMessage.value = result.exceptionOrNull()?.message ?: "Error al finalizar recorrido"
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }
}
