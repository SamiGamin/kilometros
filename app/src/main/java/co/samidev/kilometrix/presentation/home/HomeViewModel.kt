package co.samidev.kilometrix.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.domain.model.PicoPlacaStatus
import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.repository.ActiveVehicleRepository
import co.samidev.kilometrix.domain.repository.PicoYPlacaRepository
import co.samidev.kilometrix.domain.repository.UserRepository
import co.samidev.kilometrix.domain.repository.VehicleRepository
import co.samidev.kilometrix.domain.usecase.CalculatePicoYPlacaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "",
    val currentDateText: String = "",
    val activeVehicle: Vehicle? = null,
    val picoPlacaStatus: PicoPlacaStatus = PicoPlacaStatus("Cargando...", "Verificando restricciones", false, false)
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val vehicleRepository: VehicleRepository,
    private val activeVehicleRepository: ActiveVehicleRepository,
    private val picoYPlacaRepository: PicoYPlacaRepository,
    private val calculatePicoYPlacaUseCase: CalculatePicoYPlacaUseCase
) : ViewModel() {

    private val timeTicker = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(5000)
        }
    }

    private val activeVehicle: Flow<Vehicle?> = combine(
        vehicleRepository.getVehiclesRealtime(),
        activeVehicleRepository.activeVehicleId
    ) { vehicles, activeId ->
        if (activeId != null) vehicles.find { it.id == activeId } ?: vehicles.firstOrNull()
        else vehicles.firstOrNull()
    }

    val uiState: StateFlow<HomeUiState> = combine(
        userRepository.getUserProfile(),
        activeVehicle,
        picoYPlacaRepository.getPicoYPlacaData(),
        timeTicker
    ) { profile, vehicle, picoResource, _ ->
        val userName = profile?.name?.substringBefore(" ") ?: "Conductor"

        val calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Bogota"))
        val dayName = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.forLanguageTag("es-CO"))
            ?.replaceFirstChar { it.uppercase() } ?: ""
        val dayNumber = calendar.get(Calendar.DAY_OF_MONTH)
        val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.forLanguageTag("es-CO"))
        val dateText = "$dayName, $dayNumber de $monthName"

        val status = calculatePicoYPlacaUseCase(profile?.city, vehicle, picoResource)

        HomeUiState(
            userName = userName,
            currentDateText = dateText,
            activeVehicle = vehicle,
            picoPlacaStatus = status
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}
