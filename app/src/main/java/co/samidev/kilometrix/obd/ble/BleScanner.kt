package co.samidev.kilometrix.obd.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import co.samidev.kilometrix.obd.domain.ObdDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    private val bluetoothManager: BluetoothManager
) {
    fun scanDevices(): Flow<ObdDevice> = callbackFlow {
        val adapter = bluetoothManager.adapter
            ?: throw IllegalStateException("Este teléfono no tiene Bluetooth.")
        if (!adapter.isEnabled) {
            throw IllegalStateException("Activa Bluetooth para buscar el adaptador OBD.")
        }
        val scanner = adapter.bluetoothLeScanner
            ?: throw IllegalStateException("No se pudo iniciar el escaneo BLE.")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val address = device.address
                val scanRecord = result.scanRecord
                val name = scanRecord?.deviceName
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: runCatching { device.name }.getOrNull()?.trim().orEmpty()

                if (!ObdBleFilter.isCandidateObd(name, scanRecord?.serviceUuids)) return

                trySend(
                    ObdDevice(
                        address = address,
                        name = name.ifBlank { "Adaptador OBD" },
                        rssi = result.rssi
                    )
                )
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(0, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Falló el escaneo BLE (código $errorCode)."))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, callback)
        } catch (error: SecurityException) {
            close(error)
        }

        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }
}

private object ObdBleFilter {
    private val obdServiceUuid = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val obdServiceUuidFff0 = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val tvToken = Regex("(^|[^A-Z0-9])TV([^A-Z0-9]|$)")
    private val autoToken = Regex("(^|[^A-Z0-9])AUTO([^A-Z0-9]|$)")
    private val knownObdNames = listOf(
        "OBD",
        "ELM",
        "V-LINK",
        "VLINK",
        "IOS-VLINK",
        "VIECA",
        "KONNWEI",
        "V03H4"
    )

    fun isCandidateObd(name: String, advertisedServiceUuids: List<ParcelUuid>?): Boolean {
        val normalizedName = name.uppercase(Locale.ROOT)
        val isClearlyUnrelated = normalizedName.contains("SAMSUNG") ||
            normalizedName.contains("UHD") ||
            tvToken.containsMatchIn(normalizedName)
        if (isClearlyUnrelated) return false

        val matchesObdName = knownObdNames.any { keyword -> normalizedName.contains(keyword) } ||
            autoToken.containsMatchIn(normalizedName)
        val advertisesObdUart = advertisedServiceUuids.orEmpty().any {
            it.uuid == obdServiceUuid || it.uuid == obdServiceUuidFff0
        }
        return matchesObdName || advertisesObdUart
    }
}
