package com.crosstrainer.companion.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class HeartRateDevice(val address: String, val name: String)

data class HeartRateSample(val recordedAtMillis: Long, val bpm: Int)

enum class HeartRateConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

data class HeartRateMonitorState(
    val isScanning: Boolean = false,
    val devices: List<HeartRateDevice> = emptyList(),
    val connectionState: HeartRateConnectionState = HeartRateConnectionState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val currentBpm: Int? = null,
    val averageBpm: Int? = null,
    val recentSamples: List<HeartRateSample> = emptyList(),
    val error: String? = null,
)

class HeartRateMonitorManager(
    context: Context,
    private val onSessionCompleted: (averageBpm: Int) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val _state = MutableStateFlow(HeartRateMonitorState())
    val state: StateFlow<HeartRateMonitorState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var session = HeartRateSession()

    val isBluetoothAvailable: Boolean get() = adapter != null
    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = HeartRateDevice(
                address = result.device.address,
                name = result.device.name ?: result.scanRecord?.deviceName ?: "Heart-rate monitor",
            )
            _state.update { current ->
                if (current.devices.any { it.address == device.address }) current
                else current.copy(devices = current.devices + device, error = null)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update { it.copy(isScanning = false, error = "Bluetooth scan failed ($errorCode). Try again.") }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _state.update { it.copy(error = "Bluetooth scanning is unavailable. Check that Bluetooth is on.") }
            return
        }
        stopScan()
        _state.update { it.copy(isScanning = true, devices = emptyList(), error = null) }
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE)).build()),
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
        stopScan()
        disconnect()
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            _state.update { it.copy(error = "That heart-rate monitor is no longer available.") }
            return
        }
        session = HeartRateSession()
        _state.update {
            it.copy(
                connectionState = HeartRateConnectionState.CONNECTING,
                connectedDeviceName = it.devices.firstOrNull { item -> item.address == address }?.name,
                currentBpm = null,
                averageBpm = null,
                recentSamples = emptyList(),
                error = null,
            )
        }
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        completeSession()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.update {
            it.copy(
                connectionState = HeartRateConnectionState.DISCONNECTED,
                connectedDeviceName = null,
                currentBpm = null,
                averageBpm = null,
                recentSamples = emptyList(),
            )
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                this@HeartRateMonitorManager.gatt = gatt
                _state.update { it.copy(connectionState = HeartRateConnectionState.CONNECTING, error = null) }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                completeSession()
                gatt.close()
                if (this@HeartRateMonitorManager.gatt === gatt) this@HeartRateMonitorManager.gatt = null
                _state.update {
                    it.copy(
                        connectionState = HeartRateConnectionState.DISCONNECTED,
                        connectedDeviceName = null,
                        currentBpm = null,
                        averageBpm = null,
                        recentSamples = emptyList(),
                        error = if (status == BluetoothGatt.GATT_SUCCESS) it.error else "Connection lost ($status). Retry the monitor.",
                    )
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val measurement = gatt.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT)
            if (status != BluetoothGatt.GATT_SUCCESS || measurement == null) {
                _state.update { it.copy(error = "The device does not expose the Heart Rate Service.") }
                gatt.disconnect()
                return
            }
            val descriptor = measurement.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
            if (!gatt.setCharacteristicNotification(measurement, true) || descriptor == null) {
                _state.update { it.copy(error = "Could not enable heart-rate notifications.") }
                gatt.disconnect()
                return
            }
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            if (!started) {
                _state.update { it.copy(error = "Could not subscribe to heart-rate measurements.") }
                gatt.disconnect()
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG && status == BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(connectionState = HeartRateConnectionState.CONNECTED, error = null) }
            } else if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG) {
                _state.update { it.copy(error = "Heart-rate subscription failed ($status).") }
                gatt.disconnect()
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleMeasurement(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleMeasurement(value)
        }
    }

    private fun handleMeasurement(value: ByteArray) {
        val bpm = HeartRateMeasurementParser.parseBpm(value) ?: return
        session = session.add(bpm)
        val now = System.currentTimeMillis()
        _state.update { current ->
            val samples = (current.recentSamples + HeartRateSample(now, bpm))
                .dropWhile { it.recordedAtMillis < now - HISTORY_WINDOW_MILLIS }
            current.copy(currentBpm = bpm, averageBpm = session.averageBpm, recentSamples = samples, error = null)
        }
    }

    private fun completeSession() {
        val averageBpm = session.averageBpm ?: return
        session = HeartRateSession()
        onSessionCompleted(averageBpm)
    }

    fun close() {
        stopScan()
        disconnect()
    }

    companion object {
        private const val HISTORY_WINDOW_MILLIS = 10 * 60 * 1000L
        private val HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
