package com.crosstrainer.companion.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crosstrainer.companion.data.HeartRateMonitorManager
import com.crosstrainer.companion.data.HeartRateSessionStore
import com.crosstrainer.companion.data.SavedHeartRateSession
import com.crosstrainer.companion.data.FtmsManager
import com.crosstrainer.companion.data.DailyWorkoutDurationStore
import com.crosstrainer.companion.data.DailyHealthMetricsStore
import com.crosstrainer.companion.data.DailyHealthMetrics
import com.crosstrainer.companion.data.DailyHealthMetricsEntry
import com.crosstrainer.companion.data.PendingWorkoutMetricsSource
import com.crosstrainer.companion.data.TrainingProfileStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val heartRateSessions = HeartRateSessionStore(application)
    private val heartRateMonitor = HeartRateMonitorManager(application) { averageBpm ->
        heartRateSessions.save(averageBpm)
    }
    private val metricsSource = PendingWorkoutMetricsSource()
    private val trainingProfile = TrainingProfileStore(application)
    private val ftmsManager = FtmsManager(application)
    private val dailyWorkoutDuration = DailyWorkoutDurationStore(application)
    private val dailyHealthMetrics = DailyHealthMetricsStore(application)
    private val workoutDurationSeconds = MutableStateFlow(dailyWorkoutDuration.elapsedSeconds())
    val ftmsState = ftmsManager.state

    private var isPedaling = false

    init {
        viewModelScope.launch {
            combine(metricsSource.metrics, ftmsManager.state) { metrics, ftms ->
                ftms.currentConsoleRpm ?: metrics.currentCadenceRpm
            }.collect { cadence ->
                isPedaling = (cadence ?: 0) > 0
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val duration = if (isPedaling) dailyWorkoutDuration.addSecond() else dailyWorkoutDuration.elapsedSeconds()
                workoutDurationSeconds.value = duration
            }
        }
    }

    private val dashboardState = combine(metricsSource.metrics, heartRateMonitor.state, trainingProfile.state, ftmsManager.state) {
            metrics, heartRate, profile, ftms -> DashboardUiState.from(metrics, heartRate, profile, ftms)
        }

    val uiState: StateFlow<DashboardUiState> = combine(dashboardState, workoutDurationSeconds) { state, duration ->
            state.copy(activeWorkoutDurationSeconds = duration)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState.Empty,
        )

    val isBluetoothAvailable: Boolean get() = heartRateMonitor.isBluetoothAvailable
    val isBluetoothEnabled: Boolean get() = heartRateMonitor.isBluetoothEnabled
    val shouldPromptForTrainingProfile: Boolean get() = !trainingProfile.state.value.hasSeenPrompt
    val shouldPromptForDailyHealthMetrics: Boolean get() = dailyHealthMetrics.needsPromptToday()
    val todayDailyHealthMetrics: DailyHealthMetrics get() = dailyHealthMetrics.todayMetrics()
    val dailyHealthMetricsHistory: List<DailyHealthMetricsEntry> get() = dailyHealthMetrics.history()
    val heartRateSessionHistory: List<SavedHeartRateSession> get() = heartRateSessions.history()

    fun scanForHeartRateMonitors() = heartRateMonitor.startScan()
    fun stopHeartRateScan() = heartRateMonitor.stopScan()
    fun connectHeartRateMonitor(address: String) = heartRateMonitor.connect(address)
    fun disconnectHeartRateMonitor() = heartRateMonitor.disconnect()
    fun saveTrainingProfile(age: Int) = trainingProfile.saveAge(age)
    fun clearTrainingProfile() = trainingProfile.clearAge()
    fun dismissTrainingProfilePrompt() = trainingProfile.markPromptSeen()
    fun saveDailyHealthMetrics(weight: Double?, bloodSugar: Double?) = dailyHealthMetrics.save(weight, bloodSugar)
    fun dismissDailyHealthMetricsPrompt() = dailyHealthMetrics.dismissToday()
    fun scanForCrossTrainers() = ftmsManager.startScan()
    fun stopCrossTrainerScan() = ftmsManager.stopScan()
    fun connectCrossTrainer(address: String) = ftmsManager.connect(address)
    fun disconnectCrossTrainer() = ftmsManager.disconnect()

    override fun onCleared() {
        heartRateMonitor.close()
        ftmsManager.close()
        super.onCleared()
    }
}
