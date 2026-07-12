package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.Vehicle
import co.samidev.kilometrix.domain.repository.UserRepository
import co.samidev.kilometrix.domain.repository.VehicleRepository
import javax.inject.Inject

class SaveSetupDataUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val vehicleRepository: VehicleRepository
) {
    suspend operator fun invoke(
        city: String,
        platforms: List<String>,
        vehicle: Vehicle
    ): Result<Unit> {
        return try {
            val userResult = userRepository.saveSetupData(city, platforms)
            if (userResult.isFailure) {
                return Result.failure(userResult.exceptionOrNull() ?: Exception("Failed to save user setup data"))
            }

            val vehicleResult = vehicleRepository.addVehicle(vehicle)
            if (vehicleResult.isFailure) {
                return Result.failure(vehicleResult.exceptionOrNull() ?: Exception("Failed to save initial vehicle setup data"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
