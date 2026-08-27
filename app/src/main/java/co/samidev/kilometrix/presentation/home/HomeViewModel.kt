package co.samidev.kilometrix.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.PicoPlacaResponse
import co.samidev.kilometrix.domain.model.PicoPlacaStatus
import co.samidev.kilometrix.domain.model.FuelEfficiencySummary
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
import co.samidev.kilometrix.domain.usecase.CalculatePicoYPlacaUseCase
import co.samidev.kilometrix.domain.usecase.RecalculateFuelChainUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

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
    val errorMessage: String? = null
)

private data class HomeCoreData(
    val profile: UserProfile?,
    val vehicle: Vehicle?,
    val picoResource: Resource<PicoPlacaResponse>,
    val shift: WorkShift?,
    val fuelExpenses: List<VehicleExpense>,
    val allExpenses: List<VehicleExpense>
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
    private val recalculateFuelChainUseCase: RecalculateFuelChainUseCase
) : ViewModel() {

    private val actionLoading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    // Ticker que emite el timestamp actual cada segundo
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

    private val vehicleExpensesData: Flow<Pair<List<VehicleExpense>, List<VehicleExpense>>> = activeVehicle.flatMapLatest { vehicle ->
        if (vehicle != null) {
            combine(
                expenseRepository.getFuelHistory(vehicle.id),
                expenseRepository.getExpensesRealtime(vehicle.id)
            ) { fuel, all -> Pair(recalculateFuelChainUseCase(fuel), recalculateFuelChainUseCase(all)) }
        } else flowOf(Pair(emptyList(), emptyList()))
    }

    private val coreDataFlow: Flow<HomeCoreData> = combine(
        userRepository.getUserProfile(),
        activeVehicle,
        picoYPlacaRepository.getPicoYPlacaData(),
        activeShift,
        vehicleExpensesData
    ) { profile, vehicle, picoResource, shift, expensesPair ->
        HomeCoreData(profile, vehicle, picoResource, shift, expensesPair.first, expensesPair.second)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        coreDataFlow,
        timeTicker,
        actionLoading,
        errorMessage
    ) { core, now, isLoading, errorMsg ->
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

        // ── Cálculos del turno activo ──────────────────────────────────────────
        var elapsedMs = 0L
        var shiftEarningsTotal = 0.0
        var shiftExpensesTotal = 0.0
        var estKm = 0.0
        var estGallons = 0.0
        var estCost = 0.0

        val globalFuelSummary = calculateFuelEfficiencyUseCase(fuelExpenses)
        val rProm = globalFuelSummary.kmPerGallonAverage ?: globalFuelSummary.averageKmPerGallon.takeIf { it > 0 } ?: 35.0
        val lastRefuelPrice = fuelExpenses.firstOrNull()?.fuelDetails?.pricePerGallon
            ?: (if (globalFuelSummary.totalGallonsPurchased > 0) globalFuelSummary.totalSpentCash / globalFuelSummary.totalGallonsPurchased else 15529.0)

        if (shift != null) {
            shiftEarningsTotal = shift.earnings.sumOf { it.amount }
            val nonFuelExpenses = core.allExpenses
                .filter { it.date >= shift.startTime && it.type != ExpenseType.FUEL }
                .sumOf { it.amount }

            // Calcular tiempo activo efectivo
            elapsedMs = when (shift.status) {
                ShiftStatus.ACTIVE -> {
                    maxOf(0L, now - shift.startTime - shift.pausedDurationMs)
                }
                ShiftStatus.PAUSED -> {
                    val pauseStart = shift.pauseStartTime ?: now
                    maxOf(0L, pauseStart - shift.startTime - shift.pausedDurationMs)
                }
                ShiftStatus.ENDED -> 0L
            }

            // Estimación de km basada en velocidad media urbana (30 km/h)
            val elapsedHours = elapsedMs / (1000.0 * 3600.0)
            estKm = elapsedHours * 30.0

            if (rProm > 0) {
                estGallons = estKm / rProm
                estCost = estGallons * lastRefuelPrice
            }

            shiftExpensesTotal = nonFuelExpenses + estCost
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
            fuelEfficiencySummary = globalFuelSummary.takeIf { globalFuelSummary.fillUpsCount > 0 || globalFuelSummary.totalGallonsPurchased > 0 },
            actionLoading = isLoading,
            errorMessage = errorMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    // ── Funciones de interacción del turno ────────────────────────────────────

    fun startShift(initialOdometer: Int, type: co.samidev.kilometrix.domain.model.ShiftType = co.samidev.kilometrix.domain.model.ShiftType.WORK) {
        val vehicle = uiState.value.activeVehicle ?: return
        viewModelScope.launch {
            actionLoading.value = true
            errorMessage.value = null
            val result = workShiftRepository.startShift(vehicle.id, initialOdometer, type)
            actionLoading.value = false
            if (result.isFailure) {
                errorMessage.value = result.exceptionOrNull()?.message ?: "Error al iniciar recorrido"
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
        viewModelScope.launch {
            actionLoading.value = true
            errorMessage.value = null
            val result = workShiftRepository.endShift(shift.id, finalOdometer)
            actionLoading.value = false
            if (result.isFailure) {
                errorMessage.value = result.exceptionOrNull()?.message ?: "Error al finalizar recorrido"
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }
}
