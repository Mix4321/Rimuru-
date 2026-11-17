package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.camera.PoseMotionAnalyzer
import com.example.camera.PoseOverlayCanvas
import com.example.data.model.TrackingMode
import com.example.domain.DateStreakUtils
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
import java.util.concurrent.Executors

@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    onExitToDashboard: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showBelowMinimumDialog by remember { mutableStateOf(false) }
    var showConfirmExitDialog by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCameraPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        if (!uiState.cameraPermissionAsked) {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (alreadyGranted) {
                viewModel.onCameraPermissionResult(true)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // Persist active workout draft if user leaves app or locks screen mid-workout
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.persistDraftAsync()
    }

    // Intercept system back button during an active/unsaved workout
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
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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

                // Switch Camera / Mode pill
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.cameraPermissionGranted) {
                        FilterChip(
                            selected = uiState.trackingMode == TrackingMode.CAMERA_AI,
                            onClick = {
                                val next = if (uiState.trackingMode == TrackingMode.CAMERA_AI) {
                                    TrackingMode.MANUAL
                                } else {
                                    TrackingMode.CAMERA_AI
                                }
                                viewModel.switchTrackingMode(next)
                            },
                            label = {
                                Text(
                                    if (uiState.trackingMode == TrackingMode.CAMERA_AI) "โหมดกล้อง AI" else "โหมดกำหนดเอง"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (uiState.trackingMode == TrackingMode.CAMERA_AI) {
                                        Icons.Rounded.CameraAlt
                                    } else {
                                        Icons.Rounded.EditNote
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("เปิดสิทธิ์กล้อง")
                        }
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
                        onMotionFrame = viewModel::onMotionFrame,
                        onToggleCameraLens = viewModel::toggleCameraLens,
                        onSelectManualEffort = viewModel::setManualEffort,
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
                                if (uiState.canSaveWorkout) viewModel.saveWorkout()
                                else showBelowMinimumDialog = true
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
                        onMotionFrame = viewModel::onMotionFrame,
                        onToggleCameraLens = viewModel::toggleCameraLens,
                        onSelectManualEffort = viewModel::setManualEffort,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(270.dp)
                    )

                    WorkoutMetricsAndControls(
                        uiState = uiState,
                        onToggleRun = {
                            if (uiState.isRunning) viewModel.pauseTimer()
                            else viewModel.startOrResumeTimer()
                        },
                        onFinishClick = {
                            if (uiState.canSaveWorkout) viewModel.saveWorkout()
                            else showBelowMinimumDialog = true
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
    onMotionFrame: (com.example.camera.PoseSkeletonSnapshot) -> Unit,
    onToggleCameraLens: () -> Unit,
    onSelectManualEffort: (ManualEffortLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = ForestGreenDeep),
        modifier = modifier
    ) {
        if (uiState.cameraPermissionGranted && uiState.trackingMode == TrackingMode.CAMERA_AI) {
            Box(modifier = Modifier.fillMaxSize()) {
                CameraPosePreview(
                    useFrontCamera = uiState.useFrontCamera,
                    onMotionAnalyzed = onMotionFrame,
                    modifier = Modifier.fillMaxSize()
                )

                PoseOverlayCanvas(
                    snapshot = uiState.poseSnapshot,
                    modifier = Modifier.fillMaxSize()
                )

                // Top status pill + lens toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (uiState.isCurrentlyMoving) {
                            EmeraldVibrant.copy(alpha = 0.9f)
                        } else {
                            Color.Black.copy(alpha = 0.65f)
                        }
                    ) {
                        Text(
                            text = when {
                                !uiState.isRunning -> "หยุดชั่วคราว"
                                uiState.isCurrentlyMoving -> "กำลังเคลื่อนไหว • AI Pose Active"
                                else -> "กำลังค้นหาการเคลื่อนไหว..."
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleCameraLens,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cameraswitch,
                            contentDescription = "สลับกล้องหน้า-หลัง",
                            tint = Color.White
                        )
                    }
                }

                // No motion guidance alert
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.showAdjustCameraTip,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
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
                                text = "ไม่พบการเคลื่อนไหว กรุณาปรับตำแหน่งกล้องให้เห็นร่างกายชัดเจน",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        } else {
            // Manual Mode Fallback (Camera Denied or User Switched to Manual)
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
                        text = if (!uiState.cameraPermissionGranted) {
                            "โหมดบันทึกด้วยตนเอง (ไม่ใช้กล้อง)"
                        } else {
                            "โหมดบันทึกด้วยตนเอง"
                        },
                        color = MintBright,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = uiState.sportType.icon,
                        contentDescription = null,
                        tint = MintBright,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "เลือกระดับความหนักของการออกกำลังกาย",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

@Composable
private fun CameraPosePreview(
    useFrontCamera: Boolean,
    onMotionAnalyzed: (com.example.camera.PoseSkeletonSnapshot) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val poseAnalyzer = remember {
        PoseMotionAnalyzer(
            isFrontCameraProvider = { useFrontCamera },
            onMotionAnalyzed = onMotionAnalyzed
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            poseAnalyzer.close()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(useFrontCamera) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
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

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    "เหลืออีก ${DateStreakUtils.formatDurationClock(uiState.remainingToMinSeconds)} นาที เพื่อปลดล็อกการบันทึก (ขั้นต่ำ 15 นาที)"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (uiState.canSaveWorkout) EmeraldVibrant else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    // Live Metrics Row
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
private fun PostWorkoutSummaryScreen(
    uiState: WorkoutUiState,
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
                    color = MaterialTheme.colorScheme.primary
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
                    onClick = onDone,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("post_workout_done_button")
                ) {
                    Text("กลับหน้าหลัก", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
