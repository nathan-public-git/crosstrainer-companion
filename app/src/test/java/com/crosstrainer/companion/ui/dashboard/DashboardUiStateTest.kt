package com.crosstrainer.companion.ui.dashboard

import com.crosstrainer.companion.data.HeartRateMonitorState
import com.crosstrainer.companion.data.TrainingProfileState
import com.crosstrainer.companion.data.FtmsDiagnosticState
import com.crosstrainer.companion.model.HeartRateZone
import com.crosstrainer.companion.model.WorkoutMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardUiStateTest {
    @Test
    fun `pending device data maps to double dash placeholders`() {
        val state = DashboardUiState.from(WorkoutMetrics())

        assertEquals(MetricValue("--", "RPM"), state.currentCadence)
        assertEquals(MetricValue("--", "RPM"), state.averageCadence)
        assertEquals(MetricValue("--", "BPM"), state.currentHeartRate)
        assertEquals(MetricValue("--", "BPM"), state.averageHeartRate)
        assertEquals(HeartRateZone.UNAVAILABLE, state.currentHeartRateZone)
        assertEquals(HeartRateZone.UNAVAILABLE, state.averageHeartRateZone)
    }

    @Test
    fun `live heart rate populates heart metrics while cadence remains pending`() {
        val state = DashboardUiState.from(
            metrics = WorkoutMetrics(),
            heartRate = HeartRateMonitorState(currentBpm = 91, averageBpm = 88),
        )

        assertEquals(MetricValue("--", "RPM"), state.currentCadence)
        assertEquals(MetricValue("--", "RPM"), state.averageCadence)
        assertEquals(MetricValue("91", "BPM"), state.currentHeartRate)
        assertEquals(MetricValue("88", "BPM"), state.averageHeartRate)
        assertEquals(HeartRateZone.NO_PROFILE, state.currentHeartRateZone)
        assertEquals(HeartRateZone.NO_PROFILE, state.averageHeartRateZone)
    }

    @Test
    fun `saved profile maps live bpm to zone and estimated maximum`() {
        val state = DashboardUiState.from(
            metrics = WorkoutMetrics(),
            heartRate = HeartRateMonitorState(currentBpm = 144, averageBpm = 80),
            profile = TrainingProfileState(age = 40, hasSeenPrompt = true),
        )

        assertEquals(40, state.profileAge)
        assertEquals(180, state.estimatedMaximumHeartRate)
        assertEquals(HeartRateZone.VIGOROUS, state.currentHeartRateZone)
        assertEquals(HeartRateZone.BELOW_MODERATE, state.averageHeartRateZone)
    }

    @Test
    fun `live FTMS console rpm populates cadence metrics independently of heart rate`() {
        val state = DashboardUiState.from(
            metrics = WorkoutMetrics(),
            heartRate = HeartRateMonitorState(currentBpm = 90, averageBpm = 88),
            ftms = FtmsDiagnosticState(currentConsoleRpm = 30, averageConsoleRpm = 29),
        )

        assertEquals(MetricValue("30", "RPM"), state.currentCadence)
        assertEquals(MetricValue("29", "RPM"), state.averageCadence)
        assertEquals(MetricValue("90", "BPM"), state.currentHeartRate)
    }
}
