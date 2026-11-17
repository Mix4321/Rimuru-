package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.camera.PoseOverlayCanvas
import com.example.camera.StretchFormFeedback
import com.example.camera.StretchPoseAnalyzer
import com.example.data.local.AppSettings
import com.example.data.local.DetectionSensitivity
import com.example.data.model.SportType
import com.example.data.model.StretchAnimationType
import com.example.data.model.StretchExercise
import com.example.data.model.StretchingCatalog
import com.example.domain.DateStreakUtils
import com.example.ui.components.StretchAnimationCanvas
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.MintBright
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val STRETCH_DURATION_PRESETS = listOf(15, 20, 30, 45, 60)

@Composable
fun StretchingScreen(
    sportType: SportType,
    appSettings: AppSettings,
    isCoolDown: Boolean = false,
    onBack: () -> Unit,
    onStartWorkoutNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exercises = remember(sportType) { StretchingCatalog.exercisesFor(sportType) }
    val focusSummary = remember(sportType) { StretchingCatalog.summaryFocusFor(sportType) }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val context = LocalContext.current

    val toneGen = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 65) }.getOrNull()
    }

    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember(context) {
        var instance: TextToSpeech? = null
        instance = runCatching {
            TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val res = instance?.setLanguage(Locale("th", "TH"))
                    ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                }
            }
        }.getOrNull()
        instance
    }

    DisposableEffect(appSettings.keepScreenOn) {
        view.keepScreenOn = appSettings.keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { toneGen?.release() }
            runCatching {
                tts?.stop()
                tts?.shutdown()
            }
        }
    }

    var currentIndex by remember(sportType) { mutableIntStateOf(0) }
    var secondsPerStretch by remember(sportType, appSettings.defaultStretchSeconds) {
        mutableIntStateOf(appSettings.defaultStretchSeconds.coerceIn(10, 90))
    }
    var remainingSeconds by remember(sportType, appSettings.defaultStretchSeconds) {
        mutableIntStateOf(appSettings.defaultStretchSeconds.coerceIn(10, 90))
    }
    var isTimerRunning by remember(sportType) { mutableStateOf(true) }
    var isRoutineCompleted by remember(sportType) { mutableStateOf(false) }

    var useCameraCoach by remember(sportType) { mutableStateOf(false) }
    var useFrontCamera by remember(sportType) { mutableStateOf(true) }
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var stretchFeedback by remember { mutableStateOf(StretchFormFeedback()) }
    var stretchRelockTick by remember { mutableIntStateOf(0) }
    var stretchTapLockRequest by remember { mutableStateOf<StretchTapLockRequest?>(null) }
    var stretchCameraBoxSize by remember { mutableStateOf(IntSize.Zero) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPerm = granted
        useCameraCoach = granted
    }

    val currentExercise = exercises.getOrElse(currentIndex) { exercises.first() }
    val isSecondHalfOfBilateral = currentExercise.hasLeftRightSides &&
        remainingSeconds <= (secondsPerStretch / 2)

    // Voice / audio guidance on pose switch
    LaunchedEffect(currentIndex, ttsReady, appSettings.enableStretchBeep) {
        if (appSettings.enableStretchBeep && ttsReady) {
            runCatching {
                tts?.speak(
                    "ท่าที่ ${currentIndex + 1} ${currentExercise.thaiName}",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "stretch_pose_$currentIndex"
                )
            }
        }
    }

    // Haptic & subtle audio cue when switching sides halfway through bilateral stretch
    LaunchedEffect(currentIndex, isSecondHalfOfBilateral, isTimerRunning) {
        if (isTimerRunning && currentExercise.hasLeftRightSides && isSecondHalfOfBilateral) {
            if (appSettings.enableHapticFeedback) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            if (appSettings.enableStretchBeep) {
                if (ttsReady) {
                    runCatching {
                        tts?.speak(
                            "สลับอีกข้าง",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "stretch_switch_$currentIndex"
                        )
                    }
                } else {
                    runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 160) }
                }
            }
        }
    }

    // Accurate monotonic countdown loop
    LaunchedEffect(isTimerRunning, isRoutineCompleted, currentIndex) {
        if (!isTimerRunning || isRoutineCompleted) return@LaunchedEffect
        var lastReal = SystemClock.elapsedRealtime()
        var carryMs = 0L

        while (isTimerRunning && !isRoutineCompleted) {
            delay(250L)
            val nowReal = SystemClock.elapsedRealtime()
            carryMs += (nowReal - lastReal).coerceIn(0L, 2000L)
            lastReal = nowReal

            val elapsedSec = (carryMs / 1000L).toInt()
            if (elapsedSec <= 0) continue
            carryMs %= 1000L

            val nextRem = remainingSeconds - elapsedSec
            if (nextRem > 0) {
                remainingSeconds = nextRem
            } else {
                if (appSettings.enableHapticFeedback) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                if (appSettings.enableStretchBeep && !ttsReady) {
                    runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 240) }
                }
                if (currentIndex < exercises.lastIndex) {
                    currentIndex += 1
                    remainingSeconds = secondsPerStretch
                } else {
                    remainingSeconds = 0
                    isTimerRunning = false
                    isRoutineCompleted = true
                }
            }
        }
    }

    fun selectPresetSeconds(newSec: Int) {
        val clamped = newSec.coerceIn(10, 90)
        secondsPerStretch = clamped
        if (!isRoutineCompleted) {
            remainingSeconds = clamped
        }
    }

    fun nudgeDuration(delta: Int) {
        val next = (secondsPerStretch + delta).coerceIn(10, 90)
        val diff = next - secondsPerStretch
        secondsPerStretch = next
        if (!isRoutineCompleted && diff != 0) {
            remainingSeconds = (remainingSeconds + diff).coerceIn(3, next)
        }
    }

    fun jumpToExercise(index: Int) {
        val safeIdx = index.coerceIn(0, exercises.lastIndex)
        currentIndex = safeIdx
        remainingSeconds = secondsPerStretch
        isRoutineCompleted = false
    }

    BackHandler {
        onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("stretch_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "ย้อนกลับ"
                    )
                }
                Column {
                    Text(
                        text = if (isCoolDown) {
                            "คูลดาวน์ฟื้นฟู • ${sportType.thaiName}"
                        } else {
                            "ยืดกล้ามเนื้อ • ${sportType.thaiName}"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isCoolDown) {
                            "ผ่อนคลายกล้ามเนื้อและลดอาการตึงหลังออกกำลังกาย"
                        } else {
                            focusSummary
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(
                onClick = onStartWorkoutNow,
                modifier = Modifier.testTag("skip_stretch_button")
            ) {
                Text(if (isCoolDown) "เสร็จสิ้น" else "ข้าม", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = if (isCoolDown) "กลับหน้าหลัก" else "ข้ามไปออกกำลังกาย",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Interactive AI Stretch Camera Coach Toggle Bar
        if (!isRoutineCompleted) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (useCameraCoach) EmeraldVibrant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (useCameraCoach) EmeraldVibrant.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                tint = if (useCameraCoach) EmeraldVibrant else MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                text = "AI กล้องเช็กท่ายืดอัจฉริยะ",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (useCameraCoach) "AI กำลังวิเคราะห์ความถูกต้องของท่ายืดแบบเรียลไทม์" else "เปิดกล้องเพื่อเทียบท่ากับตัวการ์ตูนโค้ช",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilterChip(
                        selected = useCameraCoach,
                        onClick = {
                            if (useCameraCoach) {
                                useCameraCoach = false
                            } else if (hasCameraPerm) {
                                useCameraCoach = true
                            } else {
                                cameraLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        label = {
                            Text(
                                text = if (useCameraCoach) "เปิดอยู่" else "เปิดกล้อง",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldVibrant,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("toggle_ai_stretch_coach_button")
                    )
                }
            }
        }

        // Routine Completed Banner OR Active Pose Animation Card
        AnimatedVisibility(visible = isRoutineCompleted) {
            StretchCompletedHeroCard(
                sportType = sportType,
                isCoolDown = isCoolDown,
                exerciseCount = exercises.size,
                totalMinutesText = DateStreakUtils.formatDurationClock(exercises.size * secondsPerStretch),
                onStartWorkoutNow = onStartWorkoutNow,
                onRepeatRoutine = {
                    currentIndex = 0
                    remainingSeconds = secondsPerStretch
                    isRoutineCompleted = false
                    isTimerRunning = true
                }
            )
        }

        if (!isRoutineCompleted) {
            // Animated Stretch Stage Card (with optional live CameraX + PoseOverlay PiP)
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreenDeep),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (useCameraCoach && hasCameraPerm) 340.dp else 290.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { stretchCameraBoxSize = it }
                        .pointerInput(useCameraCoach, hasCameraPerm) {
                            if (useCameraCoach && hasCameraPerm) {
                                detectTapGestures { offset ->
                                    if (stretchCameraBoxSize.width > 0 && stretchCameraBoxSize.height > 0) {
                                        stretchTapLockRequest = StretchTapLockRequest(
                                            id = System.nanoTime(),
                                            x = offset.x,
                                            y = offset.y,
                                            viewWidth = stretchCameraBoxSize.width.toFloat(),
                                            viewHeight = stretchCameraBoxSize.height.toFloat()
                                        )
                                    }
                                }
                            }
                        }
                ) {
                    if (useCameraCoach && hasCameraPerm) {
                        StretchCameraCoachPreview(
                            stretchType = currentExercise.animationType,
                            isLeftSideActive = !isSecondHalfOfBilateral,
                            sensitivity = appSettings.detectionSensitivity,
                            useFrontCamera = useFrontCamera,
                            relockTick = stretchRelockTick,
                            tapLockRequest = stretchTapLockRequest,
                            onStretchEvaluated = { stretchFeedback = it },
                            modifier = Modifier.fillMaxSize()
                        )
                        PoseOverlayCanvas(
                            snapshot = stretchFeedback.snapshot,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Picture-in-Picture reference athlete demonstration
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, MintBright.copy(alpha = 0.65f)),
                            color = ForestGreenDeep.copy(alpha = 0.9f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 68.dp, end = 12.dp)
                                .size(width = 104.dp, height = 118.dp)
                        ) {
                            StretchAnimationCanvas(
                                animationType = currentExercise.animationType,
                                isPlaying = true,
                                isLeftSideActive = !isSecondHalfOfBilateral,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        StretchAnimationCanvas(
                            animationType = currentExercise.animationType,
                            isPlaying = true,
                            isLeftSideActive = !isSecondHalfOfBilateral,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Top Status Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "ท่าที่ ${currentIndex + 1} / ${exercises.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentExercise.hasLeftRightSides) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isSecondHalfOfBilateral) AmberWarm else EmeraldVibrant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SwapHoriz,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isSecondHalfOfBilateral) "สลับอีกข้าง" else "ข้างแรก",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.Black.copy(alpha = 0.55f)
                                ) {
                                    Text(
                                        text = if (isTimerRunning) "กำลังยืดต่อเนื่อง" else "พักจับเวลา",
                                        color = MintBright,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            if (useCameraCoach && hasCameraPerm) {
                                IconButton(
                                    onClick = { stretchRelockTick++ },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (stretchFeedback.snapshot.isForeignPersonBlocked) {
                                                AmberWarm.copy(alpha = 0.86f)
                                            } else {
                                                Color.Black.copy(alpha = 0.6f)
                                            }
                                        )
                                        .testTag("stretch_relock_subject_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CenterFocusStrong,
                                        contentDescription = "ล็อกตัวผู้ยืดใหม่",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { useFrontCamera = !useFrontCamera },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .testTag("stretch_switch_camera_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cameraswitch,
                                        contentDescription = "สลับกล้อง",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Timer, AI Stretch Score Pill & Pose Name Overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (useCameraCoach && hasCameraPerm) {
                            if (stretchFeedback.snapshot.isSubjectLocked || stretchFeedback.snapshot.isForeignPersonBlocked) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (stretchFeedback.snapshot.isForeignPersonBlocked) {
                                        AmberWarm.copy(alpha = 0.9f)
                                    } else {
                                        ForestGreenDeep.copy(alpha = 0.8f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (stretchFeedback.snapshot.isForeignPersonBlocked) {
                                            Color.White.copy(alpha = 0.65f)
                                        } else {
                                            MintBright.copy(alpha = 0.55f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
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
                                            text = if (stretchFeedback.snapshot.isForeignPersonBlocked) {
                                                "กันคนเดินผ่าน • ล็อกตัวคุณไว้แล้ว"
                                            } else {
                                                "ล็อกตัวแล้ว • แตะตัวคุณเพื่อย้ายจุดล็อก"
                                            },
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = when {
                                    stretchFeedback.snapshot.isForeignPersonBlocked -> AmberWarm.copy(alpha = 0.94f)
                                    stretchFeedback.isPostureCorrect -> EmeraldVibrant.copy(alpha = 0.92f)
                                    else -> AmberWarm.copy(alpha = 0.92f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "AI ฟอร์มยืด ${stretchFeedback.formScore}% • ${stretchFeedback.feedbackText}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentExercise.thaiName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = DateStreakUtils.formatDurationClock(remainingSeconds),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MintBright,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("stretch_countdown_text")
                            )
                        }
                        val poseProgress = (1f - (remainingSeconds.toFloat() / secondsPerStretch.coerceAtLeast(1))).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { poseProgress },
                            color = MintBright,
                            trackColor = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }
                }
            }

            // Playback Control Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        if (currentIndex > 0) jumpToExercise(currentIndex - 1)
                    },
                    enabled = currentIndex > 0,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("prev_stretch_button")
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "ท่าก่อนหน้า")
                }

                Button(
                    onClick = { isTimerRunning = !isTimerRunning },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTimerRunning) AmberWarm else EmeraldVibrant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("stretch_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isTimerRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTimerRunning) "พักจับเวลา" else "เริ่มจับเวลาต่อ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        if (currentIndex < exercises.lastIndex) {
                            jumpToExercise(currentIndex + 1)
                        } else {
                            isTimerRunning = false
                            isRoutineCompleted = true
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("next_stretch_button")
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "ท่าถัดไป")
                }
            }
        }

        // Adjustable Duration Card (Presets + Fine +/- 5s Stepper)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "ปรับเวลายืดต่อท่า",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${exercises.size} ท่า • เวลารวม ${DateStreakUtils.formatDurationClock(exercises.size * secondsPerStretch)} นาที",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { nudgeDuration(-5) },
                            enabled = secondsPerStretch > 10,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("decrease_stretch_time_button")
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "ลด 5 วินาที")
                        }
                        Text(
                            text = "${secondsPerStretch}วิ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        FilledTonalIconButton(
                            onClick = { nudgeDuration(5) },
                            enabled = secondsPerStretch < 90,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("increase_stretch_time_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "เพิ่ม 5 วินาที")
                        }
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(STRETCH_DURATION_PRESETS) { preset ->
                        FilterChip(
                            selected = secondsPerStretch == preset,
                            onClick = { selectPresetSeconds(preset) },
                            label = { Text("$preset วิ") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("stretch_preset_$preset")
                        )
                    }
                }
            }
        }

        // Step-by-Step Instructions Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "วิธีปฏิบัติทีละขั้น",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = currentExercise.targetMuscles,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                currentExercise.steps.forEachIndexed { idx, stepText ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = stepText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AmberSoft
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = AmberWarm,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = currentExercise.safetyTip,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5C3404),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // All Routine Poses List
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "รายการท่ายืดสำหรับ${sportType.thaiName} (${exercises.size} ท่า)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            exercises.forEachIndexed { idx, exercise ->
                StretchPlaylistRow(
                    index = idx,
                    exercise = exercise,
                    seconds = secondsPerStretch,
                    isCurrent = idx == currentIndex && !isRoutineCompleted,
                    isDone = isRoutineCompleted || idx < currentIndex,
                    onClick = { jumpToExercise(idx) }
                )
            }
        }

        // Bottom Direct Launch CTA
        Button(
            onClick = onStartWorkoutNow,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_workout_after_stretch_button")
        ) {
            Icon(
                imageVector = if (isCoolDown) Icons.Rounded.CheckCircle else Icons.Rounded.FitnessCenter,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    isCoolDown && isRoutineCompleted -> "เสร็จสิ้นการคูลดาวน์ กลับหน้าหลัก"
                    isCoolDown -> "เสร็จสิ้นและกลับหน้าหลัก"
                    isRoutineCompleted -> "พร้อมแล้ว! เริ่มออกกำลังกาย${sportType.thaiName}"
                    else -> "ข้ามที่เหลือ แล้วเริ่มออกกำลังกายเลย"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private data class StretchTapLockRequest(
    val id: Long,
    val x: Float,
    val y: Float,
    val viewWidth: Float,
    val viewHeight: Float
)

@Composable
private fun StretchCameraCoachPreview(
    stretchType: StretchAnimationType,
    isLeftSideActive: Boolean,
    sensitivity: DetectionSensitivity,
    useFrontCamera: Boolean,
    relockTick: Int,
    tapLockRequest: StretchTapLockRequest?,
    onStretchEvaluated: (StretchFormFeedback) -> Unit,
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

    val currentStretch by rememberUpdatedState(stretchType)
    val currentSensitivity by rememberUpdatedState(sensitivity)
    val activeFrontCamera = remember { AtomicBoolean(useFrontCamera) }
    val boundProviderRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val analyzer = remember {
        StretchPoseAnalyzer(
            stretchTypeProvider = { currentStretch },
            sensitivityProvider = { currentSensitivity },
            isFrontCameraProvider = { activeFrontCamera.get() },
            onStretchEvaluated = onStretchEvaluated
        )
    }

    LaunchedEffect(relockTick) {
        if (relockTick > 0) {
            analyzer.relockPrimarySubject()
        }
    }

    LaunchedEffect(tapLockRequest) {
        tapLockRequest?.let { req ->
            analyzer.lockSubjectAtTap(req.x, req.y, req.viewWidth, req.viewHeight)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { boundProviderRef.get()?.unbindAll() }
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(useFrontCamera) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                boundProviderRef.set(cameraProvider)
                val preferred = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val selector = when {
                    cameraProvider.hasCamera(preferred) -> preferred
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    else -> null
                } ?: return@addListener

                activeFrontCamera.set(selector == CameraSelector.DEFAULT_FRONT_CAMERA)
                analyzer.reset()

                val previewRes = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()

                val analysisRes = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(previewRes)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(analysisRes)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                Log.w("StretchCamera", "CameraX bind error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun StretchCompletedHeroCard(
    sportType: SportType,
    isCoolDown: Boolean,
    exerciseCount: Int,
    totalMinutesText: String,
    onStartWorkoutNow: () -> Unit,
    onRepeatRoutine: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = EmeraldVibrant,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = if (isCoolDown) "คูลดาวน์ฟื้นฟูกล้ามเนื้อครบแล้ว!" else "ยืดกล้ามเนื้อครบทั้ง $exerciseCount ท่าแล้ว!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isCoolDown) {
                    "ช่วยลดอาการปวดเมื่อยหลัง${sportType.thaiName}ได้เป็นอย่างดี (ใช้เวลา $totalMinutesText นาที)"
                } else {
                    "ร่างกายอบอุ่นและพร้อมสำหรับ${sportType.thaiName}แล้ว (ใช้เวลา $totalMinutesText นาที)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRepeatRoutine,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ยืดอีกรอบ")
                }
                Button(
                    onClick = onStartWorkoutNow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = if (isCoolDown) Icons.Rounded.CheckCircle else Icons.Rounded.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCoolDown) "กลับหน้าหลัก" else "เริ่มเล่นเลย",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StretchPlaylistRow(
    index: Int,
    exercise: StretchExercise,
    seconds: Int,
    isCurrent: Boolean,
    isDone: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = when {
            isCurrent -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        border = if (isCurrent) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = if (isCurrent) 2.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("stretch_item_${exercise.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isDone -> EmeraldVibrant.copy(alpha = 0.16f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isDone && !isCurrent) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldVibrant,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AccessibilityNew,
                        contentDescription = null,
                        tint = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${index + 1}. ${exercise.thaiName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = exercise.targetMuscles,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${seconds}วิ",
                style = MaterialTheme.typography.labelLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
