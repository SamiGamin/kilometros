package co.samidev.kilometrix.domain.model

enum class ExpenseType(val label: String, val emoji: String) {
    FUEL("Combustible", "⛽"),
    MAINTENANCE("Mantenimiento", "🔧"),
    TOLL("Peaje", "🛣️"),
    INSURANCE("Seguro", "🛡️"),
    PARKING("Parqueadero", "🅿️"),
    OTHER("Otro", "💳")
}

enum class FuelUnit(val label: String, val shortLabel: String) {
    GALLON("Galones", "gal"),
    LITER("Litros", "L")
}

/**
 * Fase de madurez del cálculo de eficiencia.
 *
 * - PHASE_1_CASH_FLOW: Menos de 2 hitos de calibración. Solo se reporta flujo de caja.
 * - PHASE_2_CALIBRATING: Se detectó al menos 1 ciclo calibrado, pero se sigue acumulando precisión.
 * - PHASE_3_VIRTUAL_TANK: R_prom disponible. Se activa el Tanque Virtual con proyección de autonomía.
 */
enum class DataMaturityPhase {
    PHASE_1_CASH_FLOW,
    PHASE_2_CALIBRATING,
    PHASE_3_VIRTUAL_TANK
}

/**
 * Datos específicos para gastos de combustible.
 *
 * Soporta galones como unidad primaria y litros como secundaria.
 * El odómetro previo es el valor del vehículo ANTES de este llenado.
 *
 * ## Campos de control de ciclo (Modelo Multifase):
 * - [isReserve]: El testigo de reserva estaba encendido al momento del llenado.
 *   Actúa como hito A de calibración del ciclo.
 * - [isFullTank]: Se llenó el tanque completo.
 *   Actúa como hito B de calibración del ciclo (cierre del ciclo anterior).
 * - [isPartial]: Tanqueo intermedio (ni reserva ni lleno). No genera hito de calibración.
 */
data class FuelDetails(
    // ── Odometría ──────────────────────────────────────────────────────────────
    val odometerAtRefuel: Int = 0,          // Odómetro al momento del llenado
    val previousOdometer: Int = 0,          // Odómetro del vehículo antes del llenado
    val kmTraveled: Int = 0,                // odometerAtRefuel - previousOdometer

    // ── Volumen y precio ───────────────────────────────────────────────────────
    val gallons: Double = 0.0,
    val liters: Double = 0.0,               // gallons * 3.78541
    val pricePerGallon: Double = 0.0,
    val pricePerLiter: Double = 0.0,
    val enteredUnit: FuelUnit = FuelUnit.GALLON,
    val enteredQuantity: Double = 0.0,      // Lo que el usuario ingresó
    val pricePerEnteredUnit: Double = 0.0,

    // ── Rendimiento por registro ───────────────────────────────────────────────
    val kmPerGallon: Double = 0.0,          // kmTraveled / gallons (rendimiento principal)
    val kmPerLiter: Double = 0.0,           // kmTraveled / liters (rendimiento secundario)

    // ── 🆕 Control de ciclo (Modelo Multifase) ─────────────────────────────────
    val isReserve: Boolean = false,         // Testigo de reserva encendido → hito de calibración
    val isFullTank: Boolean = false,        // Llenado completo → cierre de ciclo de calibración
    val isPartial: Boolean = false          // Tanqueo parcial (ni reserva ni tanque lleno)
) {
    val litersPer100Km: Double
        get() = if (kmTraveled > 0 && liters > 0) (liters * 100.0) / kmTraveled else if (kmPerLiter > 0) 100.0 / kmPerLiter else 0.0
}

/**
 * Gasto del vehículo. Para combustible, [fuelDetails] contiene
 * toda la info de rendimiento y consumo.
 */
data class VehicleExpense(
    val id: String = "",
    val vehicleId: String = "",
    val type: ExpenseType = ExpenseType.OTHER,
    val amount: Double = 0.0,               // Total pagado (COP)
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val fuelDetails: FuelDetails? = null    // Solo para tipo FUEL
)

/**
 * Resumen de eficiencia de combustible — Modelo Multifase.
 *
 * ## Sección 1: Flujo de Caja (siempre disponible desde el primer registro)
 * ## Sección 2: Eficiencia Operativa (disponible desde Fase 2)
 * ## Sección 3: Tanque Virtual (disponible desde Fase 3)
 * ## Sección 4: Estado de madurez del dato
 *
 * Los alias al final mantienen compatibilidad con código existente.
 */
data class FuelEfficiencySummary(

    // ── 1. FLUJO DE CAJA (Efectivo real pagado en estaciones) ──────────────────
    val totalSpentCash: Double = 0.0,           // Dinero real pagado en estaciones (COP)
    val totalGallonsPurchased: Double = 0.0,    // Volumen total comprado (galones)
    val totalKmTraveled: Int = 0,               // Km totales en el período

    // ── 2. EFICIENCIA OPERATIVA (Calibrada/Amortizada) ─────────────────────────
    val kmPerGallonAverage: Double? = null,     // null = Fase 1, sin calibración aún
    val kmPerLiterAverage: Double? = null,
    val costPerKmReal: Double = 0.0,            // Costo real operativo por km (COP/km)
    val bestKmPerGallon: Double = 0.0,
    val worstKmPerGallon: Double = 0.0,

    // ── 3. TANQUE VIRTUAL (Inventario proyectado para el conductor) ─────────────
    val virtualTankRemainingGallons: Double = 0.0,   // Galones restantes estimados
    val virtualTankRemainingCost: Double = 0.0,      // Valor económico del combustible restante
    val estimatedRemainingAutonomyKm: Double = 0.0,  // Autonomía proyectada en km
    val estimatedShiftsRemaining: Double = 0.0,      // Autonomía en turnos de trabajo

    // ── 4. ESTADO DE MADUREZ ───────────────────────────────────────────────────
    val dataMaturityPhase: DataMaturityPhase = DataMaturityPhase.PHASE_1_CASH_FLOW,
    val fillUpsCount: Int = 0,

    // ── Aliases de retro-compatibilidad ────────────────────────────────────────
    // Permiten que el código existente (TransactionsScreen, etc.) siga compilando
    // sin cambios hasta que se actualice progresivamente.
    val averageKmPerGallon: Double = 0.0,   // = kmPerGallonAverage ?: 0.0
    val averageKmPerLiter: Double = 0.0,    // = kmPerLiterAverage ?: 0.0
    val totalGallons: Double = 0.0,         // alias de totalGallonsPurchased
    val totalLiters: Double = 0.0,          // totalGallonsPurchased * 3.78541
    val totalFuelCost: Double = 0.0,        // alias de totalSpentCash
    val costPerKm: Double = 0.0,            // alias de costPerKmReal
    val totalKmFueled: Int = 0             // alias de totalKmTraveled (retro-compat)
)
