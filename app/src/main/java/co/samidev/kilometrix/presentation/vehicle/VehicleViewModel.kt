package co.samidev.kilometrix.presentation.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.usecase.AddVehicleUseCase
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
    private val updateVehicleUseCase: UpdateVehicleUseCase
) : ViewModel() {

    val vehicles: StateFlow<List<Vehicle>> = getVehiclesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow<VehicleUiState>(VehicleUiState.Idle)
    val uiState: StateFlow<VehicleUiState> = _uiState

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
