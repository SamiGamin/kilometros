package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.WorkShift
import co.samidev.kilometrix.domain.repository.WorkShiftRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWorkShiftsUseCase @Inject constructor(
    private val workShiftRepository: WorkShiftRepository
) {
    operator fun invoke(vehicleId: String): Flow<List<WorkShift>> {
        return workShiftRepository.getShiftsForVehicle(vehicleId)
    }
}
