package co.samidev.kilometrix.domain.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveVehicleRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    private val _activeVehicleId = MutableStateFlow<String?>(
        prefs.getString("active_vehicle_id", null)
    )
    val activeVehicleId: StateFlow<String?> = _activeVehicleId.asStateFlow()

    fun setActiveVehicleId(vehicleId: String) {
        prefs.edit().putString("active_vehicle_id", vehicleId).apply()
        _activeVehicleId.value = vehicleId
    }
}
