package co.samidev.kilometrix.obd.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import co.samidev.kilometrix.obd.domain.ConnectionState
import co.samidev.kilometrix.obd.domain.GattCharacteristicInfo
import co.samidev.kilometrix.obd.domain.ObdDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleTransceiver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager
) {
    private val connectionMutex = Mutex()
    private val commandMutex = Mutex()
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var events: Channel<GattEvent>? = null
    private var selectedTx: BluetoothGattCharacteristic? = null
    private var selectedRx: BluetoothGattCharacteristic? = null
    private var selectedWriteType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    private val subscribedRxCharacteristics = mutableListOf<BluetoothGattCharacteristic>()
    private val quarantinedRxChannels = mutableSetOf<RxChannelKey>()
    @Volatile
    private var ecuVerificationCompleted = false

    suspend fun connectAndInspect(device: ObdDevice): List<GattCharacteristicInfo> = connectionMutex.withLock {
        closeGatt()
        val eventChannel = Channel<GattEvent>(Channel.UNLIMITED)
        events = eventChannel

        try {
            val adapter = bluetoothManager.adapter ?: throw IOException("Bluetooth no está disponible.")
            val remoteDevice = adapter.getRemoteDevice(device.address)
            _connectionState.value = ConnectionState.Connecting(device.name)

            val callback = createCallback(eventChannel)
            @Suppress("DEPRECATION")
            val activeGatt = remoteDevice.connectGatt(
                context,
                false,
                callback,
                BluetoothDevice.TRANSPORT_LE
            ) ?: throw IOException("Android no pudo abrir la conexión GATT.")
            gatt = activeGatt

            withTimeout(CONNECTION_TIMEOUT_MS) {
                while (true) {
                    when (val event = eventChannel.receive()) {
                        GattEvent.Connected -> {
                            _connectionState.value = ConnectionState.DiscoveringServices(device.name)
                            if (!activeGatt.discoverServices()) {
                                throw IOException("No se pudo iniciar el descubrimiento GATT.")
                            }
                        }

                        is GattEvent.ServicesDiscovered -> {
                            if (event.status != BluetoothGatt.GATT_SUCCESS) {
                                throw IOException("Falló el descubrimiento GATT (código ${event.status}).")
                            }
                            val discovered = activeGatt.services.flatMap { service ->
                                service.characteristics.map { characteristic ->
                                    characteristic.toInfo(service.uuid)
                                }
                            }
                            if (discovered.isEmpty()) throw IOException("El adaptador no publicó características GATT.")
                            _connectionState.value = ConnectionState.GattReady
                            return@withTimeout discovered
                        }

                        is GattEvent.Failure -> throw IOException(event.message)
                        GattEvent.Disconnected -> throw IOException("La conexión BLE se cerró durante el descubrimiento.")
                        else -> Unit
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                emptyList()
            }
        } catch (timeout: TimeoutCancellationException) {
            closeGatt()
            _connectionState.value = ConnectionState.Error("Tiempo agotado conectando o descubriendo servicios GATT.")
            throw IOException("Tiempo agotado conectando o descubriendo servicios GATT.", timeout)
        } catch (cancelled: CancellationException) {
            closeGatt()
            _connectionState.value = ConnectionState.Disconnected
            throw cancelled
        } catch (error: Exception) {
            closeGatt()
            _connectionState.value = ConnectionState.Error(error.message ?: "Error de conexión BLE.")
            throw error
        }
    }

    suspend fun configureCharacteristics(
        txInfo: GattCharacteristicInfo,
        rxInfo: GattCharacteristicInfo
    ) = connectionMutex.withLock {
        val activeGatt = gatt ?: throw IOException("Primero conecta el adaptador BLE.")
        selectedTx = null
        selectedRx = null
        val txServiceUuid = parseUuid(txInfo.serviceUuid)
        val rxServiceUuid = parseUuid(rxInfo.serviceUuid)
        val txUuid = parseUuid(txInfo.characteristicUuid)
        val rxUuid = parseUuid(rxInfo.characteristicUuid)
        val txService = activeGatt.getService(txServiceUuid)
            ?: throw IOException("El servicio TX seleccionado ya no está disponible.")
        val rxService = activeGatt.getService(rxServiceUuid)
            ?: throw IOException("El servicio RX seleccionado ya no está disponible.")
        val tx = txService.getCharacteristic(txUuid)
            ?: throw IOException("La característica TX seleccionada no existe.")
        val rx = rxService.getCharacteristic(rxUuid)
            ?: throw IOException("La característica RX seleccionada no existe.")
        if (isRxQuarantined(rx)) {
            throw IOException("Esta entrada RX agotó el tiempo de respuesta; vuelve a conectar el adaptador para reintentarlo.")
        }
        val descriptor = rx.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIGURATION)
            ?: throw IOException("RX no incluye el descriptor de configuración de notificaciones.")
        val eventChannel = events ?: throw IOException("Canal GATT cerrado.")

        subscribedRxCharacteristics.toList().forEach { previousRx ->
            if (!sameCharacteristic(previousRx, rx)) {
                try {
                    disableRxSubscription(activeGatt, previousRx, eventChannel)
                } catch (timeout: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    if (!ecuVerificationCompleted) quarantineRx(previousRx)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
            }
        }

        val writeType = when {
            (tx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            (tx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> throw IOException("La característica TX no permite escribir.")
        }
        val notificationValue = when {
            (rx.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            (rx.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> throw IOException("La característica RX no permite notificaciones ni indicaciones.")
        }

        _connectionState.value = ConnectionState.EnablingNotifications
        try {
            if (!activeGatt.setCharacteristicNotification(rx, true)) {
                throw IOException("Android rechazó activar las notificaciones de RX.")
            }
            if (subscribedRxCharacteristics.none { sameCharacteristic(it, rx) }) {
                subscribedRxCharacteristics += rx
            }

            if (!writeDescriptor(activeGatt, descriptor, notificationValue)) {
                throw IOException("No se pudo habilitar la notificación BLE de RX.")
            }
            awaitDescriptorWrite(
                eventChannel = eventChannel,
                descriptor = descriptor,
                failureMessage = "El adaptador rechazó las notificaciones RX"
            )

            selectedTx = tx
            selectedRx = rx
            selectedWriteType = writeType
            _connectionState.value = ConnectionState.GattReady
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            clearSelectedCharacteristics()
            if (!ecuVerificationCompleted) quarantineRx(rx)
            cleanupRxSubscription(activeGatt, rx, eventChannel)
            throw timeout
        } catch (cancelled: CancellationException) {
            clearSelectedCharacteristics()
            cleanupRxSubscription(activeGatt, rx, eventChannel)
            throw cancelled
        } catch (error: Exception) {
            clearSelectedCharacteristics()
            cleanupRxSubscription(activeGatt, rx, eventChannel)
            throw error
        }
    }

    fun markInitializingElm() {
        _connectionState.value = ConnectionState.InitializingElm
    }

    fun markVerifyingEcu() {
        _connectionState.value = ConnectionState.VerifyingEcu
    }

    fun markConnected() {
        ecuVerificationCompleted = true
        _connectionState.value = ConnectionState.Connected
    }

    fun markError(message: String) {
        _connectionState.value = ConnectionState.Error(message)
    }

    suspend fun sendCommand(command: String): Result<String> {
        var commandRx: BluetoothGattCharacteristic? = null
        return try {
            val response = commandMutex.withLock {
                val activeGatt = gatt ?: throw IOException("No hay una conexión GATT activa.")
                val tx = selectedTx ?: throw IOException("Selecciona una característica TX.")
                val rx = selectedRx ?: throw IOException("Selecciona una característica RX.")
                if (isRxQuarantined(rx)) {
                    throw IOException("El canal RX agotó el tiempo de respuesta y no se puede reutilizar en esta conexión.")
                }
                commandRx = rx
                val eventChannel = events ?: throw IOException("Canal GATT cerrado.")

                val bytes = (command + COMMAND_TERMINATOR).toByteArray(StandardCharsets.US_ASCII)
                val writeStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activeGatt.writeCharacteristic(tx, bytes, selectedWriteType) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    tx.value = bytes
                    tx.writeType = selectedWriteType
                    @Suppress("DEPRECATION")
                    activeGatt.writeCharacteristic(tx)
                }
                if (!writeStarted) throw IOException("Android rechazó el comando $command.")

                val responseBuffer = ElmResponseBuffer()
                withTimeout(COMMAND_TIMEOUT_MS) {
                    while (true) {
                        when (val event = eventChannel.receive()) {
                            is GattEvent.Notification -> {
                                if (event.serviceUuid == rx.service.uuid && event.characteristicUuid == rx.uuid) {
                                    val complete = responseBuffer.append(
                                        String(event.value, StandardCharsets.US_ASCII)
                                    )
                                    if (complete != null) return@withTimeout complete
                                }
                            }

                            is GattEvent.CharacteristicWritten -> {
                                if (event.status != BluetoothGatt.GATT_SUCCESS) {
                                    val failedCurrentTx = event.serviceUuid == tx.service.uuid &&
                                        event.characteristicUuid == tx.uuid
                                    val operation = if (failedCurrentTx) {
                                        "el comando $command"
                                    } else {
                                        "una escritura GATT pendiente"
                                    }
                                    throw IOException("Falló $operation (código ${event.status}).")
                                }
                            }

                            is GattEvent.DescriptorWritten -> {
                                val isSelectedRxDescriptor =
                                    event.serviceUuid == rx.service.uuid &&
                                        event.characteristicUuid == rx.uuid &&
                                        event.descriptorUuid == BleUuids.CLIENT_CHARACTERISTIC_CONFIGURATION
                                if (isSelectedRxDescriptor && event.status != BluetoothGatt.GATT_SUCCESS) {
                                    throw IOException("Falló una actualización de notificaciones RX (código ${event.status}).")
                                }
                            }

                            is GattEvent.Failure -> throw IOException(event.message)
                            GattEvent.Disconnected -> throw IOException("La conexión BLE se perdió durante $command.")
                            else -> Unit
                        }
                    }
                    @Suppress("UNREACHABLE_CODE")
                    ""
                }
            }
            Result.success(response)
        } catch (timeout: TimeoutCancellationException) {
            if (!ecuVerificationCompleted) {
                commandRx?.let(::quarantineRx)
            }
            Result.failure(IOException("Tiempo agotado esperando la respuesta de $command.", timeout))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun disconnect() {
        closeGatt()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun createCallback(eventChannel: Channel<GattEvent>) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when {
                status != BluetoothGatt.GATT_SUCCESS -> {
                    val message = "Falló la conexión BLE (código $status)."
                    if (this@BleTransceiver.gatt === gatt) {
                        _connectionState.value = ConnectionState.Error(message)
                    }
                    eventChannel.trySend(GattEvent.Failure(message))
                }

                newState == BluetoothProfile.STATE_CONNECTED -> eventChannel.trySend(GattEvent.Connected)
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    if (this@BleTransceiver.gatt === gatt) {
                        _connectionState.value = ConnectionState.Error("Se perdió la conexión BLE con el adaptador OBD.")
                    }
                    eventChannel.trySend(GattEvent.Disconnected)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            eventChannel.trySend(GattEvent.ServicesDiscovered(status))
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            eventChannel.trySend(
                GattEvent.DescriptorWritten(
                    serviceUuid = descriptor.characteristic.service.uuid,
                    characteristicUuid = descriptor.characteristic.uuid,
                    descriptorUuid = descriptor.uuid,
                    status = status
                )
            )
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            eventChannel.trySend(
                GattEvent.CharacteristicWritten(
                    serviceUuid = characteristic.service.uuid,
                    characteristicUuid = characteristic.uuid,
                    status = status
                )
            )
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            characteristic.value?.let {
                eventChannel.trySend(
                    GattEvent.Notification(
                        serviceUuid = characteristic.service.uuid,
                        characteristicUuid = characteristic.uuid,
                        value = it.copyOf()
                    )
                )
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            eventChannel.trySend(
                GattEvent.Notification(
                    serviceUuid = characteristic.service.uuid,
                    characteristicUuid = characteristic.uuid,
                    value = value.copyOf()
                )
            )
        }
    }

    private fun BluetoothGattCharacteristic.toInfo(serviceUuid: UUID) = GattCharacteristicInfo(
        serviceUuid = serviceUuid.toString(),
        characteristicUuid = uuid.toString(),
        canRead = (properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0,
        canWrite = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0,
        canWriteWithoutResponse = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0,
        canNotify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0,
        canIndicate = (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
    )

    private fun parseUuid(value: String): UUID = try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        throw IOException("UUID GATT inválido: $value")
    }

    private suspend fun disableRxSubscription(
        activeGatt: BluetoothGatt,
        rx: BluetoothGattCharacteristic,
        eventChannel: Channel<GattEvent>
    ) {
        if (!activeGatt.setCharacteristicNotification(rx, false)) {
            throw IOException("Android rechazó desactivar las notificaciones RX anteriores.")
        }
        val descriptor = rx.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIGURATION)
        if (descriptor == null) {
            subscribedRxCharacteristics.removeAll { sameCharacteristic(it, rx) }
            return
        }
        if (!writeDescriptor(activeGatt, descriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
            throw IOException("No se pudo desactivar la notificación RX anterior.")
        }
        awaitDescriptorWrite(
            eventChannel = eventChannel,
            descriptor = descriptor,
            failureMessage = "El adaptador rechazó desactivar la notificación RX"
        )
        subscribedRxCharacteristics.removeAll { sameCharacteristic(it, rx) }
    }

    private suspend fun cleanupRxSubscription(
        activeGatt: BluetoothGatt,
        rx: BluetoothGattCharacteristic,
        eventChannel: Channel<GattEvent>
    ) {
        if (subscribedRxCharacteristics.none { sameCharacteristic(it, rx) }) return
        try {
            disableRxSubscription(activeGatt, rx, eventChannel)
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            if (!ecuVerificationCompleted) quarantineRx(rx)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    private suspend fun awaitDescriptorWrite(
        eventChannel: Channel<GattEvent>,
        descriptor: BluetoothGattDescriptor,
        failureMessage: String
    ) {
        withTimeout(DESCRIPTOR_TIMEOUT_MS) {
            while (true) {
                when (val event = eventChannel.receive()) {
                    is GattEvent.DescriptorWritten -> {
                        if (event.serviceUuid == descriptor.characteristic.service.uuid &&
                            event.characteristicUuid == descriptor.characteristic.uuid &&
                            event.descriptorUuid == descriptor.uuid
                        ) {
                            if (event.status != BluetoothGatt.GATT_SUCCESS) {
                                throw IOException("$failureMessage (código ${event.status}).")
                            }
                            return@withTimeout
                        }
                    }

                    is GattEvent.CharacteristicWritten -> {
                        if (event.status != BluetoothGatt.GATT_SUCCESS) {
                            throw IOException("Falló una escritura GATT durante la configuración de RX (código ${event.status}).")
                        }
                    }

                    is GattEvent.Failure -> throw IOException(event.message)
                    GattEvent.Disconnected -> throw IOException("La conexión BLE se perdió al actualizar RX.")
                    else -> Unit
                }
            }
        }
    }

    private fun writeDescriptor(
        activeGatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activeGatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
    } else {
        @Suppress("DEPRECATION")
        descriptor.value = value
        @Suppress("DEPRECATION")
        activeGatt.writeDescriptor(descriptor)
    }

    private fun clearSelectedCharacteristics() {
        selectedTx = null
        selectedRx = null
        selectedWriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    }

    private fun sameCharacteristic(
        first: BluetoothGattCharacteristic,
        second: BluetoothGattCharacteristic
    ): Boolean = first.uuid == second.uuid && first.service.uuid == second.service.uuid

    private fun isRxQuarantined(rx: BluetoothGattCharacteristic): Boolean =
        synchronized(quarantinedRxChannels) { rx.channelKey() in quarantinedRxChannels }

    private fun quarantineRx(rx: BluetoothGattCharacteristic) {
        synchronized(quarantinedRxChannels) { quarantinedRxChannels += rx.channelKey() }
    }

    private fun BluetoothGattCharacteristic.channelKey() =
        RxChannelKey(serviceUuid = service.uuid, characteristicUuid = uuid)

    private fun closeGatt() {
        val previousGatt = gatt
        gatt = null
        clearSelectedCharacteristics()
        subscribedRxCharacteristics.clear()
        synchronized(quarantinedRxChannels) { quarantinedRxChannels.clear() }
        ecuVerificationCompleted = false
        runCatching { previousGatt?.disconnect() }
        runCatching { previousGatt?.close() }
        events?.close()
        events = null
    }

    private sealed interface GattEvent {
        data object Connected : GattEvent
        data object Disconnected : GattEvent
        data class ServicesDiscovered(val status: Int) : GattEvent
        data class DescriptorWritten(
            val serviceUuid: UUID,
            val characteristicUuid: UUID,
            val descriptorUuid: UUID,
            val status: Int
        ) : GattEvent
        data class CharacteristicWritten(
            val serviceUuid: UUID,
            val characteristicUuid: UUID,
            val status: Int
        ) : GattEvent
        data class Notification(
            val serviceUuid: UUID,
            val characteristicUuid: UUID,
            val value: ByteArray
        ) : GattEvent
        data class Failure(val message: String) : GattEvent
    }

    private data class RxChannelKey(
        val serviceUuid: UUID,
        val characteristicUuid: UUID
    )

    private companion object {
        const val CONNECTION_TIMEOUT_MS = 15_000L
        const val DESCRIPTOR_TIMEOUT_MS = 5_000L
        const val COMMAND_TIMEOUT_MS = 5_000L
        const val COMMAND_TERMINATOR = "\r"
    }
}
