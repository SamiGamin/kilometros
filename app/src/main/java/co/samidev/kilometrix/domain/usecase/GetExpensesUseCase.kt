package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetExpensesUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository
) {
    /** Todos los gastos en tiempo real para el vehículo dado. */
    operator fun invoke(vehicleId: String): Flow<List<VehicleExpense>> =
        expenseRepository.getExpensesRealtime(vehicleId)

    /** Solo el historial de combustible (para cálculo de rendimiento). */
    fun fuelHistory(vehicleId: String): Flow<List<VehicleExpense>> =
        expenseRepository.getFuelHistory(vehicleId)
}
