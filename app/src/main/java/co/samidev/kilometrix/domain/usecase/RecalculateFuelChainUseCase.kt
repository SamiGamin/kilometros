package co.samidev.kilometrix.domain.usecase

import android.util.Log
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelDetails
import co.samidev.kilometrix.domain.model.VehicleExpense
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconstruye y calcula de forma precisa la cadena cronológica de combustible
 * (odómetro anterior, odómetro actual, km recorridos, galones, precio/galón y km/galón)
 * para todos los registros de combustible de un vehículo ordenados por fecha ascendente.
 */
@Singleton
class RecalculateFuelChainUseCase @Inject constructor() {

    operator fun invoke(expenses: List<VehicleExpense>): List<VehicleExpense> {
        val fuelExpenses = expenses.filter { it.type == ExpenseType.FUEL }
            .sortedBy { it.date }

        if (fuelExpenses.isEmpty()) return expenses

        val recalculatedMap = mutableMapOf<String, VehicleExpense>()

        fuelExpenses.forEachIndexed { index, exp ->
            val d = exp.fuelDetails ?: FuelDetails()
            val currentOd = if (d.odometerAtRefuel > 0) d.odometerAtRefuel else d.previousOdometer

            val gallons = if (d.gallons > 0) d.gallons
                          else if (d.enteredQuantity > 0 && d.enteredUnit.name == "GALLON") d.enteredQuantity
                          else if (d.pricePerGallon > 0 && exp.amount > 0) exp.amount / d.pricePerGallon
                          else 0.0

            val liters = if (d.liters > 0) d.liters else gallons * 3.78541

            val pricePerGal = if (d.pricePerGallon > 0) d.pricePerGallon
                               else if (gallons > 0) exp.amount / gallons
                               else 0.0

            val pricePerLit = if (d.pricePerLiter > 0) d.pricePerLiter else pricePerGal / 3.78541

            // ── Cálculo directo hacia adelante (Forward Leg Calculation) ──
            // Cada tanqueada 'exp' suministró los galones para recorrer la distancia
            // desde 'currentOd' hasta el odómetro de la SIGUIENTE tanqueada.
            val hasNext = index < fuelExpenses.size - 1
            val nextExp = if (hasNext) fuelExpenses[index + 1] else null
            val nextDetails = nextExp?.fuelDetails
            val nextOd = if (nextDetails != null) {
                if (nextDetails.odometerAtRefuel > 0) nextDetails.odometerAtRefuel else nextDetails.previousOdometer
            } else 0

            val kmTraveled = if (hasNext && nextOd > currentOd) (nextOd - currentOd) else 0
            val kmPerGal = if (gallons > 0.0 && kmTraveled > 0) kmTraveled / gallons else 0.0
            val kmPerLit = if (liters > 0.0 && kmTraveled > 0) kmTraveled / liters else 0.0

            val updatedDetails = d.copy(
                odometerAtRefuel = currentOd,
                previousOdometer = currentOd,
                kmTraveled = kmTraveled,
                gallons = gallons,
                liters = liters,
                pricePerGallon = pricePerGal,
                pricePerLiter = pricePerLit,
                enteredQuantity = if (d.enteredQuantity > 0) d.enteredQuantity else gallons,
                pricePerEnteredUnit = if (d.pricePerEnteredUnit > 0) d.pricePerEnteredUnit else pricePerGal,
                kmPerGallon = kmPerGal,
                kmPerLiter = kmPerLit
            )

            recalculatedMap[exp.id] = exp.copy(fuelDetails = updatedDetails)
        }

        val result = expenses.map { exp -> recalculatedMap[exp.id] ?: exp }

        // ── DEBUG: imprimir últimas 10 tanquedas recalculadas ──────────────────
        val lastTen = result
            .filter { it.type == ExpenseType.FUEL }
            .sortedByDescending { it.date }
            .take(10)

        Log.d("FUEL_CHAIN", "═══════════ ÚLTIMAS ${lastTen.size} TANQUEDAS (más reciente primero) ═══════════")
        lastTen.forEachIndexed { i, exp ->
            val fd = exp.fuelDetails
            val dateStr = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(exp.date))
            Log.d("FUEL_CHAIN", "[$i] $dateStr | " +
                "od=${fd?.odometerAtRefuel} km | " +
                "kmTrav=${fd?.kmTraveled} km | " +
                "gal=${"%.2f".format(fd?.gallons ?: 0.0)} | " +
                "kmPerGal=${"%.1f".format(fd?.kmPerGallon ?: 0.0)} | " +
                "tipo=${when { fd?.isFullTank == true -> "LLENO"; fd?.isReserve == true -> "RESERVA"; fd?.isPartial == true -> "PARCIAL"; else -> "?" }} | " +
                "id=${exp.id.take(8)}"
            )
        }
        Log.d("FUEL_CHAIN", "═══════════════════════════════════════════════════════════════════")

        return result
    }
}
