package com.crosstrainer.companion.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.crosstrainer.companion.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

data class FtmsCandidate(val address: String, val name: String, val rssi: Int)

enum class FtmsConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

data class FtmsCharacteristicDiagnostic(
    val uuid: UUID,
    val properties: Set<String>,
)

data class FtmsServiceDiagnostic(
    val uuid: UUID,
    val characteristics: List<FtmsCharacteristicDiagnostic>,
)

data class FtmsCapabilities(
    val hasCrossTrainerData: Boolean = false,
    val hasIndoorBikeData: Boolean = false,
    val hasTrainingStatus: Boolean = false,
    val hasFitnessMachineStatus: Boolean = false,
    val hasControlPoint: Boolean = false,
)

data class FtmsDiagnosticState(
    val isScanning: Boolean = false,
    val candidates: List<FtmsCandidate> = emptyList(),
    val connectionState: FtmsConnectionState = FtmsConnectionState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val selectedDeviceAddress: String? = null,
    val selectedDeviceName: String? = null,
    val canReconnect: Boolean = false,
    val services: List<FtmsServiceDiagnostic> = emptyList(),
    val capabilities: FtmsCapabilities = FtmsCapabilities(),
    val fitnessMachineFeatureHex: String? = null,
    val rawNotifications: List<String> = emptyList(),
    val currentConsoleRpm: Int? = null,
    val averageConsoleRpm: Int? = null,
    val error: String? = null,
)

data class ConsoleRpmSession(
    val sampleTotal: Long = 0,
    val sampleCount: Int = 0,
) {
    val averageRpm: Int?
        get() = if (sampleCount == 0) null else (sampleTotal.toDouble() / sampleCount).roundToInt()

    fun add(rpm: Int): ConsoleRpmSession = copy(
        sampleTotal = sampleTotal + rpm,
        sampleCount = sampleCount + 1,
    )
}

/** Extracts standard FTMS instantaneous cadence and converts its 0.5-RPM units for console display. */
fun parseIndoorBikeConsoleRpm(payload: ByteArray): Int? {
    if (payload.size < 2) return null
    val flags = payload.u16(0)
    if (flags and INSTANTANEOUS_CADENCE_PRESENT == 0) return null
    var offset = 2
    if (flags and MORE_DATA == 0) offset += 2 // instantaneous speed
    if (flags and AVERAGE_SPEED_PRESENT != 0) offset += 2
    if (offset + 2 > payload.size) return null
    val halfRpm = payload.u16(offset)
    return (halfRpm / 2.0).roundToInt()
}

private fun ByteArray.u16(offset: Int): Int =
    (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

fun isFtmsCandidate(advertisedServices: Collection<UUID>): Boolean = FTMS_SERVICE in advertisedServices

fun mapFtmsCapabilities(characteristicUuids: Collection<UUID>) = FtmsCapabilities(
    hasCrossTrainerData = CROSS_TRAINER_DATA in characteristicUuids,
    hasIndoorBikeData = INDOOR_BIKE_DATA in characteristicUuids,
    hasTrainingStatus = TRAINING_STATUS in characteristicUuids,
    hasFitnessMachineStatus = FITNESS_MACHINE_STATUS in characteristicUuids,
    hasControlPoint = FITNESS_MACHINE_CONTROL_POINT in characteristicUuids,
)

fun characteristicPropertyNames(properties: Int): Set<String> = buildSet {
    if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
    if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
    if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
    if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
    if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NO_RESPONSE")
}

enum class CrossTrainerAction(val label: String) {
    FIND("Find cross trainer"),
    STOP("Stop"),
    CONNECT("Connect"),
    CANCEL("Cancel"),
    DISCONNECT("Disconnect"),
    RECONNECT("Reconnect"),
}

fun primaryCrossTrainerAction(state: FtmsDiagnosticState): CrossTrainerAction = when {
    state.connectionState == FtmsConnectionState.CONNECTED -> CrossTrainerAction.DISCONNECT
    state.connectionState == FtmsConnectionState.CONNECTING -> CrossTrainerAction.CANCEL
    state.isScanning -> CrossTrainerAction.STOP
    state.canReconnect && state.selectedDeviceAddress != null -> CrossTrainerAction.RECONNECT
    else -> CrossTrainerAction.FIND
}

fun shouldKeepScreenAwake(state: FtmsDiagnosticState): Boolean =
    state.connectionState == FtmsConnectionState.CONNECTED && state.currentConsoleRpm != null

fun unexpectedFtmsDisconnect(state: FtmsDiagnosticState, status: Int): FtmsDiagnosticState = state.copy(
    connectionState = FtmsConnectionState.DISCONNECTED,
    connectedDeviceName = null,
    currentConsoleRpm = null,
    averageConsoleRpm = null,
    canReconnect = state.selectedDeviceAddress != null,
    error = if (status == BluetoothGatt.GATT_SUCCESS) "Cross-trainer connection lost. Reconnect when ready."
        else "Cross-trainer connection lost ($status). Retry.",
)

class FtmsManager(
    context: Context,
    private val diagnosticsEnabled: Boolean = BuildConfig.FTMS_DIAGNOSTICS_ENABLED,
) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val _state = MutableStateFlow(FtmsDiagnosticState())
    val state: StateFlow<FtmsDiagnosticState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private val subscriptionQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private var rpmSession = ConsoleRpmSession()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val advertised = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }
            if (!isFtmsCandidate(advertised)) return
            val candidate = FtmsCandidate(
                address = result.device.address,
                name = result.device.name ?: result.scanRecord?.deviceName ?: "Fitness machine",
                rssi = result.rssi,
            )
            _state.update { current ->
                val candidates = current.candidates.filterNot { it.address == candidate.address } + candidate
                current.copy(candidates = candidates.sortedByDescending(FtmsCandidate::rssi), error = null)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update { it.copy(isScanning = false, error = "Cross-trainer scan failed ($errorCode). Try again.") }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _state.update { it.copy(error = "Bluetooth scanning is unavailable.") }
            return
        }
        stopScan()
        _state.update { it.copy(isScanning = true, candidates = emptyList(), error = null) }
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(FTMS_SERVICE)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback,
        )
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _state.update { it.copy(isScanning = false) }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val selectedName = _state.value.candidates.firstOrNull { it.address == address }?.name
            ?: _state.value.selectedDeviceName
        stopScan()
        disconnect()
        rpmSession = ConsoleRpmSession()
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            _state.update { it.copy(error = "That FTMS candidate is no longer available.") }
            return
        }
        val name = selectedName
        _state.update {
            it.copy(
                connectionState = FtmsConnectionState.CONNECTING,
                connectedDeviceName = name,
                selectedDeviceAddress = address,
                selectedDeviceName = name,
                canReconnect = false,
                services = emptyList(),
                capabilities = FtmsCapabilities(),
                fitnessMachineFeatureHex = null,
                rawNotifications = emptyList(),
                currentConsoleRpm = null,
                averageConsoleRpm = null,
                error = null,
            )
        }
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        rpmSession = ConsoleRpmSession()
        subscriptionQueue.clear()
        val activeGatt = gatt
        gatt = null
        activeGatt?.disconnect()
        activeGatt?.close()
        _state.update {
            it.copy(
                connectionState = FtmsConnectionState.DISCONNECTED,
                connectedDeviceName = null,
                selectedDeviceAddress = null,
                selectedDeviceName = null,
                canReconnect = false,
                services = emptyList(),
                capabilities = FtmsCapabilities(),
                fitnessMachineFeatureHex = null,
                rawNotifications = emptyList(),
                currentConsoleRpm = null,
                averageConsoleRpm = null,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                this@FtmsManager.gatt = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                rpmSession = ConsoleRpmSession()
                gatt.close()
                if (this@FtmsManager.gatt !== gatt) return
                this@FtmsManager.gatt = null
                _state.update { unexpectedFtmsDisconnect(it, status) }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt.getService(FTMS_SERVICE) == null) {
                _state.update { it.copy(error = "Selected device did not expose the standard FTMS service.") }
                gatt.disconnect()
                return
            }
            val diagnostics = if (diagnosticsEnabled) gatt.services.map { service ->
                FtmsServiceDiagnostic(
                    uuid = service.uuid,
                    characteristics = service.characteristics.map { characteristic ->
                        FtmsCharacteristicDiagnostic(characteristic.uuid, characteristicPropertyNames(characteristic.properties))
                    },
                )
            } else emptyList()
            val characteristicUuids = gatt.services.flatMap { service -> service.characteristics.map { it.uuid } }
            _state.update {
                it.copy(
                    connectionState = FtmsConnectionState.CONNECTED,
                    canReconnect = false,
                    services = diagnostics,
                    capabilities = mapFtmsCapabilities(characteristicUuids),
                    error = null,
                )
            }
            val feature = if (diagnosticsEnabled) {
                gatt.getService(FTMS_SERVICE)?.getCharacteristic(FITNESS_MACHINE_FEATURE)
            } else null
            if (feature != null && feature.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 && gatt.readCharacteristic(feature)) return
            queueSafeSubscriptions(gatt)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handleRead(gatt, characteristic, characteristic.value, status)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            handleRead(gatt, characteristic, value, status)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            subscribeNext(gatt)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            captureNotification(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            captureNotification(characteristic.uuid, value)
        }
    }

    private fun handleRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        if (characteristic.uuid == FITNESS_MACHINE_FEATURE && status == BluetoothGatt.GATT_SUCCESS) {
            _state.update { it.copy(fitnessMachineFeatureHex = value.toHex()) }
        }
        queueSafeSubscriptions(gatt)
    }

    private fun queueSafeSubscriptions(gatt: BluetoothGatt) {
        subscriptionQueue.clear()
        val ftms = gatt.getService(FTMS_SERVICE) ?: return
        SAFE_NOTIFICATION_UUIDS.mapNotNullTo(subscriptionQueue) { uuid ->
            ftms.getCharacteristic(uuid)?.takeIf { characteristic ->
                characteristic.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            }
        }
        subscribeNext(gatt)
    }

    @SuppressLint("MissingPermission")
    private fun subscribeNext(gatt: BluetoothGatt) {
        val characteristic = subscriptionQueue.removeFirstOrNull() ?: return
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG) ?: return subscribeNext(gatt)
        if (!gatt.setCharacteristicNotification(characteristic, true)) return subscribeNext(gatt)
        val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
        if (!started) subscribeNext(gatt)
    }

    private fun captureNotification(uuid: UUID, value: ByteArray) {
        val entry = if (diagnosticsEnabled) {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            "$time  ${uuid.shortUuid()}  ${value.toHex()}"
        } else null
        val rpm = if (uuid == INDOOR_BIKE_DATA) parseIndoorBikeConsoleRpm(value) else null
        if (rpm != null) rpmSession = rpmSession.add(rpm)
        _state.update {
            it.copy(
                rawNotifications = if (entry != null) {
                    (listOf(entry) + it.rawNotifications).take(MAX_LOG_ENTRIES)
                } else emptyList(),
                currentConsoleRpm = rpm ?: it.currentConsoleRpm,
                averageConsoleRpm = if (rpm != null) rpmSession.averageRpm else it.averageConsoleRpm,
            )
        }
    }

    fun close() {
        stopScan()
        disconnect()
    }
}

private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
private fun UUID.shortUuid(): String = toString().substring(4, 8).uppercase(Locale.US)

val FTMS_SERVICE: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
val FITNESS_MACHINE_FEATURE: UUID = UUID.fromString("00002acc-0000-1000-8000-00805f9b34fb")
val CROSS_TRAINER_DATA: UUID = UUID.fromString("00002ace-0000-1000-8000-00805f9b34fb")
val INDOOR_BIKE_DATA: UUID = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
val TRAINING_STATUS: UUID = UUID.fromString("00002ad3-0000-1000-8000-00805f9b34fb")
val FITNESS_MACHINE_STATUS: UUID = UUID.fromString("00002ada-0000-1000-8000-00805f9b34fb")
val FITNESS_MACHINE_CONTROL_POINT: UUID = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")
private val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
private val SAFE_NOTIFICATION_UUIDS = listOf(CROSS_TRAINER_DATA, INDOOR_BIKE_DATA, TRAINING_STATUS, FITNESS_MACHINE_STATUS)
private const val MAX_LOG_ENTRIES = 40
private const val MORE_DATA = 1 shl 0
private const val AVERAGE_SPEED_PRESENT = 1 shl 1
private const val INSTANTANEOUS_CADENCE_PRESENT = 1 shl 2
