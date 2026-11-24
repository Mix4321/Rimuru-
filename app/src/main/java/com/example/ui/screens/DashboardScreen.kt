package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsHandball
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ActiveWorkoutDraft
import com.example.data.model.SportType
import com.example.domain.DateStreakUtils
import com.example.ui.components.DailyGoalRing
import com.example.ui.components.GoalSettingDialog
import com.example.ui.components.LivePulseBeacon
import com.example.ui.components.MetricTile
import com.example.ui.components.auroraGlowBackdrop
import com.example.ui.components.bounceClickable
import com.example.ui.components.shimmerSweepEffect
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.ForestGreenMedium
import com.example.ui.theme.MintBright
import com.example.ui.viewmodel.DashboardData
import com.example.widget.MoveWidgetProvider

@Composable
fun DashboardScreen(
    data: DashboardData,
    userEmail: String? = null,
    userPhotoUrl: String? = null,
    onStartExerciseClick: () -> Unit,
    onQuickStartSport: (SportType) -> Unit,
    onUpdateDailyGoal: (Int) -> Unit,
    onUpdateProfile: (String, Float) -> Unit,
    onResumeDraft: (ActiveWorkoutDraft) -> Unit,
    onDiscardDraft: () -> Unit,
    onViewHistory: () -> Unit,
    onOpenRecoveryPlanPage: () -> Unit = {},
    onOpenProgressionBadgesPage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showGoalDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    if (showGoalDialog) {
        GoalSettingDialog(
            currentGoalMinutes = data.dailyGoal.goalMinutes,
            onDismiss = { showGoalDialog = false },
            onSaveGoal = { newGoal ->
                showGoalDialog = false
                onUpdateDailyGoal(newGoal)
                MoveWidgetProvider.refreshAllWidgets(context)
            }
        )
    }

    if (showProfileDialog) {
        var nameInput by remember(data.profile.displayName) { mutableStateOf(data.profile.displayName) }
        var weightInput by remember(data.profile.weightKg) { mutableStateOf(data.profile.weightKg.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("โปรไฟล์นักกีฬา") },
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
                        text = if (!userEmail.isNullOrBlank()) {
                            "บัญชีที่เชื่อมต่อ: $userEmail (ซิงก์คลาวด์อัตโนมัติ)"
                        } else {
                            "ข้อมูลการออกกำลังกาย เป้าหมาย และ Streak ซิงก์กับบัญชีของคุณอย่างปลอดภัย"
                        },
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
            // 1. Clean Hero Header with Account Avatar & Cloud Badge
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
                            .auroraGlowBackdrop(
                                primaryGlow = MintBright,
                                secondaryGlow = EmeraldVibrant
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "MOVE",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MintBright,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = Color.White.copy(alpha = 0.14f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                LivePulseBeacon(
                                                    color = MintBright,
                                                    isActive = true,
                                                    size = 10.dp
                                                )
                                                Icon(
                                                    imageVector = Icons.Rounded.CloudDone,
                                                    contentDescription = null,
                                                    tint = MintBright,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "ซิงก์คลาวด์",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "สวัสดี, ${data.profile.displayName}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Level ${data.progression.level} • ${data.progression.rankTitleThai}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MintBright
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.14f),
                                    border = BorderStroke(1.5.dp, MintBright.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .bounceClickable { showProfileDialog = true }
                                        .testTag("edit_profile_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (!userPhotoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = userPhotoUrl,
                                                contentDescription = "รูปโปรไฟล์ผู้ใช้",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = "แก้ไขโปรไฟล์",
                                                tint = MintBright
                                            )
                                        }
                                    }
                                }
                            }

                            val fireTransition = rememberInfiniteTransition(label = "streak_fire_pulse")
                            val fireScale by fireTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = if (data.streak.hasCompletedToday) 1.12f else 1.04f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "fire_scale"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White.copy(alpha = 0.12f),
                                    border = if (data.streak.hasCompletedToday) {
                                        BorderStroke(1.dp, MintBright.copy(alpha = 0.38f))
                                    } else null,
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
                                            modifier = Modifier
                                                .size(22.dp)
                                                .graphicsLayer {
                                                    scaleX = fireScale
                                                    scaleY = fireScale
                                                }
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .bounceClickable(onClick = onOpenRecoveryPlanPage)
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

            // 2. Unfinished workout recovery banner if paused
            if (data.pendingDraft != null) {
                item {
                    PendingWorkoutDraftCard(
                        draft = data.pendingDraft,
                        onResume = { onResumeDraft(data.pendingDraft) },
                        onDiscard = onDiscardDraft
                    )
                }
            }

            // 3. Goal Ring + Primary CTA
            if (isWide) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DailyGoalCard(
                            data = data,
                            onEditGoal = { showGoalDialog = true },
                            modifier = Modifier.weight(1.1f)
                        )
                        Column(
                            modifier = Modifier.weight(0.9f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            StartExerciseHeroCard(onStartExerciseClick = onStartExerciseClick)
                            TodayStatsRow(data = data)
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
            }

            item {
                AnimatedVisibility(visible = !data.dailyGoal.isFreeMode && data.dailyGoal.isGoalReached) {
                    GoalReachedBanner(
                        goalMinutes = data.dailyGoal.goalMinutes,
                        completedMinutes = data.dailyGoal.completedMinutesToday
                    )
                }
            }

            if (!isWide) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "สรุปกิจกรรมวันนี้",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        TodayStatsRow(data = data)
                    }
                }
            }

            // 4. Dedicated Topic Hub (Clean separation: 1 page per topic)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "หัวข้อเฉพาะทาง",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TopicNavigationCard(
                            icon = Icons.Rounded.FitnessCenter,
                            title = "แผนฝึก & กล้ามเนื้อ",
                            subtitle = "ความพร้อม ${data.muscleRecovery.overallReadinessPercent}% • ${data.adaptivePlan.badgeTag}",
                            accentColor = EmeraldVibrant,
                            onClick = onOpenRecoveryPlanPage,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("topic_recovery_plan_card")
                        )
                        TopicNavigationCard(
                            icon = Icons.Rounded.EmojiEvents,
                            title = "เลเวล & เหรียญ",
                            subtitle = "Lv.${data.progression.level} • ${data.progression.unlockedCount}/${data.progression.badges.size} เหรียญ",
                            accentColor = AmberWarm,
                            onClick = onOpenProgressionBadgesPage,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("topic_badges_card")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TopicNavigationCard(
                            icon = Icons.Rounded.SportsHandball,
                            title = "กีฬา & ยืดเหยียด",
                            subtitle = "8 ชนิดกีฬา • กล้อง AI / GPS",
                            accentColor = ForestGreen,
                            onClick = onStartExerciseClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("topic_sports_card")
                        )
                        TopicNavigationCard(
                            icon = Icons.Rounded.BarChart,
                            title = "สถิติ & ประวัติ",
                            subtitle = "บันทึกแล้ว ${data.workouts.size} กิจกรรม",
                            accentColor = ForestGreenMedium,
                            onClick = onViewHistory,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("topic_history_card")
                        )
                    }
                }
            }

            // 5. Compact Quick-Start Sport Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "เริ่มต้นด่วน",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            listOf(
                                SportType.GENERAL,
                                SportType.RUNNING,
                                SportType.WALKING,
                                SportType.JUMP_ROPE,
                                SportType.CYCLING
                            ),
                            key = { it.name }
                        ) { sport ->
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .width(126.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .bounceClickable { onQuickStartSport(sport) }
                                    .testTag("quick_sport_${sport.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
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
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = sport.thaiName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${sport.kcalPerMinute.toInt()} kcal/น.",
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
}

@Composable
private fun TodayStatsRow(data: DashboardData) {
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

@Composable
private fun TopicNavigationCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .bounceClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val center = Offset(size.width * 0.92f, size.height * 0.1f)
                    val radius = size.maxDimension * 0.65f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.12f), Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        center = center,
                        radius = radius
                    )
                }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "เป้าหมายวันนี้",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (goal.isFreeMode) {
                            "โหมดเวลาอิสระ (ไม่มีขั้นต่ำ)"
                        } else {
                            "เป้าหมาย ${goal.goalMinutes} นาที"
                        },
                        style = MaterialTheme.typography.bodySmall,
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
                    Text("ปรับโหมด")
                }
            }

            val todayCount = remember(data.workouts) {
                val today = DateStreakUtils.todayDateString()
                data.workouts.count { it.activityDate == today }
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
                    ringSize = 140.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GoalStatRow(
                        label = "เวลาสะสมวันนี้",
                        value = DateStreakUtils.formatMinutesThai(goal.completedSecondsToday)
                    )
                    GoalStatRow(
                        label = if (goal.isFreeMode) "รูปแบบเวลา" else "เวลาที่เหลือ",
                        value = when {
                            goal.isFreeMode -> "เวลาอิสระ"
                            goal.isGoalReached -> "ครบเป้าหมาย!"
                            else -> "${goal.remainingMinutesToday} นาที"
                        }
                    )
                    GoalStatRow(
                        label = if (goal.isFreeMode) "กิจกรรมวันนี้" else "ความคืบหน้า",
                        value = if (goal.isFreeMode) "$todayCount ครั้ง" else "${goal.progressPercent}%"
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
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StartExerciseHeroCard(onStartExerciseClick: () -> Unit) {
    val pulseTransition = rememberInfiniteTransition(label = "cta_play_pulse")
    val playScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "play_icon_scale"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(24.dp))
            .bounceClickable(pressedScale = 0.97f, onClick = onStartExerciseClick)
            .testTag("start_workout_cta")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(ForestGreen, EmeraldVibrant)
                    )
                )
                .shimmerSweepEffect(shimmerAlpha = 0.22f, durationMillis = 2500),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            scaleX = playScale
                            scaleY = playScale
                        }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
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
        border = BorderStroke(1.dp, EmeraldVibrant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shimmerSweepEffect(shimmerAlpha = 0.16f, durationMillis = 3200)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_goal_badge),
                contentDescription = stringResource(R.string.goal_badge_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ยินดีด้วย! เป้าหมายรายวันสำเร็จแล้ว",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ออกกำลังกายแล้ว $completedMinutes นาที (เป้า $goalMinutes นาที)",
                    style = MaterialTheme.typography.bodySmall,
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
