package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ExerciseSetSpec
import com.example.data.model.WorkoutProgram
import com.example.domain.DateStreakUtils
import com.example.ui.components.GlassPulseCard
import com.example.ui.components.NeonPulseButton
import com.example.ui.components.PulseSecondaryActionButton
import com.example.ui.components.bounceClickable
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.DeepForestGreen
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.NeonLime
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianSurface
import kotlinx.coroutines.delay

@Composable
fun WorkoutDetailScreen(
    program: WorkoutProgram,
    onBack: () -> Unit,
    onStartWorkout: (WorkoutProgram) -> Unit,
    onStartPreStretch: (WorkoutProgram) -> Unit,
    modifier: Modifier = Modifier
) {
    var restPresetSec by remember(program.id) { mutableIntStateOf(program.defaultRestSeconds) }
    var restRemainingSec by remember(program.id) { mutableIntStateOf(program.defaultRestSeconds) }
    var isTimerRunning by remember(program.id) { mutableStateOf(false) }
    val completedExercises = remember(program.id) { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(isTimerRunning, restRemainingSec) {
        if (isTimerRunning && restRemainingSec > 0) {
            delay(1000L)
            restRemainingSec -= 1
            if (restRemainingSec == 0) {
                isTimerRunning = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Large Fitness Exercise Photo Hero
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    Image(
                        painter = painterResource(id = program.imageRes),
                        contentDescription = program.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        ObsidianBlack.copy(alpha = 0.55f),
                                        ObsidianBlack.copy(alpha = 0.25f),
                                        ObsidianBlack
                                    )
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ObsidianBlack.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("workout_detail_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "ย้อนกลับ",
                                    tint = Color.White
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = NeonLime
                        ) {
                            Text(
                                text = "${program.category.title.uppercase()} • ${program.difficulty.label.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ObsidianBlack,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = program.coachFocus,
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldPulse,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = program.title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = program.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 2. Program Specs Glass Strip
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailSpecTile(
                        icon = Icons.Rounded.Schedule,
                        label = "Duration",
                        value = "${program.durationMinutes} min",
                        accent = NeonLime,
                        modifier = Modifier.weight(1f)
                    )
                    DetailSpecTile(
                        icon = Icons.Rounded.LocalFireDepartment,
                        label = "Target Burn",
                        value = "${program.estimatedCalories} kcal",
                        accent = EmeraldPulse,
                        modifier = Modifier.weight(1f)
                    )
                    DetailSpecTile(
                        icon = Icons.Rounded.FitnessCenter,
                        label = "Exercises",
                        value = "${program.exercises.size} ท่า",
                        accent = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Interactive Rest Timer
            item {
                val fraction = if (restPresetSec > 0) restRemainingSec.toFloat() / restPresetSec.toFloat() else 0f
                GlassPulseCard(
                    highlightGlow = true,
                    cornerRadius = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "REST TIMER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonLime,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ตัวจับเวลาพักระหว่างเซต",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val stroke = 5.dp.toPx()
                                        drawArc(
                                            color = Color.White.copy(alpha = 0.14f),
                                            startAngle = 0f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            topLeft = Offset(stroke / 2f, stroke / 2f),
                                            size = Size(size.width - stroke, size.height - stroke),
                                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                                        )
                                        drawArc(
                                            brush = Brush.sweepGradient(listOf(EmeraldPulse, NeonLime, EmeraldPulse)),
                                            startAngle = -90f,
                                            sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                                            useCenter = false,
                                            topLeft = Offset(stroke / 2f, stroke / 2f),
                                            size = Size(size.width - stroke, size.height - stroke),
                                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                                        )
                                    }
                                    Text(
                                        text = DateStreakUtils.formatDurationClock(restRemainingSec),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NeonLime,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(30, 45, 60, 90).forEach { sec ->
                                val selected = restPresetSec == sec
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        restPresetSec = sec
                                        restRemainingSec = sec
                                        isTimerRunning = false
                                    },
                                    label = { Text("${sec}s") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = ObsidianSurface,
                                        labelColor = Color.White,
                                        selectedContainerColor = EmeraldPulse,
                                        selectedLabelColor = ObsidianBlack
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val timerBtnShape = RoundedCornerShape(14.dp)
                            Button(
                                onClick = {
                                    if (restRemainingSec == 0) restRemainingSec = restPresetSec
                                    isTimerRunning = !isTimerRunning
                                },
                                shape = timerBtnShape,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = ObsidianBlack
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = timerBtnShape,
                                        ambientColor = if (isTimerRunning) AmberWarm else NeonLime,
                                        spotColor = if (isTimerRunning) AmberWarm else EmeraldPulse
                                    )
                                    .clip(timerBtnShape)
                                    .background(
                                        if (isTimerRunning) {
                                            Brush.horizontalGradient(listOf(AmberWarm, Color(0xFFFFD54F)))
                                        } else {
                                            Brush.horizontalGradient(listOf(EmeraldPulse, NeonLime))
                                        }
                                    )
                                    .testTag("rest_timer_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isTimerRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = ObsidianBlack
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTimerRunning) "Pause Rest" else "Start Rest",
                                    color = ObsidianBlack,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    isTimerRunning = false
                                    restRemainingSec = restPresetSec
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("rest_timer_reset_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Reset Timer", tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 4. Exercise Instructions & Sets/Reps
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "SETS, REPETITIONS & FORM GUIDE",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPulse,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ลำดับท่าฝึกและคำแนะนำ (${program.exercises.size} ท่า)",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            itemsIndexed(program.exercises, key = { _, ex -> ex.id }) { idx, ex ->
                val done = completedExercises[ex.id] == true
                ExerciseInstructionCard(
                    index = idx + 1,
                    exercise = ex,
                    completed = done,
                    onToggleComplete = { completedExercises[ex.id] = !done },
                    onQuickRest = {
                        restPresetSec = ex.restSeconds
                        restRemainingSec = ex.restSeconds
                        isTimerRunning = true
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        // Sticky Bottom Start Workout Action Bar
        Surface(
            color = ObsidianBlack.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.25f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PulseSecondaryActionButton(
                    text = "วอร์มอัพ",
                    onClick = { onStartPreStretch(program) },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.AccessibilityNew,
                            contentDescription = "วอร์มอัพ",
                            tint = NeonLime,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("detail_stretch_button")
                )

                NeonPulseButton(
                    text = "START WORKOUT",
                    onClick = { onStartWorkout(program) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("detail_start_workout_button"),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = ObsidianBlack
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailSpecTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    GlassPulseCard(
        cornerRadius = 18.dp,
        accentBorder = accent.copy(alpha = 0.26f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun ExerciseInstructionCard(
    index: Int,
    exercise: ExerciseSetSpec,
    completed: Boolean,
    onToggleComplete: () -> Unit,
    onQuickRest: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPulseCard(
        cornerRadius = 22.dp,
        accentBorder = if (completed) NeonLime.copy(alpha = 0.65f) else EmeraldPulse.copy(alpha = 0.25f),
        modifier = modifier
            .fillMaxWidth()
            .bounceClickable(onClick = onToggleComplete)
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (completed) NeonLime else DeepForestGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$index",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (completed) ObsidianBlack else NeonLime,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${exercise.setsAndReps} • ${exercise.targetMuscle}",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonLime,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Icon(
                    imageVector = if (completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = "ทำเครื่องหมายว่าเสร็จแล้ว",
                    tint = if (completed) NeonLime else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = exercise.instruction,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DeepForestGreen.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .bounceClickable(onClick = onQuickRest)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = NeonLime, modifier = Modifier.size(14.dp))
                        Text(
                            text = "พัก ${exercise.restSeconds}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonLime,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
