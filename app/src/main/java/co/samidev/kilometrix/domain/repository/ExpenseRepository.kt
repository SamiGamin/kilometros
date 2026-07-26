package co.samidev.kilometrix.domain.repository

import co.samidev.kilometrix.domain.model.VehicleExpense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    /** Emite en tiempo real todos los gastos del vehículo seleccionado, ordenados por fecha desc. */
    fun getExpensesRealtime(vehicleId: String): Flow<List<VehicleExpense>>

    /** Emite solo los gastos de tipo FUEL con kmTraveled > 0, para cálculo de rendimiento. */
    fun getFuelHistory(vehicleId: String): Flow<List<VehicleExpense>>

    /**
     * Agrega un gasto. Si es de tipo FUEL, también actualiza el odómetro del vehículo
     * con el valor de [fuelDetails.odometerAtRefuel].
     * Retorna el ID del documento creado.
     */
    suspend fun addExpense(expense: VehicleExpense): Result<String>

    /** Elimina un gasto por ID. */
    suspend fun deleteExpense(expenseId: String): Result<Unit>
}
