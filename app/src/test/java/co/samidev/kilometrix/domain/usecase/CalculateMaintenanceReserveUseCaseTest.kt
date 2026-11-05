package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.ShiftEarning
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.model.WorkShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateMaintenanceReserveUseCaseTest {

    private val useCase = CalculateMaintenanceReserveUseCase()

    private fun createShift(earningsAmount: Double): WorkShift {
        return WorkShift(
            earnings = listOf(ShiftEarning(amount = earningsAmount))
        )
    }

    private fun createExpense(type: ExpenseType, amount: Double): VehicleExpense {
        return VehicleExpense(
            type = type,
            amount = amount
        )
    }

    @Test
    fun sin_turnos_ni_gastos_devuelve_hasData_false() {
        val result = useCase(
            reservePercent = 10,
            shifts = emptyList(),
            expenses = emptyList()
        )
        assertFalse(result.hasData)
        assertEquals(0.0, result.provisionedCop, 0.001)
        assertEquals(0.0, result.usedCop, 0.001)
        assertEquals(0.0, result.balanceCop, 0.001)
        assertEquals(10, result.reservePercent)
    }

    @Test
    fun con_turnos_y_sin_MAINTENANCE_devuelve_balance_igual_a_provisioned() {
        val shifts = listOf(createShift(100_000.0), createShift(50_000.0))
        val expenses = listOf(createExpense(ExpenseType.FUEL, 30_000.0))
        val result = useCase(
            reservePercent = 10,
            shifts = shifts,
            expenses = expenses
        )
        assertTrue(result.hasData)
        assertEquals(15_000.0, result.provisionedCop, 0.001)
        assertEquals(0.0, result.usedCop, 0.001)
        assertEquals(15_000.0, result.balanceCop, 0.001)
    }

    @Test
    fun con_MAINTENANCE_mayor_a_provisioned_devuelve_balance_negativo() {
        val shifts = listOf(createShift(100_000.0))
        val expenses = listOf(createExpense(ExpenseType.MAINTENANCE, 25_000.0))
        val result = useCase(
            reservePercent = 10,
            shifts = shifts,
            expenses = expenses
        )
        assertTrue(result.hasData)
        assertEquals(10_000.0, result.provisionedCop, 0.001)
        assertEquals(25_000.0, result.usedCop, 0.001)
        assertEquals(-15_000.0, result.balanceCop, 0.001)
    }

    @Test
    fun coerceIn_respeta_el_rango_5_25() {
        val underResult = useCase(reservePercent = 2, shifts = emptyList(), expenses = emptyList())
        assertEquals(5, underResult.reservePercent)

        val overResult = useCase(reservePercent = 30, shifts = emptyList(), expenses = emptyList())
        assertEquals(25, overResult.reservePercent)

        val withinResult = useCase(reservePercent = 15, shifts = emptyList(), expenses = emptyList())
        assertEquals(15, withinResult.reservePercent)
    }
}
