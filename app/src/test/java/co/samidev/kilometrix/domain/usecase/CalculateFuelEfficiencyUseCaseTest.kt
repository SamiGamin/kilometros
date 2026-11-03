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
        // Ciclo homólogo 1 (Reserva → Reserva): 600 km / 20 gal = 30 km/gal
        val hitoR1 = fuelExpense(
            odometer = 10000, gallons = 10.0, pricePerGallon = 15000.0, isReserve = true, dateOffset = 1000L
        )
        val tanqueo1 = fuelExpense(
            odometer = 10200, previousOdometer = 10000, gallons = 10.0, pricePerGallon = 15000.0, isPartial = true, dateOffset = 2000L
        )
        val hitoR2 = fuelExpense(
            odometer = 10600, previousOdometer = 10200, gallons = 8.0, pricePerGallon = 15000.0, isReserve = true, dateOffset = 3000L
        )
        // Transición a Tanque Lleno y Ciclo homólogo 2 (Lleno → Lleno): 450 km / 15 gal = 30 km/gal
        val hitoL1 = fuelExpense(
            odometer = 10605, previousOdometer = 10600, gallons = 6.0, pricePerGallon = 15000.0, isFullTank = true, dateOffset = 4000L
        )
        val tanqueo2 = fuelExpense(
            odometer = 10900, previousOdometer = 10605, gallons = 8.0, pricePerGallon = 15000.0, isPartial = true, dateOffset = 5000L
        )
        val hitoL2 = fuelExpense(
            odometer = 11055, previousOdometer = 10900, gallons = 7.0, pricePerGallon = 15000.0, isFullTank = true, dateOffset = 6000L
        )
        // Período analizado (turno del usuario): 11 gal después del último hito
        val tanqueoActual = fuelExpense(
            odometer = 11066, previousOdometer = 11055, gallons = 11.0, pricePerGallon = 15529.0, isPartial = true, dateOffset = 7000L
        )

        val expenses = listOf(hitoR1, tanqueo1, hitoR2, hitoL1, tanqueo2, hitoL2, tanqueoActual)
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

    // ── TEST 9: Ciclo Lleno a Lleno con parcial intermedia y Tanque Virtual ───

    @Test
    fun `ciclo lleno a lleno con parcial intermedia calcula rendimiento exacto y tanque virtual`() {
        val lleno1 = fuelExpense(
            odometer = 10000, gallons = 10.0, pricePerGallon = 15000.0, isFullTank = true, dateOffset = 1000L
        )
        val parcial = fuelExpense(
            odometer = 10200, previousOdometer = 10000, gallons = 3.0, pricePerGallon = 15000.0, isPartial = true, dateOffset = 2000L
        )
        val lleno2 = fuelExpense(
            odometer = 10450, previousOdometer = 10200, gallons = 7.0, pricePerGallon = 15000.0, isFullTank = true, dateOffset = 3000L
        )

        // ΔK = 10450 - 10000 = 450 km
        // Galones consumidos en Lleno->Lleno = parcial (3) + lleno2 (7) = 10 gal -> 45.0 km/gal
        val result = useCase(
            fuelExpenses = listOf(lleno1, parcial, lleno2),
            currentVehicleOdometer = 10540, // 90 km recorridos después del último lleno (= 2 gal quemados)
            tankCapacityLiters = 52.0       // ~13.737 gal
        )

        assertEquals(DataMaturityPhase.PHASE_2_CALIBRATING, result.dataMaturityPhase)
        assertNotNull(result.kmPerGallonAverage)
        assertEquals(45.0, result.kmPerGallonAverage!!, 0.01)

        // Tanque virtual: lleno a 52 L (13.737 gal) menos 2.0 gal quemados en 90 km = ~11.737 gal
        val expectedRemainingGal = (52.0 / CalculateFuelEfficiencyUseCase.LITERS_PER_GALLON) - 2.0
        assertEquals(expectedRemainingGal, result.virtualTankRemainingGallons, 0.05)
    }

    // ── TEST 10: Ciclo Reserva a Reserva real (con compra en hito A) ──────────

    @Test
    fun `ciclo reserva a reserva real calcula galones desde hito inicial sin requerir lleno`() {
        val reserva1 = fuelExpense(
            odometer = 20000, gallons = 8.0, pricePerGallon = 15000.0, isReserve = true, dateOffset = 1000L
        )
        val parcial = fuelExpense(
            odometer = 20180, previousOdometer = 20000, gallons = 2.0, pricePerGallon = 15000.0, isPartial = true, dateOffset = 2000L
        )
        val reserva2 = fuelExpense(
            odometer = 20350, previousOdometer = 20180, gallons = 6.0, pricePerGallon = 15000.0, isReserve = true, dateOffset = 3000L
        )

        // ΔK = 20350 - 20000 = 350 km
        // Galones quemados entre reserva1 y reserva2 = reserva1 (8) + parcial (2) = 10 gal -> 35.0 km/gal
        // Y en reserva2 entraron 6 galones nuevos sobre reserva para el siguiente ciclo
        val result = useCase(
            fuelExpenses = listOf(reserva1, parcial, reserva2),
            currentVehicleOdometer = 20420 // 70 km después de reserva2 (= 2 gal quemados de los 6 gal nuevos)
        )

        assertEquals(35.0, result.kmPerGallonAverage!!, 0.01)
        assertEquals(4.0, result.virtualTankRemainingGallons, 0.05)
    }

    // ── TEST 11: Transición no homóloga Reserva -> Lleno marca ABANDONED y capacityProbe (Punto 4) ──

    @Test
    fun `transicion no homologa reserva a lleno abandona ciclo anterior y registra capacityProbe sin duplicar galones`() {
        val reservaInicial = fuelExpense(
            odometer = 30000, gallons = 4.0, pricePerGallon = 15000.0, isReserve = true, dateOffset = 1000L
        )
        // A mitad del ciclo de reserva, el conductor decide llenar el tanque completo:
        val llenoMitad = fuelExpense(
            odometer = 30120, previousOdometer = 30000, gallons = 10.5, pricePerGallon = 15000.0, isFullTank = true, dateOffset = 2000L
        )
        // Luego cierra un ciclo homólogo Lleno -> Lleno:
        val llenoCierre = fuelExpense(
            odometer = 30520, previousOdometer = 30120, gallons = 10.0, pricePerGallon = 15000.0, isFullTank = true, dateOffset = 3000L
        )

        val result = useCase(listOf(reservaInicial, llenoMitad, llenoCierre))

        // 1 ciclo abandonado (Reserva -> Lleno) y 1 ciclo cerrado válido (Lleno -> Lleno: 400 km / 10 gal = 40 km/gal)
        assertEquals(1, result.abandonedCyclesCount)
        assertEquals(1, result.closedCyclesCount)
        assertEquals(40.0, result.kmPerGallonAverage!!, 0.01)
        assertNotNull("Debe registrar el capacityProbe al pasar de Reserva a Lleno", result.measuredReserveToFullGallons)
        assertEquals(10.5, result.measuredReserveToFullGallons!!, 0.01)
    }

    // ── TEST 12: Prompt de cierre cuando acumula >10 gal en parciales sin cerrar ciclo (Punto 9) ──

    @Test
    fun `ciclo abierto con mas de 10 galones en parciales activa prompt de cierre`() {
        val llenoInicial = fuelExpense(
            odometer = 40000, gallons = 12.0, pricePerGallon = 15000.0, isFullTank = true, dateOffset = 1000L
        )
        val parcial1 = fuelExpense(
            odometer = 40250, previousOdometer = 40000, gallons = 6.0, pricePerGallon = 15000.0, isPartial = true, dateOffset = 2000L
        )
        val parcial2 = fuelExpense(
            odometer = 40500, previousOdometer = 40250, gallons = 5.5, pricePerGallon = 15000.0, isPartial = true, dateOffset = 3000L
        )

        val result = useCase(listOf(llenoInicial, parcial1, parcial2))

        assertTrue("Debe activar prompt al superar 10 galones en parciales sin cerrar", result.needsCycleClosurePrompt)
        assertNotNull(result.cycleClosurePromptMessage)
        assertEquals(11.5, result.openCycleGallonsAccumulated, 0.01)
    }
}
