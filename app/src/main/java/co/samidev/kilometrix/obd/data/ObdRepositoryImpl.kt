package co.samidev.kilometrix.obd.data

import android.os.SystemClock
import co.samidev.kilometrix.obd.ble.BleScanner
import co.samidev.kilometrix.obd.ble.BleTransceiver
import co.samidev.kilometrix.obd.ble.ElmResponseParser
import co.samidev.kilometrix.obd.domain.ConnectionState
import co.samidev.kilometrix.obd.domain.DistancePidType
import co.samidev.kilometrix.obd.domain.EcuDiagnosticInfo
import co.samidev.kilometrix.obd.domain.FuelSensorMode
import co.samidev.kilometrix.obd.domain.GattCharacteristicInfo
import co.samidev.kilometrix.obd.domain.ObdDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObdRepositoryImpl @Inject constructor(
    private val bleScanner: BleScanner,
    private val bleTransceiver: BleTransceiver
) : ObdRepository {

    override val connectionState: StateFlow<ConnectionState> = bleTransceiver.connectionState

    private val _diagnosticInfo = MutableStateFlow(EcuDiagnosticInfo())
    override val diagnosticInfo: StateFlow<EcuDiagnosticInfo> = _diagnosticInfo.asStateFlow()
    private val telemetryRequestMutex = Mutex()
    private val telemetryPidLock = Any()
    private var lastTelemetryPid: Int? = null
    private var lastIntakeAirTemperatureRequestElapsedRealtimeMs: Long? = null
    private var lastFuelLevelRequestElapsedRealtimeMs: Long? = null

    override fun scanDevices(): Flow<ObdDevice> = bleScanner.scanDevices()

    override suspend fun connectAndVerify(device: ObdDevice): Boolean {
        log("Conectando a ${device.name} (${device.address})...")
        clearConnectionDiagnostics()
        try {
            val characteristics = bleTransceiver.connectAndInspect(device)
            _diagnosticInfo.update { it.copy(gattCharacteristics = characteristics) }
            logGattCharacteristics(characteristics)

            val candidates = rankChannels(characteristics)
            if (candidates.isEmpty()) {
                log("No hay canales GATT compatibles para enviar comandos y recibir respuestas.")
                bleTransceiver.markError(CONNECTION_FAILED_MESSAGE)
                return false
            }

            for ((index, candidate) in candidates.withIndex()) {
                clearCandidateDiagnostics()
                log("Probando canal automático ${index + 1}/${candidates.size}.")
                if (tryInitializeAndVerify(candidate)) return true
            }

            log("Se agotaron los canales compatibles sin confirmar la conexión con la ECU.")
            bleTransceiver.markError(CONNECTION_FAILED_MESSAGE)
            return false
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            log("Tiempo agotado conectando o descubriendo GATT.")
            bleTransceiver.markError(CONNECTION_FAILED_MESSAGE)
            return false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log("Error GATT: ${error.message ?: "conexión fallida"}")
            bleTransceiver.markError(CONNECTION_FAILED_MESSAGE)
            return false
        }
    }

    private suspend fun tryInitializeAndVerify(candidate: GattChannelCandidate): Boolean {
        try {
            bleTransceiver.configureCharacteristics(candidate.tx, candidate.rx)
            bleTransceiver.markInitializingElm()
            log("Notificaciones RX activas. Inicializando ELM...")

            val reset = sendAndLog("ATZ")
            val version = ElmResponseParser.parseElmVersion(reset)
                ?: throw IllegalStateException("ATZ no devolvió una versión ELM reconocible: $reset")
            _diagnosticInfo.update { it.copy(elmVersion = version) }
            delay(200)

            requireOk("ATE0")
            requireOk("ATL0")
            requireOk("ATS0")
            requireOk("ATH0")
            requireOk("ATSP6")

            val protocolDescription = ElmResponseParser.parseProtocolDescription(sendAndLog("ATDP"))
                ?: throw IllegalStateException("El adaptador no confirmó un protocolo CAN: ATDP")
            val protocolNumber = sendAndLog("ATDPN").filter(Char::isLetterOrDigit)
            if (!ElmResponseParser.isMqbCan11Bit500K(protocolNumber)) {
                throw IllegalStateException("Protocolo inesperado: ATDP=$protocolDescription, ATDPN=$protocolNumber")
            }
            _diagnosticInfo.update { it.copy(protocol = protocolDescription) }

            bleTransceiver.markVerifyingEcu()
            val pidSupport00 = sendAndLog("0100")
            if (!ElmResponseParser.supportsPidInBlock(pidSupport00, 0x00, RPM_PID)) {
                throw IllegalStateException("La ECU no confirmó soporte para RPM (010C): $pidSupport00")
            }
            val supportedPids = mutableSetOf<Int>().apply {
                add(RPM_PID)
                if (ElmResponseParser.supportsPidInBlock(pidSupport00, 0x00, SPEED_PID)) add(SPEED_PID)
                if (ElmResponseParser.supportsPidInBlock(pidSupport00, 0x00, MAP_PID)) add(MAP_PID)
                if (ElmResponseParser.supportsPidInBlock(pidSupport00, 0x00, INTAKE_AIR_TEMPERATURE_PID)) {
                    add(INTAKE_AIR_TEMPERATURE_PID)
                }
                if (ElmResponseParser.supportsPidInBlock(pidSupport00, 0x00, MAF_PID)) add(MAF_PID)
                if (ElmResponseParser.supportsPidInBlock(pidSupport00, 0x00, NEXT_BLOCK_0120_PID)) {
                    add(NEXT_BLOCK_0120_PID)
                }
            }

            // Probe de capacidades extendidas 0120 y 0140 (Punto 8)
            if (NEXT_BLOCK_0120_PID in supportedPids) {
                val pidSupport20 = sendAndLog("0120")
                if (ElmResponseParser.supportsPidInBlock(pidSupport20, 0x20, FUEL_LEVEL_PID)) {
                    supportedPids.add(FUEL_LEVEL_PID)
                }
                if (ElmResponseParser.supportsPidInBlock(pidSupport20, 0x20, NEXT_BLOCK_0140_PID)) {
                    sendAndLog("0140")
                }
            }

            // Degradación elegante de sensores de combustible (Punto 8)
            val fuelSensorMode = when {
                MAP_PID in supportedPids -> FuelSensorMode.SPEED_DENSITY_MAP
                MAF_PID in supportedPids -> FuelSensorMode.MAF_DIRECT
                else -> FuelSensorMode.UNSUPPORTED
            }
            val isIatDegraded = fuelSensorMode == FuelSensorMode.SPEED_DENSITY_MAP &&
                INTAKE_AIR_TEMPERATURE_PID !in supportedPids

            _diagnosticInfo.update {
                it.copy(
                    ecuVerified = true,
                    supportedPids = supportedPids.toSet(),
                    fuelSensorMode = fuelSensorMode,
                    isIatDegraded = isIatDegraded
                )
            }
            log("ECU respondió y confirmó soporte de RPM.")
            if (SPEED_PID in supportedPids) {
                log("La ECU confirmó soporte de velocidad (010D).")
            } else {
                log("La ECU no anunció soporte de velocidad (010D); se continuará solo con RPM.")
            }
            logOptionalPidSupport(supportedPids, MAP_PID, "presión MAP", "010B")
            logOptionalPidSupport(supportedPids, MAF_PID, "flujo MAF", "0110")
            logOptionalPidSupport(
                supportedPids,
                INTAKE_AIR_TEMPERATURE_PID,
                "temperatura de aire de admisión",
                "010F"
            )
            logOptionalPidSupport(supportedPids, FUEL_LEVEL_PID, "nivel de tanque", "012F")
            if (isIatDegraded) {
                log("Aviso: 010F (IAT) no soportado; usando aproximación ambiente (25 °C) con marca degradada.")
            }

            val rpm = readRpmOrThrow()
            bleTransceiver.markConnected()
            log("Lectura inicial confirmada: $rpm RPM (Modo combustible: $fuelSensorMode).")

            if (FUEL_LEVEL_PID in supportedPids) {
                runCatching { readFuelLevelOrNull() }
            }

            readOptionalIdentification("0902", VIN_INFO_TYPE) { copy(vin = it) }
            readOptionalIdentification("0904", CALIBRATION_INFO_TYPE) { copy(calibrationId = it) }
            readOptionalIdentification("090A", ECU_NAME_INFO_TYPE) { copy(ecuName = it) }

            return connectionState.value is ConnectionState.Connected &&
                diagnosticInfo.value.liveRpm != null
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            log("Tiempo agotado en este canal GATT; se probará otro canal compatible.")
            return false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log("Error de inicialización: ${error.message ?: "respuesta inválida"}")
            return false
        }
    }

    private fun logGattCharacteristics(characteristics: List<GattCharacteristicInfo>) {
        characteristics.groupBy(GattCharacteristicInfo::serviceUuid).forEach { (serviceUuid, items) ->
            log("Servicio GATT: $serviceUuid")
            items.forEach { characteristic ->
                val properties = buildList {
                    if (characteristic.canRead) add("READ")
                    if (characteristic.canWrite) add("WRITE")
                    if (characteristic.canWriteWithoutResponse) add("WRITE_NO_RESPONSE")
                    if (characteristic.canNotify) add("NOTIFY")
                    if (characteristic.canIndicate) add("INDICATE")
                }.joinToString()
                log("Característica ${characteristic.characteristicUuid}: $properties")
            }
        }
    }

    private fun rankChannels(characteristics: List<GattCharacteristicInfo>): List<GattChannelCandidate> =
        characteristics.flatMap { tx ->
            characteristics.mapNotNull { rx ->
                if (!(tx.canWrite || tx.canWriteWithoutResponse)) return@mapNotNull null
                if (!(rx.canNotify || rx.canIndicate)) return@mapNotNull null
                GattChannelCandidate(tx = tx, rx = rx, score = channelScore(tx, rx))
            }
        }.sortedByDescending(GattChannelCandidate::score)

    private fun channelScore(tx: GattCharacteristicInfo, rx: GattCharacteristicInfo): Int =
        (if (tx.serviceUuid == rx.serviceUuid) SAME_SERVICE_SCORE else 0) +
            (if (tx.canWrite) ACKNOWLEDGED_WRITE_SCORE else 0) +
            (if (rx.canIndicate) INDICATION_SCORE else 0) +
            (if (rx.canNotify) NOTIFICATION_SCORE else 0)

    private fun clearConnectionDiagnostics() {
        clearCandidateDiagnostics(clearGattCharacteristics = true)
    }

    private fun clearCandidateDiagnostics(clearGattCharacteristics: Boolean = false) {
        _diagnosticInfo.update { current ->
            current.copy(
                elmVersion = "",
                protocol = "",
                liveRpm = null,
                liveSpeedKph = null,
                liveMapKpa = null,
                liveIntakeAirTemperatureCelsius = null,
                liveOdometerKm = null,
                rpmSampleElapsedRealtimeMs = null,
                speedSampleElapsedRealtimeMs = null,
                mapSampleElapsedRealtimeMs = null,
                intakeAirTemperatureSampleElapsedRealtimeMs = null,
                odometerSampleElapsedRealtimeMs = null,
                distancePidType = DistancePidType.NONE,
                vin = null,
                ecuName = null,
                calibrationId = null,
                ecuVerified = false,
                supportedPids = emptySet(),
                gattCharacteristics = if (clearGattCharacteristics) emptyList() else current.gattCharacteristics
            )
        }
        synchronized(telemetryPidLock) {
            lastTelemetryPid = null
            lastIntakeAirTemperatureRequestElapsedRealtimeMs = null
        }
    }

    override suspend fun requestSingleRpm() = telemetryRequestMutex.withLock {
        if (!diagnosticInfo.value.ecuVerified) {
            log("No se pueden leer RPM antes de verificar la ECU.")
            return@withLock
        }
        try {
            readRpmOrThrow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log("No se pudo leer RPM: ${error.message ?: "respuesta inválida"}")
        }
    }

    override suspend fun sendRawCommand(command: String): String = telemetryRequestMutex.withLock {
        val cleanCmd = command.trim()
        return@withLock sendAndLog(cleanCmd)
    }

    override suspend fun readOdometer(): Double? = telemetryRequestMutex.withLock {
        if (!diagnosticInfo.value.ecuVerified) return@withLock null
        try {
            val raw = sendAndLog("01A6")
            ElmResponseParser.parseOdometerKm(raw)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun requestTelemetry(): Boolean = telemetryRequestMutex.withLock {
        val info = diagnosticInfo.value
        if (!info.ecuVerified) {
            return@withLock false
        }

        val requestTimeMs = SystemClock.elapsedRealtime()
        val nextPid = synchronized(telemetryPidLock) {
            val fastPidsForVehicle = buildList {
                if (RPM_PID in info.supportedPids) add(RPM_PID)
                if (SPEED_PID in info.supportedPids) add(SPEED_PID)
                when (info.fuelSensorMode) {
                    FuelSensorMode.SPEED_DENSITY_MAP -> if (MAP_PID in info.supportedPids) add(MAP_PID)
                    FuelSensorMode.MAF_DIRECT -> if (MAF_PID in info.supportedPids) add(MAF_PID)
                    FuelSensorMode.UNSUPPORTED -> Unit
                }
            }
            val lastIatRequestMs = lastIntakeAirTemperatureRequestElapsedRealtimeMs
            val iatIsDue = INTAKE_AIR_TEMPERATURE_PID in info.supportedPids &&
                (lastIatRequestMs == null ||
                    requestTimeMs - lastIatRequestMs >= INTAKE_AIR_TEMPERATURE_INTERVAL_MS)

            val lastFuelLevelMs = lastFuelLevelRequestElapsedRealtimeMs
            val fuelLevelIsDue = FUEL_LEVEL_PID in info.supportedPids &&
                (lastFuelLevelMs == null ||
                    requestTimeMs - lastFuelLevelMs >= FUEL_LEVEL_INTERVAL_MS)

            when {
                iatIsDue -> {
                    lastIntakeAirTemperatureRequestElapsedRealtimeMs = requestTimeMs
                    INTAKE_AIR_TEMPERATURE_PID
                }
                fuelLevelIsDue -> {
                    lastFuelLevelRequestElapsedRealtimeMs = requestTimeMs
                    FUEL_LEVEL_PID
                }
                else -> {
                    val lastIndex = lastTelemetryPid?.let { fastPidsForVehicle.indexOf(it) } ?: -1
                    val selectedPid = when {
                        fastPidsForVehicle.isEmpty() -> null
                        lastIndex < 0 -> fastPidsForVehicle.first()
                        else -> fastPidsForVehicle[(lastIndex + 1) % fastPidsForVehicle.size]
                    }
                    if (selectedPid != null) lastTelemetryPid = selectedPid
                    selectedPid
                }
            }
        }
        if (nextPid == null) {
            return@withLock false
        }

        try {
            when (nextPid) {
                RPM_PID -> readRpmOrThrow()
                SPEED_PID -> readSpeedOrThrow()
                MAP_PID -> readMapOrThrow()
                MAF_PID -> readMafOrThrow()
                INTAKE_AIR_TEMPERATURE_PID -> readIntakeAirTemperatureOrThrow()
                FUEL_LEVEL_PID -> readFuelLevelOrNull()
                else -> return@withLock false
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val metric = when (nextPid) {
                RPM_PID -> "RPM"
                SPEED_PID -> "velocidad"
                MAP_PID -> "presión MAP"
                MAF_PID -> "flujo MAF"
                INTAKE_AIR_TEMPERATURE_PID -> "temperatura IAT"
                FUEL_LEVEL_PID -> "nivel tanque (012F)"
                else -> "telemetría"
            }
            log("No se pudo leer $metric: ${error.message ?: "respuesta inválida"}")
            false
        }
    }

    private suspend fun readRpmOrThrow(): Int {
        val raw = sendAndLog("010C")
        val rpm = ElmResponseParser.parseRpm(raw)
            ?: throw IllegalStateException("Respuesta RPM inválida o incompleta: $raw")
        _diagnosticInfo.update {
            it.copy(
                liveRpm = rpm,
                rpmSampleElapsedRealtimeMs = SystemClock.elapsedRealtime()
            )
        }
        return rpm
    }

    private suspend fun readSpeedOrThrow(): Int {
        val raw = sendAndLog("010D")
        val speedKph = ElmResponseParser.parseSpeedKph(raw)
            ?: throw IllegalStateException("Respuesta de velocidad inválida o incompleta: $raw")
        _diagnosticInfo.update {
            it.copy(
                liveSpeedKph = speedKph,
                speedSampleElapsedRealtimeMs = SystemClock.elapsedRealtime()
            )
        }
        return speedKph
    }

    private suspend fun readMapOrThrow(): Int {
        val raw = sendAndLog("010B")
        val mapKpa = ElmResponseParser.parseMapKpa(raw)
            ?: throw IllegalStateException("Respuesta MAP inválida o incompleta: $raw")
        _diagnosticInfo.update {
            it.copy(
                liveMapKpa = mapKpa,
                mapSampleElapsedRealtimeMs = SystemClock.elapsedRealtime()
            )
        }
        return mapKpa
    }

    private suspend fun readIntakeAirTemperatureOrThrow(): Int {
        val raw = sendAndLog("010F")
        val temperatureCelsius = ElmResponseParser.parseIntakeAirTemperatureCelsius(raw)
            ?: throw IllegalStateException("Respuesta IAT inválida o incompleta: $raw")
        _diagnosticInfo.update {
            it.copy(
                liveIntakeAirTemperatureCelsius = temperatureCelsius,
                intakeAirTemperatureSampleElapsedRealtimeMs = SystemClock.elapsedRealtime()
            )
        }
        return temperatureCelsius
    }

    private suspend fun readMafOrThrow(): Double {
        val raw = sendAndLog("0110")
        val mafGps = ElmResponseParser.parseMafGramsPerSecond(raw)
            ?: throw IllegalStateException("Respuesta MAF inválida o incompleta: $raw")
        _diagnosticInfo.update {
            it.copy(
                liveMafGramsPerSec = mafGps,
                mafSampleElapsedRealtimeMs = SystemClock.elapsedRealtime()
            )
        }
        return mafGps
    }

    private suspend fun readFuelLevelOrNull(): Double? {
        val raw = sendAndLog("012F")
        val levelPct = ElmResponseParser.parseFuelLevelPct(raw)
        if (levelPct != null) {
            _diagnosticInfo.update {
                it.copy(
                    liveFuelLevelPct = levelPct,
                    fuelLevelSampleElapsedRealtimeMs = SystemClock.elapsedRealtime()
                )
            }
        } else {
            // Si 012F devuelve 0xFF (N/A) o NO DATA, lo removemos de supportedPids para no reintentar en vano
            _diagnosticInfo.update {
                it.copy(supportedPids = it.supportedPids - FUEL_LEVEL_PID)
            }
            log("PID 012F (nivel tanque) devolvió N/A; se mantiene Tanque Virtual puro.")
        }
        return levelPct
    }

    override fun disconnect() {
        bleTransceiver.disconnect()
        synchronized(telemetryPidLock) {
            lastTelemetryPid = null
            lastIntakeAirTemperatureRequestElapsedRealtimeMs = null
        }
        _diagnosticInfo.update {
            it.copy(
                elmVersion = "",
                protocol = "",
                liveRpm = null,
                liveSpeedKph = null,
                liveMapKpa = null,
                liveIntakeAirTemperatureCelsius = null,
                liveOdometerKm = null,
                rpmSampleElapsedRealtimeMs = null,
                speedSampleElapsedRealtimeMs = null,
                mapSampleElapsedRealtimeMs = null,
                intakeAirTemperatureSampleElapsedRealtimeMs = null,
                odometerSampleElapsedRealtimeMs = null,
                distancePidType = DistancePidType.NONE,
                ecuVerified = false,
                supportedPids = emptySet()
            )
        }
        log("Desconectado.")
    }

    private suspend fun sendAndLog(cmd: String): String {
        log(">> $cmd")
        val result = bleTransceiver.sendCommand(cmd)
        val response = result.getOrElse { error ->
            if (error is CancellationException) throw error
            "ERROR: ${error.message ?: "timeout"}"
        }
        log("<< ${response.trim()}")
        return response.trim()
    }

    private suspend fun requireOk(command: String) {
        val response = sendAndLog(command)
        if (!response.contains("OK", ignoreCase = true)) {
            throw IllegalStateException("$command no fue aceptado por el adaptador: $response")
        }
    }

    private fun logOptionalPidSupport(
        supportedPids: Set<Int>,
        pid: Int,
        metric: String,
        command: String
    ) {
        if (pid in supportedPids) {
            log("La ECU confirmó soporte de $metric ($command).")
        } else {
            log("La ECU no anunció soporte de $metric ($command); se omitirá esa lectura.")
        }
    }

    private suspend fun readOptionalIdentification(
        command: String,
        infoType: Int,
        update: EcuDiagnosticInfo.(String) -> EcuDiagnosticInfo
    ) {
        val response = sendAndLog(command)
        val value = if (infoType == VIN_INFO_TYPE) {
            ElmResponseParser.parseVin(response)
        } else {
            ElmResponseParser.parseMode09Ascii(response, infoType)
        }
        if (value != null) {
            _diagnosticInfo.update { it.update(value) }
        }
    }

    private fun log(text: String) {
        _diagnosticInfo.update {
            it.copy(logs = (it.logs + text).takeLast(MAX_LOG_LINES))
        }
    }

    private data class GattChannelCandidate(
        val tx: GattCharacteristicInfo,
        val rx: GattCharacteristicInfo,
        val score: Int
    )

    private companion object {
        const val MAX_LOG_LINES = 300
        const val SAME_SERVICE_SCORE = 100
        const val ACKNOWLEDGED_WRITE_SCORE = 20
        const val INDICATION_SCORE = 10
        const val NOTIFICATION_SCORE = 5
        const val CONNECTION_FAILED_MESSAGE =
            "No se pudo conectar con la ECU. Verifica que el contacto del vehículo esté encendido."
        const val RPM_PID = 0x0C
        const val SPEED_PID = 0x0D
        const val MAP_PID = 0x0B
        const val INTAKE_AIR_TEMPERATURE_PID = 0x0F
        const val MAF_PID = 0x10
        const val NEXT_BLOCK_0120_PID = 0x20
        const val FUEL_LEVEL_PID = 0x2F
        const val NEXT_BLOCK_0140_PID = 0x40
        const val INTAKE_AIR_TEMPERATURE_INTERVAL_MS = 30_000L
        const val FUEL_LEVEL_INTERVAL_MS = 60_000L
        const val VIN_INFO_TYPE = 0x02
        const val CALIBRATION_INFO_TYPE = 0x04
        const val ECU_NAME_INFO_TYPE = 0x0A
    }
}
