package co.samidev.kilometrix.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.domain.model.FuelEfficiencySummary
import co.samidev.kilometrix.domain.model.FuelUnit
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.repository.ActiveVehicleRepository
import co.samidev.kilometrix.domain.usecase.AddExpenseUseCase
import co.samidev.kilometrix.domain.usecase.CalculateFuelEfficiencyUseCase
import co.samidev.kilometrix.domain.usecase.GetExpensesUseCase
import co.samidev.kilometrix.domain.usecase.GetVehiclesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsUiState(
    val expenses: List<VehicleExpense> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicle: Vehicle? = null,
    val fuelSummary: FuelEfficiencySummary? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val actionSuccess: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    getVehiclesUseCase: GetVehiclesUseCase,
    private val activeVehicleRepository: ActiveVehicleRepository,
    private val getExpensesUseCase: GetExpensesUseCase,
    private val addExpenseUseCase: AddExpenseUseCase,
    private val calculateFuelEfficiencyUseCase: CalculateFuelEfficiencyUseCase
) : ViewModel() {

    // ── Vehicles & selection ───────────────────────────────────────────────────

    val vehicles: StateFlow<List<Vehicle>> = getVehiclesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Vehículo activo seleccionado por el usuario. */
    val selectedVehicle: StateFlow<Vehicle?> = combine(vehicles, activeVehicleRepository.activeVehicleId) { list, id ->
        if (id != null) list.firstOrNull { it.id == id } else list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Expenses for selected vehicle ──────────────────────────────────────────

    val expenses: StateFlow<List<VehicleExpense>> = selectedVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) flowOf(emptyList())
            else getExpensesUseCase(vehicle.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Historial completo de combustible (sin filtro de kmTraveled — el UseCase multifase
    // procesa todos los registros incluyendo parciales y reservas).
    val fuelHistory: StateFlow<List<VehicleExpense>> = selectedVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) flowOf(emptyList())
            else getExpensesUseCase.fuelHistory(vehicle.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fuelSummary: StateFlow<FuelEfficiencySummary?> = fuelHistory.map { history ->
        if (history.isEmpty()) null
        else calculateFuelEfficiencyUseCase(history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Action state ───────────────────────────────────────────────────────────

    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState

    // ── Public actions ─────────────────────────────────────────────────────────

    fun selectVehicle(vehicleId: String) {
        activeVehicleRepository.setActiveVehicleId(vehicleId)
    }

    fun addExpense(expense: VehicleExpense) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            val result = addExpenseUseCase(expense)
            _actionState.value = if (result.isSuccess) ActionState.Success
            else ActionState.Error(result.exceptionOrNull()?.message ?: "Error al guardar gasto")
        }
    }

    fun resetActionState() {
        _actionState.value = ActionState.Idle
    }

    /**
     * Construye un [FuelDetails] completo para el UseCase Multifase.
     *
     * @param isReserve El testigo de reserva estaba encendido → hito A de calibración.
     * @param isFullTank Se realizó un llenado completo → hito B de calibración.
     * @param isPartial Tanqueo parcial (default cuando ninguna opción especial aplica).
     */
    fun buildFuelDetails(
        enteredQuantity: Double,
        enteredUnit: FuelUnit,
        pricePerUnit: Double,
        odometerAtRefuel: Int,
        previousOdometer: Int,
        isReserve: Boolean = false,
        isFullTank: Boolean = false,
        isPartial: Boolean = false
    ): co.samidev.kilometrix.domain.model.FuelDetails {
        val litersPerGallon = 3.78541
        val gallons = when (enteredUnit) {
            FuelUnit.GALLON -> enteredQuantity
            FuelUnit.LITER -> enteredQuantity / litersPerGallon
        }
        val liters = when (enteredUnit) {
            FuelUnit.LITER -> enteredQuantity
            FuelUnit.GALLON -> enteredQuantity * litersPerGallon
        }
        val pricePerGallon = when (enteredUnit) {
            FuelUnit.GALLON -> pricePerUnit
            FuelUnit.LITER -> pricePerUnit * litersPerGallon
        }
        val pricePerLiter = when (enteredUnit) {
            FuelUnit.LITER -> pricePerUnit
            FuelUnit.GALLON -> pricePerUnit / litersPerGallon
        }
        val kmTraveled = maxOf(0, odometerAtRefuel - previousOdometer)
        val kmPerGallon = if (gallons > 0.0 && kmTraveled > 0) kmTraveled / gallons else 0.0
        val kmPerLiter = if (liters > 0.0 && kmTraveled > 0) kmTraveled / liters else 0.0

        // Garantizar coherencia: solo uno de los tres flags puede ser true
        val resolvedPartial = !isReserve && !isFullTank

        return co.samidev.kilometrix.domain.model.FuelDetails(
            gallons = gallons,
            liters = liters,
            pricePerGallon = pricePerGallon,
            pricePerLiter = pricePerLiter,
            enteredUnit = enteredUnit,
            enteredQuantity = enteredQuantity,
            pricePerEnteredUnit = pricePerUnit,
            odometerAtRefuel = odometerAtRefuel,
            previousOdometer = previousOdometer,
            kmTraveled = kmTraveled,
            kmPerGallon = kmPerGallon,
            kmPerLiter = kmPerLiter,
            // 🆕 Campos Multifase
            isReserve = isReserve,
            isFullTank = isFullTank,
            isPartial = resolvedPartial
        )
    }
}

sealed interface ActionState {
    object Idle : ActionState
    object Loading : ActionState
    object Success : ActionState
    data class Error(val message: String) : ActionState
}
