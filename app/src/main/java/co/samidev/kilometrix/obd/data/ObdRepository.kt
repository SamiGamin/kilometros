package co.samidev.kilometrix.obd.data

import co.samidev.kilometrix.obd.domain.ConnectionState
import co.samidev.kilometrix.obd.domain.EcuDiagnosticInfo
import co.samidev.kilometrix.obd.domain.ObdDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ObdRepository {
    val connectionState: StateFlow<ConnectionState>
    val diagnosticInfo: StateFlow<EcuDiagnosticInfo>

    fun scanDevices(): Flow<ObdDevice>
    suspend fun connectAndVerify(device: ObdDevice): Boolean
    suspend fun requestSingleRpm()
    suspend fun requestTelemetry(): Boolean
    suspend fun sendRawCommand(command: String): String
    suspend fun readOdometer(): Double?
    fun disconnect()
}
