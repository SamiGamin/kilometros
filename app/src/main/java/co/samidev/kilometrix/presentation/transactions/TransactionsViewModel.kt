package co.samidev.kilometrix.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelEfficiencySummary
import co.samidev.kilometrix.domain.model.FuelUnit
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.model.VehicleExpense
import org.json.JSONArray
import co.samidev.kilometrix.domain.model.WorkShift
import co.samidev.kilometrix.domain.repository.ActiveVehicleRepository
import co.samidev.kilometrix.domain.repository.ExpenseRepository
import co.samidev.kilometrix.domain.repository.WorkShiftRepository
import co.samidev.kilometrix.domain.usecase.AddExpenseUseCase
import co.samidev.kilometrix.domain.usecase.CalculateFuelEfficiencyUseCase
import co.samidev.kilometrix.domain.usecase.GetExpensesUseCase
import co.samidev.kilometrix.domain.usecase.GetVehiclesUseCase
import co.samidev.kilometrix.domain.usecase.RecalculateFuelChainUseCase
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
    val hasActiveShift: Boolean = false,
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
    private val expenseRepository: ExpenseRepository,
    private val addExpenseUseCase: AddExpenseUseCase,
    private val calculateFuelEfficiencyUseCase: CalculateFuelEfficiencyUseCase,
    private val workShiftRepository: WorkShiftRepository,
    private val recalculateFuelChainUseCase: RecalculateFuelChainUseCase
) : ViewModel() {

    val hasActiveShift: StateFlow<Boolean> = workShiftRepository.getAnyActiveShift()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    fun setSelectedTab(index: Int) {
        _selectedTabIndex.value = index
    }

    // ── Vehicles & selection ──────────────────────────────────────────────────—

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
            else getExpensesUseCase(vehicle.id).map { list -> recalculateFuelChainUseCase(list) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Shifts for selected vehicle ────────────────────────────────────────────

    val shifts: StateFlow<List<WorkShift>> = selectedVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) flowOf(emptyList())
            else workShiftRepository.getShiftsForVehicle(vehicle.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Historial completo de combustible (sin filtro de kmTraveled — el UseCase multifase
    // procesa todos los registros incluyendo parciales y reservas).
    val fuelHistory: StateFlow<List<VehicleExpense>> = selectedVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) flowOf(emptyList())
            else getExpensesUseCase.fuelHistory(vehicle.id).map { list -> recalculateFuelChainUseCase(list) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fuelSummary: StateFlow<FuelEfficiencySummary?> = fuelHistory.map { history ->
        if (history.isEmpty()) null
        else calculateFuelEfficiencyUseCase(history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Actions ───────────────────────────────────────────────────────────────

    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    fun addExpense(expense: VehicleExpense) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            addExpenseUseCase(expense)
                .onSuccess { _actionState.value = ActionState.Success }
                .onFailure { e -> _actionState.value = ActionState.Error(e.message ?: "Error al guardar el gasto") }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            expenseRepository.deleteExpense(expenseId)
                .onSuccess { _actionState.value = ActionState.Success }
                .onFailure { e -> _actionState.value = ActionState.Error(e.message ?: "Error al eliminar el gasto") }
        }
    }

    fun deleteShift(shiftId: String) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            workShiftRepository.deleteShift(shiftId)
                .onSuccess { _actionState.value = ActionState.Success }
                .onFailure { e -> _actionState.value = ActionState.Error(e.message ?: "Error al eliminar el recorrido") }
        }
    }

    fun deleteEarning(shiftId: String, earningId: String) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            workShiftRepository.deleteEarning(shiftId, earningId)
                .onSuccess { _actionState.value = ActionState.Success }
                .onFailure { e -> _actionState.value = ActionState.Error(e.message ?: "Error al eliminar la ganancia") }
        }
    }

    fun importHistoricalFromAsset(context: android.content.Context) {
        val vehicle = selectedVehicle.value ?: return
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            try {
                val jsonString = context.assets.open("mis_ahorros_procesado_firestore.json")
                    .bufferedReader()
                    .use { it.readText() }

                val array = JSONArray(jsonString)
                val list = mutableListOf<VehicleExpense>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val fuelDetailsObj = obj.optJSONObject("fuelDetails")
                    val gal = fuelDetailsObj?.optDouble("gallons", 0.0) ?: obj.optDouble("gallons", 0.0)
                    val od = fuelDetailsObj?.optInt("odometerAtRefuel", 0) ?: obj.optInt("odometerAtRefuel", 0)
                    val prevOd = fuelDetailsObj?.optInt("previousOdometer", 0) ?: obj.optInt("previousOdometer", 0)
                    val kmTrav = fuelDetailsObj?.optInt("kmTraveled", 0) ?: obj.optInt("kmTraveled", 0)
                    val pricePerGal = fuelDetailsObj?.optDouble("pricePerGallon", 0.0) ?: obj.optDouble("pricePerGallon", 0.0)
                    val kmPerGal = fuelDetailsObj?.optDouble("kmPerGallon", 0.0) ?: obj.optDouble("kmPerGallon", 0.0)
                    val isFull = fuelDetailsObj?.optBoolean("isFullTank", false) ?: obj.optBoolean("isFullTank", false)

                    val fuelDetails = co.samidev.kilometrix.domain.model.FuelDetails(
                        gallons = gal,
                        liters = gal * 3.78541,
                        pricePerGallon = pricePerGal,
                        pricePerLiter = if (pricePerGal > 0) pricePerGal / 3.78541 else 0.0,
                        enteredUnit = FuelUnit.GALLON,
                        enteredQuantity = gal,
                        pricePerEnteredUnit = pricePerGal,
                        odometerAtRefuel = od,
                        previousOdometer = prevOd,
                        kmTraveled = kmTrav,
                        kmPerGallon = kmPerGal,
                        kmPerLiter = if (kmPerGal > 0) kmPerGal / 3.78541 else 0.0,
                        isFullTank = isFull,
                        isPartial = !isFull,
                        isReserve = false
                    )

                    val exp = VehicleExpense(
                        id = obj.optString("id", ""),
                        vehicleId = vehicle.id,
                        type = ExpenseType.FUEL,
                        amount = obj.optDouble("amount", 0.0),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        notes = obj.optString("notes", ""),
                        fuelDetails = fuelDetails
                    )
                    list.add(exp)
                }

                val result = expenseRepository.importExpensesBatch(vehicle.id, list)
                if (result.isSuccess) {
                    _actionState.value = ActionState.Success
                } else {
                    _actionState.value = ActionState.Error(result.exceptionOrNull()?.message ?: "Error al importar datos")
                }
            } catch (e: Exception) {
                _actionState.value = ActionState.Error("Error al importar historial: ${e.message}")
            }
        }
    }

    // ── Public actions ─────────────────────────────────────────────────────────

    fun selectVehicle(vehicleId: String) {
        activeVehicleRepository.setActiveVehicleId(vehicleId)
    }
    
    fun addStandaloneEarning(vehicleId: String?, appName: String, appEmoji: String, amount: Double, isBonus: Boolean, date: Long) {
        if (vehicleId == null) {
            _actionState.value = ActionState.Error("No hay vehículo activo seleccionado")
            return
        }
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            val finalName = if (isBonus) "$appName (Bono)" else appName
            val finalEmoji = if (isBonus) "🎁" else appEmoji
            val earning = co.samidev.kilometrix.domain.model.ShiftEarning(
                appName = finalName,
                appEmoji = finalEmoji,
                amount = amount,
                registeredAt = date
            )
            val result = workShiftRepository.addStandaloneEarning(vehicleId, earning)
            _actionState.value = if (result.isSuccess) ActionState.Success
            else ActionState.Error(result.exceptionOrNull()?.message ?: "Error al guardar ganancia")
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
