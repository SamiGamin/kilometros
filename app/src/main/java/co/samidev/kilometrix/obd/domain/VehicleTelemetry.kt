package co.samidev.kilometrix.obd.domain

enum class EngineOperatingRegime {
    NORMAL_LOAD,
    IDLE,
    FUEL_CUTOFF
}

/**
 * Cálculo Mode 01 Speed-Density y MAF directo para motor de 4 tiempos.
 *
 * ## Limitaciones del modelo VE y correcciones de régimen (Puntos 1 y 3):
 * - `VE = 0.85` es una **semilla nominal** a carga media, no una constante física invariante.
 *   La eficiencia volumétrica real varía de forma no lineal con RPM, presión del múltiple (MAP),
 *   temperatura y traslape valvular.
 * - **Ralentí (`IDLE`):** Con mariposa casi cerrada (`speed <= 2 km/h`, `RPM in 500..1050`, `MAP <= 45 kPa`),
 *   el llenado volumétrico real cae drásticamente respecto a carga media. Se aplica el factor
 *   correctivo `IDLE_VE_FACTOR = 0.68` para evitar sobreestimación sistemática en tráfico urbano.
 * - **Corte de inyección por desaceleración (`FUEL_CUTOFF` / DFCO):** Cuando el vehículo desacelera
 *   engranado con mariposa cerrada (`RPM >= 1350`, `MAP <= 26 kPa`, `speed >= 15 km/h` y velocidad
 *   decreciente o constante en vacío), la ECU corta los inyectores (`L/h = 0.0`), aunque el motor
 *   siga bombeando aire.
 * - El factor `k_fuel` calibrado por ciclo absorbe el sesgo promedio del patrón de conducción del ciclo,
 *   pero no elimina la varianza instantánea entre ciudad densa y carretera.
 */
object VehicleTelemetry {

    fun detectOperatingRegime(
        rpm: Int,
        mapKpa: Int?,
        currentSpeedKph: Int? = null,
        previousSpeedKph: Int? = null
    ): EngineOperatingRegime {
        if (rpm <= 0) return EngineOperatingRegime.FUEL_CUTOFF
        if (mapKpa != null) {
            val speed = currentSpeedKph ?: 0
            val prevSpeed = previousSpeedKph ?: speed
            val isDeceleratingOrCoasting = speed <= prevSpeed
            if (rpm >= CUTOFF_MIN_RPM && mapKpa <= CUTOFF_MAX_MAP_KPA && speed >= CUTOFF_MIN_SPEED_KPH && isDeceleratingOrCoasting) {
                return EngineOperatingRegime.FUEL_CUTOFF
            }
            if (speed <= IDLE_MAX_SPEED_KPH && rpm in IDLE_MIN_RPM..IDLE_MAX_RPM && mapKpa <= IDLE_MAX_MAP_KPA) {
                return EngineOperatingRegime.IDLE
            }
        }
        return EngineOperatingRegime.NORMAL_LOAD
    }

    fun estimateAirMassFlowGramsPerSecond(
        rpm: Int,
        mapKpa: Int,
        intakeAirTemperatureCelsius: Int,
        profile: VehicleProfile,
        regime: EngineOperatingRegime = EngineOperatingRegime.NORMAL_LOAD
    ): Double? {
        if (!profile.isValid) return null
        if (rpm !in MIN_RPM..MAX_RPM) return null
        if (mapKpa !in MIN_MAP_KPA..MAX_MAP_KPA) return null
        if (intakeAirTemperatureCelsius !in MIN_IAT_CELSIUS..MAX_IAT_CELSIUS) return null
        if (regime == EngineOperatingRegime.FUEL_CUTOFF) return 0.0

        val intakeTemperatureKelvin = intakeAirTemperatureCelsius + CELSIUS_TO_KELVIN
        if (!intakeTemperatureKelvin.isFinite() || intakeTemperatureKelvin <= 0.0) return null

        val effectiveVe = when (regime) {
            EngineOperatingRegime.IDLE -> profile.volumetricEfficiency * IDLE_VE_MULTIPLIER
            EngineOperatingRegime.NORMAL_LOAD -> profile.volumetricEfficiency
            EngineOperatingRegime.FUEL_CUTOFF -> 0.0
        }

        val airDensityKgPerCubicMeter =
            (mapKpa * PASCALS_PER_KILOPASCAL) / (AIR_SPECIFIC_GAS_CONSTANT * intakeTemperatureKelvin)
        val intakeVolumeCubicMetersPerMinute =
            (profile.engineDisplacementLiters / LITERS_PER_CUBIC_METER) *
                (rpm / FOUR_STROKE_REVOLUTIONS_PER_CYCLE) *
                effectiveVe
        val massFlowGramsPerSecond =
            airDensityKgPerCubicMeter * intakeVolumeCubicMetersPerMinute * GRAMS_PER_KILOGRAM /
                SECONDS_PER_MINUTE

        return massFlowGramsPerSecond.takeIf { it.isFinite() && it >= 0.0 }
    }

    fun estimateFuelFlowLitersPerHour(
        rpm: Int,
        mapKpa: Int,
        intakeAirTemperatureCelsius: Int,
        profile: VehicleProfile,
        currentSpeedKph: Int? = null,
        previousSpeedKph: Int? = null
    ): Double? {
        if (!profile.isValid) return null
        val regime = detectOperatingRegime(
            rpm = rpm,
            mapKpa = mapKpa,
            currentSpeedKph = currentSpeedKph,
            previousSpeedKph = previousSpeedKph
        )
        if (regime == EngineOperatingRegime.FUEL_CUTOFF) return 0.0

        val airMassFlow = estimateAirMassFlowGramsPerSecond(
            rpm = rpm,
            mapKpa = mapKpa,
            intakeAirTemperatureCelsius = intakeAirTemperatureCelsius,
            profile = profile,
            regime = regime
        ) ?: return null

        val fuelFlowLitersPerHour = airMassFlow /
            profile.stoichiometricAirFuelRatio * SECONDS_PER_HOUR /
            (GRAMS_PER_KILOGRAM * profile.fuelDensityKgPerLiter)
        return fuelFlowLitersPerHour.takeIf { it.isFinite() && it >= 0.0 }
    }

    /**
     * Fallback directo por sensor MAF (PID 0110) en gramos/segundo (Punto 8).
     */
    fun estimateFuelFlowFromMafLitersPerHour(
        mafGramsPerSecond: Double,
        profile: VehicleProfile,
        rpm: Int? = null
    ): Double? {
        if (!profile.isValid) return null
        if (!mafGramsPerSecond.isFinite() || mafGramsPerSecond < 0.0 || mafGramsPerSecond > MAX_MAF_GRAMS_PER_SEC) {
            return null
        }
        if (rpm != null && rpm <= 0) return 0.0
        val fuelFlowLitersPerHour = mafGramsPerSecond /
            profile.stoichiometricAirFuelRatio * SECONDS_PER_HOUR /
            (GRAMS_PER_KILOGRAM * profile.fuelDensityKgPerLiter)
        return fuelFlowLitersPerHour.takeIf { it.isFinite() && it >= 0.0 }
    }

    private const val AIR_SPECIFIC_GAS_CONSTANT = 287.05
    private const val PASCALS_PER_KILOPASCAL = 1_000.0
    private const val LITERS_PER_CUBIC_METER = 1_000.0
    private const val GRAMS_PER_KILOGRAM = 1_000.0
    private const val SECONDS_PER_MINUTE = 60.0
    private const val SECONDS_PER_HOUR = 3_600.0
    private const val CELSIUS_TO_KELVIN = 273.15
    private const val FOUR_STROKE_REVOLUTIONS_PER_CYCLE = 2.0
    private const val MIN_RPM = 0
    private const val MAX_RPM = 16_383
    private const val MIN_MAP_KPA = 1
    private const val MAX_MAP_KPA = 255
    private const val MIN_IAT_CELSIUS = -40
    private const val MAX_IAT_CELSIUS = 215
    private const val MAX_MAF_GRAMS_PER_SEC = 655.35

    // Umbrales físicos de régimen (Punto 3)
    const val IDLE_MIN_RPM = 500
    const val IDLE_MAX_RPM = 1_050
    const val IDLE_MAX_MAP_KPA = 45
    const val IDLE_MAX_SPEED_KPH = 2
    const val IDLE_VE_MULTIPLIER = 0.68

    const val CUTOFF_MIN_RPM = 1_350
    const val CUTOFF_MAX_MAP_KPA = 26
    const val CUTOFF_MIN_SPEED_KPH = 15
}
