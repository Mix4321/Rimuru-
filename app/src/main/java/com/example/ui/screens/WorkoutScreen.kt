package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.camera.GeneralExerciseFocus
import com.example.camera.PoseMotionAnalyzer
import com.example.camera.PoseOverlayCanvas
import com.example.data.local.AppSettings
import com.example.data.local.DetectionSensitivity
import com.example.data.model.SportType
import com.example.data.model.TrackingMode
import com.example.domain.DateStreakUtils
import com.example.sensors.DeviceMotionTracker
import com.example.sensors.LocationDistanceTracker
import com.example.ui.components.MetricTile
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.MintBright
import com.example.ui.viewmodel.ManualEffortLevel
import com.example.ui.viewmodel.WorkoutUiState
import com.example.ui.viewmodel.WorkoutViewModel
import java.util.Locale
import java.util.concurrent.Executors

private fun checkCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun checkLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun checkActivityPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    appSettings: AppSettings,
    onExitToDashboard: () -> Unit,
    onStartCoolDown: (SportType) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    val currentSport by rememberUpdatedState(uiState.sportType)
    val currentSensitivity by rememberUpdatedState(appSettings.detectionSensitivity)

    LaunchedEffect(appSettings) {
        viewModel.updateAppSettings(appSettings)
    }

    DisposableEffect(appSettings.keepScreenOn) {
        view.keepScreenOn = appSettings.keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    var showBelowMinimumDialog by remember { mutableStateOf(false) }
    var showConfirmExitDialog by remember { mutableStateOf(false) }
    var selectedExerciseFocus by remember(uiState.sessionId) { mutableStateOf(GeneralExerciseFocus.AUTO) }

    val motionTracker = remember(context) {
        DeviceMotionTracker(
            context = context,
            sportProvider = { currentSport },
            sensitivityProvider = { currentSensitivity },
            onStepDetected = viewModel::onSensorStepDetected,
            onJumpRepDetected = viewModel::onSensorJumpRepDetected,
            onMotionStateChanged = viewModel::onSensorMotionStateChanged
        )
    }

    val locationTracker = remember(context) {
        LocationDistanceTracker(
            context = context,
            sportProvider = { currentSport },
            sensitivityProvider = { currentSensitivity },
            onDistanceAddedMeters = viewModel::onGpsDistanceAdded,
            onGpsStateChanged = viewModel::onGpsStateChanged
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            motionTracker.stop()
            locationTracker.stop()
        }
    }

    // Activate or stop hardware sensors & GPS cleanly based on current tracking mode & permissions
    LaunchedEffect(
        uiState.trackingMode,
        uiState.locationPermissionGranted,
        uiState.activityPermissionGranted,
        uiState.saveOutcome
    ) {
        if (uiState.saveOutcome != null) {
            motionTracker.stop()
            locationTracker.stop()
            return@LaunchedEffect
        }

        if (uiState.trackingMode == TrackingMode.SENSOR_GPS) {
            motionTracker.start()
            if (uiState.sportType.supportsDistance) {
                locationTracker.start()
            } else {
                locationTracker.stop()
            }
        } else {
            motionTracker.stop()
            locationTracker.stop()
        }
    }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val cam = checkCameraPermission(context)
        val loc = checkLocationPermission(context)
        val act = checkActivityPermission(context)
        viewModel.updatePermissionStates(
            cameraGranted = cam,
            locationGranted = loc,
            activityGranted = act,
            autoStartIfFirstTime = !uiState.hasStartedOnce
        )
    }

    val cameraOnlyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updatePermissionStates(
            cameraGranted = granted,
            locationGranted = checkLocationPermission(context),
            activityGranted = checkActivityPermission(context),
            autoStartIfFirstTime = false
        )
        if (granted) {
            viewModel.switchTrackingMode(TrackingMode.CAMERA_AI)
        }
    }

    // Initial permission evaluation based on selected mode
    LaunchedEffect(uiState.sessionId) {
        if (uiState.sessionId.isBlank() || uiState.permissionsEvaluated) return@LaunchedEffect

        val cam = checkCameraPermission(context)
        val loc = checkLocationPermission(context)
        val act = checkActivityPermission(context)

        val missingToRequest = buildList {
            if (uiState.trackingMode == TrackingMode.CAMERA_AI && !cam) {
                add(Manifest.permission.CAMERA)
            }
            if (uiState.trackingMode == TrackingMode.SENSOR_GPS) {
                if (uiState.sportType.supportsDistance && !loc) {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !act) {
                    add(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        }

        if (missingToRequest.isEmpty()) {
            viewModel.updatePermissionStates(
                cameraGranted = cam,
                locationGranted = loc,
                activityGranted = act,
                autoStartIfFirstTime = !uiState.hasStartedOnce
            )
        } else {
            multiPermissionLauncher.launch(missingToRequest.toTypedArray())
        }
    }

    // Re-check permissions automatically if user returned from Android Settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val cam = checkCameraPermission(context)
        val loc = checkLocationPermission(context)
        val act = checkActivityPermission(context)
        viewModel.updatePermissionStates(
            cameraGranted = cam,
            locationGranted = loc,
            activityGranted = act,
            autoStartIfFirstTime = false
        )
    }

    // Persist active workout draft if user leaves app or locks screen mid-workout
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.persistDraftAsync()
    }

    BackHandler(enabled = uiState.saveOutcome == null) {
        showConfirmExitDialog = true
    }

    if (showBelowMinimumDialog) {
        AlertDialog(
            onDismissRequest = { showBelowMinimumDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = AmberWarm
                )
            },
            title = {
                Text("ยังออกกำลังกายไม่ครบ 15 นาที")
            },
            text = {
                val remainText = DateStreakUtils.formatDurationClock(uiState.remainingToMinSeconds)
                Text(
                    "ระบบกำหนดเวลาขั้นต่ำ 15 นาทีในการบันทึกกิจกรรม\n\n" +
                        "• เวลาปัจจุบัน: ${DateStreakUtils.formatDurationClock(uiState.elapsedSeconds)}\n" +
                        "• เวลาที่เหลือ: $remainText นาที\n\n" +
                        "คุณต้องการทำกิจกรรมต่อหรือยกเลิกกิจกรรมนี้?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBelowMinimumDialog = false
                        if (!uiState.isRunning) viewModel.startOrResumeTimer()
                    },
                    modifier = Modifier.testTag("continue_workout_dialog_button")
                ) {
                    Text("ทำกิจกรรมต่อ")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBelowMinimumDialog = false
                        motionTracker.stop()
                        locationTracker.stop()
                        viewModel.discardAndClearSession()
                        onExitToDashboard()
                    },
                    modifier = Modifier.testTag("discard_short_workout_button")
                ) {
                    Text("ยกเลิกกิจกรรม", color = CoralError)
                }
            }
        )
    }

    if (showConfirmExitDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmExitDialog = false },
            title = { Text("พักกิจกรรมชั่วคราว?") },
            text = {
                Text(
                    "คุณสามารถบันทึกกิจกรรมค้างไว้เพื่อกลับมาทำต่อภายหลัง หรือยกเลิกเซสชันนี้ได้"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmExitDialog = false
                        motionTracker.stop()
                        locationTracker.stop()
                        viewModel.pauseTimer()
                        onExitToDashboard()
                    }
                ) {
                    Text("พักไว้แล้วออก")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmExitDialog = false
                        motionTracker.stop()
                        locationTracker.stop()
                        viewModel.discardAndClearSession()
                        onExitToDashboard()
                    }
                ) {
                    Text("ยกเลิกกิจกรรม", color = CoralError)
                }
            }
        )
    }

    if (uiState.saveOutcome != null && uiState.savedSession != null) {
        PostWorkoutSummaryScreen(
            uiState = uiState,
            onStartCoolDown = { onStartCoolDown(uiState.savedSession!!.sport) },
            onDone = onExitToDashboard
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isWide = maxWidth >= 720.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showConfirmExitDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "ย้อนกลับ"
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = uiState.sportType.thaiName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.trackingMode.thaiLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (!uiState.cameraPermissionGranted && uiState.trackingMode != TrackingMode.CAMERA_AI) {
                    OutlinedButton(
                        onClick = { cameraOnlyLauncher.launch(Manifest.permission.CAMERA) }
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ใช้กล้อง AI")
                    }
                }
            }

            // 3-Mode Tracking Selector (Camera AI | Sensor & GPS | Manual)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                TrackingMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.trackingMode == mode,
                        onClick = {
                            if (mode == TrackingMode.CAMERA_AI && !uiState.cameraPermissionGranted) {
                                cameraOnlyLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                if (mode == TrackingMode.SENSOR_GPS) {
                                    val needed = buildList {
                                        if (uiState.sportType.supportsDistance && !uiState.locationPermissionGranted) {
                                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !uiState.activityPermissionGranted) {
                                            add(Manifest.permission.ACTIVITY_RECOGNITION)
                                        }
                                    }
                                    if (needed.isNotEmpty()) {
                                        multiPermissionLauncher.launch(needed.toTypedArray())
                                    }
                                }
                                viewModel.switchTrackingMode(mode)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TrackingMode.entries.size
                        ),
                        icon = {
                            Icon(
                                imageVector = when (mode) {
                                    TrackingMode.CAMERA_AI -> Icons.Rounded.CameraAlt
                                    TrackingMode.SENSOR_GPS -> Icons.Rounded.Sensors
                                    TrackingMode.MANUAL -> Icons.Rounded.EditNote
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text(
                            text = mode.shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
            }

            val openAppSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }

            val requestSensorPermissions = {
                val needed = buildList {
                    if (!uiState.locationPermissionGranted) {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !uiState.activityPermissionGranted) {
                        add(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                }
                if (needed.isNotEmpty()) {
                    multiPermissionLauncher.launch(needed.toTypedArray())
                } else {
                    openAppSettings()
                }
            }

            if (uiState.sportType == SportType.GENERAL && uiState.trackingMode == TrackingMode.CAMERA_AI) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(GeneralExerciseFocus.entries) { focus ->
                        FilterChip(
                            selected = selectedExerciseFocus == focus,
                            onClick = { selectedExerciseFocus = focus },
                            label = {
                                Text(
                                    text = focus.thaiLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("exercise_focus_${focus.name}")
                        )
                    }
                }
            }

            if (isWide) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TrackingViewportCard(
                        uiState = uiState,
                        sensitivity = appSettings.detectionSensitivity,
                        exerciseFocus = selectedExerciseFocus,
                        onMotionFrame = viewModel::onMotionFrame,
                        onToggleCameraLens = viewModel::toggleCameraLens,
                        onSelectManualEffort = viewModel::setManualEffort,
                        onRequestSensorPermissions = requestSensorPermissions,
                        onOpenAppSettings = openAppSettings,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        WorkoutMetricsAndControls(
                            uiState = uiState,
                            onToggleRun = {
                                if (uiState.isRunning) viewModel.pauseTimer()
                                else viewModel.startOrResumeTimer()
                            },
                            onFinishClick = {
                                if (uiState.canSaveWorkout) {
                                    motionTracker.stop()
                                    locationTracker.stop()
                                    viewModel.saveWorkout()
                                } else {
                                    showBelowMinimumDialog = true
                                }
                            }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TrackingViewportCard(
                        uiState = uiState,
                        sensitivity = appSettings.detectionSensitivity,
                        exerciseFocus = selectedExerciseFocus,
                        onMotionFrame = viewModel::onMotionFrame,
                        onToggleCameraLens = viewModel::toggleCameraLens,
                        onSelectManualEffort = viewModel::setManualEffort,
                        onRequestSensorPermissions = requestSensorPermissions,
                        onOpenAppSettings = openAppSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                when (uiState.trackingMode) {
                                    TrackingMode.CAMERA_AI -> 390.dp
                                    TrackingMode.SENSOR_GPS -> 240.dp
                                    TrackingMode.MANUAL -> 220.dp
                                }
                            )
                    )

                    WorkoutMetricsAndControls(
                        uiState = uiState,
                        onToggleRun = {
                            if (uiState.isRunning) viewModel.pauseTimer()
                            else viewModel.startOrResumeTimer()
                        },
                        onFinishClick = {
                            if (uiState.canSaveWorkout) {
                                motionTracker.stop()
                                locationTracker.stop()
                                viewModel.saveWorkout()
                            } else {
                                showBelowMinimumDialog = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackingViewportCard(
    uiState: WorkoutUiState,
    sensitivity: DetectionSensitivity,
    exerciseFocus: GeneralExerciseFocus,
    onMotionFrame: (com.example.camera.PoseSkeletonSnapshot) -> Unit,
    onToggleCameraLens: () -> Unit,
    onSelectManualEffort: (ManualEffortLevel) -> Unit,
    onRequestSensorPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSensitivityForCard = sensitivity
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = ForestGreenDeep),
        modifier = modifier
    ) {
        when {
            uiState.trackingMode == TrackingMode.CAMERA_AI && uiState.cameraPermissionGranted -> {
                var relockTick by remember { mutableIntStateOf(0) }
                var tapLockRequest by remember { mutableStateOf<WorkoutTapLockRequest?>(null) }
                var cameraBoxSize by remember { mutableStateOf(IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { cameraBoxSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (cameraBoxSize.width > 0 && cameraBoxSize.height > 0) {
                                    tapLockRequest = WorkoutTapLockRequest(
                                        id = System.nanoTime(),
                                        x = offset.x,
                                        y = offset.y,
                                        viewWidth = cameraBoxSize.width.toFloat(),
                                        viewHeight = cameraBoxSize.height.toFloat()
                                    )
                                }
                            }
                        }
                ) {
                    CameraPosePreview(
                        sportType = uiState.sportType,
                        sensitivity = currentSensitivityForCard,
                        exerciseFocus = exerciseFocus,
                        useFrontCamera = uiState.useFrontCamera,
                        relockTick = relockTick,
                        tapLockRequest = tapLockRequest,
                        onMotionAnalyzed = onMotionFrame,
                        modifier = Modifier.fillMaxSize()
                    )

                    PoseOverlayCanvas(
                        snapshot = uiState.poseSnapshot,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top status pill + subject re-lock & lens toggle
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = when {
                                    !uiState.isRunning -> Color.Black.copy(alpha = 0.7f)
                                    uiState.poseSnapshot.isForeignPersonBlocked ->
                                        AmberWarm.copy(alpha = 0.94f)
                                    uiState.poseSnapshot.isBodyVisible && uiState.isCurrentlyMoving ->
                                        EmeraldVibrant.copy(alpha = 0.92f)
                                    uiState.poseSnapshot.isBodyVisible ->
                                        ForestGreen.copy(alpha = 0.88f)
                                    else -> Color.Black.copy(alpha = 0.72f)
                                },
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = when {
                                        !uiState.isRunning -> "หยุดชั่วคราว"
                                        else -> uiState.poseSnapshot.postureHint
                                    },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { relockTick++ },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (uiState.poseSnapshot.isForeignPersonBlocked) {
                                                AmberWarm.copy(alpha = 0.86f)
                                            } else {
                                                Color.Black.copy(alpha = 0.55f)
                                            }
                                        )
                                        .testTag("relock_subject_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CenterFocusStrong,
                                        contentDescription = "ล็อกตัวผู้เล่นใหม่",
                                        tint = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = onToggleCameraLens,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .testTag("switch_camera_lens_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cameraswitch,
                                        contentDescription = "สลับกล้องหน้า-หลัง",
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        // Subject Lock + AI Form % & Symmetry % Live Badges
                        androidx.compose.animation.AnimatedVisibility(
                            visible = uiState.poseSnapshot.isBodyVisible ||
                                uiState.poseSnapshot.isSubjectLocked ||
                                uiState.poseSnapshot.isForeignPersonBlocked
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (uiState.poseSnapshot.isSubjectLocked) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (uiState.poseSnapshot.isForeignPersonBlocked) {
                                            AmberWarm.copy(alpha = 0.88f)
                                        } else {
                                            ForestGreenDeep.copy(alpha = 0.78f)
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (uiState.poseSnapshot.isForeignPersonBlocked) {
                                                Color.White.copy(alpha = 0.65f)
                                            } else {
                                                MintBright.copy(alpha = 0.6f)
                                            }
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CenterFocusStrong,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = if (uiState.poseSnapshot.isForeignPersonBlocked) {
                                                    "กันคนเดินผ่าน • ล็อกตัวคุณ"
                                                } else {
                                                    "ล็อกตัวแล้ว • แตะเพื่อย้ายจุด"
                                                },
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (uiState.poseSnapshot.isBodyVisible) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color.Black.copy(alpha = 0.58f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                tint = MintBright,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "ฟอร์ม ${uiState.formScorePercent}%",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color.Black.copy(alpha = 0.58f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Balance,
                                                contentDescription = null,
                                                tint = if (uiState.symmetryPercent >= 85) MintBright else AmberWarm,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "สมดุล ${uiState.symmetryPercent}%",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom alerts (Fatigue Guard or No-body/idle tip)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = uiState.fatigueWarning && uiState.isRunning && uiState.fatigueHint.isNotBlank()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFB45309).copy(alpha = 0.94f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Fatigue Guard: ${uiState.fatigueHint}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = uiState.showAdjustCameraTip && uiState.isRunning
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = AmberWarm.copy(alpha = 0.95f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Text(
                                        text = if (!uiState.poseSnapshot.isBodyVisible) {
                                            "ไม่พบตัวในกล้อง กรุณาตั้งมือถือให้เห็นร่างกายชัดเจน"
                                        } else {
                                            "พบตัวแล้วแต่ยังนิ่งอยู่ — ขยับร่างกายเพื่อเริ่มนับและเผาผลาญแคลอรี"
                                        },
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }

            uiState.trackingMode == TrackingMode.CAMERA_AI && !uiState.cameraPermissionGranted -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = MintBright,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ต้องอนุญาตสิทธิ์กล้องเพื่อตรวจจับโครงกระดูก AI",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onOpenAppSettings) {
                        Text("เปิดสิทธิ์กล้องในการตั้งค่า")
                    }
                }
            }

            uiState.trackingMode == TrackingMode.SENSOR_GPS -> {
                SensorGpsStatusContent(
                    uiState = uiState,
                    onRequestPermissions = onRequestSensorPermissions
                )
            }

            else -> {
                // Explicit Manual Mode
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MintBright.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "โหมดบันทึกด้วยตนเอง (คำนวณจากเวลาและความหนัก)",
                            color = MintBright,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = uiState.sportType.icon,
                            contentDescription = null,
                            tint = MintBright,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "เลือกระดับความหนักของการออกกำลังกาย",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ManualEffortLevel.entries.forEach { level ->
                            val selected = uiState.manualEffort == level
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectManualEffort(level) },
                                label = { Text(level.thaiLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MintBright,
                                    selectedLabelColor = ForestGreenDeep,
                                    containerColor = Color.White.copy(alpha = 0.14f),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorGpsStatusContent(
    uiState: WorkoutUiState,
    onRequestPermissions: () -> Unit
) {
    val gps = uiState.gpsSnapshot
    val sensor = uiState.sensorSnapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (uiState.isRunning && uiState.isCurrentlyMoving) {
                    EmeraldVibrant.copy(alpha = 0.9f)
                } else {
                    Color.White.copy(alpha = 0.14f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sensors,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when {
                            !uiState.isRunning -> "หยุดชั่วคราว"
                            uiState.isCurrentlyMoving -> "กำลังตรวจจับการเคลื่อนไหวจริง"
                            else -> "นิ่งอยู่ — ขยับตัวหรือเริ่มเดิน/วิ่ง"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (uiState.sportType.supportsDistance) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (gps.hasFix) Icons.Rounded.GpsFixed else Icons.Rounded.GpsNotFixed,
                            contentDescription = null,
                            tint = if (gps.hasFix) MintBright else AmberWarm,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = when {
                                !gps.hasLocationPermission -> "ไม่มีสิทธิ์ GPS"
                                !gps.isGpsEnabled -> "GPS ปิดอยู่"
                                gps.hasFix -> "GPS ±${(gps.accuracyMeters ?: 0f).toInt()}ม."
                                else -> "กำลังจับสัญญาณ GPS..."
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sensor.sensorSourceLabel,
                    color = MintBright,
                    style = MaterialTheme.typography.labelLarge
                )
                if (uiState.sportType.supportsDistance) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f กม./ชม.", gps.currentSpeedKmh),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { uiState.currentIntensity.coerceIn(0f, 1f) },
                color = MintBright,
                trackColor = Color.White.copy(alpha = 0.18f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
            )
            Text(
                text = "ระดับแรงสั่นสะเทือน/การเคลื่อนไหวจริง: ${(uiState.currentIntensity * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        val needsPermPrompt = (uiState.sportType.supportsDistance && !uiState.locationPermissionGranted) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !uiState.activityPermissionGranted)

        if (needsPermPrompt) {
            OutlinedButton(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("อนุญาตสิทธิ์ GPS / ตัวนับก้าวเพื่อให้วัดแม่นยำ", color = MintBright)
            }
        } else {
            Text(
                text = if (uiState.sportType.supportsDistance && !gps.hasFix) {
                    "คำนวณระยะทางจากจำนวนก้าวจริงระหว่างรอสัญญาณ GPS (เหมาะสำหรับในอาคาร/ลู่วิ่ง)"
                } else {
                    "พกมือถือติดตัวขณะออกกำลังกาย ระบบจะนับก้าว ระยะทาง และแคลอรีจากการเคลื่อนไหวจริงเท่านั้น"
                },
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private data class WorkoutTapLockRequest(
    val id: Long,
    val x: Float,
    val y: Float,
    val viewWidth: Float,
    val viewHeight: Float
)

@Composable
private fun CameraPosePreview(
    sportType: SportType,
    sensitivity: DetectionSensitivity,
    exerciseFocus: GeneralExerciseFocus,
    useFrontCamera: Boolean,
    relockTick: Int,
    tapLockRequest: WorkoutTapLockRequest?,
    onMotionAnalyzed: (com.example.camera.PoseSkeletonSnapshot) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val currentSport by rememberUpdatedState(sportType)
    val currentSensitivity by rememberUpdatedState(sensitivity)
    val currentFocus by rememberUpdatedState(exerciseFocus)
    val activeFrontCamera = remember { java.util.concurrent.atomic.AtomicBoolean(useFrontCamera) }
    val boundProviderRef = remember { java.util.concurrent.atomic.AtomicReference<ProcessCameraProvider?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val poseAnalyzer = remember {
        PoseMotionAnalyzer(
            sportProvider = { currentSport },
            sensitivityProvider = { currentSensitivity },
            isFrontCameraProvider = { activeFrontCamera.get() },
            exerciseFocusProvider = { currentFocus },
            onMotionAnalyzed = onMotionAnalyzed
        )
    }

    LaunchedEffect(relockTick) {
        if (relockTick > 0) {
            poseAnalyzer.relockPrimarySubject()
        }
    }

    LaunchedEffect(tapLockRequest) {
        tapLockRequest?.let { req ->
            poseAnalyzer.lockSubjectAtTap(req.x, req.y, req.viewWidth, req.viewHeight)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                boundProviderRef.get()?.unbindAll()
            }
            poseAnalyzer.close()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(useFrontCamera) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                boundProviderRef.set(cameraProvider)
                val preferredSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val selector = when {
                    cameraProvider.hasCamera(preferredSelector) -> preferredSelector
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> null
                } ?: return@addListener

                activeFrontCamera.set(selector == CameraSelector.DEFAULT_FRONT_CAMERA)
                poseAnalyzer.resetTracking()

                val previewResSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()

                val analysisResSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(previewResSelector)
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(analysisResSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor, poseAnalyzer)
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                Log.w("MoveCamera", "CameraX bind error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun WorkoutMetricsAndControls(
    uiState: WorkoutUiState,
    onToggleRun: () -> Unit,
    onFinishClick: () -> Unit
) {
    // Clock & 15-min Minimum Progress Card
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ระยะเวลาออกกำลังกาย",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = DateStreakUtils.formatDurationClock(uiState.elapsedSeconds),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("workout_timer_text")
            )

            LinearProgressIndicator(
                progress = { uiState.minRequirementProgress },
                color = if (uiState.canSaveWorkout) EmeraldVibrant else ForestGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
            )

            Text(
                text = if (uiState.canSaveWorkout) {
                    "ผ่านเกณฑ์ขั้นต่ำ 15 นาทีแล้ว — สามารถจบและบันทึกกิจกรรมได้"
                } else {
                    "เหลืออีก ${DateStreakUtils.formatDurationClock(uiState.remainingToMinSeconds)} นาที (เคลื่อนไหวจริงแล้ว ${DateStreakUtils.formatDurationClock(uiState.motionActiveSeconds)})"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (uiState.canSaveWorkout) EmeraldVibrant else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    // Ghost Pacer Duel HUD when user has a past best workout for this sport
    if (uiState.ghostRival.hasRival) {
        GhostPacerDuelCard(uiState = uiState)
    }

    // Symmetry advice chip when Camera AI sees posture imbalance
    if (uiState.trackingMode == TrackingMode.CAMERA_AI &&
        uiState.poseSnapshot.isBodyVisible &&
        uiState.symmetryHint.isNotBlank() &&
        uiState.symmetryPercent < 86
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Balance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "AI โค้ช: ${uiState.symmetryHint}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Live Real Metrics Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricTile(
            icon = Icons.Rounded.LocalFireDepartment,
            label = "แคลอรี",
            value = "${uiState.caloriesBurned} kcal",
            accentColor = AmberWarm,
            modifier = Modifier.weight(1f)
        )
        if (uiState.sportType.supportsSteps) {
            MetricTile(
                icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                label = "จำนวนก้าว",
                value = "${uiState.steps}",
                accentColor = EmeraldVibrant,
                modifier = Modifier.weight(1f)
            )
        }
        if (uiState.sportType.supportsReps) {
            MetricTile(
                icon = Icons.Rounded.Repeat,
                label = if (uiState.sportType.name == "JUMP_ROPE") "กระโดด" else "จำนวนครั้ง",
                value = "${uiState.repsCount}",
                accentColor = EmeraldVibrant,
                modifier = Modifier.weight(1f)
            )
        }
        if (uiState.sportType.supportsDistance) {
            MetricTile(
                icon = Icons.Rounded.Route,
                label = "ระยะทาง",
                value = DateStreakUtils.formatDistanceKm(uiState.distanceMeters),
                accentColor = ForestGreen,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (uiState.errorMessage != null) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = uiState.errorMessage!!,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    // Controls
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onToggleRun,
            enabled = !uiState.isSaving,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isRunning) AmberWarm else ForestGreen
            ),
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .testTag("workout_pause_resume_button")
        ) {
            Icon(
                imageVector = if (uiState.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when {
                    uiState.isRunning -> "หยุดชั่วคราว"
                    uiState.hasStartedOnce -> "ทำต่อ"
                    else -> "เริ่ม"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }

        Button(
            onClick = onFinishClick,
            enabled = !uiState.isSaving,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.canSaveWorkout) EmeraldVibrant else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (uiState.canSaveWorkout) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .testTag("workout_finish_button")
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (uiState.canSaveWorkout) Icons.Rounded.CheckCircle else Icons.Rounded.Stop,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (uiState.canSaveWorkout) "บันทึกกิจกรรม" else "จบกิจกรรม",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun GhostPacerDuelCard(uiState: WorkoutUiState) {
    val ghostAtNow = uiState.ghostRival.expectedProgressAt(uiState.elapsedSeconds)
    val sport = uiState.sportType

    val (currentVal, ghostVal, unitLabel) = when {
        sport.supportsDistance && uiState.ghostRival.targetTotalMeters > 0 ->
            Triple(uiState.distanceMeters, ghostAtNow.meters, "ม.")
        sport.supportsReps && uiState.ghostRival.targetTotalReps > 0 ->
            Triple(uiState.repsCount, ghostAtNow.reps, "ครั้ง")
        sport.supportsSteps && uiState.ghostRival.targetTotalSteps > 0 ->
            Triple(uiState.steps, ghostAtNow.steps, "ก้าว")
        else ->
            Triple(uiState.caloriesBurned, ghostAtNow.calories, "kcal")
    }

    val diff = currentVal - ghostVal
    val isAhead = diff >= 0
    val duelRatio = when {
        ghostVal <= 0 && currentVal <= 0 -> 0.5f
        ghostVal <= 0 -> 0.85f
        else -> (0.5f + ((diff.toFloat() / maxOf(ghostVal, 10)) * 0.45f)).coerceIn(0.08f, 0.96f)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ForestGreenDeep),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ghost_pacer_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(ForestGreenDeep, ForestGreen)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = MintBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Ghost Pacer • แข่งกับเงาสถิติสูงสุด (${uiState.ghostRival.rivalDateLabel})",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isAhead) "คุณนำหน้าเงาอยู่ +$diff $unitLabel" else "ตามหลังเงา ${kotlin.math.abs(diff)} $unitLabel",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isAhead) MintBright else AmberWarm,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "เงาตอนนี้: $ghostVal $unitLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }

            LinearProgressIndicator(
                progress = { duelRatio },
                color = if (isAhead) MintBright else AmberWarm,
                trackColor = Color.White.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
            )
        }
    }
}

@Composable
private fun PostWorkoutSummaryScreen(
    uiState: WorkoutUiState,
    onStartCoolDown: () -> Unit,
    onDone: () -> Unit
) {
    val session = uiState.savedSession!!
    val outcome = uiState.saveOutcome!!

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_goal_badge),
                    contentDescription = stringResource(R.string.goal_badge_desc),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(22.dp))
                )

                Text(
                    text = "บันทึกกิจกรรมสำเร็จ!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${session.sport.thaiName} • ${DateStreakUtils.formatMinutesThai(session.durationSeconds)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        icon = Icons.Rounded.LocalFireDepartment,
                        label = "เผาผลาญ",
                        value = "${session.caloriesBurned} kcal",
                        accentColor = AmberWarm,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        icon = Icons.Rounded.LocalFireDepartment,
                        label = "Streak ล่าสุด",
                        value = "${outcome.updatedStreak.currentStreak} วัน",
                        accentColor = EmeraldVibrant,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = outcome.updatedStreak.statusMessageThai,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onStartCoolDown,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldVibrant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("post_workout_cooldown_button")
                ) {
                    Icon(Icons.Rounded.AccessibilityNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ยืดคลายกล้ามเนื้อหลังเล่น (Cool-Down)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onDone,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("post_workout_done_button")
                ) {
                    Text("กลับหน้าหลัก", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
