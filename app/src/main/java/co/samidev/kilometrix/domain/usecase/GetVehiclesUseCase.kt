package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetVehiclesUseCase @Inject constructor(
    private val repository: VehicleRepository
) {
    operator fun invoke(): Flow<List<Vehicle>> = repository.getVehiclesRealtime()
}
