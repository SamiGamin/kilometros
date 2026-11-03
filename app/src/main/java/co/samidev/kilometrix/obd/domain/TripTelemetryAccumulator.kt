package co.samidev.kilometrix.obd.domain

/**
 * Integra muestras OBD-II frescas y monotónicas para un turno activo.
 *
 * Garantías de precisión y cobertura (Puntos 2, 3, 6, 10):
 * - Trapecio monotónico con descarte estricto de intervalos `dt > 2.0 s` (`MAX_INTEGRATION_GAP_MS = 2000L`).
 * - Cuando ocurre un intervalo `dt > 2.0 s` (o se notifica un gap externo tras recuperación de crash/reinicio),
 *   NO se integra combustible ni distancia sobre el hueco: se incrementa `gapCount`, se registra `maxGapSec`
 *   y `unmeasuredGapSec`, y se reinicia la base del trapecio en la nueva muestra.
 * - Contabiliza `sampleCount`, `meanDtMs`, `idleSec`, `cutoffSec` y `coveragePct`.
 */
class TripTelemetryAccumulator {

    var metrics: TripMetrics = TripMetrics()
        private set

    private var previousSpeedKph: Int? = null
    private var previousSpeedSampleElapsedRealtimeMs: Long? = null
    private var previousFuelFlowLitersPerHour: Double? = null
    private var previousFuelEstimateElapsedRealtimeMs: Long? = null
    private var previousFuelModelProfile: VehicleProfile? = null
    private var activeTurnProfile: VehicleProfile? = null
    private var turnFuelPriceCopPerLiter = 0.0
    private var minimumAcceptedSampleElapsedRealtimeMs: Long? = null
    private var initialOdometerKm: Double? = null

    fun startNewTurn(
        profile: VehicleProfile,
        nowElapsedRealtimeMs: Long,
        allowFuelEstimation: Boolean = true,
        initialOdometer: Double? = null,
        connectedAtStart: Boolean = true
    ): TripMetrics {
        resetBaselines()
        minimumAcceptedSampleElapsedRealtimeMs = nowElapsedRealtimeMs
        activeTurnProfile = profile
        initialOdometerKm = initialOdometer?.takeIf { it.isFinite() && it > 0.0 }
        turnFuelPriceCopPerLiter = profile.fuelPriceCopPerLiter.takeIf {
            allowFuelEstimation && it.isFinite() && it >= 0.0
        } ?: 0.0
        metrics = TripMetrics(
            isTurnActive = true,
            fuelEstimationEnabledForTurn = allowFuelEstimation,
            initialOdometerKm = initialOdometerKm,
            currentOdometerKm = initialOdometerKm,
            distanceSource = if (initialOdometerKm != null) DistanceSource.ODOMETER_PID else DistanceSource.SPEED_INTEGRATION,
            connectedAtStart = connectedAtStart,
            connectedAtEnd = connectedAtStart,
            coveragePct = if (connectedAtStart) 100.0 else 0.0
        )
        return metrics
    }

    fun stopTurn(connectedAtEnd: Boolean = true): TripMetrics {
        metrics = metrics.copy(
            isTurnActive = false,
            connectedAtEnd = connectedAtEnd
        )
        activeTurnProfile = null
        initialOdometerKm = null
        resetBaselines()
        return metrics
    }

    fun restoreActiveTurn(
        restoredMetrics: TripMetrics,
        profile: VehicleProfile,
        allowFuelEstimation: Boolean,
        minimumSampleElapsedRealtimeMs: Long
    ): TripMetrics {
        resetBaselines()
        minimumAcceptedSampleElapsedRealtimeMs = minimumSampleElapsedRealtimeMs
        activeTurnProfile = profile
        initialOdometerKm = restoredMetrics.initialOdometerKm?.takeIf { it.isFinite() && it > 0.0 }
        turnFuelPriceCopPerLiter = profile.fuelPriceCopPerLiter.takeIf {
            allowFuelEstimation && it.isFinite() && it >= 0.0
        } ?: 0.0
        metrics = restoredMetrics.copy(
            isTurnActive = true,
            fuelEstimationEnabledForTurn = allowFuelEstimation,
            instantaneousFuelFlowLitersPerHour = null,
            lastSpeedSampleElapsedRealtimeMs = null,
            lastFuelEstimateElapsedRealtimeMs = null
        )
        return metrics
    }

    /**
     * Registra un hueco (gap) medido por reloj de pared tras muerte del proceso o reinicio del teléfono (Punto 6).
     */
    fun recordExternalGap(gapSeconds: Double): TripMetrics {
        if (!gapSeconds.isFinite() || gapSeconds <= MAX_INTEGRATION_GAP_MS / 1000.0) {
            return metrics
        }
        val newGapCount = metrics.gapCount + 1
        val newMaxGap = maxOf(metrics.maxGapSec, gapSeconds)
        val newUnmeasured = metrics.unmeasuredGapSec + gapSeconds
        val totalTime = metrics.activeMonitoredSec + newUnmeasured
        val newCoverage = if (totalTime > 0.0) {
            ((metrics.activeMonitoredSec / totalTime) * 100.0).coerceIn(0.0, 100.0)
        } else 0.0
        metrics = metrics.copy(
            gapCount = newGapCount,
            maxGapSec = newMaxGap,
            unmeasuredGapSec = newUnmeasured,
            coveragePct = newCoverage
        )
        return metrics
    }

    fun resetSampleBaselines(
        minimumSampleElapsedRealtimeMs: Long? = null
    ): TripMetrics {
        val previousMinimum = minimumAcceptedSampleElapsedRealtimeMs
        resetBaselines()
        minimumAcceptedSampleElapsedRealtimeMs =
            minimumSampleElapsedRealtimeMs ?: previousMinimum
        metrics = metrics.copy(
            instantaneousFuelFlowLitersPerHour = null,
            lastSpeedSampleElapsedRealtimeMs = null,
            lastFuelEstimateElapsedRealtimeMs = null
        )
        return metrics
    }

    fun update(
        diagnosticInfo: EcuDiagnosticInfo,
        profile: VehicleProfile,
        nowElapsedRealtimeMs: Long,
        allowFuelEstimation: Boolean = true
    ): TripMetrics {
        if (!diagnosticInfo.ecuVerified) {
            return resetSampleBaselines(nowElapsedRealtimeMs)
        }

        val calculationProfile = if (metrics.isTurnActive) activeTurnProfile ?: profile else profile

        val liveOdo = diagnosticInfo.liveOdometerKm?.takeIf { it.isFinite() && it > 0.0 }
        if (metrics.isTurnActive && initialOdometerKm == null && liveOdo != null) {
            initialOdometerKm = liveOdo
        }

        val priorSpeedForRegime = previousSpeedKph
        val distanceKilometers: Double
        val distanceSource: DistanceSource
        if (metrics.isTurnActive && initialOdometerKm != null && liveOdo != null && liveOdo >= initialOdometerKm!!) {
            distanceKilometers = liveOdo - initialOdometerKm!!
            distanceSource = when (diagnosticInfo.distancePidType) {
                DistancePidType.PID_01A6 -> DistanceSource.ODOMETER_01A6
                DistancePidType.PID_0131 -> DistanceSource.ODOMETER_0131
                else -> DistanceSource.ODOMETER_PID
            }
        } else {
            distanceKilometers = updateDistance(diagnosticInfo, nowElapsedRealtimeMs)
            distanceSource = if (initialOdometerKm != null && liveOdo != null) {
                DistanceSource.ODOMETER_PID
            } else {
                DistanceSource.SPEED_INTEGRATION
            }
        }

        val fuelEstimationAllowed = if (metrics.isTurnActive) {
            metrics.fuelEstimationEnabledForTurn == true &&
                diagnosticInfo.fuelSensorMode != FuelSensorMode.UNSUPPORTED
        } else {
            allowFuelEstimation && diagnosticInfo.fuelSensorMode != FuelSensorMode.UNSUPPORTED
        }
        if (!fuelEstimationAllowed) {
            resetFuelBaseline()
            metrics = metrics.copy(
                distanceKilometers = distanceKilometers,
                instantaneousFuelFlowLitersPerHour = null,
                averageKilometersPerLiter = null,
                lastSpeedSampleElapsedRealtimeMs = diagnosticInfo.speedSampleElapsedRealtimeMs,
                lastFuelEstimateElapsedRealtimeMs = null,
                initialOdometerKm = initialOdometerKm,
                currentOdometerKm = liveOdo ?: metrics.currentOdometerKm ?: initialOdometerKm,
                distanceSource = distanceSource,
                connectedAtEnd = true
            )
            return metrics
        }

        val instantaneousFuelFlow = updateFuelFlow(
            diagnosticInfo = diagnosticInfo,
            profile = calculationProfile,
            nowElapsedRealtimeMs = nowElapsedRealtimeMs,
            priorSpeedKph = priorSpeedForRegime
        )

        val fuelConsumed = metrics.fuelConsumedLiters
        val averageKilometersPerLiter = if (fuelConsumed > 0.0) {
            distanceKilometers / fuelConsumed
        } else {
            null
        }
        metrics = metrics.copy(
            distanceKilometers = distanceKilometers,
            fuelConsumedLiters = fuelConsumed,
            instantaneousFuelFlowLitersPerHour = instantaneousFuelFlow,
            averageKilometersPerLiter = averageKilometersPerLiter,
            estimatedCostCop = fuelConsumed * turnFuelPriceCopPerLiter,
            lastSpeedSampleElapsedRealtimeMs = diagnosticInfo.speedSampleElapsedRealtimeMs,
            lastFuelEstimateElapsedRealtimeMs = previousFuelEstimateElapsedRealtimeMs,
            initialOdometerKm = initialOdometerKm,
            currentOdometerKm = liveOdo ?: metrics.currentOdometerKm ?: initialOdometerKm,
            distanceSource = distanceSource,
            connectedAtEnd = true
        )
        return metrics
    }

    private fun updateDistance(
        diagnosticInfo: EcuDiagnosticInfo,
        nowElapsedRealtimeMs: Long
    ): Double {
        val speedKph = diagnosticInfo.liveSpeedKph
        val sampleTimeMs = diagnosticInfo.speedSampleElapsedRealtimeMs
        if (speedKph == null || sampleTimeMs == null || speedKph !in MIN_SPEED_KPH..MAX_SPEED_KPH) {
            resetSpeedBaseline()
            return metrics.distanceKilometers
        }
        if (!isFreshSample(sampleTimeMs, nowElapsedRealtimeMs, MAX_SPEED_SAMPLE_AGE_MS)) {
            resetSpeedBaseline()
            return metrics.distanceKilometers
        }

        val priorTimeMs = previousSpeedSampleElapsedRealtimeMs
        val priorSpeedKph = previousSpeedKph
        if (priorTimeMs != null && sampleTimeMs <= priorTimeMs) {
            return metrics.distanceKilometers
        }

        var nextDistance = metrics.distanceKilometers
        if (metrics.isTurnActive && priorTimeMs != null && priorSpeedKph != null) {
            val elapsedMs = sampleTimeMs - priorTimeMs
            if (elapsedMs in 1L..MAX_INTEGRATION_GAP_MS) {
                val meanSpeedKph = (priorSpeedKph + speedKph) / 2.0
                nextDistance += meanSpeedKph * elapsedMs / MILLIS_PER_HOUR
            }
        }

        previousSpeedKph = speedKph
        previousSpeedSampleElapsedRealtimeMs = sampleTimeMs
        return nextDistance.takeIf { it.isFinite() && it >= 0.0 } ?: metrics.distanceKilometers
    }

    private fun updateFuelFlow(
        diagnosticInfo: EcuDiagnosticInfo,
        profile: VehicleProfile,
        nowElapsedRealtimeMs: Long,
        priorSpeedKph: Int?
    ): Double? {
        if (profile != previousFuelModelProfile) {
            previousFuelFlowLitersPerHour = null
            previousFuelEstimateElapsedRealtimeMs = null
            previousFuelModelProfile = profile
        }

        val rpm = diagnosticInfo.liveRpm
        val rpmTimeMs = diagnosticInfo.rpmSampleElapsedRealtimeMs
        if (!profile.isValid || rpm == null || rpmTimeMs == null) {
            resetFuelBaseline()
            return null
        }

        val newestInputTimeMs: Long
        val flowLitersPerHour: Double?
        val regime: EngineOperatingRegime

        when (diagnosticInfo.fuelSensorMode) {
            FuelSensorMode.SPEED_DENSITY_MAP -> {
                val mapKpa = diagnosticInfo.liveMapKpa
                val mapTimeMs = diagnosticInfo.mapSampleElapsedRealtimeMs
                if (mapKpa == null || mapTimeMs == null) {
                    resetFuelBaseline()
                    return null
                }
                val dynamicInputsFresh = listOf(rpmTimeMs, mapTimeMs).all { sampleTimeMs ->
                    isFreshSample(sampleTimeMs, nowElapsedRealtimeMs, MAX_DYNAMIC_FUEL_INPUT_AGE_MS)
                }
                if (!dynamicInputsFresh) {
                    resetFuelBaseline()
                    return null
                }
                newestInputTimeMs = maxOf(rpmTimeMs, mapTimeMs)
                val intakeTemperatureCelsius =
                    diagnosticInfo.liveIntakeAirTemperatureCelsius ?: DEFAULT_FALLBACK_IAT_CELSIUS
                regime = VehicleTelemetry.detectOperatingRegime(
                    rpm = rpm,
                    mapKpa = mapKpa,
                    currentSpeedKph = diagnosticInfo.liveSpeedKph,
                    previousSpeedKph = priorSpeedKph
                )
                flowLitersPerHour = VehicleTelemetry.estimateFuelFlowLitersPerHour(
                    rpm = rpm,
                    mapKpa = mapKpa,
                    intakeAirTemperatureCelsius = intakeTemperatureCelsius,
                    profile = profile,
                    currentSpeedKph = diagnosticInfo.liveSpeedKph,
                    previousSpeedKph = priorSpeedKph
                )
            }

            FuelSensorMode.MAF_DIRECT -> {
                val mafGps = diagnosticInfo.liveMafGramsPerSec
                val mafTimeMs = diagnosticInfo.mafSampleElapsedRealtimeMs
                if (mafGps == null || mafTimeMs == null) {
                    resetFuelBaseline()
                    return null
                }
                val dynamicInputsFresh = listOf(rpmTimeMs, mafTimeMs).all { sampleTimeMs ->
                    isFreshSample(sampleTimeMs, nowElapsedRealtimeMs, MAX_DYNAMIC_FUEL_INPUT_AGE_MS)
                }
                if (!dynamicInputsFresh) {
                    resetFuelBaseline()
                    return null
                }
                newestInputTimeMs = maxOf(rpmTimeMs, mafTimeMs)
                regime = if ((diagnosticInfo.liveSpeedKph ?: 0) <= VehicleTelemetry.IDLE_MAX_SPEED_KPH &&
                    rpm in VehicleTelemetry.IDLE_MIN_RPM..VehicleTelemetry.IDLE_MAX_RPM
                ) EngineOperatingRegime.IDLE else EngineOperatingRegime.NORMAL_LOAD
                flowLitersPerHour = VehicleTelemetry.estimateFuelFlowFromMafLitersPerHour(
                    mafGramsPerSecond = mafGps,
                    profile = profile,
                    rpm = rpm
                )
            }

            FuelSensorMode.UNSUPPORTED -> {
                resetFuelBaseline()
                return null
            }
        }

        if (flowLitersPerHour == null) {
            resetFuelBaseline()
            return null
        }

        val priorTimeMs = previousFuelEstimateElapsedRealtimeMs
        val priorFlow = previousFuelFlowLitersPerHour
        if (priorTimeMs != null && newestInputTimeMs <= priorTimeMs) {
            return metrics.instantaneousFuelFlowLitersPerHour ?: flowLitersPerHour
        }

        if (metrics.isTurnActive && priorTimeMs != null && priorFlow != null) {
            val elapsedMs = newestInputTimeMs - priorTimeMs
            val dtSec = elapsedMs / 1000.0
            if (elapsedMs in 1L..MAX_INTEGRATION_GAP_MS) {
                val meanFuelFlowLitersPerHour = (priorFlow + flowLitersPerHour) / 2.0
                val fuelIncrement = meanFuelFlowLitersPerHour * elapsedMs / MILLIS_PER_HOUR
                val newFuel = if (fuelIncrement.isFinite() && fuelIncrement >= 0.0) {
                    metrics.fuelConsumedLiters + fuelIncrement
                } else metrics.fuelConsumedLiters

                val newSampleCount = metrics.sampleCount + 1
                val newActiveSec = metrics.activeMonitoredSec + dtSec
                val newMeanDtMs = (newActiveSec * 1000.0) / newSampleCount
                val newIdleSec = if (regime == EngineOperatingRegime.IDLE) metrics.idleSec + dtSec else metrics.idleSec
                val newCutoffSec = if (regime == EngineOperatingRegime.FUEL_CUTOFF) metrics.cutoffSec + dtSec else metrics.cutoffSec
                val totalTime = newActiveSec + metrics.unmeasuredGapSec
                val newCoveragePct = if (totalTime > 0.0) {
                    ((newActiveSec / totalTime) * 100.0).coerceIn(0.0, 100.0)
                } else 100.0

                metrics = metrics.copy(
                    fuelConsumedLiters = newFuel,
                    sampleCount = newSampleCount,
                    meanDtMs = newMeanDtMs,
                    activeMonitoredSec = newActiveSec,
                    coveragePct = newCoveragePct,
                    idleSec = newIdleSec,
                    cutoffSec = newCutoffSec,
                    isFuelCutOffActive = regime == EngineOperatingRegime.FUEL_CUTOFF,
                    isIdleActive = regime == EngineOperatingRegime.IDLE
                )
            } else if (elapsedMs > MAX_INTEGRATION_GAP_MS) {
                // Punto 6: Descartar muestras con dt > 2.0 s para evitar integraciones basura tras gaps
                val newGapCount = metrics.gapCount + 1
                val newMaxGapSec = maxOf(metrics.maxGapSec, dtSec)
                val newUnmeasuredSec = metrics.unmeasuredGapSec + dtSec
                val totalTime = metrics.activeMonitoredSec + newUnmeasuredSec
                val newCoveragePct = if (totalTime > 0.0) {
                    ((metrics.activeMonitoredSec / totalTime) * 100.0).coerceIn(0.0, 100.0)
                } else 0.0
                metrics = metrics.copy(
                    gapCount = newGapCount,
                    maxGapSec = newMaxGapSec,
                    unmeasuredGapSec = newUnmeasuredSec,
                    coveragePct = newCoveragePct,
                    isFuelCutOffActive = regime == EngineOperatingRegime.FUEL_CUTOFF,
                    isIdleActive = regime == EngineOperatingRegime.IDLE
                )
            }
        }

        previousFuelFlowLitersPerHour = flowLitersPerHour
        previousFuelEstimateElapsedRealtimeMs = newestInputTimeMs
        return flowLitersPerHour
    }

    private fun resetBaselines() {
        resetSpeedBaseline()
        resetFuelBaseline()
        previousFuelModelProfile = null
        minimumAcceptedSampleElapsedRealtimeMs = null
    }

    private fun resetSpeedBaseline() {
        previousSpeedKph = null
        previousSpeedSampleElapsedRealtimeMs = null
    }

    private fun resetFuelBaseline() {
        previousFuelFlowLitersPerHour = null
        previousFuelEstimateElapsedRealtimeMs = null
        metrics = metrics.copy(
            instantaneousFuelFlowLitersPerHour = null,
            lastFuelEstimateElapsedRealtimeMs = null,
            isFuelCutOffActive = false,
            isIdleActive = false
        )
    }

    private fun isFresh(
        sampleTimeMs: Long,
        nowElapsedRealtimeMs: Long,
        maxAgeMs: Long
    ): Boolean = sampleTimeMs <= nowElapsedRealtimeMs &&
        nowElapsedRealtimeMs - sampleTimeMs <= maxAgeMs

    private fun isFreshSample(
        sampleTimeMs: Long,
        nowElapsedRealtimeMs: Long,
        maxAgeMs: Long
    ): Boolean = isFresh(sampleTimeMs, nowElapsedRealtimeMs, maxAgeMs) &&
        sampleTimeMs >= (minimumAcceptedSampleElapsedRealtimeMs ?: Long.MIN_VALUE)

    companion object {
        const val MIN_SPEED_KPH = 0
        const val MAX_SPEED_KPH = 255
        const val MAX_SPEED_SAMPLE_AGE_MS = 2_000L
        const val MAX_DYNAMIC_FUEL_INPUT_AGE_MS = 2_000L
        /** Umbral estricto de trapecio (Punto 6): si dt > 2.0 s se registra como gap y no se integra. */
        const val MAX_INTEGRATION_GAP_MS = 2_000L
        const val MILLIS_PER_HOUR = 3_600_000.0
        const val DEFAULT_FALLBACK_IAT_CELSIUS = 25
    }
}
