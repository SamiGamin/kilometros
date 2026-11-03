package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.model.WorkShift
import co.samidev.kilometrix.domain.util.FuelUnits
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

data class CycleKFuelSample(
    val cycleOrder: Int,
    val deltaKm: Int,
    val realLiters: Double,
    val obdLitersRaw: Double,
    val kFuel: Double
)

data class ObdCalibrationFactors(
    // ── Fase 3b: Calibración de Combustible (EWMA + Banda de Confianza, Punto 1) ─
    val kFuel: Double = 1.0,
    val obdFuelFactorStdev: Double = 0.0,
    val confidenceBandPct: Double = 0.0,
    val isFuelCalibrated: Boolean = false,
    val isFuelStable: Boolean = true,
    val consecutiveJumpPct: Double = 0.0,
    val calibratedFuelCycles: Int = 0,
    val incompleteCoverageCycles: Int = 0,
    val cycleSamples: List<CycleKFuelSample> = emptyList(),
    // ── Fase 3a: Calibración de Distancia por Turno (Mediana Robusta, Punto 7) ─
    val kDist: Double = 1.0,
    val isDistCalibrated: Boolean = false,
    val calibratedDistShifts: Int = 0,
    val outlierDistShifts: Int = 0,
    val kDistDriftAlert: Boolean = false,
    val kDistDriftMessage: String? = null
) {
    /**
     * Etiquetas UI honestas con banda de confianza (Puntos 1 y 9).
     */
    fun formatFuelSourceBadge(
        hasLiveObdFuel: Boolean,
        hasPumpCycleCalibration: Boolean,
        isIatDegraded: Boolean = false
    ): String {
        val iatSuffix = if (isIatDegraded) " · IAT est." else ""
        return when {
            hasLiveObdFuel && isFuelCalibrated && isFuelStable -> {
                val band = if (calibratedFuelCycles >= 2) {
                    "±${"%.1f".format(confidenceBandPct)}%"
                } else {
                    "1 ciclo"
                }
                "🟢 OBD calibrado ($band)$iatSuffix"
            }
            hasLiveObdFuel && isFuelCalibrated && !isFuelStable -> {
                "🟠 Calibración inestable (±${"%.1f".format(confidenceBandPct)}%)$iatSuffix"
            }
            hasLiveObdFuel && !isFuelCalibrated -> {
                "🟡 OBD en vivo (semilla VE=0,85)$iatSuffix"
            }
            hasPumpCycleCalibration -> {
                "🔵 Estimado por ciclo (R_prom)"
            }
            else -> {
                "⏳ Calibrando 1er ciclo"
            }
        }
    }
}

/**
 * Calibración cruzada en dos subfases independientes (Puntos 1, 2, 5, 7, 10):
 *
 * - **Fase 3a (`k_dist` por turno con mediana robusta):**
 *   El PID `010D` ya viene calibrado de fábrica (`k_dist ≈ 1.00`). Se calcula la razón
 *   $\Delta K_{tablero} / km_{OBD}$ en cada turno válido, descartando errores de digitación
 *   del odómetro (`outliers`) mediante la **mediana** y emitiendo alerta si $|k_{dist} - 1.0| > 5\%$.
 *
 * - **Fase 3b (`k_fuel` por ciclo cerrado con EWMA ponderado por km y regla estricta de cobertura):**
 *   `VE = 0.85` es una semilla cuya forma de error depende de carga, RPM, ralentí y DFCO.
 *   Por ello se guarda `k_fuel` por cada ciclo que cumpla $\ge 90\%$ de cobertura sin gaps $> 60\text{ s}$,
 *   se suaviza con **EWMA ponderado por km**, se calcula su desviación estándar (`obdFuelFactorStdev`)
 *   y se degrada la etiqueta a `🟡 Medición OBD inestable` si dos ciclos consecutivos difieren $> 10\%$.
 */
@Singleton
class CalculateObdCalibrationUseCase @Inject constructor() {

    operator fun invoke(
        fuelExpenses: List<VehicleExpense>,
        shifts: List<WorkShift>
    ): ObdCalibrationFactors {
        // ── FASE 3a: k_dist por turno con mediana robusta y detección de outliers (Punto 7) ──
        val validShiftRatios = mutableListOf<Double>()
        var outlierShiftCount = 0

        shifts.forEach { shift ->
            val finalOd = shift.finalOdometer ?: return@forEach
            val boardKm = (finalOd - shift.initialOdometer).toDouble()
            val obdKm = shift.obdDistanceKm
            val shiftMeetsCoverage = isShiftQualifiedForCalibration(shift)

            if (boardKm >= MIN_SHIFT_KM_FOR_DIST_CAL && obdKm >= MIN_SHIFT_KM_FOR_DIST_CAL && shiftMeetsCoverage) {
                val ratio = boardKm / obdKm
                if (ratio in MIN_DIST_FACTOR..MAX_DIST_FACTOR) {
                    validShiftRatios.add(ratio)
                } else {
                    // Outlier (ej. error de digitación del odómetro manual)
                    outlierShiftCount++
                }
            }
        }

        val rawMedianKDist = if (validShiftRatios.isNotEmpty()) {
            computeMedian(validShiftRatios)
        } else 1.0

        val isDistCalibrated = validShiftRatios.isNotEmpty()
        val kDistDriftAlert = isDistCalibrated && abs(rawMedianKDist - 1.0) > MAX_NORMAL_DIST_DRIFT
        val kDistDriftMessage = if (kDistDriftAlert) {
            val driftPct = (rawMedianKDist - 1.0) * 100.0
            "⚠️ Desviación de distancia OBD vs Tablero (${"%+.1f".format(driftPct)}%). Verifica medida/presión de llantas o digitación de odómetro."
        } else null

        // Si hay alerta de deriva > 5 %, acotamos la absorción automática a [0.95, 1.05] para no ocultar anomalías
        val effectiveKDist = if (isDistCalibrated) {
            rawMedianKDist.coerceIn(0.95, 1.05)
        } else 1.0

        // ── FASE 3b: k_fuel por ciclo homólogo con cobertura >= 90%, EWMA y varianza (Puntos 1 y 2) ──
        val sortedFuel = fuelExpenses
            .filter { it.type == ExpenseType.FUEL && it.fuelDetails != null }
            .sortedWith(compareBy<VehicleExpense> { it.date }.thenBy { it.fuelDetails?.odometerAtRefuel ?: 0 })

        val milestoneIndices = sortedFuel.mapIndexedNotNull { idx, exp ->
            val fd = exp.fuelDetails
            if (fd != null && (fd.isFullTank || fd.isReserve)) idx else null
        }

        val cycleSamples = mutableListOf<CycleKFuelSample>()
        var incompleteCoverageCycles = 0

        for (i in 0 until milestoneIndices.size - 1) {
            val idxA = milestoneIndices[i]
            val idxB = milestoneIndices[i + 1]
            val expA = sortedFuel[idxA]
            val expB = sortedFuel[idxB]
            val fdA = expA.fuelDetails ?: continue
            val fdB = expB.fuelDetails ?: continue

            val odA = fdA.odometerAtRefuel
            val odB = fdB.odometerAtRefuel
            val deltaKm = odB - odA
            if (deltaKm <= 0) continue

            // Solo ciclos homólogos cerrados (Lleno->Lleno o Reserva->Reserva)
            val gallonsInCycle = when {
                fdA.isFullTank && fdB.isFullTank ->
                    sortedFuel.subList(idxA + 1, idxB + 1).sumOf { it.fuelDetails?.gallons ?: 0.0 }
                fdA.isReserve && fdB.isReserve -> {
                    if (fdA.gallons == 0.0) {
                        sortedFuel.subList(idxA + 1, idxB + 1).sumOf { it.fuelDetails?.gallons ?: 0.0 }
                    } else {
                        sortedFuel.subList(idxA, idxB).sumOf { it.fuelDetails?.gallons ?: 0.0 }
                    }
                }
                else -> 0.0 // Ciclo no homólogo (ABANDONED): no calibra k_fuel
            }
            if (gallonsInCycle <= 0.0) continue

            // Unidades canónicas en Litros (Punto 5)
            val realLiters = FuelUnits.gallonsToLiters(gallonsInCycle)

            // Turnos ocurridos dentro del rango del ciclo A -> B
            val shiftsInCycle = shifts.filter { shift ->
                val byOdometer = shift.initialOdometer >= odA &&
                    (shift.finalOdometer ?: shift.initialOdometer) <= odB + 5 &&
                    (shift.finalOdometer ?: 0) > shift.initialOdometer
                val byDate = shift.startTime >= expA.date &&
                    (shift.endTime ?: shift.startTime) <= expB.date + 3_600_000L
                (byOdometer || byDate) && (shift.obdDistanceKm > 0.0 || shift.obdFuelLitersRaw > 0.0)
            }

            // Filtrar solo turnos que cumplan la Regla de Cobertura explícita (Punto 2):
            // obdCoveragePct >= 90%, obdMaxGapSec <= 60s, conectado al inicio y al final
            val qualifiedShifts = shiftsInCycle.filter { isShiftQualifiedForCalibration(it) }
            val hasDisqualifiedShift = shiftsInCycle.any { !isShiftQualifiedForCalibration(it) }

            val cycleObdKm = qualifiedShifts.sumOf { it.obdDistanceKm } * effectiveKDist
            val cycleObdLiters = qualifiedShifts.sumOf { it.obdFuelLitersRaw }
            val cycleDistanceCoverageRatio = cycleObdKm / deltaKm.toDouble()

            if (!hasDisqualifiedShift &&
                cycleDistanceCoverageRatio >= MIN_CYCLE_DISTANCE_COVERAGE_RATIO &&
                cycleObdLiters > 0.2
            ) {
                val factor = realLiters / cycleObdLiters
                if (factor in MIN_FUEL_FACTOR..MAX_FUEL_FACTOR) {
                    cycleSamples.add(
                        CycleKFuelSample(
                            cycleOrder = cycleSamples.size + 1,
                            deltaKm = deltaKm,
                            realLiters = realLiters,
                            obdLitersRaw = cycleObdLiters,
                            kFuel = factor
                        )
                    )
                }
            } else if (shiftsInCycle.isNotEmpty()) {
                incompleteCoverageCycles++
            }
        }

        if (cycleSamples.isEmpty()) {
            return ObdCalibrationFactors(
                kFuel = 1.0,
                obdFuelFactorStdev = 0.0,
                confidenceBandPct = 0.0,
                isFuelCalibrated = false,
                isFuelStable = true,
                consecutiveJumpPct = 0.0,
                calibratedFuelCycles = 0,
                incompleteCoverageCycles = incompleteCoverageCycles,
                cycleSamples = emptyList(),
                kDist = effectiveKDist,
                isDistCalibrated = isDistCalibrated,
                calibratedDistShifts = validShiftRatios.size,
                outlierDistShifts = outlierShiftCount,
                kDistDriftAlert = kDistDriftAlert,
                kDistDriftMessage = kDistDriftMessage
            )
        }

        // EWMA ponderado por los kilómetros de cada ciclo (Punto 1)
        val meanCycleKm = cycleSamples.map { it.deltaKm }.average().coerceAtLeast(1.0)
        var ewmaKFuel = cycleSamples.first().kFuel
        for (idx in 1 until cycleSamples.size) {
            val sample = cycleSamples[idx]
            val kmWeight = (sample.deltaKm / meanCycleKm).coerceIn(0.5, 1.6)
            val alpha = (EWMA_BASE_ALPHA * kmWeight).coerceIn(0.20, 0.70)
            ewmaKFuel = (alpha * sample.kFuel) + ((1.0 - alpha) * ewmaKFuel)
        }
        ewmaKFuel = ewmaKFuel.coerceIn(MIN_FUEL_FACTOR, MAX_FUEL_FACTOR)

        // Desviación estándar ponderada por km y banda de confianza relativa (±x %)
        val stdev = if (cycleSamples.size >= 2) {
            val totalWeight = cycleSamples.sumOf { it.deltaKm.toDouble() }
            val weightedVariance = if (totalWeight > 0.0) {
                cycleSamples.sumOf { sample ->
                    val diff = sample.kFuel - ewmaKFuel
                    sample.deltaKm.toDouble() * diff * diff
                } / totalWeight
            } else 0.0
            sqrt(weightedVariance.coerceAtLeast(0.0))
        } else 0.0

        val confidenceBandPct = if (ewmaKFuel > 0.0) {
            (stdev / ewmaKFuel) * 100.0
        } else 0.0

        // Estabilidad entre los dos últimos ciclos consecutivos (> 10 % degrada etiqueta, Punto 1)
        val consecutiveJumpPct = if (cycleSamples.size >= 2) {
            val prev = cycleSamples[cycleSamples.size - 2].kFuel
            val last = cycleSamples.last().kFuel
            if (prev > 0.0) (abs(last - prev) / prev) * 100.0 else 0.0
        } else 0.0

        val isFuelStable = consecutiveJumpPct <= MAX_STABLE_CONSECUTIVE_JUMP_PCT

        return ObdCalibrationFactors(
            kFuel = ewmaKFuel,
            obdFuelFactorStdev = stdev,
            confidenceBandPct = confidenceBandPct,
            isFuelCalibrated = true,
            isFuelStable = isFuelStable,
            consecutiveJumpPct = consecutiveJumpPct,
            calibratedFuelCycles = cycleSamples.size,
            incompleteCoverageCycles = incompleteCoverageCycles,
            cycleSamples = cycleSamples.takeLast(MAX_EXPOSED_CYCLES),
            kDist = effectiveKDist,
            isDistCalibrated = isDistCalibrated,
            calibratedDistShifts = validShiftRatios.size,
            outlierDistShifts = outlierShiftCount,
            kDistDriftAlert = kDistDriftAlert,
            kDistDriftMessage = kDistDriftMessage
        )
    }

    /**
     * Regla de cobertura explícita por turno (Punto 2):
     * - Si el turno tiene métricas de cobertura registradas (`obdSampleCount > 0` o `obdCoveragePct > 0`),
     *   exige `obdValid == true` (`obdCoveragePct >= 90%`, `obdMaxGapSec <= 60s`, conectado inicio y fin).
     * - Soporta retro-compatibilidad con turnos previos a la migración si `obdSampleCount == 0` y `obdCoveragePct == 0.0`.
     */
    private fun isShiftQualifiedForCalibration(shift: WorkShift): Boolean {
        val hasCoverageMetadata = shift.obdSampleCount > 0 || shift.obdCoveragePct > 0.0 || shift.obdGapCount > 0
        if (!hasCoverageMetadata) {
            return shift.obdDistanceKm > 0.0
        }
        return shift.obdValid &&
            shift.obdCoveragePct >= MIN_SHIFT_COVERAGE_PCT &&
            shift.obdMaxGapSec <= MAX_SHIFT_GAP_SEC &&
            shift.obdConnectedAtStart &&
            shift.obdConnectedAtEnd
    }

    private fun computeMedian(values: List<Double>): Double {
        if (values.isEmpty()) return 1.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }

    companion object {
        const val MAX_EXPOSED_CYCLES = 10
        const val MIN_SHIFT_COVERAGE_PCT = 90.0
        const val MAX_SHIFT_GAP_SEC = 60.0
        const val MIN_CYCLE_DISTANCE_COVERAGE_RATIO = 0.90
        const val MIN_SHIFT_KM_FOR_DIST_CAL = 3.0
        const val MIN_DIST_FACTOR = 0.75
        const val MAX_DIST_FACTOR = 1.35
        const val MAX_NORMAL_DIST_DRIFT = 0.05 // |k_dist - 1.0| > 5 % dispara alerta
        const val MIN_FUEL_FACTOR = 0.35
        const val MAX_FUEL_FACTOR = 2.80
        const val EWMA_BASE_ALPHA = 0.45
        const val MAX_STABLE_CONSECUTIVE_JUMP_PCT = 10.0 // > 10 % entre ciclos consecutivos degrada a inestable
    }
}
