package com.example.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.SportType
import com.example.data.model.StretchExercise
import com.example.data.model.StretchingCatalog
import com.example.domain.DateStreakUtils
import com.example.ui.components.StretchAnimationCanvas
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.MintBright
import kotlinx.coroutines.delay

private val STRETCH_DURATION_PRESETS = listOf(15, 20, 30, 45, 60)

@Composable
fun StretchingScreen(
    sportType: SportType,
    onBack: () -> Unit,
    onStartWorkoutNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exercises = remember(sportType) { StretchingCatalog.exercisesFor(sportType) }
    val focusSummary = remember(sportType) { StretchingCatalog.summaryFocusFor(sportType) }
    val haptic = LocalHapticFeedback.current

    var currentIndex by remember(sportType) { mutableIntStateOf(0) }
    var secondsPerStretch by remember(sportType) { mutableIntStateOf(30) }
    var remainingSeconds by remember(sportType) { mutableIntStateOf(30) }
    var isTimerRunning by remember(sportType) { mutableStateOf(true) }
    var isRoutineCompleted by remember(sportType) { mutableStateOf(false) }

    val currentExercise = exercises.getOrElse(currentIndex) { exercises.first() }
    val isSecondHalfOfBilateral = currentExercise.hasLeftRightSides &&
        remainingSeconds <= (secondsPerStretch / 2)

    // Haptic nudge when switching to the opposite side halfway through bilateral stretch
    LaunchedEffect(currentIndex, isSecondHalfOfBilateral, isTimerRunning) {
        if (isTimerRunning && currentExercise.hasLeftRightSides && isSecondHalfOfBilateral) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Accurate monotonic countdown loop that advances through every stretch automatically
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
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        text = "ยืดกล้ามเนื้อ • ${sportType.thaiName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = focusSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(
                onClick = onStartWorkoutNow,
                modifier = Modifier.testTag("skip_stretch_button")
            ) {
                Text("ข้าม", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "ข้ามไปออกกำลังกาย",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Routine Completed Banner OR Active Pose Animation Card
        AnimatedVisibility(visible = isRoutineCompleted) {
            StretchCompletedHeroCard(
                sportType = sportType,
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
            // Animated Stretch Stage Card
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreenDeep),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    StretchAnimationCanvas(
                        animationType = currentExercise.animationType,
                        isPlaying = true,
                        isLeftSideActive = !isSecondHalfOfBilateral,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top Status Row (Pose Index + Side Indicator)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            Text(
                                text = "ท่าที่ ${currentIndex + 1} / ${exercises.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

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
                                color = Color.Black.copy(alpha = 0.45f)
                            ) {
                                Text(
                                    text = if (isTimerRunning) "กำลังยืดต่อเนื่อง" else "พักจับเวลา (ดูตัวอย่างท่า)",
                                    color = MintBright,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Bottom Timer & Pose Name Overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.48f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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

        // All Routine Poses List (Tap to jump/preview)
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

        // Bottom Direct Workout Launch CTA
        Button(
            onClick = onStartWorkoutNow,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_workout_after_stretch_button")
        ) {
            Icon(Icons.Rounded.FitnessCenter, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRoutineCompleted) {
                    "พร้อมแล้ว! เริ่มออกกำลังกาย${sportType.thaiName}"
                } else {
                    "ข้ามที่เหลือ แล้วเริ่มออกกำลังกายเลย"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun StretchCompletedHeroCard(
    sportType: SportType,
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
                text = "ยืดกล้ามเนื้อครบทั้ง $exerciseCount ท่าแล้ว!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "ร่างกายอบอุ่นและพร้อมสำหรับ${sportType.thaiName}แล้ว (ใช้เวลา $totalMinutesText นาที)",
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
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("เริ่มเล่นเลย", fontWeight = FontWeight.Bold)
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
