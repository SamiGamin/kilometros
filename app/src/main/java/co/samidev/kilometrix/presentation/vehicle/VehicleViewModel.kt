package co.samidev.kilometrix.presentation.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.PicoPlacaResponse
import co.samidev.kilometrix.domain.model.PicoPlacaStatus
import co.samidev.kilometrix.domain.model.UserProfile
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.repository.UserRepository
import co.samidev.kilometrix.domain.usecase.AddVehicleUseCase
import co.samidev.kilometrix.domain.usecase.CalculatePicoYPlacaUseCase
import co.samidev.kilometrix.domain.usecase.GetPicoYPlacaUseCase
import co.samidev.kilometrix.domain.usecase.GetVehiclesUseCase
import co.samidev.kilometrix.domain.usecase.UpdateVehicleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val userRepository: UserRepository,
    private val getPicoYPlacaUseCase: GetPicoYPlacaUseCase,
    private val calculatePicoYPlacaUseCase: CalculatePicoYPlacaUseCase
) : ViewModel() {

    val vehicles: StateFlow<List<Vehicle>> = getVehiclesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
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
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Error al agregar vehículo"
                _uiState.value = VehicleUiState.Error(errorMsg)
            }
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

