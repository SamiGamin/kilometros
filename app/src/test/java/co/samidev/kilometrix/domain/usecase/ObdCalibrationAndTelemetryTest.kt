package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelDetails
import co.samidev.kilometrix.domain.model.FuelUnit
import co.samidev.kilometrix.domain.model.ShiftStatus
import co.samidev.kilometrix.domain.model.VehicleExpense
import co.samidev.kilometrix.domain.model.WorkShift
import co.samidev.kilometrix.domain.util.FuelUnits
import co.samidev.kilometrix.obd.ble.ElmResponseParser
import co.samidev.kilometrix.obd.domain.EcuDiagnosticInfo
import co.samidev.kilometrix.obd.domain.EngineOperatingRegime
import co.samidev.kilometrix.obd.domain.FuelSensorMode
import co.samidev.kilometrix.obd.domain.TripTelemetryAccumulator
import co.samidev.kilometrix.obd.domain.VehicleProfile
import co.samidev.kilometrix.obd.domain.VehicleTelemetry
import org.junit.Assert.*
import org.junit.Test

class ObdCalibrationAndTelemetryTest {

    private val calibrationUseCase = CalculateObdCalibrationUseCase()

    private fun refuel(
        id: String,
        odometer: Int,
        gallons: Double,
        isFullTank: Boolean = false,
        isReserve: Boolean = false,
        dateMs: Long
    ) = VehicleExpense(
        id = id,
        vehicleId = "v1",
        type = ExpenseType.FUEL,
        amount = gallons * 15000.0,
        date = dateMs,
        fuelDetails = FuelDetails(
            odometerAtRefuel = odometer,
            gallons = gallons,
            liters = FuelUnits.gallonsToLiters(gallons),
            pricePerGallon = 15000.0,
            pricePerLiter = FuelUnits.pricePerGallonToPricePerLiter(15000.0),
            enteredUnit = FuelUnit.GALLON,
            enteredQuantity = gallons,
            isFullTank = isFullTank,
            isReserve = isReserve,
            isPartial = !isFullTank && !isReserve
        )
    )

    // ── PUNTO 1: EWMA, Banda de Confianza y Degradación por Inestabilidad (>10%) ──

    @Test
    fun `k_fuel calcula EWMA ponderado por km y degrada estabilidad cuando dos ciclos difieren mas del 10 por ciento`() {
        val f1 = refuel("f1", odometer = 10000, gallons = 10.0, isFullTank = true, dateMs = 1_000L)
        val f2 = refuel("f2", odometer = 10400, gallons = 10.0, isFullTank = true, dateMs = 10_000L) // 37.854 L reales
        val f3 = refuel("f3", odometer = 10800, gallons = 10.0, isFullTank = true, dateMs = 20_000L) // 37.854 L reales

        // Ciclo 1: OBD midió 37.854 L -> k_fuel_1 = 1.00
        val shift1 = WorkShift(
            id = "s1",
            initialOdometer = 10000,
            finalOdometer = 10400,
            startTime = 2_000L,
            endTime = 9_000L,
            status = ShiftStatus.ENDED,
            obdDistanceKm = 400.0,
            obdFuelLitersRaw = 37.85411784,
            obdCoveragePct = 98.0,
            obdMaxGapSec = 5.0,
            obdValid = true,
            obdConnectedAtStart = true,
            obdConnectedAtEnd = true,
            obdSampleCount = 500
        )

        // Ciclo 2: OBD midió 30.0 L -> k_fuel_2 = 1.2618 (>10% diferencia respecto a 1.00)
        val shift2 = WorkShift(
            id = "s2",
            initialOdometer = 10400,
            finalOdometer = 10800,
            startTime = 11_000L,
            endTime = 19_000L,
            status = ShiftStatus.ENDED,
            obdDistanceKm = 400.0,
            obdFuelLitersRaw = 30.0,
            obdCoveragePct = 96.0,
            obdMaxGapSec = 12.0,
            obdValid = true,
            obdConnectedAtStart = true,
            obdConnectedAtEnd = true,
            obdSampleCount = 500
        )

        val result = calibrationUseCase(listOf(f1, f2, f3), listOf(shift1, shift2))

        assertTrue(result.isFuelCalibrated)
        assertEquals(2, result.calibratedFuelCycles)
        assertFalse("Dos ciclos con salto >10% deben marcar isFuelStable = false", result.isFuelStable)
        assertTrue(result.obdFuelFactorStdev > 0.0)
        assertTrue(result.formatFuelSourceBadge(hasLiveObdFuel = true, hasPumpCycleCalibration = true).startsWith("🟠"))
        assertTrue(
            "prioridad 2 (semilla) debe usar 🟡, no 🟠",
            CalculateObdCalibrationUseCase().let { uc ->
                uc(emptyList(), emptyList()).formatFuelSourceBadge(
                    hasLiveObdFuel = true, hasPumpCycleCalibration = false
                ).startsWith("🟡")
            }
        )
    }

    // ── PUNTO 2: Regla de Cobertura explícita excluye turnos con gap > 60s o cobertura < 90% ──

    @Test
    fun `turno con cobertura menor a 90 por ciento o gap mayor a 60s no contamina k_fuel`() {
        val f1 = refuel("f1", odometer = 20000, gallons = 10.0, isFullTank = true, dateMs = 1_000L)
        val f2 = refuel("f2", odometer = 20400, gallons = 10.0, isFullTank = true, dateMs = 10_000L)

        val invalidShift = WorkShift(
            id = "s_invalid",
            initialOdometer = 20000,
            finalOdometer = 20400,
            startTime = 2_000L,
            endTime = 9_000L,
            status = ShiftStatus.ENDED,
            obdDistanceKm = 320.0,
            obdFuelLitersRaw = 25.0,
            obdCoveragePct = 78.0, // < 90%
            obdMaxGapSec = 95.0,   // > 60s
            obdGapCount = 2,
            obdValid = false,
            obdConnectedAtStart = true,
            obdConnectedAtEnd = true,
            obdSampleCount = 300
        )

        val result = calibrationUseCase(listOf(f1, f2), listOf(invalidShift))

        assertFalse("Ciclo con turno inválido no debe calibrar k_fuel", result.isFuelCalibrated)
        assertEquals(1, result.incompleteCoverageCycles)
        assertEquals(1.0, result.kFuel, 0.001)
    }

    // ── PUNTO 3: Ralentí y Corte por Desaceleración (DFCO) en VehicleTelemetry ──

    @Test
    fun `corte de inyeccion en desaceleracion devuelve 0 litros por hora y ralenti reduce VE`() {
        val profile = VehicleProfile()

        // DFCO: 2200 RPM, MAP 22 kPa (vacío alto con mariposa cerrada), bajando de 60 a 55 km/h
        val dfcoFlow = VehicleTelemetry.estimateFuelFlowLitersPerHour(
            rpm = 2200,
            mapKpa = 22,
            intakeAirTemperatureCelsius = 30,
            profile = profile,
            currentSpeedKph = 55,
            previousSpeedKph = 60
        )
        assertEquals(0.0, dfcoFlow!!, 0.0001)

        // Ralentí: 780 RPM, MAP 34 kPa, 0 km/h -> aplica IDLE_VE_MULTIPLIER (0.68)
        val idleRegime = VehicleTelemetry.detectOperatingRegime(780, 34, currentSpeedKph = 0, previousSpeedKph = 0)
        assertEquals(EngineOperatingRegime.IDLE, idleRegime)

        val idleFlow = VehicleTelemetry.estimateFuelFlowLitersPerHour(
            rpm = 780,
            mapKpa = 34,
            intakeAirTemperatureCelsius = 35,
            profile = profile,
            currentSpeedKph = 0,
            previousSpeedKph = 0
        )!!
        val unadjustedFlow = VehicleTelemetry.estimateFuelFlowLitersPerHour(
            rpm = 780,
            mapKpa = 34,
            intakeAirTemperatureCelsius = 35,
            profile = profile,
            currentSpeedKph = 20, // Fuera de ralentí
            previousSpeedKph = 20
        )!!
        assertEquals(unadjustedFlow * VehicleTelemetry.IDLE_VE_MULTIPLIER, idleFlow, 0.01)
    }

    // ── PUNTO 5: Unidades Canónicas (Litros <-> Galones) ────────────────────────

    @Test
    fun `conversiones canonicas entre litros y galones mantienen consistencia exacta`() {
        val tankLiters = 52.0
        val tankGallons = FuelUnits.litersToGallons(tankLiters)
        assertEquals(13.7369, tankGallons, 0.001)
        assertEquals(tankLiters, FuelUnits.gallonsToLiters(tankGallons), 0.0001)

        val pricePerGal = 15800.0
        val pricePerLiter = FuelUnits.pricePerGallonToPricePerLiter(pricePerGal)
        // Costo de 10 L debe ser idéntico calculado en Litros o en Galones
        val costInLiters = 10.0 * pricePerLiter
        val costInGallons = FuelUnits.litersToGallons(10.0) * pricePerGal
        assertEquals(costInLiters, costInGallons, 0.01)
    }

    // ── PUNTO 6: Trapecio descarta dt > 2s y contabiliza gaps sin integrar basura ──

    @Test
    fun `acumulador descarta dt mayor a 2 segundos y registra gap sin inflar combustible`() {
        val acc = TripTelemetryAccumulator()
        val profile = VehicleProfile()
        acc.startNewTurn(profile, nowElapsedRealtimeMs = 1_000L, connectedAtStart = true)

        // Muestra 1 en t = 1000 ms
        val diag1 = EcuDiagnosticInfo(
            ecuVerified = true,
            liveRpm = 2000,
            liveSpeedKph = 50,
            liveMapKpa = 60,
            liveIntakeAirTemperatureCelsius = 30,
            rpmSampleElapsedRealtimeMs = 1_000L,
            speedSampleElapsedRealtimeMs = 1_000L,
            mapSampleElapsedRealtimeMs = 1_000L,
            fuelSensorMode = FuelSensorMode.SPEED_DENSITY_MAP
        )
        acc.update(diag1, profile, nowElapsedRealtimeMs = 1_000L)

        // Muestra 2 en t = 2000 ms (dt = 1.0 s <= 2.0 s -> se integra normalmente)
        val diag2 = diag1.copy(
            rpmSampleElapsedRealtimeMs = 2_000L,
            speedSampleElapsedRealtimeMs = 2_000L,
            mapSampleElapsedRealtimeMs = 2_000L
        )
        val afterValidStep = acc.update(diag2, profile, nowElapsedRealtimeMs = 2_000L)
        assertTrue(afterValidStep.fuelConsumedLiters > 0.0)
        assertEquals(0, afterValidStep.gapCount)

        // Muestra 3 en t = 12_000 ms (dt = 10.0 s > 2.0 s -> GAP! No debe integrar esos 10 s)
        val diag3 = diag1.copy(
            rpmSampleElapsedRealtimeMs = 12_000L,
            speedSampleElapsedRealtimeMs = 12_000L,
            mapSampleElapsedRealtimeMs = 12_000L
        )
        val afterGap = acc.update(diag3, profile, nowElapsedRealtimeMs = 12_000L)
        assertEquals("El combustible no debe incrementarse durante un gap > 2s", afterValidStep.fuelConsumedLiters, afterGap.fuelConsumedLiters, 0.00001)
        assertEquals(1, afterGap.gapCount)
        assertEquals(10.0, afterGap.maxGapSec, 0.01)
        assertTrue(afterGap.coveragePct < 20.0)
    }

    // ── PUNTO 7: k_dist por turno con mediana robusta y alerta > 5 % ────────────

    @Test
    fun `k_dist usa mediana por turno descartando outlier de digitacion y alerta si supera 5 por ciento`() {
        val s1 = WorkShift(id = "1", initialOdometer = 1000, finalOdometer = 1108, obdDistanceKm = 100.0, obdValid = true, obdCoveragePct = 98.0, obdConnectedAtStart = true, obdConnectedAtEnd = true, obdSampleCount = 100)
        val s2 = WorkShift(id = "2", initialOdometer = 1108, finalOdometer = 1215, obdDistanceKm = 100.0, obdValid = true, obdCoveragePct = 97.0, obdConnectedAtStart = true, obdConnectedAtEnd = true, obdSampleCount = 100)
        // Error de digitación (1.60x): debe rechazarse como outlier
        val sOutlier = WorkShift(id = "3", initialOdometer = 1215, finalOdometer = 1375, obdDistanceKm = 100.0, obdValid = true, obdCoveragePct = 99.0, obdConnectedAtStart = true, obdConnectedAtEnd = true, obdSampleCount = 100)

        val result = calibrationUseCase(emptyList(), listOf(s1, s2, sOutlier))

        assertTrue(result.isDistCalibrated)
        assertEquals(1, result.outlierDistShifts)
        assertTrue("Desviación de ~7.5% debe activar kDistDriftAlert", result.kDistDriftAlert)
        assertNotNull(result.kDistDriftMessage)
    }

    // ── PUNTO 8: Probe de PIDs extendidos (0120, 0110 MAF, 012F nivel tanque) ───

    @Test
    fun `parser decodifica MAF 0110 y nivel de tanque 012F ignorando 0xFF`() {
        // 41 10 01 F4 -> (500 / 100.0) = 5.00 g/s
        assertEquals(5.0, ElmResponseParser.parseMafGramsPerSecond("41 10 01 F4 >")!!, 0.001)

        // 41 2F 80 -> 128 / 255 = 50.196%
        assertEquals(128.0 * 100.0 / 255.0, ElmResponseParser.parseFuelLevelPct("41 2F 80 >")!!, 0.01)

        // 41 2F FF -> 255 = N/A (null)
        assertNull(ElmResponseParser.parseFuelLevelPct("41 2F FF >"))
    }
}
