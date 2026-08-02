package com.crosstrainer.companion.ui.dashboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.crosstrainer.companion.BuildConfig
import com.crosstrainer.companion.data.HeartRateConnectionState
import com.crosstrainer.companion.data.HeartRateDevice
import com.crosstrainer.companion.data.FtmsCandidate
import com.crosstrainer.companion.data.FtmsConnectionState
import com.crosstrainer.companion.data.FtmsDiagnosticState
import com.crosstrainer.companion.data.TrainingProfileStore
import com.crosstrainer.companion.data.shouldKeepScreenAwake
import com.crosstrainer.companion.model.HeartRateZone
import com.crosstrainer.companion.model.HeartRateZoneRange
import com.crosstrainer.companion.model.heartRateZoneRanges
import com.crosstrainer.companion.model.WorkoutMetrics
import com.crosstrainer.companion.ui.theme.CrosstrainerCompanionTheme
import com.crosstrainer.companion.ui.theme.ZoneBelow
import com.crosstrainer.companion.ui.theme.ZoneHigh
import com.crosstrainer.companion.ui.theme.ZoneModerate
import com.crosstrainer.companion.ui.theme.ZoneVigorous

@Composable
fun DashboardRoute(viewModel: DashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ftmsState by viewModel.ftmsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val keepScreenAwake = shouldKeepScreenAwake(ftmsState)
    DisposableEffect(view, keepScreenAwake) {
        view.keepScreenOn = keepScreenAwake
        onDispose { view.keepScreenOn = false }
    }
    var guidance by remember { mutableStateOf<String?>(null) }
    var ftmsGuidance by remember { mutableStateOf<String?>(null) }
    var pendingScan by remember { mutableStateOf(ScanTarget.HEART_RATE) }
    var showTrainingProfile by rememberSaveable { mutableStateOf(viewModel.shouldPromptForTrainingProfile) }
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    fun hasPermissions() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    fun runPendingScan() {
        when (pendingScan) {
            ScanTarget.HEART_RATE -> viewModel.scanForHeartRateMonitors()
            ScanTarget.CROSS_TRAINER -> viewModel.scanForCrossTrainers()
        }
    }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (viewModel.isBluetoothEnabled) {
            guidance = null
            ftmsGuidance = null
            runPendingScan()
        } else if (pendingScan == ScanTarget.HEART_RATE) guidance = "Bluetooth must be enabled to find your Polar H10."
        else ftmsGuidance = "Bluetooth must be enabled to find the cross trainer."
    }
    val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            guidance = null
            ftmsGuidance = null
            if (viewModel.isBluetoothEnabled) runPendingScan()
            else enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            guidance = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                "Android 11 requires location permission to scan for nearby Bluetooth devices."
            } else "Nearby devices permission is required for Bluetooth scanning."
            if (pendingScan == ScanTarget.CROSS_TRAINER) {
                ftmsGuidance = guidance
                guidance = null
            }
        }
    }
    fun beginScan(target: ScanTarget) {
        pendingScan = target
        when {
            !viewModel.isBluetoothAvailable -> guidance = "This device does not support Bluetooth Low Energy."
            !hasPermissions() -> requestPermissions.launch(permissions)
            !viewModel.isBluetoothEnabled -> enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> {
                guidance = null
                ftmsGuidance = null
                runPendingScan()
            }
        }
    }
    DashboardScreen(
        uiState = uiState,
        ftmsState = ftmsState,
        bluetoothGuidance = guidance,
        onScan = { beginScan(ScanTarget.HEART_RATE) },
        onStopScan = viewModel::stopHeartRateScan,
        onConnect = viewModel::connectHeartRateMonitor,
        onDisconnect = viewModel::disconnectHeartRateMonitor,
        onOpenTrainingProfile = { showTrainingProfile = true },
        ftmsGuidance = ftmsGuidance,
        onFtmsScan = { beginScan(ScanTarget.CROSS_TRAINER) },
        onFtmsStopScan = viewModel::stopCrossTrainerScan,
        onFtmsConnect = viewModel::connectCrossTrainer,
        onFtmsDisconnect = viewModel::disconnectCrossTrainer,
    )
    if (showTrainingProfile) {
        TrainingProfileDialog(
            savedAge = uiState.profileAge,
            onSave = { age ->
                viewModel.saveTrainingProfile(age)
                showTrainingProfile = false
            },
            onClear = {
                viewModel.clearTrainingProfile()
                showTrainingProfile = false
            },
            onDismiss = {
                viewModel.dismissTrainingProfilePrompt()
                showTrainingProfile = false
            },
        )
    }
}

private enum class ScanTarget { HEART_RATE, CROSS_TRAINER }

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    ftmsState: FtmsDiagnosticState = FtmsDiagnosticState(),
    modifier: Modifier = Modifier,
    bluetoothGuidance: String? = null,
    onScan: () -> Unit = {},
    onStopScan: () -> Unit = {},
    onConnect: (String) -> Unit = {},
    onDisconnect: () -> Unit = {},
    onOpenTrainingProfile: () -> Unit = {},
    ftmsGuidance: String? = null,
    onFtmsScan: () -> Unit = {},
    onFtmsStopScan: () -> Unit = {},
    onFtmsConnect: (String) -> Unit = {},
    onFtmsDisconnect: () -> Unit = {},
) {
    var zoneGuideMetric by rememberSaveable { mutableStateOf<HeartRateMetric?>(null) }
    var showFtmsDiagnostics by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            val portraitMetricHeight = maxHeight * 0.58f
            if (maxWidth > maxHeight) {
                Column(Modifier.fillMaxSize()) {
                    DashboardHeader(onOpenTrainingProfile)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MetricGrid(
                            uiState = uiState,
                            modifier = Modifier.weight(2f).fillMaxHeight(),
                            onCurrentHeartRateClick = { zoneGuideMetric = HeartRateMetric.CURRENT },
                            onAverageHeartRateClick = { zoneGuideMetric = HeartRateMetric.AVERAGE },
                        )
                        Column(Modifier.weight(1f)) {
                            HeartRateMonitorPanel(
                                uiState = uiState,
                                guidance = bluetoothGuidance,
                                onScan = onScan,
                                onStopScan = onStopScan,
                                onConnect = onConnect,
                                onDisconnect = onDisconnect,
                            )
                            Spacer(Modifier.height(8.dp))
                            CrossTrainerPanel(
                                state = ftmsState,
                                guidance = ftmsGuidance,
                                onScan = onFtmsScan,
                                onStopScan = onFtmsStopScan,
                                onConnect = onFtmsConnect,
                                onDisconnect = onFtmsDisconnect,
                                onDiagnostics = { showFtmsDiagnostics = true },
                            )
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    DashboardHeader(onOpenTrainingProfile)
                    Spacer(Modifier.height(12.dp))
                    MetricGrid(
                        uiState = uiState,
                        modifier = Modifier.fillMaxWidth().height(portraitMetricHeight),
                        onCurrentHeartRateClick = { zoneGuideMetric = HeartRateMetric.CURRENT },
                        onAverageHeartRateClick = { zoneGuideMetric = HeartRateMetric.AVERAGE },
                    )
                    // A stable half-line breathing gap keeps controls clear of the BPM row.
                    Spacer(Modifier.height(8.dp))
                    HeartRateMonitorPanel(
                        uiState = uiState,
                        guidance = bluetoothGuidance,
                        onScan = onScan,
                        onStopScan = onStopScan,
                        onConnect = onConnect,
                        onDisconnect = onDisconnect,
                    )
                    Spacer(Modifier.height(8.dp))
                    CrossTrainerPanel(
                        state = ftmsState,
                        guidance = ftmsGuidance,
                        onScan = onFtmsScan,
                        onStopScan = onFtmsStopScan,
                        onConnect = onFtmsConnect,
                        onDisconnect = onFtmsDisconnect,
                        onDiagnostics = { showFtmsDiagnostics = true },
                    )
                }
            }
        }
    }
    zoneGuideMetric?.let { metric ->
        HeartRateZoneGuideDialog(uiState = uiState, metric = metric, onDismiss = { zoneGuideMetric = null })
    }
    if (BuildConfig.FTMS_DIAGNOSTICS_ENABLED && showFtmsDiagnostics) {
        FtmsDiagnosticDialog(state = ftmsState, onDismiss = { showFtmsDiagnostics = false })
    }
}

private enum class HeartRateMetric(val label: String) {
    CURRENT("Current heart rate"),
    AVERAGE("Average heart rate"),
}

@Composable
private fun MetricGrid(
    uiState: DashboardUiState,
    modifier: Modifier = Modifier,
    onCurrentHeartRateClick: () -> Unit,
    onAverageHeartRateClick: () -> Unit,
) {
    Column(modifier) {
        MetricRow(
            modifier = Modifier.weight(1f),
            firstLabel = "CURRENT CADENCE",
            firstMetric = uiState.currentCadence,
            secondLabel = "AVERAGE CADENCE",
            secondMetric = uiState.averageCadence,
        )
        Spacer(Modifier.height(14.dp))
        MetricRow(
            modifier = Modifier.weight(1f),
            firstLabel = "CURRENT HEART RATE",
            firstMetric = uiState.currentHeartRate,
            secondLabel = "AVERAGE HEART RATE",
            secondMetric = uiState.averageHeartRate,
            firstValueColor = zoneColor(uiState.currentHeartRateZone),
            firstDetail = zoneLabel(uiState.currentHeartRateZone, uiState.profileAge),
            onFirstClick = onCurrentHeartRateClick,
            secondValueColor = zoneColor(uiState.averageHeartRateZone),
            secondDetail = zoneLabel(uiState.averageHeartRateZone, uiState.profileAge),
            onSecondClick = onAverageHeartRateClick,
        )
    }
}

@Composable
private fun HeartRateMonitorPanel(
    uiState: DashboardUiState,
    guidance: String?,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HEART-RATE MONITOR", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    val status = when (uiState.heartRateConnectionState) {
                        HeartRateConnectionState.CONNECTED -> "Connected to ${uiState.connectedDeviceName ?: "monitor"}"
                        HeartRateConnectionState.CONNECTING -> "Connecting…"
                        HeartRateConnectionState.DISCONNECTED -> if (uiState.isScanning) "Scanning nearby…" else "Polar H10 connects without pairing"
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    uiState.heartRateConnectionState != HeartRateConnectionState.DISCONNECTED ->
                        OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                    uiState.isScanning -> {
                        CircularProgressIndicator(Modifier.width(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onStopScan) { Text("Stop") }
                    }
                    else -> Button(onClick = onScan) { Text("Scan") }
                }
            }
            val message = guidance ?: uiState.heartRateError
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (uiState.heartRateConnectionState == HeartRateConnectionState.DISCONNECTED) {
                uiState.heartRateDevices.forEach { device ->
                    DeviceRow(device = device, onConnect = onConnect)
                }
                if (uiState.isScanning && uiState.heartRateDevices.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Keep the H10 awake and nearby. Devices appear here when found.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CrossTrainerPanel(
    state: FtmsDiagnosticState,
    guidance: String?,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CROSS TRAINER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    val status = when (state.connectionState) {
                        FtmsConnectionState.CONNECTED -> if (state.currentConsoleRpm != null) {
                            "Live Console RPM from ${state.connectedDeviceName ?: "fitness machine"}"
                        } else "FTMS connected: ${state.connectedDeviceName ?: "fitness machine"}"
                        FtmsConnectionState.CONNECTING -> "Connecting..."
                        FtmsConnectionState.DISCONNECTED -> when {
                            state.isScanning -> "Looking for compatible cross trainers..."
                            state.canReconnect -> "Connection lost: ${state.selectedDeviceName ?: "cross trainer"}"
                            else -> "Ready to connect"
                        }
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    state.connectionState == FtmsConnectionState.CONNECTED -> Row {
                        if (BuildConfig.FTMS_DIAGNOSTICS_ENABLED) {
                            TextButton(onClick = onDiagnostics) { Text(if (state.currentConsoleRpm == null) "Diagnostics" else "Debug") }
                        }
                        OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                    }
                    state.connectionState == FtmsConnectionState.CONNECTING ->
                        OutlinedButton(onClick = onDisconnect) { Text("Cancel") }
                    state.isScanning ->
                        OutlinedButton(onClick = onStopScan) { Text("Stop") }
                    state.canReconnect && state.selectedDeviceAddress != null ->
                        Button(onClick = { onConnect(state.selectedDeviceAddress) }) { Text("Reconnect") }
                    else -> Button(onClick = onScan) { Text("Find cross trainer") }
                }
            }
            val message = guidance ?: state.error
            if (message != null) {
                Spacer(Modifier.height(6.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (state.connectionState == FtmsConnectionState.DISCONNECTED) {
                if (state.canReconnect) {
                    TextButton(onClick = onScan) { Text("Find another cross trainer") }
                }
                if (state.isScanning) {
                    state.candidates.forEach { candidate ->
                        FtmsCandidateRow(candidate, onConnect)
                    }
                }
            }
        }
    }
}

@Composable
private fun FtmsCandidateRow(candidate: FtmsCandidate, onConnect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(candidate.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text("FTMS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text("${candidate.address}  •  ${candidate.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { onConnect(candidate.address) }) { Text("Connect") }
    }
}

@Composable
private fun FtmsDiagnosticDialog(state: FtmsDiagnosticState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("FTMS diagnostics") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${state.connectedDeviceName ?: "Fitness machine"} • read-only inspection", fontWeight = FontWeight.SemiBold)
                Text("No control-point writes or machine commands are issued.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Candidate decoded Console RPM", fontWeight = FontWeight.SemiBold)
                Text(
                    state.currentConsoleRpm?.let { "$it RPM (standard 2AD2 cadence field)" }
                        ?: "Waiting for Indoor Bike Data",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Validate against the E95 console; raw packets remain below.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text("Fitness Machine Feature (2ACC)", fontWeight = FontWeight.SemiBold)
                Text(state.fitnessMachineFeatureHex ?: "Unavailable or not readable", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Text("Key FTMS characteristics", fontWeight = FontWeight.SemiBold)
                CapabilityLine("Cross Trainer Data", "2ACE", state.capabilities.hasCrossTrainerData)
                CapabilityLine("Indoor Bike Data", "2AD2", state.capabilities.hasIndoorBikeData)
                CapabilityLine("Training Status", "2AD3", state.capabilities.hasTrainingStatus)
                CapabilityLine("Fitness Machine Status", "2ADA", state.capabilities.hasFitnessMachineStatus)
                CapabilityLine("Control Point (never written)", "2AD9", state.capabilities.hasControlPoint)
                Spacer(Modifier.height(10.dp))
                Text("Discovered services", fontWeight = FontWeight.SemiBold)
                state.services.forEach { service ->
                    Text(service.uuid.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    service.characteristics.forEach { characteristic ->
                        Text("  ${characteristic.uuid}  [${characteristic.properties.joinToString()}]", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Raw notifications (newest first, max 40)", fontWeight = FontWeight.SemiBold)
                Text("For parser correlation, record the E95 Console RPM beside these timestamps. RPM meaning/scaling is not assumed yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.rawNotifications.isEmpty()) {
                    Text("No safe data/status notifications captured yet. Keep the E95 moving.", style = MaterialTheme.typography.bodySmall)
                } else state.rawNotifications.forEach { entry ->
                    Text(entry, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun CapabilityLine(name: String, shortUuid: String, available: Boolean) {
    Text(
        "${if (available) "✓" else "—"}  $name ($shortUuid)",
        style = MaterialTheme.typography.bodySmall,
        color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DeviceRow(device: HeartRateDevice, onConnect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { onConnect(device.address) }) { Text("Connect") }
    }
}

@Composable
private fun DashboardHeader(onOpenTrainingProfile: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "WORKOUT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Crosstrainer Companion",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onOpenTrainingProfile) { Text("Training profile") }
    }
}

@Composable
private fun MetricRow(
    firstLabel: String,
    firstMetric: MetricValue,
    secondLabel: String,
    secondMetric: MetricValue,
    modifier: Modifier = Modifier,
    firstValueColor: Color? = null,
    firstDetail: String? = null,
    onFirstClick: (() -> Unit)? = null,
    secondValueColor: Color? = null,
    secondDetail: String? = null,
    onSecondClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MetricCard(firstLabel, firstMetric, Modifier.weight(1f).fillMaxHeight(), firstValueColor, firstDetail, onFirstClick)
        MetricCard(secondLabel, secondMetric, Modifier.weight(1f).fillMaxHeight(), secondValueColor, secondDetail, onSecondClick)
    }
}

@Composable
private fun MetricCard(
    label: String,
    metric: MetricValue,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val cardModifier = if (onClick == null) modifier else modifier
        .clickable(onClick = onClick, role = Role.Button)
        .semantics {
            role = Role.Button
            contentDescription = "$label. Open personalized heart-rate zone guide."
        }
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(18.dp)),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (detail != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(detail, color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = metric.value,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = metric.unit,
                    modifier = Modifier.padding(bottom = 9.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun zoneColor(zone: HeartRateZone): Color? = when (zone) {
    HeartRateZone.BELOW_MODERATE -> ZoneBelow
    HeartRateZone.MODERATE -> ZoneModerate
    HeartRateZone.VIGOROUS -> ZoneVigorous
    HeartRateZone.HIGH -> ZoneHigh
    HeartRateZone.UNAVAILABLE, HeartRateZone.NO_PROFILE -> null
}

@Composable
private fun HeartRateZoneGuideDialog(
    uiState: DashboardUiState,
    metric: HeartRateMetric,
    onDismiss: () -> Unit,
) {
    val activeZone = when (metric) {
        HeartRateMetric.CURRENT -> uiState.currentHeartRateZone
        HeartRateMetric.AVERAGE -> uiState.averageHeartRateZone
    }
    val ranges = uiState.profileAge?.let(::heartRateZoneRanges)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Heart-rate zone guide") },
        text = {
            Column {
                if (ranges == null) {
                    Text("Save your age in Training profile to calculate personal BPM ranges. Percentages are shown without inventing thresholds.")
                } else {
                    Text("${metric.label} • Age ${uiState.profileAge} • Estimated max ${uiState.estimatedMaximumHeartRate} BPM")
                }
                Spacer(Modifier.height(12.dp))
                ZONE_ORDER.forEach { zone ->
                    ZoneGuideRow(
                        zone = zone,
                        range = ranges?.first { it.zone == zone },
                        isActive = zone == activeZone,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Colors estimate workout intensity only. They are not medical guidance or a safety assessment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun ZoneGuideRow(
    zone: HeartRateZone,
    range: HeartRateZoneRange?,
    isActive: Boolean,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .then(if (isActive) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
        .padding(horizontal = 10.dp, vertical = 8.dp)
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(zoneColor(zone) ?: MaterialTheme.colorScheme.onSurface),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(zoneGuideLabel(zone), fontWeight = FontWeight.SemiBold)
            Text(
                range?.let(::formatBpmRange) ?: "Save age to calculate BPM range",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isActive) Text("ACTIVE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

private fun zoneGuideLabel(zone: HeartRateZone): String = when (zone) {
    HeartRateZone.BELOW_MODERATE -> "Neutral • Below 50%"
    HeartRateZone.MODERATE -> "Green • 50% to under 70%"
    HeartRateZone.VIGOROUS -> "Yellow • 70% through 85%"
    HeartRateZone.HIGH -> "Red • Above 85%"
    HeartRateZone.UNAVAILABLE, HeartRateZone.NO_PROFILE -> error("Not a display zone")
}

private fun formatBpmRange(range: HeartRateZoneRange): String = when (range.zone) {
    HeartRateZone.BELOW_MODERATE -> "Below ${(range.maximumBpm ?: 0) + 1} BPM"
    HeartRateZone.HIGH -> "${range.minimumBpm}+ BPM"
    else -> "${range.minimumBpm}–${range.maximumBpm} BPM"
}

private val ZONE_ORDER = listOf(
    HeartRateZone.BELOW_MODERATE,
    HeartRateZone.MODERATE,
    HeartRateZone.VIGOROUS,
    HeartRateZone.HIGH,
)

private fun zoneLabel(zone: HeartRateZone, profileAge: Int?): String? = when (zone) {
    HeartRateZone.UNAVAILABLE -> profileAge?.let { "AGE $it • ZONE GUIDE ›" } ?: "ZONE GUIDE ›"
    HeartRateZone.NO_PROFILE -> "NO PROFILE • INTENSITY COLOR OFF"
    HeartRateZone.BELOW_MODERATE -> "BELOW 50% • ZONE GUIDE ›"
    HeartRateZone.MODERATE -> "MODERATE 50–70% • GUIDE ›"
    HeartRateZone.VIGOROUS -> "VIGOROUS 70–85% • GUIDE ›"
    HeartRateZone.HIGH -> "HIGH >85% • ZONE GUIDE ›"
}

@Composable
private fun TrainingProfileDialog(
    savedAge: Int?,
    onSave: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var ageText by rememberSaveable(savedAge) { mutableStateOf(savedAge?.toString().orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Training profile") },
        text = {
            Column {
                Text("Your age estimates exercise-intensity zones using 220 minus age. Colors describe workout intensity only and are not medical guidance.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ageText,
                    onValueChange = {
                        ageText = it.filter(Char::isDigit).take(3)
                        error = null
                    },
                    label = { Text("Age") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "Adults ${TrainingProfileStore.MINIMUM_AGE}–${TrainingProfileStore.MAXIMUM_AGE}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val age = ageText.toIntOrNull()
                if (age == null) error = "Enter an age to save, or choose Not now."
                else if (age !in TrainingProfileStore.MINIMUM_AGE..TrainingProfileStore.MAXIMUM_AGE) {
                    error = "Enter an age from ${TrainingProfileStore.MINIMUM_AGE} to ${TrainingProfileStore.MAXIMUM_AGE}."
                } else onSave(age)
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (savedAge != null) TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        },
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 732)
@Composable
private fun DashboardPreview() {
    CrosstrainerCompanionTheme {
        DashboardScreen(
            DashboardUiState.from(WorkoutMetrics()),
        )
    }
}
