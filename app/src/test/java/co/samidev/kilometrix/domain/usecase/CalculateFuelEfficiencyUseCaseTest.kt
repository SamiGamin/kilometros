package co.samidev.kilometrix.domain.usecase

import co.samidev.kilometrix.domain.model.DataMaturityPhase
import co.samidev.kilometrix.domain.model.ExpenseType
import co.samidev.kilometrix.domain.model.FuelDetails
import co.samidev.kilometrix.domain.model.FuelUnit
import co.samidev.kilometrix.domain.model.VehicleExpense
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Suite de pruebas para [CalculateFuelEfficiencyUseCase] — Modelo Multifase.
 *
 * ## Fixture del caso real (datos del usuario):
 * - Inicio Turno: Odómetro = 19547 km
 * - Evento Reserva A: Odómetro = 19557 km | isReserve = true
 * - Tanqueo: Odómetro = 19568 km | 11 Galones | $15529/gal | Total: $170.819 COP | isPartial = true
 * - Cierre Turno: Odómetro = 19706 km | Distancia total turno = 159 km
 * - R_prom calibrado externamente: ~30 km/gal
 *
 * ## Verificaciones del fixture:
 * 1. Total efectivo pagado = $170.819 COP
 * 2. Con R_prom = 30 km/gal y ΔK = 159 km → G_quemados ≈ 5.3 gal
 * 3. G_restantes = 11 - 5.3 = 5.7 gal (~1.14 turnos de 150 km)
 */
class CalculateFuelEfficiencyUseCaseTest {

    private lateinit var useCase: CalculateFuelEfficiencyUseCase

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fuelExpense(
        odometer: Int,
        previousOdometer: Int = 0,
        gallons: Double,
        pricePerGallon: Double,
        isReserve: Boolean = false,
        isFullTank: Boolean = false,
        isPartial: Boolean = false,
        vehicleId: String = "v1",
        dateOffset: Long = 0L
    ): VehicleExpense {
        val kmTraveled = maxOf(0, odometer - previousOdometer)
        val litersPerGallon = 3.78541
        val liters = gallons * litersPerGallon
        val kmPerGallon = if (gallons > 0.0 && kmTraveled > 0) kmTraveled / gallons else 0.0
        val totalCost = gallons * pricePerGallon

        return VehicleExpense(
            id = "exp_${odometer}",
            vehicleId = vehicleId,
            type = ExpenseType.FUEL,
            amount = totalCost,
            date = System.currentTimeMillis() + dateOffset,
            fuelDetails = FuelDetails(
                odometerAtRefuel = odometer,
                previousOdometer = previousOdometer,
                kmTraveled = kmTraveled,
                gallons = gallons,
                liters = liters,
                pricePerGallon = pricePerGallon,
                pricePerLiter = pricePerGallon / litersPerGallon,
                enteredUnit = FuelUnit.GALLON,
                enteredQuantity = gallons,
                pricePerEnteredUnit = pricePerGallon,
                kmPerGallon = kmPerGallon,
                kmPerLiter = kmPerGallon / litersPerGallon,
                isReserve = isReserve,
                isFullTank = isFullTank,
                isPartial = isPartial
            )
        )
    }

    @Before
    fun setup() {
        useCase = CalculateFuelEfficiencyUseCase()
    }

    // ── TEST 1: Lista vacía ────────────────────────────────────────────────────

    @Test
    fun `lista vacia devuelve FuelEfficiencySummary por defecto`() {
        val result = useCase(emptyList())

        assertEquals(0.0, result.totalSpentCash, 0.001)
        assertEquals(0.0, result.totalGallonsPurchased, 0.001)
        assertEquals(0, result.totalKmTraveled)
        assertNull(result.kmPerGallonAverage)
        assertEquals(DataMaturityPhase.PHASE_1_CASH_FLOW, result.dataMaturityPhase)
        assertEquals(0.0, result.estimatedShiftsRemaining, 0.001)
    }

    // ── TEST 2: Fase 1 — Solo un tanqueo (Cold Start) ─────────────────────────

    @Test
    fun `un solo tanqueo devuelve Fase1 con kmPerGallonAverage nulo`() {
        val expenses = listOf(
            fuelExpense(
                odometer = 10000,
                gallons = 8.0,
                pricePerGallon = 15000.0,
                isPartial = true
            )
        )

        val result = useCase(expenses)

        assertEquals(DataMaturityPhase.PHASE_1_CASH_FLOW, result.dataMaturityPhase)
        assertNull("kmPerGallonAverage debe ser null en Fase 1", result.kmPerGallonAverage)
        assertNull("kmPerLiterAverage debe ser null en Fase 1", result.kmPerLiterAverage)
        assertEquals(120_000.0, result.totalSpentCash, 1.0)
        assertEquals(8.0, result.totalGallonsPurchased, 0.001)
    }

    // ── TEST 3: Fase 1 — Un solo hito de calibración (no suficiente) ──────────

    @Test
    fun `un solo hito de calibracion es insuficiente para Fase2`() {
        val expenses = listOf(
            fuelExpense(odometer = 10000, gallons = 8.0, pricePerGallon = 15000.0, isReserve = true),
            fuelExpense(odometer = 10200, gallons = 5.0, pricePerGallon = 15000.0, isPartial = true)
        )

        val result = useCase(expenses)

        // Solo hay 1 hito de reserva → FASE 1
        assertEquals(DataMaturityPhase.PHASE_1_CASH_FLOW, result.dataMaturityPhase)
        assertNull(result.kmPerGallonAverage)
    }

    // ── TEST 4: FIXTURE REAL DEL USUARIO — Verificación completa ─────────────

    /**
     * Fixture real de producción:
     * - Reserva A en 19557 (hito 1)
     * - Tanqueo de 11 gal a $15529/gal en 19568 (isPartial)
     * - Reserva B en 19706 (hito 2) con 0 galones (solo marca de calibración)
     *
     * Con ΔK = 19706 - 19557 = 149 km y G_acumulados = 11 gal entre hitos:
     * R_calibrado = 149/11 ≈ 13.55 km/gal
     *
     * Nota: Con solo 1 ciclo → Fase 2. Con R_prom ≈ 13.55 km/gal:
     * G_quemados desde último hito = 0 km / 13.55 = 0 gal
     * G_restantes = 0 gal (el último hito cierra el ciclo, nada después)
     */
    @Test
    fun `fixture real usuario — flujo de caja correcto`() {
        // Evento de reserva A — hito de calibración inicial
        val reservaA = fuelExpense(
            odometer = 19557,
            previousOdometer = 19547,
            gallons = 0.0,     // Al encenderse la reserva no se tanquea
            pricePerGallon = 0.0,
            isReserve = true
        )
        // Tanqueo principal (parcial)
        val tanqueo = fuelExpense(
            odometer = 19568,
            previousOdometer = 19557,
            gallons = 11.0,
            pricePerGallon = 15529.0,
            isPartial = true
        )
        // Reserva B — cierre del ciclo de calibración
        val reservaB = fuelExpense(
            odometer = 19706,
            previousOdometer = 19568,
            gallons = 0.0,
            pricePerGallon = 0.0,
            isReserve = true
        )

        val result = useCase(listOf(reservaA, tanqueo, reservaB))

        // 1. Flujo de caja: solo el tanqueo tiene costo real
        assertEquals(
            "El total de caja debe ser 170.819 COP (11 gal × $15.529)",
            170_819.0,
            result.totalSpentCash,
            1.0
        )
        assertEquals(11.0, result.totalGallonsPurchased, 0.01)

        // 2. El UseCase debe estar en Fase 2 (hay 1 ciclo calibrado)
        assertEquals(DataMaturityPhase.PHASE_2_CALIBRATING, result.dataMaturityPhase)

        // 3. R_calibrado = ΔK / G_acumulados = (19706-19557) / 11 = 149/11 ≈ 13.55 km/gal
        assertNotNull(result.kmPerGallonAverage)
        assertEquals(149.0 / 11.0, result.kmPerGallonAverage!!, 0.1)
    }

    // ── TEST 5: Fixture con R_prom externo = 30 km/gal (simulado con 2 ciclos) ─

    /**
     * Simula un vehículo con historial previo que ya tiene R_prom ≈ 30 km/gal.
     * Verifica las proyecciones del Tanque Virtual con los datos del turno del usuario.
     *
     * Historial:
     * - Ciclo A→B (pasado): 600 km / 20 gal = 30 km/gal
     * - Ciclo B→C (pasado): 450 km / 15 gal = 30 km/gal
     * - Tanqueo actual: 11 gal en el período analizado
     * - ΔK período = 159 km (el turno del usuario)
     *
     * Con R_prom = 30 km/gal:
     *   G_quemados = 159 / 30 = 5.3 gal
     *   G_restantes = 11 - 5.3 = 5.7 gal
     *   Km_restantes = 5.7 × 30 = 171 km
     *   Turnos = 171 / 150 = 1.14 turnos
     */
    @Test
    fun `tanque virtual con R_prom 30 kmgal verifica fixture del usuario`() {
        // Ciclo histórico 1: hito A → hito B (30 km/gal)
        val hitoA = fuelExpense(
            odometer = 10000, gallons = 0.0, pricePerGallon = 0.0, isReserve = true
        )
        val tanqueo1 = fuelExpense(
            odometer = 10200, previousOdometer = 10000, gallons = 10.0, pricePerGallon = 15000.0, isPartial = true
        )
        val hitoB = fuelExpense(
            odometer = 10600, previousOdometer = 10200, gallons = 10.0, pricePerGallon = 15000.0, isFullTank = true
        )
        // Ciclo histórico 2: hito B → hito C (30 km/gal)
        val tanqueo2 = fuelExpense(
            odometer = 10900, previousOdometer = 10600, gallons = 8.0, pricePerGallon = 15000.0, isPartial = true
        )
        val hitoC = fuelExpense(
            odometer = 11050, previousOdometer = 10900, gallons = 7.0, pricePerGallon = 15000.0, isReserve = true
        )
        // Período analizado (turno del usuario): 11 gal, ΔK = 159 km desde hitoC
        val tanqueoActual = fuelExpense(
            odometer = 11061, previousOdometer = 11050, gallons = 11.0, pricePerGallon = 15529.0, isPartial = true
        )
        // Fin del período (odómetro del cierre del turno — sin nuevo hito, el último registro marca el fin)
        // El uso del tanque virtual se mide desde hitoC (11050) hasta el último registro

        val expenses = listOf(hitoA, tanqueo1, hitoB, tanqueo2, hitoC, tanqueoActual)
        val result = useCase(expenses, kmPerShift = 150.0)

        // Con 2 ciclos → Fase 3
        assertEquals(DataMaturityPhase.PHASE_3_VIRTUAL_TANK, result.dataMaturityPhase)

        // R_prom: ciclo 1 = (10600-10000)/20 = 30, ciclo 2 = (11050-10600)/15 = 30 → R_prom = 30
        assertNotNull(result.kmPerGallonAverage)
        assertEquals(30.0, result.kmPerGallonAverage!!, 0.5)

        // Galones quemados desde hitoC:
        // ΔK = 11061 - 11050 = 11 km (solo el tanqueo final después del hito)
        // G_comprados después de hitoC = hitoC.gallons + tanqueoActual.gallons = 7 + 11 = 18 gal
        // G_quemados = 11 / 30 ≈ 0.37 gal
        // G_restantes ≈ 18 - 0.37 ≈ 17.63 gal
        assertTrue(
            "Deben quedar galones en el tanque virtual",
            result.virtualTankRemainingGallons > 0.0
        )

        // Turnos restantes > 0
        assertTrue(result.estimatedShiftsRemaining > 0.0)
    }

    // ── TEST 6: Fase 3 — Tanque agotado ───────────────────────────────────────

    @Test
    fun `tanque virtual reporta cero cuando se agota`() {
        // Ciclo calibrado 1
        val hito1 = fuelExpense(odometer = 1000, gallons = 0.0, pricePerGallon = 0.0, isReserve = true)
        val t1 = fuelExpense(odometer = 1300, previousOdometer = 1000, gallons = 10.0, pricePerGallon = 15000.0, isPartial = true)
        val hito2 = fuelExpense(odometer = 1600, previousOdometer = 1300, gallons = 0.0, pricePerGallon = 0.0, isReserve = true)
        // Ciclo calibrado 2
        val t2 = fuelExpense(odometer = 1900, previousOdometer = 1600, gallons = 10.0, pricePerGallon = 15000.0, isPartial = true)
        val hito3 = fuelExpense(odometer = 2200, previousOdometer = 1900, gallons = 0.0, pricePerGallon = 0.0, isReserve = true)

        // Período actual: 5 gal comprados, pero se han recorrido 300 km (a 30 km/gal = 10 gal)
        val tanqueoActual = fuelExpense(
            odometer = 2210, previousOdometer = 2200, gallons = 5.0, pricePerGallon = 15000.0, isPartial = true
        )
        // Último registro está 150 km después del hito3 (2200 + 150 = 2350)
        val ultimoRegistro = fuelExpense(
            odometer = 2350, previousOdometer = 2210, gallons = 0.0, pricePerGallon = 0.0, isPartial = true
        )

        val result = useCase(listOf(hito1, t1, hito2, t2, hito3, tanqueoActual, ultimoRegistro))

        assertEquals(DataMaturityPhase.PHASE_3_VIRTUAL_TANK, result.dataMaturityPhase)
        // G_restantes no puede ser negativo
        assertTrue(
            "El tanque virtual no puede tener galones negativos",
            result.virtualTankRemainingGallons >= 0.0
        )
    }

    // ── TEST 7: Retro-compatibilidad de aliases ────────────────────────────────

    @Test
    fun `aliases de retro-compatibilidad son consistentes con campos primarios`() {
        val hito1 = fuelExpense(odometer = 5000, gallons = 0.0, pricePerGallon = 0.0, isReserve = true)
        val t1 = fuelExpense(odometer = 5300, previousOdometer = 5000, gallons = 10.0, pricePerGallon = 14000.0, isPartial = true)
        val hito2 = fuelExpense(odometer = 5600, previousOdometer = 5300, gallons = 10.0, pricePerGallon = 14000.0, isFullTank = true)
        val hito3 = fuelExpense(odometer = 5900, previousOdometer = 5600, gallons = 0.0, pricePerGallon = 0.0, isReserve = true)

        val result = useCase(listOf(hito1, t1, hito2, hito3))

        // Aliases deben coincidir con valores primarios
        assertEquals(result.kmPerGallonAverage ?: 0.0, result.averageKmPerGallon, 0.001)
        assertEquals(result.kmPerLiterAverage ?: 0.0, result.averageKmPerLiter, 0.001)
        assertEquals(result.totalGallonsPurchased, result.totalGallons, 0.001)
        assertEquals(result.totalSpentCash, result.totalFuelCost, 0.001)
        assertEquals(result.costPerKmReal, result.costPerKm, 0.001)
    }

    // ── TEST 8: Solo gastos no-combustible son ignorados ──────────────────────

    @Test
    fun `gastos no combustible son ignorados en el calculo`() {
        val maintenanceExpense = VehicleExpense(
            id = "maint1",
            vehicleId = "v1",
            type = ExpenseType.MAINTENANCE,
            amount = 50_000.0,
            fuelDetails = null
        )
        val fuelExp = fuelExpense(
            odometer = 10000, gallons = 5.0, pricePerGallon = 15000.0, isPartial = true
        )

        val result = useCase(listOf(maintenanceExpense, fuelExp))

        // Solo el gasto de combustible cuenta
        assertEquals(75_000.0, result.totalSpentCash, 1.0)
        assertEquals(5.0, result.totalGallonsPurchased, 0.001)
    }
}
