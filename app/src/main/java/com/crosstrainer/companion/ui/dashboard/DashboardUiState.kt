package com.crosstrainer.companion.ui.dashboard

import com.crosstrainer.companion.data.HeartRateConnectionState
import com.crosstrainer.companion.data.HeartRateDevice
import com.crosstrainer.companion.data.HeartRateMonitorState
import com.crosstrainer.companion.data.HeartRateSample
import com.crosstrainer.companion.data.FtmsDiagnosticState
import com.crosstrainer.companion.data.TrainingProfileState
import com.crosstrainer.companion.model.HeartRateZone
import com.crosstrainer.companion.model.WorkoutMetrics
import com.crosstrainer.companion.model.estimatedMaximumHeartRate
import com.crosstrainer.companion.model.heartRateZone

data class DashboardUiState(
    val currentCadence: MetricValue,
    val averageCadence: MetricValue,
    val currentHeartRate: MetricValue,
    val averageHeartRate: MetricValue,
    val isScanning: Boolean = false,
    val heartRateDevices: List<HeartRateDevice> = emptyList(),
    val heartRateConnectionState: HeartRateConnectionState = HeartRateConnectionState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val heartRateError: String? = null,
    val profileAge: Int? = null,
    val estimatedMaximumHeartRate: Int? = null,
    val currentHeartRateZone: HeartRateZone = HeartRateZone.UNAVAILABLE,
    val averageHeartRateZone: HeartRateZone = HeartRateZone.UNAVAILABLE,
    val recentHeartRateSamples: List<HeartRateSample> = emptyList(),
    val activeWorkoutDurationSeconds: Long = 0,
) {
    companion object {
        fun from(
            metrics: WorkoutMetrics,
            heartRate: HeartRateMonitorState = HeartRateMonitorState(),
            profile: TrainingProfileState = TrainingProfileState(),
            ftms: FtmsDiagnosticState = FtmsDiagnosticState(),
        ): DashboardUiState {
            val currentBpm = heartRate.currentBpm ?: metrics.currentHeartRateBpm
            val averageBpm = heartRate.averageBpm ?: metrics.averageHeartRateBpm
            return DashboardUiState(
            currentCadence = MetricValue((ftms.currentConsoleRpm ?: metrics.currentCadenceRpm).displayValue(), "RPM"),
            averageCadence = MetricValue((ftms.averageConsoleRpm ?: metrics.averageCadenceRpm).displayValue(), "RPM"),
            currentHeartRate = MetricValue(currentBpm.displayValue(), "BPM"),
            averageHeartRate = MetricValue(averageBpm.displayValue(), "BPM"),
            isScanning = heartRate.isScanning,
            heartRateDevices = heartRate.devices,
            heartRateConnectionState = heartRate.connectionState,
            connectedDeviceName = heartRate.connectedDeviceName,
            heartRateError = heartRate.error,
            profileAge = profile.age,
            estimatedMaximumHeartRate = profile.age?.let(::estimatedMaximumHeartRate),
            currentHeartRateZone = heartRateZone(currentBpm, profile.age),
            averageHeartRateZone = heartRateZone(averageBpm, profile.age),
            recentHeartRateSamples = heartRate.recentSamples,
        )
        }

        val Empty = from(WorkoutMetrics())
    }
}

private fun Int?.displayValue(): String = this?.toString() ?: "--"

data class MetricValue(
    val value: String,
    val unit: String,
)
