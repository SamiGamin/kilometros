package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.repository.VehicleRepository
import javax.inject.Inject

class UpdateVehicleUseCase @Inject constructor(
    private val repository: VehicleRepository
) {
    suspend operator fun invoke(vehicle: Vehicle): Result<Unit> = repository.updateVehicle(vehicle)
}
