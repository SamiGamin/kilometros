package co.samidev.kilometrix.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveVehicleRepository @Inject constructor() {
    private val _activeVehicleId = MutableStateFlow<String?>(null)
    val activeVehicleId: StateFlow<String?> = _activeVehicleId.asStateFlow()

    fun setActiveVehicleId(vehicleId: String) {
        _activeVehicleId.value = vehicleId
    }
}
