package co.samidev.kilometrix.obd.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import co.samidev.kilometrix.MainActivity
import co.samidev.kilometrix.R
import co.samidev.kilometrix.obd.data.ObdRepository
import co.samidev.kilometrix.obd.domain.ConnectionState
import co.samidev.kilometrix.obd.domain.ObdFaultType
import co.samidev.kilometrix.obd.domain.ShiftTelemetryState
import co.samidev.kilometrix.obd.domain.TripMetrics
import co.samidev.kilometrix.obd.domain.TripTelemetryAccumulator
import co.samidev.kilometrix.obd.domain.VehicleProfile
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObdServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun startShiftTelemetry(shiftId: String, initialOdometerKm: Int, pricePerLiterCop: Double) {
        val intent = Intent(context, ObdTripForegroundService::class.java).apply {
            action = ObdTripForegroundService.ACTION_START_SHIFT
            putExtra(ObdTripForegroundService.EXTRA_SHIFT_ID, shiftId)
            putExtra(ObdTripForegroundService.EXTRA_INITIAL_ODO, initialOdometerKm.toDouble())
            putExtra(ObdTripForegroundService.EXTRA_PRICE_PER_LITER, pricePerLiterCop)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun startMonitoring() {
        val intent = Intent(context, ObdTripForegroundService::class.java).apply {
            action = ObdTripForegroundService.ACTION_START_MONITORING
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopShiftTelemetry() {
        val intent = Intent(context, ObdTripForegroundService::class.java).apply {
            action = ObdTripForegroundService.ACTION_STOP_SHIFT
        }
        context.startService(intent)
    }
}

@AndroidEntryPoint
class ObdTripForegroundService : Service() {

    @Inject lateinit var obdRepository: ObdRepository
    @Inject lateinit var sessionState: ObdSessionState
    @Inject lateinit var checkpointStore: ObdShiftCheckpointStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val accumulator = TripTelemetryAccumulator()
    private var pollingJob: Job? = null
    private var connectionWatchJob: Job? = null
    private var activeShiftId: String? = null
    private var activeProfile = VehicleProfile()
    private var lastCheckpointElapsedMs: Long = 0L
    private var samplesSinceLastCheckpoint: Int = 0

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        observeConnectionDrops()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SHIFT -> {
                val shiftId = intent.getStringExtra(EXTRA_SHIFT_ID) ?: "shift_active"
                val initOdo = intent.getDoubleExtra(EXTRA_INITIAL_ODO, 0.0)
                val pricePerLiter = intent.getDoubleExtra(EXTRA_PRICE_PER_LITER, 0.0)
                activeProfile = VehicleProfile(fuelPriceCopPerLiter = pricePerLiter)
                startOrRestoreShift(shiftId, initOdo)
            }
            ACTION_START_MONITORING -> {
                startForegroundCompat(buildNotification("Monitoreo OBD-II activo", isAlert = false))
                startPollingLoop()
            }
            ACTION_STOP_SHIFT -> {
                stopShiftAndService()
                return START_NOT_STICKY
            }
            else -> {
                // Recuperación automática tras muerte del proceso o reinicio del dispositivo (Punto 6)
                val restored = checkpointStore.load()
                if (restored != null) {
                    startOrRestoreShift(restored.shiftId, restored.initialOdometerKm ?: 0.0)
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun startOrRestoreShift(shiftId: String, initialOdometerKm: Double) {
        activeShiftId = shiftId
        val existingCheckpoint = checkpointStore.load(shiftId)
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val nowWallMs = System.currentTimeMillis()
        val isCurrentlyConnected = obdRepository.connectionState.value is ConnectionState.Connected &&
            obdRepository.diagnosticInfo.value.ecuVerified

        val initialMetrics = if (existingCheckpoint != null &&
            (existingCheckpoint.sampleCount > 0 || existingCheckpoint.distanceKm > 0.0 || existingCheckpoint.fuelLiters > 0.0)
        ) {
            accumulator.restoreActiveTurn(
                restoredMetrics = TripMetrics(
                    isTurnActive = true,
                    distanceKilometers = existingCheckpoint.distanceKm,
                    fuelConsumedLiters = existingCheckpoint.fuelLiters,
                    initialOdometerKm = existingCheckpoint.initialOdometerKm ?: initialOdometerKm,
                    sampleCount = existingCheckpoint.sampleCount,
                    meanDtMs = existingCheckpoint.meanDtMs,
                    gapCount = existingCheckpoint.gapCount,
                    maxGapSec = existingCheckpoint.maxGapSec,
                    activeMonitoredSec = existingCheckpoint.activeMonitoredSec,
                    unmeasuredGapSec = existingCheckpoint.unmeasuredGapSec,
                    coveragePct = existingCheckpoint.coveragePct,
                    idleSec = existingCheckpoint.idleSec,
                    cutoffSec = existingCheckpoint.cutoffSec,
                    connectedAtStart = existingCheckpoint.connectedAtStart,
                    connectedAtEnd = isCurrentlyConnected
                ),
                profile = activeProfile,
                allowFuelEstimation = true,
                minimumSampleElapsedRealtimeMs = nowElapsedMs
            )

            // Algoritmo de reanudación tras muerte o reboot (Punto 6):
            // Si el reloj monotónico se reinició (nowElapsedMs < existingCheckpoint.elapsedRealtimeMs)
            // o el reloj de pared avanzó > 2 s, registramos el intervalo offline como un gap explícito
            val rebootDetected = existingCheckpoint.elapsedRealtimeMs > 0L &&
                nowElapsedMs < existingCheckpoint.elapsedRealtimeMs
            val wallDeltaSec = if (existingCheckpoint.wallClockEpochMs > 0L && nowWallMs > existingCheckpoint.wallClockEpochMs) {
                (nowWallMs - existingCheckpoint.wallClockEpochMs) / 1000.0
            } else 0.0

            val offlineGapSec = when {
                wallDeltaSec > 2.0 -> wallDeltaSec
                rebootDetected -> 5.0 // Sanity check si el reloj de pared no estaba sincronizado tras reboot
                else -> 0.0
            }
            if (offlineGapSec > 2.0) {
                Log.i(TAG, "Reanudando turno $shiftId tras gap offline de ${"%.1f".format(offlineGapSec)} s (reboot=$rebootDetected)")
                accumulator.recordExternalGap(offlineGapSec)
            } else {
                accumulator.metrics
            }
        } else {
            accumulator.startNewTurn(
                profile = activeProfile,
                nowElapsedRealtimeMs = nowElapsedMs,
                allowFuelEstimation = true,
                initialOdometer = initialOdometerKm.takeIf { it > 0.0 },
                connectedAtStart = isCurrentlyConnected
            )
        }

        lastCheckpointElapsedMs = nowElapsedMs
        samplesSinceLastCheckpoint = 0
        checkpointStore.save(shiftId, initialMetrics, nowElapsedMs, nowWallMs)
        sessionState.beginShift(shiftId, initialMetrics, isConnected = isCurrentlyConnected)
        startForegroundCompat(
            buildNotification(
                "Recorrido activo · ${String.format(Locale.ROOT, "%.1f", initialMetrics.distanceKilometers)} km",
                isAlert = false
            )
        )
        startPollingLoop()
    }

    private fun observeConnectionDrops() {
        connectionWatchJob?.cancel()
        connectionWatchJob = serviceScope.launch {
            obdRepository.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        // Al reconectar el adaptador, limpiamos la alerta y retomamos muestreo sin perder acumulados
                        accumulator.resetSampleBaselines(SystemClock.elapsedRealtime())
                        sessionState.clearDisconnectionAlert()
                        if (activeShiftId != null && (pollingJob == null || pollingJob?.isActive != true)) {
                            startPollingLoop()
                        }
                    }
                    is ConnectionState.Error -> {
                        if (activeShiftId != null) {
                            saveImmediateCheckpoint()
                            val msg = "🔌 BLE caído: ${state.message} Reconecta el adaptador físico."
                            sessionState.triggerDisconnectionAlert(msg, ObdFaultType.BLE_DISCONNECTED)
                            updateNotification(msg, isAlert = true)
                        }
                    }
                    is ConnectionState.Disconnected -> {
                        if (activeShiftId != null) {
                            saveImmediateCheckpoint()
                            val msg = "🔌 Adaptador BLE desconectado durante el turno. Reconéctalo al puerto OBD-II."
                            sessionState.triggerDisconnectionAlert(msg, ObdFaultType.BLE_DISCONNECTED)
                            updateNotification(msg, isAlert = true)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun startPollingLoop() {
        if (pollingJob?.isActive == true) return
        pollingJob = serviceScope.launch {
            var consecutiveEcuFailures = 0
            var windowStartMs = SystemClock.elapsedRealtime()
            var validResponsesInWindow = 0
            var currentRps = 0f
            var disconnectGapStartMs: Long? = null

            while (isActive) {
                val connState = obdRepository.connectionState.value
                val diag = obdRepository.diagnosticInfo.value
                val nowLoopMs = SystemClock.elapsedRealtime()

                if (connState !is ConnectionState.Connected || !diag.ecuVerified) {
                    if (disconnectGapStartMs == null) {
                        disconnectGapStartMs = nowLoopMs
                    }
                    if (activeShiftId != null) {
                        val faultType = if (connState is ConnectionState.Connected) {
                            ObdFaultType.ECU_SILENT
                        } else {
                            ObdFaultType.BLE_DISCONNECTED
                        }
                        val alertMsg = if (faultType == ObdFaultType.ECU_SILENT) {
                            "🚗 Adaptador OK, pero la ECU está muda (NO DATA). Verifica que el contacto esté encendido."
                        } else {
                            "🔌 Enlace BLE caído con el adaptador OBD-II. Reconecta para continuar midiendo."
                        }
                        sessionState.triggerDisconnectionAlert(alertMsg, faultType)
                        updateNotification(alertMsg, isAlert = true)
                    }
                    delay(1000L)
                    continue
                }

                // Si veníamos de una caída de conexión o ECU muda, registrar el gap transcurrido
                if (disconnectGapStartMs != null) {
                    val gapSec = (nowLoopMs - disconnectGapStartMs) / 1000.0
                    if (gapSec > 2.0) {
                        accumulator.recordExternalGap(gapSec)
                    }
                    disconnectGapStartMs = null
                }

                val cmdStartMs = SystemClock.elapsedRealtime()
                val ok = try {
                    obdRepository.requestTelemetry()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                val nowMs = SystemClock.elapsedRealtime()
                val cmdDurationMs = nowMs - cmdStartMs

                if (ok) {
                    consecutiveEcuFailures = 0
                    validResponsesInWindow++
                    val windowElapsedMs = nowMs - windowStartMs
                    if (windowElapsedMs >= 1000L) {
                        currentRps = (validResponsesInWindow * 1000f) / windowElapsedMs.toFloat()
                        validResponsesInWindow = 0
                        windowStartMs = nowMs
                    }

                    val updatedMetrics = accumulator.update(
                        diagnosticInfo = obdRepository.diagnosticInfo.value,
                        profile = activeProfile,
                        nowElapsedRealtimeMs = nowMs,
                        allowFuelEstimation = true
                    )

                    val shiftId = activeShiftId
                    if (shiftId != null) {
                        samplesSinceLastCheckpoint++
                        val timeSinceCheckpointMs = nowMs - lastCheckpointElapsedMs
                        // Cadencia de checkpoint (Punto 6): cada 30 s o cada 50 muestras
                        if (timeSinceCheckpointMs >= CHECKPOINT_INTERVAL_MS ||
                            samplesSinceLastCheckpoint >= CHECKPOINT_SAMPLE_CADENCE
                        ) {
                            checkpointStore.save(shiftId, updatedMetrics, nowMs, System.currentTimeMillis())
                            lastCheckpointElapsedMs = nowMs
                            samplesSinceLastCheckpoint = 0
                        }

                        sessionState.updateShiftMetrics(
                            shiftId = shiftId,
                            metrics = updatedMetrics,
                            polling = true,
                            responsesPerSecond = currentRps,
                            disconnectionAlert = false,
                            alertMessage = null,
                            faultType = ObdFaultType.NONE,
                            telemetryState = ShiftTelemetryState.RUNNING
                        )
                        updateNotification(
                            "OBD-II activo · ${String.format(Locale.ROOT, "%.2f", updatedMetrics.distanceKilometers)} km · " +
                                "${String.format(Locale.ROOT, "%.2f", updatedMetrics.fuelConsumedLiters)} L " +
                                "(Cob. ${updatedMetrics.coveragePct.toInt()}%)",
                            isAlert = false
                        )
                    } else {
                        sessionState.updateMonitoring(
                            polling = true,
                            responsesPerSecond = currentRps
                        )
                    }

                    val remainingDelayMs = (TARGET_SAMPLE_INTERVAL_MS - cmdDurationMs).coerceAtLeast(10L)
                    delay(remainingDelayMs)
                } else {
                    consecutiveEcuFailures++
                    // Punto 2 y 10: 5 timeouts consecutivos a ~200 ms por lectura (~1 s) => ECU muda (adaptador BLE OK, NO DATA)
                    if (consecutiveEcuFailures >= MAX_CONSECUTIVE_ECU_SILENT_POLLS) {
                        if (disconnectGapStartMs == null) {
                            disconnectGapStartMs = cmdStartMs
                        }
                        val msg = "🚗 ECU muda ($consecutiveEcuFailures lecturas sin datos). Adaptador BLE conectado, verifica contacto/motor."
                        sessionState.triggerDisconnectionAlert(msg, ObdFaultType.ECU_SILENT)
                        if (activeShiftId != null) {
                            saveImmediateCheckpoint()
                            updateNotification(msg, isAlert = true)
                        }
                    }
                    val backoffMs = (200L * consecutiveEcuFailures).coerceAtMost(2000L)
                    delay(backoffMs)
                }
            }
        }
    }

    private fun saveImmediateCheckpoint() {
        val shiftId = activeShiftId ?: return
        checkpointStore.save(shiftId, accumulator.metrics, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        lastCheckpointElapsedMs = SystemClock.elapsedRealtime()
        samplesSinceLastCheckpoint = 0
    }

    private fun stopShiftAndService() {
        val isConnectedAtEnd = obdRepository.connectionState.value is ConnectionState.Connected &&
            obdRepository.diagnosticInfo.value.ecuVerified
        pollingJob?.cancel()
        pollingJob = null
        val finalMetrics = accumulator.stopTurn(connectedAtEnd = isConnectedAtEnd)
        // Punto 10: Loggear diagnóstico del turno para pruebas reales en el Polo Track
        Log.i(
            TAG,
            "Turno cerrado id=$activeShiftId | sampleCount=${finalMetrics.sampleCount} | " +
                "meanDtMs=${"%.1f".format(finalMetrics.meanDtMs)} | gapCount=${finalMetrics.gapCount} | " +
                "maxGapSec=${"%.1f".format(finalMetrics.maxGapSec)} | coveragePct=${"%.1f".format(finalMetrics.coveragePct)}% | " +
                "idleSec=${"%.1f".format(finalMetrics.idleSec)} | cutoffSec=${"%.1f".format(finalMetrics.cutoffSec)} | " +
                "obdValid=${finalMetrics.isValidForCalibration()}"
        )
        activeShiftId = null
        checkpointStore.clear()
        sessionState.finishShift()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        saveImmediateCheckpoint()
        pollingJob?.cancel()
        connectionWatchJob?.cancel()
        serviceScope.cancel()
        sessionState.serviceStopped()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
        }
    }

    private fun updateNotification(contentText: String, isAlert: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            manager.notify(NOTIFICATION_ID, buildNotification(contentText, isAlert))
        }
    }

    private fun buildNotification(contentText: String, isAlert: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (isAlert) "⚠️ Alerta de Telemetría OBD-II" else "Kilometrix · Telemetría OBD-II")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(!isAlert)
            .setPriority(if (isAlert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Telemetría OBD-II en Recorrido",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Mantiene activa la lectura OBD-II del vehículo y alerta desconexiones BLE o ECU muda."
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "ObdTripService"
        const val ACTION_START_SHIFT = "co.samidev.kilometrix.obd.START_SHIFT"
        const val ACTION_START_MONITORING = "co.samidev.kilometrix.obd.START_MONITORING"
        const val ACTION_STOP_SHIFT = "co.samidev.kilometrix.obd.STOP_SHIFT"

        const val EXTRA_SHIFT_ID = "extra_shift_id"
        const val EXTRA_INITIAL_ODO = "extra_initial_odo"
        const val EXTRA_PRICE_PER_LITER = "extra_price_per_liter"

        private const val CHANNEL_ID = "kilometrix_obd_telemetry"
        private const val NOTIFICATION_ID = 4102
        private const val TARGET_SAMPLE_INTERVAL_MS = 200L
        /** Punto 10: 5 timeouts consecutivos a 200 ms por lectura (~1 s) declaran ECU muda. */
        private const val MAX_CONSECUTIVE_ECU_SILENT_POLLS = 5
        /** Punto 6: Cadencia de checkpoint cada 30 segundos o cada 50 muestras. */
        private const val CHECKPOINT_INTERVAL_MS = 30_000L
        private const val CHECKPOINT_SAMPLE_CADENCE = 50
    }
}
