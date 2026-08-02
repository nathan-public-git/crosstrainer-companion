package com.crosstrainer.companion.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crosstrainer.companion.data.HeartRateMonitorManager
import com.crosstrainer.companion.data.FtmsManager
import com.crosstrainer.companion.data.PendingWorkoutMetricsSource
import com.crosstrainer.companion.data.TrainingProfileStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val heartRateMonitor = HeartRateMonitorManager(application)
    private val metricsSource = PendingWorkoutMetricsSource()
    private val trainingProfile = TrainingProfileStore(application)
    private val ftmsManager = FtmsManager(application)
    val ftmsState = ftmsManager.state

    val uiState: StateFlow<DashboardUiState> = combine(metricsSource.metrics, heartRateMonitor.state, trainingProfile.state, ftmsManager.state) {
            metrics, heartRate, profile, ftms -> DashboardUiState.from(metrics, heartRate, profile, ftms)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState.Empty,
        )

    val isBluetoothAvailable: Boolean get() = heartRateMonitor.isBluetoothAvailable
    val isBluetoothEnabled: Boolean get() = heartRateMonitor.isBluetoothEnabled
    val shouldPromptForTrainingProfile: Boolean get() = !trainingProfile.state.value.hasSeenPrompt

    fun scanForHeartRateMonitors() = heartRateMonitor.startScan()
    fun stopHeartRateScan() = heartRateMonitor.stopScan()
    fun connectHeartRateMonitor(address: String) = heartRateMonitor.connect(address)
    fun disconnectHeartRateMonitor() = heartRateMonitor.disconnect()
    fun saveTrainingProfile(age: Int) = trainingProfile.saveAge(age)
    fun clearTrainingProfile() = trainingProfile.clearAge()
    fun dismissTrainingProfilePrompt() = trainingProfile.markPromptSeen()
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
