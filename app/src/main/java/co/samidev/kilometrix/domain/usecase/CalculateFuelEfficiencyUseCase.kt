package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.DataMaturityPhase
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelDetails
import co.samidev.kilometrix.domain.model.FuelEfficiencySummary
import co.samidev.kilometrix.domain.model.VehicleExpense
import javax.inject.Inject

/**
 * Calcula el resumen multifase de eficiencia de combustible.
 *
 * ## Fases de Madurez
 *
 * ### FASE 1 — Cold Start / Solo Caja
 * Activa cuando hay menos de 2 hitos de calibración en el historial.
 * Reporta únicamente el flujo de caja real (dinero pagado, km registrados).
 * `kmPerGallonAverage == null`
 *
 * ### FASE 2 — Calibración por Ciclo
 * Al detectar al menos 2 hitos de calibración (`isReserve == true` OR `isFullTank == true`),
 * se puede calcular un primer R_calibrado:
 *   ΔK = odometer_B - odometer_A
 *   G_acumulados = Σ galones entre A y B (inclusive)
 *   R_calibrado = ΔK / G_acumulados
 * Con solo 1 ciclo completado, la fase es PHASE_2_CALIBRATING.
 *
 * ### FASE 3 — Tanque Virtual
 * Con R_prom disponible (≥1 ciclo calibrado):
 *   G_quemados = ΔK_periodo / R_prom
 *   Costo_operativo = G_quemados × precioGalon_promedio
 *   G_restantes = G_comprados - G_quemados
 *   Km_restantes = G_restantes × R_prom
 *   Turnos_restantes = Km_restantes / KM_POR_TURNO
 * La fase es PHASE_3_VIRTUAL_TANK cuando hay ≥2 ciclos calibrados.
 *
 * @param kmPerShift Promedio de km por turno de trabajo. Default: 150 km.
 */
class CalculateFuelEfficiencyUseCase @Inject constructor() {

    companion object {
        const val LITERS_PER_GALLON = 3.78541
        const val KM_PER_SHIFT = 150.0  // km promedio por turno de trabajo (configurable)
    }

    operator fun invoke(
        fuelExpenses: List<VehicleExpense>,
        kmPerShift: Double = KM_PER_SHIFT
    ): FuelEfficiencySummary {

        // ── 0. Filtrado y ordenamiento ─────────────────────────────────────────
        val fuelRecords: List<FuelDetails> = fuelExpenses
            .filter { it.type == ExpenseType.FUEL && it.fuelDetails != null }
            .sortedBy { it.fuelDetails!!.odometerAtRefuel }
            .mapNotNull { it.fuelDetails }

        if (fuelRecords.isEmpty()) return FuelEfficiencySummary()

        // ── 1. FLUJO DE CAJA (siempre calculable) ─────────────────────────────
        val totalSpentCash = fuelExpenses
            .filter { it.type == ExpenseType.FUEL }
            .sumOf { it.amount }

        val totalGallonsPurchased = fuelRecords.sumOf { it.gallons }
        val totalLiters = totalGallonsPurchased * LITERS_PER_GALLON
        val totalKmTraveled = fuelRecords.sumOf { it.kmTraveled }
        val avgPricePerGallon = if (totalGallonsPurchased > 0)
            totalSpentCash / totalGallonsPurchased else 0.0

        val fillUpsCount = fuelRecords.size

        // ── 2. DETECCIÓN DE HITOS DE CALIBRACIÓN ──────────────────────────────
        // Un hito de calibración es cualquier registro marcado como isReserve o isFullTank.
        // Cada par consecutivo de hitos forma un "ciclo calibrado".
        val calibrationHits = fuelRecords
            .mapIndexedNotNull { index, fd ->
                if (fd.isReserve || fd.isFullTank) index else null
            }

        // ── 3. EVALUACIÓN DE FASE ─────────────────────────────────────────────
        return when {
            calibrationHits.size < 2 -> buildPhase1(
                totalSpentCash = totalSpentCash,
                totalGallonsPurchased = totalGallonsPurchased,
                totalLiters = totalLiters,
                totalKmTraveled = totalKmTraveled,
                fillUpsCount = fillUpsCount
            )
            else -> buildPhase2or3(
                fuelRecords = fuelRecords,
                calibrationHits = calibrationHits,
                totalSpentCash = totalSpentCash,
                totalGallonsPurchased = totalGallonsPurchased,
                totalLiters = totalLiters,
                totalKmTraveled = totalKmTraveled,
                avgPricePerGallon = avgPricePerGallon,
                fillUpsCount = fillUpsCount,
                kmPerShift = kmPerShift
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FASE 1 — Cold Start: Solo Flujo de Caja
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildPhase1(
        totalSpentCash: Double,
        totalGallonsPurchased: Double,
        totalLiters: Double,
        totalKmTraveled: Int,
        fillUpsCount: Int
    ): FuelEfficiencySummary {
        val costPerKm = if (totalKmTraveled > 0) totalSpentCash / totalKmTraveled else 0.0
        return FuelEfficiencySummary(
            // Flujo de caja
            totalSpentCash = totalSpentCash,
            totalGallonsPurchased = totalGallonsPurchased,
            totalKmTraveled = totalKmTraveled,
            // Eficiencia: null hasta calibración
            kmPerGallonAverage = null,
            kmPerLiterAverage = null,
            costPerKmReal = costPerKm,
            // Tanque Virtual: no disponible
            virtualTankRemainingGallons = 0.0,
            virtualTankRemainingCost = 0.0,
            estimatedRemainingAutonomyKm = 0.0,
            estimatedShiftsRemaining = 0.0,
            // Madurez
            dataMaturityPhase = DataMaturityPhase.PHASE_1_CASH_FLOW,
            fillUpsCount = fillUpsCount,
            // Aliases retro-compat
            averageKmPerGallon = 0.0,
            averageKmPerLiter = 0.0,
            totalGallons = totalGallonsPurchased,
            totalLiters = totalLiters,
            totalFuelCost = totalSpentCash,
            costPerKm = costPerKm,
            totalKmFueled = totalKmTraveled
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FASE 2 y 3 — Calibración y Tanque Virtual
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildPhase2or3(
        fuelRecords: List<FuelDetails>,
        calibrationHits: List<Int>,
        totalSpentCash: Double,
        totalGallonsPurchased: Double,
        totalLiters: Double,
        totalKmTraveled: Int,
        avgPricePerGallon: Double,
        fillUpsCount: Int,
        kmPerShift: Double
    ): FuelEfficiencySummary {

        // ── Calcular R_calibrado por cada ciclo (par consecutivo de hitos) ────
        data class CalibratedCycle(
            val deltaKm: Int,
            val gallonsConsumed: Double,
            val rCalibrado: Double  // km/gal
        )

        val cycles = mutableListOf<CalibratedCycle>()

        for (i in 0 until calibrationHits.size - 1) {
            val idxA = calibrationHits[i]
            val idxB = calibrationHits[i + 1]

            val odA = fuelRecords[idxA].odometerAtRefuel
            val odB = fuelRecords[idxB].odometerAtRefuel
            val deltaKm = odB - odA

            // Galones acumulados entre A (exclusive) y B (inclusive)
            // El tanqueo del hito A ya alimentó el ciclo anterior, no este.
            val gallonsInCycle = fuelRecords
                .subList(idxA + 1, idxB + 1)
                .sumOf { it.gallons }

            if (deltaKm > 0 && gallonsInCycle > 0.0) {
                val rCal = deltaKm.toDouble() / gallonsInCycle
                cycles.add(CalibratedCycle(deltaKm, gallonsInCycle, rCal))
            }
        }

        // Si no se pudo formar ningún ciclo válido, fallback a Fase 1
        if (cycles.isEmpty()) {
            return buildPhase1(
                totalSpentCash, totalGallonsPurchased, totalLiters, totalKmTraveled, fillUpsCount
            )
        }

        // ── R_prom ponderado por km (más km en el ciclo → más peso) ──────────
        val totalCycleKm = cycles.sumOf { it.deltaKm }
        val rProm = cycles.sumOf { it.rCalibrado * it.deltaKm } / totalCycleKm

        // ── Eficiencia Operativa ──────────────────────────────────────────────
        val rPromLiters = rProm / LITERS_PER_GALLON
        val costPerKmReal = if (rProm > 0) avgPricePerGallon / rProm else 0.0
        val bestKmPerGallon = cycles.maxOf { it.rCalibrado }
        val worstKmPerGallon = cycles.minOf { it.rCalibrado }

        // ── Tanque Virtual ────────────────────────────────────────────────────
        // G_quemados desde el ÚLTIMO hito de calibración hasta el último registro
        val lastCalibrationIdx = calibrationHits.last()
        val lastCalibrationRecord = fuelRecords[lastCalibrationIdx]
        val lastRecord = fuelRecords.last()

        val deltaKmFromLastHito = lastRecord.odometerAtRefuel - lastCalibrationRecord.odometerAtRefuel

        // Galones comprados después del último hito
        val gallonsPurchasedAfterHito = fuelRecords
            .subList(lastCalibrationIdx, fuelRecords.size)
            .sumOf { it.gallons }

        val gallonsEstimatedBurned = if (rProm > 0) deltaKmFromLastHito.toDouble() / rProm else 0.0
        val gallonsRemaining = maxOf(0.0, gallonsPurchasedAfterHito - gallonsEstimatedBurned)
        val remainingCost = gallonsRemaining * avgPricePerGallon
        val remainingAutonomyKm = gallonsRemaining * rProm
        val shiftsRemaining = if (kmPerShift > 0) remainingAutonomyKm / kmPerShift else 0.0

        // ── Determinar fase final ─────────────────────────────────────────────
        val phase = if (cycles.size >= 2) DataMaturityPhase.PHASE_3_VIRTUAL_TANK
                    else DataMaturityPhase.PHASE_2_CALIBRATING

        return FuelEfficiencySummary(
            // Flujo de caja
            totalSpentCash = totalSpentCash,
            totalGallonsPurchased = totalGallonsPurchased,
            totalKmTraveled = totalKmTraveled,
            // Eficiencia Operativa
            kmPerGallonAverage = rProm,
            kmPerLiterAverage = rPromLiters,
            costPerKmReal = costPerKmReal,
            bestKmPerGallon = bestKmPerGallon,
            worstKmPerGallon = worstKmPerGallon,
            // Tanque Virtual
            virtualTankRemainingGallons = gallonsRemaining,
            virtualTankRemainingCost = remainingCost,
            estimatedRemainingAutonomyKm = remainingAutonomyKm,
            estimatedShiftsRemaining = shiftsRemaining,
            // Madurez
            dataMaturityPhase = phase,
            fillUpsCount = fillUpsCount,
            // Aliases retro-compat
            averageKmPerGallon = rProm,
            averageKmPerLiter = rPromLiters,
            totalGallons = totalGallonsPurchased,
            totalLiters = totalLiters,
            totalFuelCost = totalSpentCash,
            costPerKm = costPerKmReal,
            totalKmFueled = totalKmTraveled
        )
    }
}
