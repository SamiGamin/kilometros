package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.repository.UserRepository
import javax.inject.Inject

class UpdateUserProfileUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        name: String,
        city: String,
        platforms: List<String>,
        maintenanceReservePercent: Int
    ): Result<Unit> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("El nombre del conductor no puede estar vacío."))
        }
        if (platforms.isEmpty()) {
            return Result.failure(IllegalArgumentException("Debes seleccionar al menos una plataforma de trabajo."))
        }
        if (maintenanceReservePercent !in 5..25) {
            return Result.failure(IllegalArgumentException("La reserva de mantenimiento debe estar entre el 5% y el 25%."))
        }

        return userRepository.updateUserProfile(
            name = trimmedName,
            city = city.trim(),
            platforms = platforms,
            maintenanceReservePercent = maintenanceReservePercent
        )
    }
}
