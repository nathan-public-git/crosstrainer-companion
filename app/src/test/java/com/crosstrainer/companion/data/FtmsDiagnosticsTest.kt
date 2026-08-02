package com.crosstrainer.companion.data

import android.bluetooth.BluetoothGatt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class FtmsDiagnosticsTest {
    @Test
    fun `candidate requires advertised fitness machine service`() {
        assertTrue(isFtmsCandidate(listOf(FTMS_SERVICE)))
        assertFalse(isFtmsCandidate(listOf(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"))))
        assertFalse(isFtmsCandidate(emptyList()))
    }

    @Test
    fun `capability mapping reports only discovered FTMS characteristics`() {
        val capabilities = mapFtmsCapabilities(
            listOf(CROSS_TRAINER_DATA, TRAINING_STATUS, FITNESS_MACHINE_CONTROL_POINT),
        )

        assertTrue(capabilities.hasCrossTrainerData)
        assertFalse(capabilities.hasIndoorBikeData)
        assertTrue(capabilities.hasTrainingStatus)
        assertFalse(capabilities.hasFitnessMachineStatus)
        assertTrue(capabilities.hasControlPoint)
    }

    @Test
    fun `captured indoor bike packet maps standard half rpm cadence to console rpm`() {
        val packet = hex("FC 1F 4A 01 3C 00 1A 00 79 01 00 01 00 09 00 05 00 29 00 48 01 05 6B 15 81 02 00 00")

        assertEquals(30, parseIndoorBikeConsoleRpm(packet))
    }

    @Test
    fun `cadence parser follows flags when speed fields are absent or present`() {
        assertEquals(25, parseIndoorBikeConsoleRpm(hex("05 00 32 00"))) // more-data + cadence
        assertEquals(20, parseIndoorBikeConsoleRpm(hex("06 00 10 00 20 00 28 00"))) // speed + average speed + cadence
        assertNull(parseIndoorBikeConsoleRpm(hex("00 00 10 00")))
    }

    @Test
    fun `cadence parser safely rejects truncated payloads`() {
        assertNull(parseIndoorBikeConsoleRpm(byteArrayOf()))
        assertNull(parseIndoorBikeConsoleRpm(hex("04 00 10")))
        assertNull(parseIndoorBikeConsoleRpm(hex("06 00 10 00 20 00 28")))
    }

    @Test
    fun `console rpm session averages samples and a fresh session is empty`() {
        val session = ConsoleRpmSession().add(30).add(31).add(32)

        assertEquals(31, session.averageRpm)
        assertNull(ConsoleRpmSession().averageRpm)
    }

    @Test
    fun `primary connection actions use normal product labels`() {
        assertEquals("Find cross trainer", primaryCrossTrainerAction(FtmsDiagnosticState()).label)
        assertEquals("Stop", primaryCrossTrainerAction(FtmsDiagnosticState(isScanning = true)).label)
        assertEquals("Cancel", primaryCrossTrainerAction(FtmsDiagnosticState(connectionState = FtmsConnectionState.CONNECTING)).label)
        assertEquals("Disconnect", primaryCrossTrainerAction(FtmsDiagnosticState(connectionState = FtmsConnectionState.CONNECTED)).label)
        assertEquals(
            "Reconnect",
            primaryCrossTrainerAction(FtmsDiagnosticState(selectedDeviceAddress = "41:11:B1:6D:87:98", canReconnect = true)).label,
        )
        assertEquals("Connect", CrossTrainerAction.CONNECT.label)
    }

    @Test
    fun `unexpected disconnect clears rpm and retains selected machine for reconnect`() {
        val connected = FtmsDiagnosticState(
            connectionState = FtmsConnectionState.CONNECTED,
            connectedDeviceName = "E95",
            selectedDeviceAddress = "41:11:B1:6D:87:98",
            selectedDeviceName = "E95",
            currentConsoleRpm = 30,
            averageConsoleRpm = 29,
        )

        val lost = unexpectedFtmsDisconnect(connected, BluetoothGatt.GATT_SUCCESS)

        assertEquals(FtmsConnectionState.DISCONNECTED, lost.connectionState)
        assertNull(lost.currentConsoleRpm)
        assertNull(lost.averageConsoleRpm)
        assertEquals("E95", lost.selectedDeviceName)
        assertEquals("41:11:B1:6D:87:98", lost.selectedDeviceAddress)
        assertTrue(lost.canReconnect)
        assertEquals(CrossTrainerAction.RECONNECT, primaryCrossTrainerAction(lost))
        assertFalse(shouldKeepScreenAwake(lost))
    }

    @Test
    fun `screen stays awake only for an active E95 data stream`() {
        assertFalse(shouldKeepScreenAwake(FtmsDiagnosticState(connectionState = FtmsConnectionState.CONNECTED)))
        assertFalse(shouldKeepScreenAwake(FtmsDiagnosticState(currentConsoleRpm = 30)))
        assertTrue(
            shouldKeepScreenAwake(
                FtmsDiagnosticState(connectionState = FtmsConnectionState.CONNECTED, currentConsoleRpm = 30),
            ),
        )
    }
}

private fun hex(value: String): ByteArray = value.split(" ").map { it.toInt(16).toByte() }.toByteArray()
