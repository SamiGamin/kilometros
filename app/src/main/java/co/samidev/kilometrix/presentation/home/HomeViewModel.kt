package co.samidev.kilometrix.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samidev.kilometrix.core.util.Resource
import co.samidev.kilometrix.domain.model.PicoPlacaStatus
import co.samidev.kilometrix.domain.repository.PicoYPlacaRepository
import co.samidev.kilometrix.domain.repository.UserRepository
import co.samidev.kilometrix.domain.repository.VehicleRepository
import co.samidev.kilometrix.domain.usecase.CalculatePicoYPlacaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val picoPlacaStatus: PicoPlacaStatus = PicoPlacaStatus("Cargando...", "Verificando restricciones", false, false)
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val vehicleRepository: VehicleRepository,
    private val picoYPlacaRepository: PicoYPlacaRepository,
    private val calculatePicoYPlacaUseCase: CalculatePicoYPlacaUseCase
) : ViewModel() {

    private val timeTicker = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(5000) // Emit every 5 seconds
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        userRepository.getUserProfile(),
        vehicleRepository.getVehiclesRealtime(),
        picoYPlacaRepository.getPicoYPlacaData(),
        timeTicker
    ) { profile, vehicles, picoResource, _ ->
        val userName = profile?.name?.substringBefore(" ") ?: "Conductor"

        // Date formatting
        val calendar = Calendar.getInstance()
        val dayName = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.forLanguageTag("es-CO"))
            ?.replaceFirstChar { it.uppercase() } ?: ""
        val dayNumber = calendar.get(Calendar.DAY_OF_MONTH)
        val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.forLanguageTag("es-CO"))
        val dateText = "$dayName, $dayNumber de $monthName"

        val status = calculatePicoYPlacaUseCase(profile?.city, vehicles.firstOrNull(), picoResource)

        HomeUiState(
            userName = userName,
            currentDateText = dateText,
            picoPlacaStatus = status
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}
