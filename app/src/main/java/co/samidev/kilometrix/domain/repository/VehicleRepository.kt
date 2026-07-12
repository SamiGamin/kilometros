package co.samidev.kilometrix.domain.repository

import co.samidev.kilometrix.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun getVehiclesRealtime(): Flow<List<Vehicle>>
    suspend fun addVehicle(vehicle: Vehicle): Result<String>
    suspend fun updateVehicle(vehicle: Vehicle): Result<Unit>
    suspend fun deleteVehicle(vehicleId: String): Result<Unit>
}
