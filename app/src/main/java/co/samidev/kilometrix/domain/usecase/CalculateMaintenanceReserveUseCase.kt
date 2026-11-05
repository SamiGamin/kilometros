package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.model.WorkShift
import javax.inject.Inject

data class MaintenanceReserveSnapshot(
    val reservePercent: Int,
    val provisionedCop: Double,       // Σ(earnings × % / 100)
    val usedCop: Double,              // Σ(MAINTENANCE)
    val balanceCop: Double,           // provisioned - used (puede ser negativo)
    val hasData: Boolean              // true si hay al menos 1 turno o 1 MAINTENANCE
)

class CalculateMaintenanceReserveUseCase @Inject constructor() {

    operator fun invoke(
        reservePercent: Int,
        shifts: List<WorkShift>,
        expenses: List<VehicleExpense>
    ): MaintenanceReserveSnapshot {
        val safePercent = reservePercent.coerceIn(5, 25)
        val totalEarnings = shifts.sumOf { shift -> shift.earnings.sumOf { it.amount } }
        val provisioned = totalEarnings * safePercent / 100.0
        val used = expenses
            .filter { it.type == ExpenseType.MAINTENANCE }
            .sumOf { it.amount }
        val balance = provisioned - used
        val hasData = shifts.isNotEmpty() || used > 0.0
        return MaintenanceReserveSnapshot(
            reservePercent = safePercent,
            provisionedCop = provisioned,
            usedCop = used,
            balanceCop = balance,
            hasData = hasData
        )
    }
}
