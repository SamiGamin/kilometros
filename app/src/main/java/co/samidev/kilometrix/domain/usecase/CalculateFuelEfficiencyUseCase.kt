package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.DataMaturityPhase
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelCycleStatus
import co.samidev.kilometrix.domain.model.FuelDetails
import co.samidev.kilometrix.domain.model.FuelEfficiencySummary
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.util.FuelUnits
import javax.inject.Inject

/**
 * Calcula el resumen multifase de eficiencia de combustible con **Ciclos Exclusivos** (Punto 4).
 *
 * ## Regla de Ciclos Exclusivos (`FuelCycleStatus { OPEN, CLOSED, ABANDONED }`)
 * Solo puede existir **un ciclo abierto (`OPEN`) a la vez**:
 * 1. **Cierre homólogo (`Lleno → Lleno` o `Reserva → Reserva`):**
 *    El ciclo abierto se cierra como `CLOSED`, aporta $(\Delta K, G_{ciclo})$ a $R_{prom} = \sum \Delta K / \sum G$,
 *    y el hito de cierre abre inmediatamente el siguiente ciclo `OPEN`.
 * 2. **Transición no homóloga (`Reserva → Lleno` o `Lleno → Reserva`):**
 *    - El ciclo abierto anterior se marca como `ABANDONED` (sus galones no se usan para $R_{prom}$ ni se duplican).
 *    - Si la transición es `Reserva → Lleno`, se registra el evento como `capacityProbe`
 *      (`measuredReserveToFullGallons`) para conocer la capacidad útil real entre reserva y tanque lleno.
 *    - El nuevo hito abre inmediatamente un nuevo ciclo `OPEN` de su propio tipo.
 */
class CalculateFuelEfficiencyUseCase @Inject constructor() {

    companion object {
        const val LITERS_PER_GALLON = FuelUnits.LITERS_PER_US_GALLON
        const val KM_PER_SHIFT = 150.0  // km promedio por turno de trabajo (configurable)
        const val DEFAULT_TANK_CAPACITY_LITERS = 52.0 // Capacidad estándar (ej. Polo Track 52 L)
        const val OPEN_CYCLE_PROMPT_DAYS_THRESHOLD = 7
        const val OPEN_CYCLE_PROMPT_GALLONS_THRESHOLD = 10.0
        private const val MILLIS_PER_DAY = 86_400_000L
    }

    operator fun invoke(
        fuelExpenses: List<VehicleExpense>,
        kmPerShift: Double = KM_PER_SHIFT,
        currentVehicleOdometer: Int = 0,
        tankCapacityLiters: Double = DEFAULT_TANK_CAPACITY_LITERS,
        nowMillis: Long = System.currentTimeMillis()
    ): FuelEfficiencySummary {

        // ── 0. Filtrado y ordenamiento cronológico y por odómetro ──────────────
        val sortedFuelExpenses: List<VehicleExpense> = fuelExpenses
            .filter { it.type == ExpenseType.FUEL && it.fuelDetails != null }
            .sortedWith(compareBy<VehicleExpense> { it.date }.thenBy { it.fuelDetails?.odometerAtRefuel ?: 0 })

        val fuelRecords: List<FuelDetails> = sortedFuelExpenses.mapNotNull { it.fuelDetails }

        if (fuelRecords.isEmpty()) return FuelEfficiencySummary()

        // ── 1. FLUJO DE CAJA (siempre calculable) ─────────────────────────────
        val totalSpentCash = sortedFuelExpenses.sumOf { it.amount }
        val totalGallonsPurchased = fuelRecords.sumOf { it.gallons }
        val totalLiters = FuelUnits.gallonsToLiters(totalGallonsPurchased)
        val firstOdometer = fuelRecords.firstOrNull { it.odometerAtRefuel > 0 }?.odometerAtRefuel ?: 0
        val lastOdometer = maxOf(
            fuelRecords.maxOfOrNull { it.odometerAtRefuel } ?: 0,
            currentVehicleOdometer
        )
        val totalKmSpan = if (firstOdometer > 0 && lastOdometer > firstOdometer) {
            lastOdometer - firstOdometer
        } else {
            fuelRecords.sumOf { it.kmTraveled }
        }
        val avgPricePerGallon = if (totalGallonsPurchased > 0)
            totalSpentCash / totalGallonsPurchased else 0.0

        val fillUpsCount = fuelRecords.size

        // ── 2. DETECCIÓN DE HITOS DE CALIBRACIÓN ──────────────────────────────
        val calibrationHits = fuelRecords
            .mapIndexedNotNull { index, fd ->
                if (fd.isReserve || fd.isFullTank) index else null
            }

        // ── 3. MÉTRICAS DEL CICLO ABIERTO ACTUAL Y PROMPT DE CIERRE (Punto 9) ──
        val openAnchorIdx = calibrationHits.lastOrNull()
        val openAnchorRecord = openAnchorIdx?.let { fuelRecords[it] }
        val openAnchorType = when {
            openAnchorRecord?.isFullTank == true -> "FULL_TANK"
            openAnchorRecord?.isReserve == true -> "RESERVE"
            else -> null
        }
        val openStartMillis = if (openAnchorIdx != null) {
            sortedFuelExpenses[openAnchorIdx].date
        } else {
            sortedFuelExpenses.first().date
        }
        val openCycleDaysElapsed = maxOf(0L, (nowMillis - openStartMillis) / MILLIS_PER_DAY).toInt()
        val openCycleGallonsAccumulated = when {
            openAnchorIdx == null -> totalGallonsPurchased
            openAnchorRecord?.isFullTank == true ->
                fuelRecords.subList(openAnchorIdx + 1, fuelRecords.size).sumOf { it.gallons }
            openAnchorRecord?.isReserve == true ->
                fuelRecords.subList(openAnchorIdx, fuelRecords.size).sumOf { it.gallons }
            else -> 0.0
        }

        val needsPrompt = openCycleDaysElapsed >= OPEN_CYCLE_PROMPT_DAYS_THRESHOLD ||
            openCycleGallonsAccumulated >= OPEN_CYCLE_PROMPT_GALLONS_THRESHOLD ||
            (calibrationHits.isEmpty() && fillUpsCount >= 2)

        val promptMessage = if (needsPrompt) {
            when (openAnchorType) {
                "FULL_TANK" -> "💡 Llevas ${"%.1f".format(openCycleGallonsAccumulated)} gal en recargas parciales (${openCycleDaysElapsed}d). Haz un Tanque Lleno para cerrar el ciclo y calibrar OBD-II."
                "RESERVE" -> "💡 Llevas ${"%.1f".format(openCycleGallonsAccumulated)} gal desde la última reserva (${openCycleDaysElapsed}d). Registra al encender Reserva (o llena el tanque) para calibrar."
                else -> "💡 Para calibrar tu rendimiento real y el factor OBD-II, haz un ciclo Lleno → Lleno o Reserva → Reserva."
            }
        } else null

        // ── 4. EVALUACIÓN DE FASE ─────────────────────────────────────────────
        return when {
            calibrationHits.size < 2 -> buildPhase1(
                totalSpentCash = totalSpentCash,
                totalGallonsPurchased = totalGallonsPurchased,
                totalLiters = totalLiters,
                totalKmTraveled = totalKmSpan,
                fillUpsCount = fillUpsCount,
                openCycleAnchorType = openAnchorType,
                openCycleDaysElapsed = openCycleDaysElapsed,
                openCycleGallonsAccumulated = openCycleGallonsAccumulated,
                needsCycleClosurePrompt = needsPrompt,
                cycleClosurePromptMessage = promptMessage
            )
            else -> buildPhase2or3(
                fuelRecords = fuelRecords,
                calibrationHits = calibrationHits,
                totalSpentCash = totalSpentCash,
                totalGallonsPurchased = totalGallonsPurchased,
                totalLiters = totalLiters,
                totalKmTraveled = totalKmSpan,
                avgPricePerGallon = avgPricePerGallon,
                fillUpsCount = fillUpsCount,
                kmPerShift = kmPerShift,
                currentVehicleOdometer = currentVehicleOdometer,
                tankCapacityLiters = tankCapacityLiters,
                openCycleAnchorType = openAnchorType,
                openCycleDaysElapsed = openCycleDaysElapsed,
                openCycleGallonsAccumulated = openCycleGallonsAccumulated,
                needsCycleClosurePrompt = needsPrompt,
                cycleClosurePromptMessage = promptMessage
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FASE 1 — Cold Start: Solo Flujo de Caja (sin inventar km/gal)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildPhase1(
        totalSpentCash: Double,
        totalGallonsPurchased: Double,
        totalLiters: Double,
        totalKmTraveled: Int,
        fillUpsCount: Int,
        abandonedCyclesCount: Int = 0,
        measuredReserveToFullGallons: Double? = null,
        openCycleAnchorType: String? = null,
        openCycleDaysElapsed: Int = 0,
        openCycleGallonsAccumulated: Double = 0.0,
        needsCycleClosurePrompt: Boolean = false,
        cycleClosurePromptMessage: String? = null
    ): FuelEfficiencySummary {
        return FuelEfficiencySummary(
            totalSpentCash = totalSpentCash,
            totalGallonsPurchased = totalGallonsPurchased,
            totalKmTraveled = totalKmTraveled,
            kmPerGallonAverage = null,
            kmPerLiterAverage = null,
            costPerKmReal = 0.0,
            virtualTankRemainingGallons = 0.0,
            virtualTankRemainingCost = 0.0,
            estimatedRemainingAutonomyKm = 0.0,
            estimatedShiftsRemaining = 0.0,
            dataMaturityPhase = DataMaturityPhase.PHASE_1_CASH_FLOW,
            fillUpsCount = fillUpsCount,
            activeCycleStatus = FuelCycleStatus.OPEN,
            closedCyclesCount = 0,
            abandonedCyclesCount = abandonedCyclesCount,
            measuredReserveToFullGallons = measuredReserveToFullGallons,
            openCycleAnchorType = openCycleAnchorType,
            openCycleDaysElapsed = openCycleDaysElapsed,
            openCycleGallonsAccumulated = openCycleGallonsAccumulated,
            needsCycleClosurePrompt = needsCycleClosurePrompt,
            cycleClosurePromptMessage = cycleClosurePromptMessage,
            averageKmPerGallon = 0.0,
            averageKmPerLiter = 0.0,
            totalGallons = totalGallonsPurchased,
            totalLiters = totalLiters,
            totalFuelCost = totalSpentCash,
            costPerKm = 0.0,
            totalKmFueled = totalKmTraveled
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FASE 2 y 3 — Ciclos Exclusivos Homólogos (OPEN / CLOSED / ABANDONED)
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
        kmPerShift: Double,
        currentVehicleOdometer: Int,
        tankCapacityLiters: Double,
        openCycleAnchorType: String?,
        openCycleDaysElapsed: Int,
        openCycleGallonsAccumulated: Double,
        needsCycleClosurePrompt: Boolean,
        cycleClosurePromptMessage: String?
    ): FuelEfficiencySummary {

        data class CalibratedCycle(
            val deltaKm: Int,
            val gallonsConsumed: Double,
            val rCalibrado: Double
        )

        val cycles = mutableListOf<CalibratedCycle>()
        var abandonedCyclesCount = 0
        var latestCapacityProbeGallons: Double? = null

        for (i in 0 until calibrationHits.size - 1) {
            val idxA = calibrationHits[i]
            val idxB = calibrationHits[i + 1]

            val recA = fuelRecords[idxA]
            val recB = fuelRecords[idxB]
            val odA = recA.odometerAtRefuel
            val odB = recB.odometerAtRefuel
            if (odA <= 0 || odB <= 0 || odB <= odA) continue
            val deltaKm = odB - odA

            // Regla de Ciclos Exclusivos (Punto 4):
            // - Solo un ciclo está OPEN entre idxA e idxB.
            // - Si idxA e idxB son homólogos (Lleno->Lleno o Reserva->Reserva), se cierra como CLOSED.
            // - Si no son homólogos (Reserva->Lleno o Lleno->Reserva en registros reales), el ciclo anterior
            //   se cierra como ABANDONED, registra capacityProbe si fue Reserva->Lleno, y abre el nuevo ciclo en B.
            when {
                recA.isFullTank && recB.isFullTank -> {
                    val gallonsInCycle = fuelRecords.subList(idxA + 1, idxB + 1).sumOf { it.gallons }
                    if (deltaKm > 0 && gallonsInCycle > 0.0) {
                        cycles.add(CalibratedCycle(deltaKm, gallonsInCycle, deltaKm.toDouble() / gallonsInCycle))
                    }
                }
                recA.isReserve && recB.isReserve -> {
                    val gallonsInCycle = if (recA.gallons == 0.0) {
                        fuelRecords.subList(idxA + 1, idxB + 1).sumOf { it.gallons }
                    } else {
                        fuelRecords.subList(idxA, idxB).sumOf { it.gallons }
                    }
                    if (deltaKm > 0 && gallonsInCycle > 0.0) {
                        cycles.add(CalibratedCycle(deltaKm, gallonsInCycle, deltaKm.toDouble() / gallonsInCycle))
                    }
                }
                recA.isReserve && recB.isFullTank -> {
                    // Transición no homóloga Reserva -> Lleno:
                    // Se abandona el ciclo de reserva abierto, se registra capacityProbe y se abre ciclo Lleno en B.
                    abandonedCyclesCount++
                    if (recB.gallons > 0.0) {
                        latestCapacityProbeGallons = if (idxB == idxA + 1 && deltaKm <= 15) {
                            recA.gallons + recB.gallons
                        } else {
                            recB.gallons
                        }
                    }
                }
                recA.isFullTank && recB.isReserve -> {
                    // Transición no homóloga Lleno -> Reserva:
                    // Se abandona el ciclo de tanque lleno no cerrado y se abre nuevo ciclo de reserva en B.
                    abandonedCyclesCount++
                }
            }
        }

        // Si todos los ciclos entre hitos fueron ABANDONED (ninguno CLOSED), permanecemos en Fase 1
        if (cycles.isEmpty()) {
            return buildPhase1(
                totalSpentCash = totalSpentCash,
                totalGallonsPurchased = totalGallonsPurchased,
                totalLiters = totalLiters,
                totalKmTraveled = totalKmTraveled,
                fillUpsCount = fillUpsCount,
                abandonedCyclesCount = abandonedCyclesCount,
                measuredReserveToFullGallons = latestCapacityProbeGallons,
                openCycleAnchorType = openCycleAnchorType,
                openCycleDaysElapsed = openCycleDaysElapsed,
                openCycleGallonsAccumulated = openCycleGallonsAccumulated,
                needsCycleClosurePrompt = needsCycleClosurePrompt,
                cycleClosurePromptMessage = cycleClosurePromptMessage
            )
        }

        // ── R_prom físico real: Σ ΔK / Σ Galones_consumidos (solo ciclos CLOSED) ──
        val totalCycleKm = cycles.sumOf { it.deltaKm }
        val totalCycleGallons = cycles.sumOf { it.gallonsConsumed }
        val rProm = if (totalCycleGallons > 0.0) totalCycleKm.toDouble() / totalCycleGallons else 0.0

        // ── Eficiencia Operativa ──────────────────────────────────────────────
        val rPromLiters = FuelUnits.kmPerGallonToKmPerLiter(rProm)
        val costPerKmReal = if (rProm > 0) avgPricePerGallon / rProm else 0.0
        val bestKmPerGallon = cycles.maxOf { it.rCalibrado }
        val worstKmPerGallon = cycles.minOf { it.rCalibrado }

        // ── Tanque Virtual (soporta partir de Reserva o de Tanque Lleno) ──────
        val lastCalibrationIdx = calibrationHits.last()
        val lastCalibrationRecord = fuelRecords[lastCalibrationIdx]
        val lastRecord = fuelRecords.last()

        val latestKnownOdometer = maxOf(lastRecord.odometerAtRefuel, currentVehicleOdometer)
        val deltaKmFromLastHito = maxOf(0, latestKnownOdometer - lastCalibrationRecord.odometerAtRefuel)
        val gallonsEstimatedBurned = if (rProm > 0) deltaKmFromLastHito.toDouble() / rProm else 0.0

        val gallonsRemaining = if (lastCalibrationRecord.isReserve) {
            val gallonsAddedAboveReserve = fuelRecords
                .subList(lastCalibrationIdx, fuelRecords.size)
                .sumOf { it.gallons }
            maxOf(0.0, gallonsAddedAboveReserve - gallonsEstimatedBurned)
        } else {
            val tankCapacityGallons = if (tankCapacityLiters > 0.0) {
                FuelUnits.litersToGallons(tankCapacityLiters)
            } else {
                lastCalibrationRecord.gallons
            }
            val initialGallonsInTank = maxOf(tankCapacityGallons, lastCalibrationRecord.gallons)
            val partialGallonsAfterFull = fuelRecords
                .subList(lastCalibrationIdx + 1, fuelRecords.size)
                .sumOf { it.gallons }
            maxOf(
                0.0,
                minOf(initialGallonsInTank, initialGallonsInTank + partialGallonsAfterFull - gallonsEstimatedBurned)
            )
        }

        val remainingCost = gallonsRemaining * avgPricePerGallon
        val remainingAutonomyKm = gallonsRemaining * rProm
        val shiftsRemaining = if (kmPerShift > 0) remainingAutonomyKm / kmPerShift else 0.0

        val phase = if (cycles.size >= 2) DataMaturityPhase.PHASE_3_VIRTUAL_TANK
                    else DataMaturityPhase.PHASE_2_CALIBRATING

        return FuelEfficiencySummary(
            totalSpentCash = totalSpentCash,
            totalGallonsPurchased = totalGallonsPurchased,
            totalKmTraveled = totalKmTraveled,
            kmPerGallonAverage = rProm,
            kmPerLiterAverage = rPromLiters,
            costPerKmReal = costPerKmReal,
            bestKmPerGallon = bestKmPerGallon,
            worstKmPerGallon = worstKmPerGallon,
            virtualTankRemainingGallons = gallonsRemaining,
            virtualTankRemainingCost = remainingCost,
            estimatedRemainingAutonomyKm = remainingAutonomyKm,
            estimatedShiftsRemaining = shiftsRemaining,
            dataMaturityPhase = phase,
            fillUpsCount = fillUpsCount,
            activeCycleStatus = FuelCycleStatus.OPEN,
            closedCyclesCount = cycles.size,
            abandonedCyclesCount = abandonedCyclesCount,
            measuredReserveToFullGallons = latestCapacityProbeGallons,
            openCycleAnchorType = openCycleAnchorType,
            openCycleDaysElapsed = openCycleDaysElapsed,
            openCycleGallonsAccumulated = openCycleGallonsAccumulated,
            needsCycleClosurePrompt = needsCycleClosurePrompt,
            cycleClosurePromptMessage = cycleClosurePromptMessage,
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
