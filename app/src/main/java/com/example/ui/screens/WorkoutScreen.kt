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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
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
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.WorkoutVoiceCoach
import com.example.camera.GeneralExerciseFocus
import com.example.camera.PoseMotionAnalyzer
import com.example.camera.PoseOverlayCanvas
import com.example.camera.PoseSkeletonSnapshot
import com.example.data.local.AppSettings
import com.example.data.local.DetectionSensitivity
import com.example.data.model.SportType
import com.example.data.model.TrackingMode
import com.example.domain.DateStreakUtils
import com.example.sensors.DeviceMotionTracker
import com.example.sensors.LocationDistanceTracker
import com.example.ui.components.MoveMapStyle
import com.example.ui.components.RealLiveTrackingMap
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.MintBright
import com.example.ui.viewmodel.HiitPhase
import com.example.ui.viewmodel.ManualEffortLevel
import com.example.ui.viewmodel.WorkoutTimerStyle
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var showAdvancedOptionsSheet by remember { mutableStateOf(false) }
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
            onGpsStateChanged = viewModel::onGpsStateChanged,
            onRoutePointRecorded = viewModel::onGpsRoutePointRecorded
        )
    }

    val voiceCoach = remember(context) { WorkoutVoiceCoach(context) }

    LaunchedEffect(uiState.coachCue?.id) {
        val cue = uiState.coachCue
        if (cue != null) {
            if (appSettings.enableVoiceCoach && !uiState.voiceCoachMuted) {
                voiceCoach.playTone(isHighChime = cue.isHighChime)
                voiceCoach.speak(
                    message = cue.textThai,
                    priority = cue.priority
                )
            }
            viewModel.consumeCoachCue()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            motionTracker.stop()
            locationTracker.stop()
            voiceCoach.shutdown()
        }
    }

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
                Text("จับเวลาสั้นกว่า ${DateStreakUtils.MIN_WORKOUT_SECONDS} วินาที")
            },
            text = {
                Text(
                    "คุณจับเวลาไป ${DateStreakUtils.formatDurationClock(uiState.elapsedSeconds)} — กรุณาจับเวลาอย่างน้อย ${DateStreakUtils.MIN_WORKOUT_SECONDS} วินาทีเพื่อบันทึกกิจกรรม\n\n" +
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
                Text("คุณสามารถบันทึกกิจกรรมค้างไว้เพื่อกลับมาทำต่อภายหลัง หรือยกเลิกเซสชันนี้ได้")
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

    if (showAdvancedOptionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAdvancedOptionsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            WorkoutAdvancedOptionsSheet(
                uiState = uiState,
                onSelectTimerStyle = viewModel::setTimerStyle,
                onApplyHiitPreset = viewModel::applyHiitPreset,
                onUpdateCustomHiit = viewModel::updateCustomHiitConfig,
                onClose = { showAdvancedOptionsSheet = false }
            )
        }
    }

    if (uiState.saveOutcome != null && uiState.savedSession != null) {
        PostWorkoutSummaryScreen(
            uiState = uiState,
            onStartCoolDown = { onStartCoolDown(uiState.savedSession!!.sport) },
            onDone = onExitToDashboard
        )
        return
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

    val onToggleRunClick = {
        if (uiState.isRunning) viewModel.pauseTimer()
        else viewModel.startOrResumeTimer()
    }

    val onFinishClick = {
        if (uiState.canSaveWorkout) {
            motionTracker.stop()
            locationTracker.stop()
            viewModel.saveWorkout()
        } else {
            showBelowMinimumDialog = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isWide = maxWidth >= 720.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Compact Header (~52.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = { showConfirmExitDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "ย้อนกลับ"
                        )
                    }
                    Column {
                        Text(
                            text = uiState.sportType.thaiName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = uiState.trackingMode.thaiLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = viewModel::toggleVoiceCoachMute,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_voice_coach_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.voiceCoachMuted || !appSettings.enableVoiceCoach) {
                                Icons.Rounded.VolumeOff
                            } else {
                                Icons.Rounded.VolumeUp
                            },
                            contentDescription = "เปิด/ปิดเสียงโค้ช AI",
                            tint = if (uiState.voiceCoachMuted || !appSettings.enableVoiceCoach) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    IconButton(
                        onClick = { showAdvancedOptionsSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("workout_options_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "ตั้งค่า HIIT และข้อมูลเพิ่มเติม",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 2. Compact 3-Mode Tracking Selector
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
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
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    ) {
                        Text(
                            text = mode.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }

            // General Exercise Focus Selector Chips (Compact row)
            if (uiState.sportType == SportType.GENERAL && uiState.trackingMode == TrackingMode.CAMERA_AI) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(GeneralExerciseFocus.entries) { focus ->
                        FilterChip(
                            selected = selectedExerciseFocus == focus,
                            onClick = { selectedExerciseFocus = focus },
                            label = {
                                Text(
                                    text = focus.thaiLabel,
                                    style = MaterialTheme.typography.labelSmall,
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

            // 3. NON-SCROLLING BODY: Camera/Sensor Viewport + Always-Pinned Live Counters & Controls
            if (isWide) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
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
                            .weight(1.1f)
                            .fillMaxHeight()
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        PinnedLiveTelemetryDock(
                            uiState = uiState,
                            selectedExerciseFocus = selectedExerciseFocus,
                            onSelectTimerStyle = viewModel::setTimerStyle,
                            onOpenOptionsSheet = { showAdvancedOptionsSheet = true },
                            onToggleRun = onToggleRunClick,
                            onFinishClick = onFinishClick
                        )
                    }
                }
            } else {
                // PORTRAIT PHONE ZERO-SCROLL LAYOUT:
                // Viewport takes flexible weight(1f) so the bottom telemetry dock + Reps + Controls
                // are ALWAYS visible without scrolling!
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            .weight(1f)
                    )

                    PinnedLiveTelemetryDock(
                        uiState = uiState,
                        selectedExerciseFocus = selectedExerciseFocus,
                        onSelectTimerStyle = viewModel::setTimerStyle,
                        onOpenOptionsSheet = { showAdvancedOptionsSheet = true },
                        onToggleRun = onToggleRunClick,
                        onFinishClick = onFinishClick
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedLiveTelemetryDock(
    uiState: WorkoutUiState,
    selectedExerciseFocus: GeneralExerciseFocus,
    onSelectTimerStyle: (WorkoutTimerStyle) -> Unit,
    onOpenOptionsSheet: () -> Unit,
    onToggleRun: () -> Unit,
    onFinishClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Unified Telemetry Card: Timer + Mode Switch + ALL Core Live Numbers side-by-side!
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Timer Display + Mode Quick Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (uiState.timerStyle == WorkoutTimerStyle.HIIT) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.testTag("hiit_live_card")
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (uiState.hiitPhase == HiitPhase.REST) AmberWarm else ForestGreen
                                ) {
                                    Text(
                                        text = "${uiState.hiitPhase.thaiLabel} ${uiState.hiitCurrentRound}/${uiState.hiitTotalRounds}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = if (uiState.hiitPhase == HiitPhase.COMPLETED) "สำเร็จ!" else "${uiState.hiitPhaseRemainingSec} วิ",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${uiState.hiitCircuitLabel} • รวม ${DateStreakUtils.formatDurationClock(uiState.elapsedSeconds)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("workout_timer_text")
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = DateStreakUtils.formatDurationClock(uiState.elapsedSeconds),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("workout_timer_text")
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (uiState.canSaveWorkout) {
                                        EmeraldVibrant.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    }
                                ) {
                                    Text(
                                        text = if (uiState.canSaveWorkout) {
                                            "บันทึกได้ทันที"
                                        } else {
                                            "${uiState.elapsedSeconds}/${DateStreakUtils.MIN_WORKOUT_SECONDS}วิ"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (uiState.canSaveWorkout) EmeraldVibrant else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Compact Timer Mode Toggle Pill (Free / HIIT)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val nextStyle = if (uiState.timerStyle == WorkoutTimerStyle.FREE) {
                                    WorkoutTimerStyle.HIIT
                                } else {
                                    WorkoutTimerStyle.FREE
                                }
                                onSelectTimerStyle(nextStyle)
                            }
                            .testTag("timer_style_selector")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.timerStyle == WorkoutTimerStyle.FREE) Icons.Rounded.Timer else Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = uiState.timerStyle.thaiLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Row 2: ALWAYS-VISIBLE LIVE COUNTERS (Reps / Steps / Distance / Calories / Plank)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.sportType.supportsReps) {
                        CompactStatPill(
                            icon = Icons.Rounded.Repeat,
                            label = if (uiState.sportType == SportType.JUMP_ROPE) "กระโดด" else "จำนวนครั้ง",
                            value = "${uiState.repsCount}",
                            accent = EmeraldVibrant,
                            highlighted = true,
                            modifier = Modifier
                                .weight(1.15f)
                                .testTag("live_reps_stat")
                        )
                    }
                    if (uiState.sportType.supportsSteps) {
                        CompactStatPill(
                            icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                            label = "ก้าว",
                            value = "${uiState.steps}",
                            accent = EmeraldVibrant,
                            highlighted = !uiState.sportType.supportsReps,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("live_steps_stat")
                        )
                    }
                    if (uiState.sportType.supportsDistance) {
                        CompactStatPill(
                            icon = Icons.Rounded.Route,
                            label = "ระยะทาง",
                            value = DateStreakUtils.formatDistanceKm(uiState.distanceMeters),
                            accent = ForestGreen,
                            highlighted = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    CompactStatPill(
                        icon = Icons.Rounded.LocalFireDepartment,
                        label = "แคลอรี",
                        value = "${uiState.caloriesBurned}",
                        unit = "kcal",
                        accent = AmberWarm,
                        highlighted = false,
                        modifier = Modifier.weight(0.95f)
                    )
                    if (uiState.trackingMode == TrackingMode.CAMERA_AI &&
                        (selectedExerciseFocus == GeneralExerciseFocus.PLANK || uiState.plankHoldSeconds > 0)
                    ) {
                        CompactStatPill(
                            icon = Icons.Rounded.Timer,
                            label = "แพลงก์",
                            value = DateStreakUtils.formatDurationClock(uiState.plankHoldSeconds),
                            accent = if (uiState.poseSnapshot.isPlankHoldingValid) EmeraldVibrant else AmberWarm,
                            highlighted = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("plank_hold_card")
                        )
                    }
                }
            }
        }

        // Ultra-compact 1-line Ghost Pacer / 1km split indicator (tapping opens full view without pushing controls off-screen)
        if (uiState.latestSplitBanner != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ForestGreenDeep,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, tint = MintBright, modifier = Modifier.size(16.dp))
                    Text(
                        text = uiState.latestSplitBanner,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else if (uiState.ghostRival.hasRival) {
            val ghostAtNow = uiState.ghostRival.expectedProgressAt(uiState.elapsedSeconds)
            val diff = when {
                uiState.sportType.supportsDistance && uiState.ghostRival.targetTotalMeters > 0 ->
                    uiState.distanceMeters - ghostAtNow.meters
                uiState.sportType.supportsReps && uiState.ghostRival.targetTotalReps > 0 ->
                    uiState.repsCount - ghostAtNow.reps
                uiState.sportType.supportsSteps && uiState.ghostRival.targetTotalSteps > 0 ->
                    uiState.steps - ghostAtNow.steps
                else -> uiState.caloriesBurned - ghostAtNow.calories
            }
            val unit = when {
                uiState.sportType.supportsDistance && uiState.ghostRival.targetTotalMeters > 0 -> "ม."
                uiState.sportType.supportsReps && uiState.ghostRival.targetTotalReps > 0 -> "ครั้ง"
                uiState.sportType.supportsSteps && uiState.ghostRival.targetTotalSteps > 0 -> "ก้าว"
                else -> "kcal"
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ForestGreenDeep,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenOptionsSheet)
                    .testTag("ghost_pacer_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = MintBright, modifier = Modifier.size(15.dp))
                        Text(
                            text = if (diff >= 0) "นำเงา +$diff $unit" else "ตามเงา ${kotlin.math.abs(diff)} $unit",
                            color = if (diff >= 0) MintBright else AmberWarm,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "แตะดูรายละเอียด",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (uiState.errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // Pinned Action Controls Row (Always at bottom — Zero Scroll)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onToggleRun,
                enabled = !uiState.isSaving,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isRunning) AmberWarm else ForestGreen
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onFinishClick,
                enabled = !uiState.isSaving,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.canSaveWorkout) EmeraldVibrant else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (uiState.canSaveWorkout) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
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
                        text = if (uiState.canSaveWorkout) "บันทึกผล" else "จบ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactStatPill(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    highlighted: Boolean,
    unit: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (highlighted) BorderStroke(1.dp, accent.copy(alpha = 0.45f)) else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    style = if (highlighted) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = if (highlighted) accent else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
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
    onMotionFrame: (PoseSkeletonSnapshot) -> Unit,
    onToggleCameraLens: () -> Unit,
    onSelectManualEffort: (ManualEffortLevel) -> Unit,
    onRequestSensorPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
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
                        sensitivity = sensitivity,
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

                    // Top status pill + subject re-lock & lens toggle + Form/Symmetry
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = when {
                                    !uiState.isRunning -> Color.Black.copy(alpha = 0.72f)
                                    uiState.poseSnapshot.isForeignPersonBlocked -> AmberWarm.copy(alpha = 0.94f)
                                    uiState.poseSnapshot.isBodyVisible && uiState.isCurrentlyMoving -> EmeraldVibrant.copy(alpha = 0.92f)
                                    uiState.poseSnapshot.isBodyVisible -> ForestGreen.copy(alpha = 0.88f)
                                    else -> Color.Black.copy(alpha = 0.72f)
                                },
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = if (!uiState.isRunning) "หยุดชั่วคราว" else uiState.poseSnapshot.postureHint,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(
                                    onClick = { relockTick++ },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (uiState.poseSnapshot.isForeignPersonBlocked) AmberWarm.copy(alpha = 0.88f)
                                            else Color.Black.copy(alpha = 0.55f)
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

                        AnimatedVisibility(
                            visible = uiState.poseSnapshot.isBodyVisible || uiState.poseSnapshot.isSubjectLocked
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (uiState.poseSnapshot.isSubjectLocked) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (uiState.poseSnapshot.isForeignPersonBlocked) AmberWarm.copy(alpha = 0.9f) else ForestGreenDeep.copy(alpha = 0.78f),
                                        border = BorderStroke(1.dp, MintBright.copy(alpha = 0.55f))
                                    ) {
                                        Text(
                                            text = if (uiState.poseSnapshot.isForeignPersonBlocked) "กันคนผ่าน • ล็อกตัวคุณ" else "ล็อกตัวผู้เล่นแล้ว",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (uiState.poseSnapshot.isBodyVisible) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color.Black.copy(alpha = 0.58f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MintBright, modifier = Modifier.size(13.dp))
                                            Text(
                                                text = "ฟอร์ม ${uiState.formScorePercent}% • สมดุล ${uiState.symmetryPercent}%",
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

                    // Bottom Overlay inside Camera Viewport: Alerts + HUGE Live Rep Counter HUD
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AnimatedVisibility(
                            visible = uiState.fatigueWarning && uiState.isRunning && uiState.fatigueHint.isNotBlank()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFB45309).copy(alpha = 0.92f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Fatigue Guard: ${uiState.fatigueHint}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = uiState.showAdjustCameraTip && uiState.isRunning) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AmberWarm.copy(alpha = 0.94f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = if (!uiState.poseSnapshot.isBodyVisible) {
                                            "ไม่พบตัวในกล้อง กรุณาตั้งมือถือให้เห็นร่างกายชัดเจน"
                                        } else {
                                            "ขยับร่างกายเพื่อเริ่มนับครั้งและเผาผลาญแคลอรี"
                                        },
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Pinned Dark-Glass Big Counter HUD right inside Camera Viewport!
                        if (uiState.sportType.supportsReps || uiState.sportType.supportsSteps) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = ForestGreenDeep.copy(alpha = 0.84f),
                                border = BorderStroke(1.dp, MintBright.copy(alpha = 0.55f)),
                                modifier = Modifier.align(Alignment.Start)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.sportType.supportsReps) Icons.Rounded.Repeat else Icons.AutoMirrored.Rounded.DirectionsWalk,
                                            contentDescription = null,
                                            tint = MintBright,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                text = when {
                                                    uiState.sportType == SportType.JUMP_ROPE -> "จำนวนกระโดด"
                                                    uiState.sportType.supportsReps -> "จำนวนครั้งที่ทำได้"
                                                    else -> "จำนวนก้าวจริง"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.82f)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.Bottom,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = if (uiState.sportType.supportsReps) "${uiState.repsCount} ครั้ง" else "${uiState.steps} ก้าว",
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    color = MintBright,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (uiState.sportType.supportsReps && uiState.poseSnapshot.repPhaseLabel.isNotBlank()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (uiState.poseSnapshot.repPhaseProgress >= 0.85f) {
                                                            EmeraldVibrant.copy(alpha = 0.35f)
                                                        } else {
                                                            Color.White.copy(alpha = 0.14f)
                                                        },
                                                        modifier = Modifier.padding(bottom = 3.dp)
                                                    ) {
                                                        Text(
                                                            text = uiState.poseSnapshot.repPhaseLabel,
                                                            color = if (uiState.poseSnapshot.repPhaseProgress >= 0.85f) MintBright else Color.White,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (uiState.sportType.supportsReps && uiState.poseSnapshot.isBodyVisible) {
                                        val progress = uiState.poseSnapshot.repPhaseProgress.coerceIn(0f, 1f)
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            color = if (progress >= 0.85f) MintBright else EmeraldVibrant,
                                            trackColor = Color.White.copy(alpha = 0.18f),
                                            modifier = Modifier
                                                .width(164.dp)
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .testTag("rep_phase_progress_bar")
                                        )
                                    }
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
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
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
    val needsPermPrompt = (uiState.sportType.supportsDistance && !uiState.locationPermissionGranted) ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !uiState.activityPermissionGranted)

    if (uiState.sportType.supportsDistance) {
        var mapStyle by remember { mutableStateOf(MoveMapStyle.STREET) }
        var followUser by remember { mutableStateOf(true) }
        var recenterTick by remember { mutableIntStateOf(0) }

        val livePaceLabel = remember(uiState.distanceMeters, uiState.elapsedSeconds) {
            if (uiState.distanceMeters >= 50 && uiState.elapsedSeconds > 0) {
                val secPerKm = (uiState.elapsedSeconds * 1000.0 / uiState.distanceMeters).toInt()
                String.format(Locale.US, "%d:%02d น./กม.", secPerKm / 60, secPerKm % 60)
            } else {
                "--:-- น./กม."
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            RealLiveTrackingMap(
                routePoints = uiState.routePoints,
                currentLat = gps.currentLat,
                currentLng = gps.currentLng,
                accuracyMeters = gps.accuracyMeters,
                mapStyle = mapStyle,
                followUser = followUser,
                recenterRequestTick = recenterTick,
                onUserPanDetected = { followUser = false },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("live_real_route_map")
            )

            // Top HUD Overlay: Status pills + Map Layer/Follow buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (uiState.isRunning && uiState.isCurrentlyMoving) {
                            EmeraldVibrant.copy(alpha = 0.92f)
                        } else {
                            ForestGreenDeep.copy(alpha = 0.86f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sensors,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = when {
                                    !uiState.isRunning && !uiState.hasStartedOnce -> "พร้อมบันทึกเส้นทางบนแผนที่"
                                    !uiState.isRunning -> "หยุดชั่วคราว"
                                    uiState.isCurrentlyMoving -> "กำลัง${uiState.sportType.thaiName}จริง"
                                    else -> "นิ่งอยู่ — เริ่มออกตัวได้เลย"
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ForestGreenDeep.copy(alpha = 0.84f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (gps.hasFix) Icons.Rounded.GpsFixed else Icons.Rounded.GpsNotFixed,
                                contentDescription = null,
                                tint = if (gps.hasFix) MintBright else AmberWarm,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = when {
                                    !gps.hasLocationPermission -> "ไม่มีสิทธิ์ตำแหน่ง"
                                    !gps.isGpsEnabled -> "กรุณาเปิด GPS"
                                    gps.hasFix -> "${gps.providerLabel} ±${(gps.accuracyMeters ?: 0f).toInt()}ม. • ${uiState.routePoints.size} จุด"
                                    else -> "กำลังจับสัญญาณ GPS..."
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { mapStyle = mapStyle.next() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ForestGreenDeep.copy(alpha = 0.86f))
                            .testTag("toggle_live_map_style_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = "เปลี่ยนมุมมองแผนที่ (${mapStyle.thaiLabel})",
                            tint = MintBright
                        )
                    }
                    IconButton(
                        onClick = {
                            followUser = true
                            recenterTick++
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (followUser) ForestGreen.copy(alpha = 0.92f)
                                else ForestGreenDeep.copy(alpha = 0.86f)
                            )
                            .testTag("recenter_live_map_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = "จัดตำแหน่งตามตัวผู้เล่น",
                            tint = if (followUser) MintBright else Color.White
                        )
                    }
                }
            }

            // Bottom HUD Overlay: Live Speed, Pace, Motion Bar or Permission Prompt
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (needsPermPrompt) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ForestGreenDeep.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, MintBright.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "อนุญาตสิทธิ์ตำแหน่ง (GPS) เพื่อปักหมุดและวาดเส้นทางวิ่ง/เดินบนแผนที่จริง",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Button(
                                onClick = onRequestPermissions,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("request_map_gps_permission_button")
                            ) {
                                Text("เปิดใช้งานตำแหน่งและ GPS", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ForestGreenDeep.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Speed,
                                        contentDescription = null,
                                        tint = MintBright,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f กม./ชม.", gps.currentSpeedKmh),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "เพซ $livePaceLabel",
                                    color = MintBright,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = mapStyle.thaiLabel,
                                    color = Color.White.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            LinearProgressIndicator(
                                progress = { uiState.currentIntensity.coerceIn(0f, 1f) },
                                color = MintBright,
                                trackColor = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                            )
                        }
                    }
                }
            }
        }
        return
    }

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
                            else -> "นิ่งอยู่ — เริ่มขยับตัว"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = sensor.sensorSourceLabel,
                color = MintBright,
                style = MaterialTheme.typography.labelLarge
            )

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
                text = "ระดับการเคลื่อนไหวจริง: ${(uiState.currentIntensity * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        if (needsPermPrompt) {
            OutlinedButton(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("อนุญาตสิทธิ์ตัวนับก้าวเพื่อให้วัดแม่นยำ", color = MintBright)
            }
        } else {
            Text(
                text = "พกมือถือติดตัวขณะออกกำลังกาย ระบบจะนับก้าวและการเคลื่อนไหวจริง",
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
    onMotionAnalyzed: (PoseSkeletonSnapshot) -> Unit,
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
