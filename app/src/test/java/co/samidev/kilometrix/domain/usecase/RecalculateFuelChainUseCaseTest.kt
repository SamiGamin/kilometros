package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelDetails
import co.samidev.kilometrix.domain.model.FuelUnit
import co.samidev.kilometrix.domain.model.VehicleExpense
import org.junit.Assert.assertEquals
import org.junit.Test

class RecalculateFuelChainUseCaseTest {

    @Test
    fun `recalculate fuel chain no asigna kmPerGallon a Full que cierra un Reserve cero galones`() {
        val reserve0 = VehicleExpense(
            id = "r0", vehicleId = "v1", type = ExpenseType.FUEL,
            amount = 0.0, date = 1000L,
            fuelDetails = FuelDetails(
                odometerAtRefuel = 50000, gallons = 0.0, isReserve = true,
                enteredUnit = FuelUnit.GALLON
            )
        )
        val full = VehicleExpense(
            id = "f1", vehicleId = "v1", type = ExpenseType.FUEL,
            amount = 150000.0, date = 2000L,
            fuelDetails = FuelDetails(
                odometerAtRefuel = 50500, previousOdometer = 50000, gallons = 10.0,
                isFullTank = true, enteredUnit = FuelUnit.GALLON
            )
        )
        val result = RecalculateFuelChainUseCase()(listOf(reserve0, full))
        val fullRecalculated = result.first { it.id == "f1" }
        assertEquals(
            "Reserve(0) → Full debe ser ABANDONED: el Full no debe tener kmPerGallon",
            0.0,
            fullRecalculated.fuelDetails?.kmPerGallon ?: -1.0,
            0.001
        )
    }
}
