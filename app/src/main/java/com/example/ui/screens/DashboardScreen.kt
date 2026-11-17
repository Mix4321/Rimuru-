package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ActiveWorkoutDraft
import com.example.data.model.SportType
import com.example.domain.DateStreakUtils
import com.example.ui.components.DailyGoalRing
import com.example.ui.components.GoalSettingDialog
import com.example.ui.components.MetricTile
import com.example.ui.components.MuscleRecoveryHeatmapCard
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.ForestGreenMedium
import com.example.ui.theme.MintBright
import com.example.ui.viewmodel.DashboardData

@Composable
fun DashboardScreen(
    data: DashboardData,
    onStartExerciseClick: () -> Unit,
    onQuickStartSport: (SportType) -> Unit,
    onUpdateDailyGoal: (Int) -> Unit,
    onUpdateProfile: (String, Float) -> Unit,
    onResumeDraft: (ActiveWorkoutDraft) -> Unit,
    onDiscardDraft: () -> Unit,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showGoalDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    if (showGoalDialog) {
        GoalSettingDialog(
            currentGoalMinutes = data.dailyGoal.goalMinutes,
            onDismiss = { showGoalDialog = false },
            onSaveGoal = { newGoal ->
                showGoalDialog = false
                onUpdateDailyGoal(newGoal)
            }
        )
    }

    if (showProfileDialog) {
        var nameInput by remember(data.profile.displayName) { mutableStateOf(data.profile.displayName) }
        var weightInput by remember(data.profile.weightKg) { mutableStateOf(data.profile.weightKg.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("โปรไฟล์ผู้ใช้") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("ชื่อที่แสดงในแอป") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { raw -> weightInput = raw.filter { it.isDigit() || it == '.' }.take(5) },
                        label = { Text("น้ำหนักตัว (กิโลกรัม) — ใช้คำนวณแคลอรีจริง") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "ข้อมูลการออกกำลังกาย เป้าหมาย และ Streak ถูกบันทึกไว้ในฐานข้อมูลบนอุปกรณ์อย่างถาวร",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showProfileDialog = false
                        val parsedWeight = weightInput.toFloatOrNull() ?: data.profile.weightKg
                        onUpdateProfile(nameInput, parsedWeight)
                    }
                ) {
                    Text("บันทึก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Atmospheric Athletic Hero Greeting Header
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = ForestGreenDeep),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ForestGreenDeep, ForestGreen, ForestGreenMedium)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "MOVE • Smart Fitness",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MintBright,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "สวัสดี, ${data.profile.displayName}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "พร้อมเคลื่อนไหวเพื่อสุขภาพที่ดีขึ้นวันนี้หรือยัง?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, MintBright.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable { showProfileDialog = true }
                                        .testTag("edit_profile_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = "แก้ไขโปรไฟล์",
                                            tint = MintBright
                                        )
                                    }
                                }
                            }

                            // Integrated Streak + Readiness Summary Pill Row inside Hero
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LocalFireDepartment,
                                            contentDescription = "Streak",
                                            tint = if (data.streak.hasCompletedToday) MintBright else AmberWarm,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "Streak ${data.streak.currentStreak} วัน",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.testTag("streak_count_text")
                                                )
                                                if (data.streak.hasCompletedToday) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CheckCircle,
                                                        contentDescription = null,
                                                        tint = MintBright,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = if (data.streak.longestStreak > 0) "สูงสุด ${data.streak.longestStreak} วัน" else "เริ่มสะสมสถิติ",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.72f)
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MintBright,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "พร้อม ${data.muscleRecovery.overallReadinessPercent}%",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "แนะนำ: ${data.muscleRecovery.recommendedSport.thaiName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MintBright
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Unfinished workout recovery banner if user exited mid-session
            if (data.pendingDraft != null) {
                item {
                    PendingWorkoutDraftCard(
                        draft = data.pendingDraft,
                        onResume = { onResumeDraft(data.pendingDraft) },
                        onDiscard = onDiscardDraft
                    )
                }
            }

            // Adaptive Main Section: Side-by-side on Tablet, stacked on Phone
            if (isWide) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DailyGoalCard(
                            data = data,
                            onEditGoal = { showGoalDialog = true },
                            modifier = Modifier.weight(1.1f)
                        )
                        Column(
                            modifier = Modifier.weight(0.9f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            StartExerciseHeroCard(onStartExerciseClick = onStartExerciseClick)
                            MuscleRecoveryHeatmapCard(
                                report = data.muscleRecovery,
                                onStartRecommendedSport = onQuickStartSport
                            )
                        }
                    }
                }
            } else {
                item {
                    DailyGoalCard(
                        data = data,
                        onEditGoal = { showGoalDialog = true }
                    )
                }
                item {
                    StartExerciseHeroCard(onStartExerciseClick = onStartExerciseClick)
                }
                item {
                    MuscleRecoveryHeatmapCard(
                        report = data.muscleRecovery,
                        onStartRecommendedSport = onQuickStartSport
                    )
                }
            }

            // Goal reached congratulations banner
            item {
                AnimatedVisibility(visible = data.dailyGoal.isGoalReached) {
                    GoalReachedBanner(
                        goalMinutes = data.dailyGoal.goalMinutes,
                        completedMinutes = data.dailyGoal.completedMinutesToday
                    )
                }
            }

            // Today's live stats row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "สรุปกิจกรรมวันนี้",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            icon = Icons.Rounded.LocalFireDepartment,
                            label = "แคลอรีวันนี้",
                            value = "${data.dailyGoal.caloriesToday} kcal",
                            accentColor = AmberWarm,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                            label = "ก้าววันนี้",
                            value = "${data.dailyGoal.stepsToday}",
                            accentColor = EmeraldVibrant,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            icon = Icons.Rounded.Route,
                            label = "ระยะทาง",
                            value = DateStreakUtils.formatDistanceKm(data.dailyGoal.distanceMetersToday),
                            accentColor = ForestGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick Sport Shortcuts + Recent History Preview
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "เริ่มต้นด่วน",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onViewHistory) {
                            Text("ดูประวัติทั้งหมด")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(SportType.RUNNING, SportType.WALKING, SportType.JUMP_ROPE).forEach { sport ->
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onQuickStartSport(sport) }
                                    .testTag("quick_sport_${sport.name}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp, horizontal = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(EmeraldVibrant, ForestGreen)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = sport.icon,
                                            contentDescription = sport.thaiName,
                                            tint = Color.White
                                        )
                                    }
                                    Text(
                                        text = sport.thaiName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "~${sport.kcalPerMinute.toInt()} kcal/น.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyGoalCard(
    data: DashboardData,
    onEditGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val goal = data.dailyGoal
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "เป้าหมายการออกกำลังกายวันนี้",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "เป้าหมาย ${goal.goalMinutes} นาที",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onEditGoal,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("edit_goal_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ปรับเป้า")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DailyGoalRing(
                    progressFraction = goal.progressFraction,
                    progressPercent = goal.progressPercent,
                    completedMinutes = goal.completedMinutesToday,
                    goalMinutes = goal.goalMinutes,
                    ringSize = 152.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GoalStatRow(
                        label = "เวลาสะสมวันนี้",
                        value = "${goal.completedMinutesToday} นาที"
                    )
                    GoalStatRow(
                        label = "เวลาที่เหลือ",
                        value = if (goal.isGoalReached) "ครบเป้าหมาย!" else "${goal.remainingMinutesToday} นาที"
                    )
                    GoalStatRow(
                        label = "ความคืบหน้า",
                        value = "${goal.progressPercent}%"
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalStatRow(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StartExerciseHeroCard(onStartExerciseClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onStartExerciseClick)
            .testTag("start_workout_cta")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(ForestGreen, EmeraldVibrant)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "เริ่มออกกำลังกายเลย",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GoalReachedBanner(goalMinutes: Int, completedMinutes: Int) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_goal_badge),
                contentDescription = stringResource(R.string.goal_badge_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ยินดีด้วย! คุณทำเป้าหมายรายวันสำเร็จแล้ว",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "วันนี้คุณออกกำลังกายไปแล้ว $completedMinutes นาที (เป้าหมาย $goalMinutes นาที)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun PendingWorkoutDraftCard(
    draft: ActiveWorkoutDraft,
    onResume: () -> Unit,
    onDiscard: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "พบกิจกรรม${draft.sportType.thaiName}ที่ยังไม่จบ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "เวลาที่จับไว้: ${DateStreakUtils.formatDurationClock(draft.elapsedSeconds)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("ยกเลิก")
                }
                Button(
                    onClick = onResume,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("ทำกิจกรรมต่อ")
                }
            }
        }
    }
}
