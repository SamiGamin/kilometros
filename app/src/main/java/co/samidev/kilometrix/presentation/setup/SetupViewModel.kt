package co.samidev.kilometrix.presentation.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.model.CityData
import co.samidev.kilometrix.domain.repository.PicoYPlacaRepository
import co.samidev.kilometrix.domain.usecase.SaveSetupDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SetupUiState {
    object Idle : SetupUiState
    object Loading : SetupUiState
    object Success : SetupUiState
    data class Error(val message: String) : SetupUiState
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val saveSetupDataUseCase: SaveSetupDataUseCase,
    private val picoYPlacaRepository: PicoYPlacaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SetupUiState>(SetupUiState.Idle)
    val uiState: StateFlow<SetupUiState> = _uiState

    private val _cities = MutableStateFlow<List<CityData>>(emptyList())
    val cities: StateFlow<List<CityData>> = _cities

    init {
        loadCities()
    }

    private fun loadCities() {
        viewModelScope.launch {
            picoYPlacaRepository.getPicoYPlacaData().collectLatest { resource ->
                if (resource is Resource.Success && resource.data != null) {
                    _cities.value = resource.data.cities
                }
            }
        }
    }

    fun saveSetupData(
        city: String,
        platforms: List<String>,
        vehicle: Vehicle,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = SetupUiState.Loading
            val result = saveSetupDataUseCase(city, platforms, vehicle)
            if (result.isSuccess) {
                _uiState.value = SetupUiState.Success
                onComplete()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Error al guardar los datos"
                _uiState.value = SetupUiState.Error(errorMsg)
            }
        }
    }
}
