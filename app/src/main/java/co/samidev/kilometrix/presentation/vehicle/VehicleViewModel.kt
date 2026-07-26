package co.samidev.kilometrix.presentation.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.PicoPlacaResponse
import co.samidev.kilometrix.domain.model.PicoPlacaStatus
import co.samidev.kilometrix.domain.model.UserProfile
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.repository.ActiveVehicleRepository
import co.samidev.kilometrix.domain.repository.UserRepository
import co.samidev.kilometrix.domain.repository.WorkShiftRepository
import co.samidev.kilometrix.domain.usecase.AddVehicleUseCase
import co.samidev.kilometrix.domain.usecase.CalculatePicoYPlacaUseCase
import co.samidev.kilometrix.domain.usecase.GetPicoYPlacaUseCase
import co.samidev.kilometrix.domain.usecase.GetVehiclesUseCase
import co.samidev.kilometrix.domain.usecase.UpdateVehicleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VehicleUiState {
    object Idle : VehicleUiState
    object Loading : VehicleUiState
    object Success : VehicleUiState
    data class Error(val message: String) : VehicleUiState
}

@HiltViewModel
class VehicleViewModel @Inject constructor(
    getVehiclesUseCase: GetVehiclesUseCase,
    private val addVehicleUseCase: AddVehicleUseCase,
    private val updateVehicleUseCase: UpdateVehicleUseCase,
    private val activeVehicleRepository: ActiveVehicleRepository,
    private val userRepository: UserRepository,
    private val getPicoYPlacaUseCase: GetPicoYPlacaUseCase,
    private val calculatePicoYPlacaUseCase: CalculatePicoYPlacaUseCase,
    private val workShiftRepository: WorkShiftRepository
) : ViewModel() {

    val hasActiveShift: StateFlow<Boolean> = workShiftRepository.getAnyActiveShift()
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val vehicles: StateFlow<List<Vehicle>> = getVehiclesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeVehicleId: StateFlow<String?> = activeVehicleRepository.activeVehicleId

    val activeVehicle: StateFlow<Vehicle?> = combine(vehicles, activeVehicleId) { list, id ->
        if (id != null) list.firstOrNull { it.id == id } else list.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val userProfile: StateFlow<UserProfile?> = userRepository.getUserProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val picoPlacaState: StateFlow<Resource<PicoPlacaResponse>> = getPicoYPlacaUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Resource.Loading
        )

    private val _uiState = MutableStateFlow<VehicleUiState>(VehicleUiState.Idle)
    val uiState: StateFlow<VehicleUiState> = _uiState

    fun setActiveVehicle(vehicleId: String) {
        activeVehicleRepository.setActiveVehicleId(vehicleId)
    }

    fun getPicoPlacaStatus(
        vehicle: Vehicle?,
        userCity: String?,
        picoResource: Resource<PicoPlacaResponse>
    ): PicoPlacaStatus {
        return calculatePicoYPlacaUseCase(userCity, vehicle, picoResource)
    }

    fun addVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            _uiState.value = VehicleUiState.Loading
            val result = addVehicleUseCase(vehicle)
            if (result.isSuccess) {
                _uiState.value = VehicleUiState.Success
                result.getOrNull()?.let { newId ->
                    setActiveVehicle(newId)
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Error al agregar vehículo"
                _uiState.value = VehicleUiState.Error(errorMsg)
            }
        }
    }

    fun seedDemoVehicles() {
        viewModelScope.launch {
            _uiState.value = VehicleUiState.Loading
            val demoVehicles = listOf(
                Vehicle(
                    type = "PARTICULAR",
                    nickname = "Sparky",
                    brand = "Chevrolet",
                    model = "Spark GT",
                    year = 2021,
                    plate = "QIR098",
                    fuel = "GASOLINE",
                    odometer = 45200,
                    soatExpiry = "15/12/2026",
                    soatEnabled = true
                ),
                Vehicle(
                    type = "PARTICULAR",
                    nickname = "La Bestia",
                    brand = "Toyota",
                    model = "Hilux 4x4",
                    year = 2023,
                    plate = "TSU456",
                    fuel = "DIESEL",
                    odometer = 82100,
                    soatExpiry = "20/08/2026",
                    tecnomecExpiry = "20/08/2026",
                    soatEnabled = true,
                    tecnomecEnabled = true
                ),
                Vehicle(
                    type = "PARTICULAR",
                    nickname = "El Económico",
                    brand = "Renault",
                    model = "Logan",
                    year = 2020,
                    plate = "MXN123",
                    fuel = "GNV",
                    odometer = 115000,
                    soatEnabled = true
                ),
                Vehicle(
                    type = "PARTICULAR",
                    nickname = "Rayo EV",
                    brand = "BYD",
                    model = "Yuan Plus",
                    year = 2024,
                    plate = "EVX789",
                    fuel = "ELECTRIC",
                    odometer = 18500,
                    soatExpiry = "10/01/2027",
                    soatEnabled = true
                )
            )
            var firstId: String? = null
            for (v in demoVehicles) {
                val res = addVehicleUseCase(v)
                if (firstId == null) {
                    firstId = res.getOrNull()
                }
            }
            firstId?.let { setActiveVehicle(it) }
            _uiState.value = VehicleUiState.Success
        }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            _uiState.value = VehicleUiState.Loading
            val result = updateVehicleUseCase(vehicle)
            if (result.isSuccess) {
                _uiState.value = VehicleUiState.Success
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Error al actualizar vehículo"
                _uiState.value = VehicleUiState.Error(errorMsg)
            }
        }
    }

    fun resetUiState() {
        _uiState.value = VehicleUiState.Idle
    }
}
