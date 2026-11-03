package co.samidev.kilometrix.obd.domain

enum class DistancePidType {
    NONE,
    PID_01A6,
    PID_0131
}

enum class DistanceSource {
    SPEED_INTEGRATION,
    ODOMETER_PID,
    ODOMETER_01A6,
    ODOMETER_0131
}

/**
 * Modo de sensor utilizado para estimar el flujo de combustible (Punto 8).
 * - SPEED_DENSITY_MAP: 010C (RPM) + 010B (MAP) + 010F (IAT o fallback ambiente).
 * - MAF_DIRECT: 0110 (MAF g/s) cuando la ECU no soporta 010B.
 * - UNSUPPORTED: La ECU no expone ni MAP ni MAF; se deshabilita L_obd y cae a Prioridad 3/4.
 */
enum class FuelSensorMode {
    SPEED_DENSITY_MAP,
    MAF_DIRECT,
    UNSUPPORTED
}

/**
 * Distinción explícita de fallos durante el turno (Punto 2):
 * - BLE_DISCONNECTED: Se cayó el enlace Bluetooth GATT con el adaptador ELM327.
 * - ECU_SILENT: El adaptador BLE responde, pero la ECU lleva >5 lecturas sin responder (NO DATA / contacto apagado).
 */
enum class ObdFaultType {
    NONE,
    BLE_DISCONNECTED,
    ECU_SILENT
}

/**
 * Máquina de estados explícita de la telemetría del turno (Punto 10).
 */
enum class ShiftTelemetryState {
    AWAITING_CONNECTION,
    RUNNING,
    DEGRADED_ECU_SILENT,
    DEGRADED_BLE_LOST,
    PAUSED,
    CLOSING
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val deviceName: String) : ConnectionState
    data class DiscoveringServices(val deviceName: String) : ConnectionState
    data object GattReady : ConnectionState
    data object EnablingNotifications : ConnectionState
    data object InitializingElm : ConnectionState
    data object VerifyingEcu : ConnectionState
    data object Connected : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class ObdDevice(
    val address: String,
    val name: String,
    val rssi: Int
)

data class GattCharacteristicInfo(
    val serviceUuid: String,
    val characteristicUuid: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canWriteWithoutResponse: Boolean,
    val canNotify: Boolean,
    val canIndicate: Boolean
) {
    val id: String get() = "$serviceUuid/$characteristicUuid"
}

data class EcuDiagnosticInfo(
    val elmVersion: String = "",
    val protocol: String = "",
    val liveRpm: Int? = null,
    val liveSpeedKph: Int? = null,
    val rpmSampleElapsedRealtimeMs: Long? = null,
    val speedSampleElapsedRealtimeMs: Long? = null,
    val liveMapKpa: Int? = null,
    val mapSampleElapsedRealtimeMs: Long? = null,
    val liveMafGramsPerSec: Double? = null,
    val mafSampleElapsedRealtimeMs: Long? = null,
    val liveIntakeAirTemperatureCelsius: Int? = null,
    val intakeAirTemperatureSampleElapsedRealtimeMs: Long? = null,
    val liveFuelLevelPct: Double? = null,
    val fuelLevelSampleElapsedRealtimeMs: Long? = null,
    val liveOdometerKm: Double? = null,
    val odometerSampleElapsedRealtimeMs: Long? = null,
    val distancePidType: DistancePidType = DistancePidType.NONE,
    val fuelSensorMode: FuelSensorMode = FuelSensorMode.SPEED_DENSITY_MAP,
    val isIatDegraded: Boolean = false,
    val vin: String? = null,
    val ecuName: String? = null,
    val calibrationId: String? = null,
    val ecuVerified: Boolean = false,
    val supportedPids: Set<Int> = emptySet(),
    val gattCharacteristics: List<GattCharacteristicInfo> = emptyList(),
    val logs: List<String> = emptyList()
)

data class TripMetrics(
    val isTurnActive: Boolean = false,
    val fuelEstimationEnabledForTurn: Boolean? = null,
    val distanceKilometers: Double = 0.0,
    val fuelConsumedLiters: Double = 0.0,
    val instantaneousFuelFlowLitersPerHour: Double? = null,
    val averageKilometersPerLiter: Double? = null,
    val estimatedCostCop: Double = 0.0,
    val lastSpeedSampleElapsedRealtimeMs: Long? = null,
    val lastFuelEstimateElapsedRealtimeMs: Long? = null,
    val initialOdometerKm: Double? = null,
    val currentOdometerKm: Double? = null,
    val distanceSource: DistanceSource = DistanceSource.SPEED_INTEGRATION,
    // ── Cobertura y Diagnóstico del Turno (Puntos 2, 3, 6, 10) ────────────────
    val sampleCount: Int = 0,
    val meanDtMs: Double = 0.0,
    val gapCount: Int = 0,
    val maxGapSec: Double = 0.0,
    val activeMonitoredSec: Double = 0.0,
    val unmeasuredGapSec: Double = 0.0,
    val coveragePct: Double = 100.0,
    val idleSec: Double = 0.0,
    val cutoffSec: Double = 0.0,
    val connectedAtStart: Boolean = true,
    val connectedAtEnd: Boolean = true,
    val isFuelCutOffActive: Boolean = false,
    val isIdleActive: Boolean = false
) {
    /**
     * Regla de cobertura explícita (Punto 2):
     * Un turno es válido para calibración si:
     * 1. obdCoveragePct >= 90 % del tiempo del turno
     * 2. Ningún gap > 60 s (maxGapSec <= 60.0)
     * 3. BLE + ECU conectados al inicio y al cierre del turno
     */
    fun isValidForCalibration(
        minCoveragePct: Double = MIN_CALIBRATION_COVERAGE_PCT,
        maxAllowedGapSec: Double = MAX_ALLOWED_GAP_SEC
    ): Boolean = sampleCount > 0 &&
        coveragePct >= minCoveragePct &&
        maxGapSec <= maxAllowedGapSec &&
        connectedAtStart &&
        connectedAtEnd

    companion object {
        const val MIN_CALIBRATION_COVERAGE_PCT = 90.0
        const val MAX_ALLOWED_GAP_SEC = 60.0
    }
}

data class VehicleProfile(
    val engineDisplacementLiters: Double = DEFAULT_ENGINE_DISPLACEMENT_LITERS,
    val volumetricEfficiency: Double = DEFAULT_VOLUMETRIC_EFFICIENCY,
    val stoichiometricAirFuelRatio: Double = DEFAULT_STOICHIOMETRIC_AFR,
    val fuelDensityKgPerLiter: Double = DEFAULT_FUEL_DENSITY_KG_PER_LITER,
    val tankCapacityLiters: Double = DEFAULT_TANK_CAPACITY_LITERS,
    val fuelPriceCopPerLiter: Double = DEFAULT_FUEL_PRICE_COP_PER_LITER
) {
    val isValid: Boolean
        get() = validationError() == null

    fun validationError(): String? = when {
        !engineDisplacementLiters.isFinite() ||
            engineDisplacementLiters !in MIN_ENGINE_DISPLACEMENT_LITERS..MAX_ENGINE_DISPLACEMENT_LITERS ->
            "La cilindrada debe estar entre 0,5 y 10 L."

        !volumetricEfficiency.isFinite() ||
            volumetricEfficiency !in MIN_VOLUMETRIC_EFFICIENCY..MAX_VOLUMETRIC_EFFICIENCY ->
            "La eficiencia volumétrica debe estar entre 0,10 y 1,50."

        !stoichiometricAirFuelRatio.isFinite() ||
            stoichiometricAirFuelRatio !in MIN_AFR..MAX_AFR ->
            "La relación aire/combustible debe estar entre 5 y 30."

        !fuelDensityKgPerLiter.isFinite() ||
            fuelDensityKgPerLiter !in MIN_FUEL_DENSITY..MAX_FUEL_DENSITY ->
            "La densidad del combustible debe estar entre 0,5 y 1,5 kg/L."

        !tankCapacityLiters.isFinite() ||
            tankCapacityLiters !in MIN_TANK_CAPACITY_LITERS..MAX_TANK_CAPACITY_LITERS ->
            "La capacidad del tanque debe estar entre 1 y 200 L."

        !fuelPriceCopPerLiter.isFinite() ||
            fuelPriceCopPerLiter !in MIN_FUEL_PRICE_COP_PER_LITER..MAX_FUEL_PRICE_COP_PER_LITER ->
            "El precio debe ser un valor entre 0 y 2.000.000 COP/L."

        else -> null
    }

    companion object {
        const val DEFAULT_ENGINE_DISPLACEMENT_LITERS = 1.598 // Polo Track 1.6L MSI EA211
        const val DEFAULT_VOLUMETRIC_EFFICIENCY = 0.85       // Semilla inicial VE (sujeta a no-linealidad por carga)
        const val DEFAULT_STOICHIOMETRIC_AFR = 14.7
        const val DEFAULT_FUEL_DENSITY_KG_PER_LITER = 0.745
        const val DEFAULT_TANK_CAPACITY_LITERS = 52.0
        const val DEFAULT_FUEL_PRICE_COP_PER_LITER = 0.0

        const val MIN_ENGINE_DISPLACEMENT_LITERS = 0.5
        const val MAX_ENGINE_DISPLACEMENT_LITERS = 10.0
        const val MIN_VOLUMETRIC_EFFICIENCY = 0.1
        const val MAX_VOLUMETRIC_EFFICIENCY = 1.5
        const val MIN_AFR = 5.0
        const val MAX_AFR = 30.0
        const val MIN_FUEL_DENSITY = 0.5
        const val MAX_FUEL_DENSITY = 1.5
        const val MIN_TANK_CAPACITY_LITERS = 1.0
        const val MAX_TANK_CAPACITY_LITERS = 200.0
        const val MIN_FUEL_PRICE_COP_PER_LITER = 0.0
        const val MAX_FUEL_PRICE_COP_PER_LITER = 2_000_000.0
    }
}
