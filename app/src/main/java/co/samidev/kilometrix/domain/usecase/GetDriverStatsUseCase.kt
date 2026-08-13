package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.DriverStats
import co.samidev.kilometrix.domain.repository.ActiveVehicleRepository
import co.samidev.kilometrix.domain.repository.VehicleRepository
import co.samidev.kilometrix.domain.repository.WorkShiftRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class GetDriverStatsUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val activeVehicleRepository: ActiveVehicleRepository,
    private val workShiftRepository: WorkShiftRepository
) {
    operator fun invoke(): Flow<DriverStats> {
        return combine(
            vehicleRepository.getVehiclesRealtime(),
            activeVehicleRepository.activeVehicleId
        ) { vehicles, activeId ->
            val activeVehicle = vehicles.firstOrNull { it.id == activeId } ?: vehicles.firstOrNull()
            activeVehicle
        }.flatMapLatest { activeVehicle ->
            if (activeVehicle == null) {
                flowOf(DriverStats(totalShifts = 0, totalGrossEarnings = 0.0, activeVehicleName = "Sin vehículo"))
            } else {
                val vehicleName = "${activeVehicle.brand} ${activeVehicle.model}".trim().ifEmpty { activeVehicle.nickname }
                val displayName = if (activeVehicle.plate.isNotBlank()) "$vehicleName (${activeVehicle.plate})" else vehicleName

                workShiftRepository.getShiftsForVehicle(activeVehicle.id).flatMapLatest { shifts ->
                    val totalShifts = shifts.size
                    val totalGrossEarnings = shifts.sumOf { shift ->
                        shift.earnings.sumOf { earning -> earning.amount }
                    }
                    flowOf(
                        DriverStats(
                            totalShifts = totalShifts,
                            totalGrossEarnings = totalGrossEarnings,
                            activeVehicleName = displayName
                        )
                    )
                }
            }
        }
    }
}
