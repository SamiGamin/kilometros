package co.samidev.kilometrix.obd.service

import android.content.Context
import android.os.SystemClock
import co.samidev.kilometrix.obd.domain.ObdFaultType
import co.samidev.kilometrix.obd.domain.ShiftTelemetryState
import co.samidev.kilometrix.obd.domain.TripMetrics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class ObdSessionSnapshot(
    val activeShiftId: String? = null,
    val telemetryState: ShiftTelemetryState = ShiftTelemetryState.AWAITING_CONNECTION,
    val faultType: ObdFaultType = ObdFaultType.NONE,
    val metrics: TripMetrics = TripMetrics(),
    val serviceRunning: Boolean = false,
    val isMonitoring: Boolean = false,
    val polling: Boolean = false,
    val responsesPerSecond: Float = 0f,
    val disconnectionAlert: Boolean = false,
    val alertMessage: String? = null
)

/**
 * Almacén de checkpoints persistentes para recuperación tras muerte de proceso o reinicio del teléfono (Punto 6).
 *
 * Cadencia: cada 30 s o cada 50 muestras (más eventos de inicio, alerta y parada).
 * Guarda tanto `elapsedRealtimeMs` (monotónico) como `wallClockEpochMs` (reloj de pared) para detectar
 * reinicios del teléfono (`nowElapsedRealtimeMs < saved.elapsedRealtimeMs`) y contabilizar el intervalo
 * offline como un gap explícito sin perder el acumulado previo ni integrar basura.
 */
@Singleton
class ObdShiftCheckpointStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("obd_shift_checkpoint", Context.MODE_PRIVATE)

    data class Checkpoint(
        val shiftId: String,
        val distanceKm: Double,
        val fuelLiters: Double,
        val initialOdometerKm: Double?,
        val sampleCount: Int,
        val meanDtMs: Double,
        val gapCount: Int,
        val maxGapSec: Double,
        val activeMonitoredSec: Double,
        val unmeasuredGapSec: Double,
        val coveragePct: Double,
        val idleSec: Double,
        val cutoffSec: Double,
        val connectedAtStart: Boolean,
        val elapsedRealtimeMs: Long,
        val wallClockEpochMs: Long
    )

    fun save(
        shiftId: String,
        metrics: TripMetrics,
        nowElapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
        nowWallClockEpochMs: Long = System.currentTimeMillis()
    ) {
        prefs.edit()
            .putString(KEY_SHIFT_ID, shiftId)
            .putString(KEY_DISTANCE_KM, metrics.distanceKilometers.toString())
            .putString(KEY_FUEL_LITERS, metrics.fuelConsumedLiters.toString())
            .putString(KEY_INITIAL_ODO, metrics.initialOdometerKm?.toString() ?: "")
            .putInt(KEY_SAMPLE_COUNT, metrics.sampleCount)
            .putString(KEY_MEAN_DT_MS, metrics.meanDtMs.toString())
            .putInt(KEY_GAP_COUNT, metrics.gapCount)
            .putString(KEY_MAX_GAP_SEC, metrics.maxGapSec.toString())
            .putString(KEY_ACTIVE_SEC, metrics.activeMonitoredSec.toString())
            .putString(KEY_UNMEASURED_SEC, metrics.unmeasuredGapSec.toString())
            .putString(KEY_COVERAGE_PCT, metrics.coveragePct.toString())
            .putString(KEY_IDLE_SEC, metrics.idleSec.toString())
            .putString(KEY_CUTOFF_SEC, metrics.cutoffSec.toString())
            .putBoolean(KEY_CONNECTED_AT_START, metrics.connectedAtStart)
            .putLong(KEY_ELAPSED_REALTIME_MS, nowElapsedRealtimeMs)
            .putLong(KEY_WALL_CLOCK_MS, nowWallClockEpochMs)
            .apply()
    }

    fun load(shiftId: String? = null): Checkpoint? {
        val savedId = prefs.getString(KEY_SHIFT_ID, null) ?: return null
        if (shiftId != null && savedId != shiftId) return null
        return Checkpoint(
            shiftId = savedId,
            distanceKm = prefs.getString(KEY_DISTANCE_KM, "0.0")?.toDoubleOrNull() ?: 0.0,
            fuelLiters = prefs.getString(KEY_FUEL_LITERS, "0.0")?.toDoubleOrNull() ?: 0.0,
            initialOdometerKm = prefs.getString(KEY_INITIAL_ODO, "")?.toDoubleOrNull(),
            sampleCount = prefs.getInt(KEY_SAMPLE_COUNT, 0),
            meanDtMs = prefs.getString(KEY_MEAN_DT_MS, "0.0")?.toDoubleOrNull() ?: 0.0,
            gapCount = prefs.getInt(KEY_GAP_COUNT, 0),
            maxGapSec = prefs.getString(KEY_MAX_GAP_SEC, "0.0")?.toDoubleOrNull() ?: 0.0,
            activeMonitoredSec = prefs.getString(KEY_ACTIVE_SEC, "0.0")?.toDoubleOrNull() ?: 0.0,
            unmeasuredGapSec = prefs.getString(KEY_UNMEASURED_SEC, "0.0")?.toDoubleOrNull() ?: 0.0,
            coveragePct = prefs.getString(KEY_COVERAGE_PCT, "100.0")?.toDoubleOrNull() ?: 100.0,
            idleSec = prefs.getString(KEY_IDLE_SEC, "0.0")?.toDoubleOrNull() ?: 0.0,
            cutoffSec = prefs.getString(KEY_CUTOFF_SEC, "0.0")?.toDoubleOrNull() ?: 0.0,
            connectedAtStart = prefs.getBoolean(KEY_CONNECTED_AT_START, true),
            elapsedRealtimeMs = prefs.getLong(KEY_ELAPSED_REALTIME_MS, 0L),
            wallClockEpochMs = prefs.getLong(KEY_WALL_CLOCK_MS, 0L)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_SHIFT_ID = "shift_id"
        const val KEY_DISTANCE_KM = "distance_km"
        const val KEY_FUEL_LITERS = "fuel_liters"
        const val KEY_INITIAL_ODO = "initial_odo"
        const val KEY_SAMPLE_COUNT = "sample_count"
        const val KEY_MEAN_DT_MS = "mean_dt_ms"
        const val KEY_GAP_COUNT = "gap_count"
        const val KEY_MAX_GAP_SEC = "max_gap_sec"
        const val KEY_ACTIVE_SEC = "active_sec"
        const val KEY_UNMEASURED_SEC = "unmeasured_sec"
        const val KEY_COVERAGE_PCT = "coverage_pct"
        const val KEY_IDLE_SEC = "idle_sec"
        const val KEY_CUTOFF_SEC = "cutoff_sec"
        const val KEY_CONNECTED_AT_START = "connected_at_start"
        const val KEY_ELAPSED_REALTIME_MS = "elapsed_realtime_ms"
        const val KEY_WALL_CLOCK_MS = "wall_clock_ms"
    }
}

@Singleton
class ObdSessionState @Inject constructor() {
    private val _snapshot = MutableStateFlow(ObdSessionSnapshot())
    val snapshot: StateFlow<ObdSessionSnapshot> = _snapshot.asStateFlow()

    fun beginShift(shiftId: String, metrics: TripMetrics, isConnected: Boolean = true) {
        _snapshot.value = ObdSessionSnapshot(
            activeShiftId = shiftId,
            telemetryState = if (isConnected) ShiftTelemetryState.RUNNING else ShiftTelemetryState.AWAITING_CONNECTION,
            faultType = ObdFaultType.NONE,
            metrics = metrics.copy(isTurnActive = true),
            serviceRunning = true,
            isMonitoring = false,
            disconnectionAlert = false,
            alertMessage = null
        )
    }

    fun updateShiftMetrics(
        shiftId: String,
        metrics: TripMetrics,
        polling: Boolean,
        responsesPerSecond: Float,
        disconnectionAlert: Boolean,
        alertMessage: String? = null,
        faultType: ObdFaultType = ObdFaultType.NONE,
        telemetryState: ShiftTelemetryState = ShiftTelemetryState.RUNNING
    ) {
        _snapshot.update { current ->
            val targetId = current.activeShiftId ?: shiftId
            if (targetId != shiftId) return@update current
            current.copy(
                activeShiftId = shiftId,
                telemetryState = telemetryState,
                faultType = faultType,
                metrics = metrics.copy(isTurnActive = true),
                serviceRunning = true,
                isMonitoring = false,
                polling = polling,
                responsesPerSecond = responsesPerSecond.coerceAtLeast(0f),
                disconnectionAlert = disconnectionAlert,
                alertMessage = alertMessage
            )
        }
    }

    fun updateMonitoring(
        polling: Boolean,
        responsesPerSecond: Float,
        disconnectionAlert: Boolean = false,
        alertMessage: String? = null
    ) {
        _snapshot.update { current ->
            if (current.activeShiftId != null && current.metrics.isTurnActive) return@update current
            current.copy(
                serviceRunning = true,
                isMonitoring = true,
                polling = polling,
                responsesPerSecond = responsesPerSecond.coerceAtLeast(0f),
                disconnectionAlert = disconnectionAlert,
                alertMessage = alertMessage
            )
        }
    }

    fun triggerDisconnectionAlert(
        message: String,
        faultType: ObdFaultType = ObdFaultType.BLE_DISCONNECTED
    ) {
        _snapshot.update { current ->
            val newState = when (faultType) {
                ObdFaultType.BLE_DISCONNECTED -> ShiftTelemetryState.DEGRADED_BLE_LOST
                ObdFaultType.ECU_SILENT -> ShiftTelemetryState.DEGRADED_ECU_SILENT
                ObdFaultType.NONE -> current.telemetryState
            }
            current.copy(
                telemetryState = newState,
                faultType = faultType,
                polling = false,
                responsesPerSecond = 0f,
                disconnectionAlert = true,
                alertMessage = message
            )
        }
    }

    fun clearDisconnectionAlert() {
        _snapshot.update { current ->
            current.copy(
                telemetryState = if (current.activeShiftId != null) ShiftTelemetryState.RUNNING else ShiftTelemetryState.AWAITING_CONNECTION,
                faultType = ObdFaultType.NONE,
                disconnectionAlert = false,
                alertMessage = null
            )
        }
    }

    fun finishShift(): TripMetrics {
        val finalMetrics = _snapshot.value.metrics.copy(isTurnActive = false)
        _snapshot.value = ObdSessionSnapshot(
            telemetryState = ShiftTelemetryState.CLOSING,
            metrics = finalMetrics
        )
        return finalMetrics
    }

    fun serviceStopped() {
        _snapshot.update { current ->
            if (current.metrics.isTurnActive) {
                current.copy(
                    telemetryState = ShiftTelemetryState.DEGRADED_BLE_LOST,
                    faultType = ObdFaultType.BLE_DISCONNECTED,
                    serviceRunning = false,
                    polling = false,
                    responsesPerSecond = 0f,
                    disconnectionAlert = true,
                    alertMessage = "Servicio detenido. Reconecta el adaptador OBD-II."
                )
            } else {
                current.copy(
                    telemetryState = ShiftTelemetryState.AWAITING_CONNECTION,
                    faultType = ObdFaultType.NONE,
                    serviceRunning = false,
                    isMonitoring = false,
                    polling = false,
                    responsesPerSecond = 0f
                )
            }
        }
    }
}
