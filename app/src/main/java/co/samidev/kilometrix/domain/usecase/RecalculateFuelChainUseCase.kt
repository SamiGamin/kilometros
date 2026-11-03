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
            .sortedWith(compareBy<VehicleExpense> { it.date }.thenBy { it.fuelDetails?.odometerAtRefuel ?: 0 })

        if (fuelExpenses.isEmpty()) return expenses

        val recalculatedMap = mutableMapOf<String, VehicleExpense>()

        // Pre-normalizar odómetros y volúmenes
        data class NormalizedFuel(
            val exp: VehicleExpense,
            val details: FuelDetails,
            val od: Int,
            val gallons: Double,
            val liters: Double,
            val pricePerGal: Double,
            val pricePerLit: Double
        )

        val normalized = fuelExpenses.map { exp ->
            val d = exp.fuelDetails ?: FuelDetails()
            val currentOd = if (d.odometerAtRefuel > 0) d.odometerAtRefuel else d.previousOdometer

            val gallons = if (d.gallons > 0) d.gallons
                          else if (d.enteredQuantity > 0 && d.enteredUnit.name == "GALLON") d.enteredQuantity
                          else if (d.pricePerGallon > 0 && exp.amount > 0) exp.amount / d.pricePerGallon
                          else 0.0

            val liters = if (d.liters > 0) d.liters else gallons * CalculateFuelEfficiencyUseCase.LITERS_PER_GALLON

            val pricePerGal = if (d.pricePerGallon > 0) d.pricePerGallon
                              else if (gallons > 0) exp.amount / gallons
                              else 0.0

            val pricePerLit = if (d.pricePerLiter > 0) d.pricePerLiter
                              else pricePerGal / CalculateFuelEfficiencyUseCase.LITERS_PER_GALLON

            NormalizedFuel(exp, d, currentOd, gallons, liters, pricePerGal, pricePerLit)
        }

        // Índices de hitos de calibración (isFullTank o isReserve)
        val milestoneIndices = normalized.mapIndexedNotNull { idx, item ->
            if (item.details.isFullTank || item.details.isReserve) idx else null
        }

        // Mapa de cierre de ciclo por índice de hito B -> (deltaKm, gallonsConsumed)
        val cycleClosureByIdx = mutableMapOf<Int, Pair<Int, Double>>()
        for (m in 0 until milestoneIndices.size - 1) {
            val idxA = milestoneIndices[m]
            val idxB = milestoneIndices[m + 1]
            val itemA = normalized[idxA]
            val itemB = normalized[idxB]
            val deltaKm = itemB.od - itemA.od

            val gallonsConsumed = when {
                itemA.details.isFullTank && itemB.details.isFullTank ->
                    normalized.subList(idxA + 1, idxB + 1).sumOf { it.gallons }
                itemA.details.isReserve && itemB.details.isReserve ->
                    normalized.subList(idxA, idxB).sumOf { it.gallons }
                itemA.details.isReserve && itemA.gallons == 0.0 ->
                    normalized.subList(idxA + 1, idxB + 1).sumOf { it.gallons }
                itemA.details.isFullTank && itemB.details.isReserve && itemB.gallons > 0.0 ->
                    normalized.subList(idxA + 1, idxB + 1).sumOf { it.gallons }
                else -> 0.0
            }

            if (deltaKm > 0 && gallonsConsumed > 0.0) {
                cycleClosureByIdx[idxB] = deltaKm to gallonsConsumed
            }
        }

        normalized.forEachIndexed { index, item ->
            val d = item.details
            val currentOd = item.od
            val prevOd = if (index > 0) normalized[index - 1].od else d.previousOdometer
            val nextOd = if (index < normalized.size - 1) normalized[index + 1].od else 0

            val closure = cycleClosureByIdx[index]

            // Mantenemos siempre visible la distancia avanzada en el tramo o en el ciclo cerrado
            val kmTraveled = when {
                closure != null -> closure.first
                index > 0 && currentOd > prevOd -> currentOd - prevOd
                nextOd > currentOd -> nextOd - currentOd
                else -> 0
            }

            // El rendimiento km/gal solo se asigna cuando el registro cierra un ciclo calibrado válido
            val kmPerGal = if (closure != null && closure.second > 0.0) {
                closure.first.toDouble() / closure.second
            } else {
                0.0
            }
            val kmPerLit = if (kmPerGal > 0.0) {
                kmPerGal / CalculateFuelEfficiencyUseCase.LITERS_PER_GALLON
            } else {
                0.0
            }

            val updatedDetails = d.copy(
                odometerAtRefuel = currentOd,
                previousOdometer = if (prevOd > 0) prevOd else currentOd,
                kmTraveled = kmTraveled,
                gallons = item.gallons,
                liters = item.liters,
                pricePerGallon = item.pricePerGal,
                pricePerLiter = item.pricePerLit,
                enteredQuantity = if (d.enteredQuantity > 0) d.enteredQuantity else item.gallons,
                pricePerEnteredUnit = if (d.pricePerEnteredUnit > 0) d.pricePerEnteredUnit else item.pricePerGal,
                kmPerGallon = kmPerGal,
                kmPerLiter = kmPerLit
            )

            recalculatedMap[item.exp.id] = item.exp.copy(fuelDetails = updatedDetails)
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
