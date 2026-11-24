package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ActiveWorkoutDraft
import com.example.data.model.SportType
import com.example.data.model.WorkoutCatalog
import com.example.data.model.WorkoutProgram
import com.example.domain.DateStreakUtils
import com.example.ui.components.GlassPulseCard
import com.example.ui.components.GoalSettingDialog
import com.example.ui.components.LivePulseBeacon
import com.example.ui.components.MultiActivityRings
import com.example.ui.components.NeonPulseButton
import com.example.ui.components.auroraGlowBackdrop
import com.example.ui.components.bounceClickable
import com.example.ui.theme.DeepForestGreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.GlassBorderEmerald
import com.example.ui.theme.NeonLime
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianElevated
import com.example.ui.viewmodel.DashboardData
import com.example.widget.MoveWidgetProvider
import java.util.Calendar

@Composable
fun DashboardScreen(
    data: DashboardData,
    userEmail: String? = null,
    userPhotoUrl: String? = null,
    onStartExerciseClick: () -> Unit,
    onQuickStartSport: (SportType) -> Unit,
    onOpenProgramDetail: (WorkoutProgram) -> Unit = {},
    onUpdateDailyGoal: (Int) -> Unit,
    onUpdateProfile: (String, Float) -> Unit,
    onResumeDraft: (ActiveWorkoutDraft) -> Unit,
    onDiscardDraft: () -> Unit,
    onViewHistory: () -> Unit,
    onOpenRecoveryPlanPage: () -> Unit = {},
    onOpenProgressionBadgesPage: () -> Unit = {},
    onOpenNearbySpots: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showGoalDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    val recommendedProgram = remember(data.muscleRecovery.overallReadinessPercent) {
        WorkoutCatalog.recommendedForReadiness(data.muscleRecovery.overallReadinessPercent)
    }

    val greetingLabel = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "GOOD MORNING"
            hour < 17 -> "GOOD AFTERNOON"
            else -> "GOOD EVENING"
        }
    }

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
                        label = { Text("น้ำหนักตัว (กิโลกรัม)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianBlack, DeepForestGreen.copy(alpha = 0.25f), ObsidianBlack)
                )
            )
    ) {
        val isWide = maxWidth >= 720.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. GREEN PULSE Personalized Hero Greeting
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = DeepForestGreen.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    LivePulseBeacon(color = NeonLime, isActive = true, size = 8.dp)
                                    Text(
                                        text = "GREEN PULSE • $greetingLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonLime,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = "สวัสดี, ${data.profile.displayName}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Train Hard. Live Strong. • Lv.${data.progression.level} ${data.progression.rankTitleThai}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = ObsidianElevated,
                        border = BorderStroke(2.dp, Brush.linearGradient(listOf(EmeraldPulse, NeonLime))),
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .bounceClickable { showProfileDialog = true }
                            .testTag("edit_profile_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!userPhotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = userPhotoUrl,
                                    contentDescription = "รูปโปรไฟล์",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = "โปรไฟล์",
                                    tint = NeonLime
                                )
                            }
                        }
                    }
                }
            }

            // Unfinished workout banner
            if (data.pendingDraft != null) {
                item {
                    PendingDraftBanner(
                        draft = data.pendingDraft,
                        onResume = { onResumeDraft(data.pendingDraft) },
                        onDiscard = onDiscardDraft
                    )
                }
            }

            // 2. Daily Fitness Summary + Triple Concentric Activity Progress Rings
            item {
                val goalMin = if (data.dailyGoal.isFreeMode) 30 else data.dailyGoal.goalMinutes.coerceAtLeast(10)
                val minutesFraction = (data.dailyGoal.completedSecondsToday / 60f) / goalMin.toFloat()
                val caloriesFraction = data.dailyGoal.caloriesToday / 450f
                val stepsFraction = data.dailyGoal.stepsToday / 8000f

                GlassPulseCard(
                    highlightGlow = true,
                    cornerRadius = 28.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .auroraGlowBackdrop(primaryGlow = NeonLime, secondaryGlow = EmeraldPulse)
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "DAILY FITNESS PULSE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonLime,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "สรุปกิจกรรมประจำวัน",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = DeepForestGreen.copy(alpha = 0.9f),
                                    border = BorderStroke(1.2.dp, NeonLime.copy(alpha = 0.65f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .bounceClickable { showGoalDialog = true }
                                        .testTag("edit_goal_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "ตั้งเป้าหมายรายวัน",
                                            tint = NeonLime,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (data.dailyGoal.isFreeMode) "เป้า: อิสระ" else "เป้า ${data.dailyGoal.goalMinutes}น.",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NeonLime,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                MultiActivityRings(
                                    minutesFraction = if (data.dailyGoal.isFreeMode && data.dailyGoal.completedSecondsToday > 0) 1f else minutesFraction,
                                    caloriesFraction = caloriesFraction,
                                    stepsFraction = stepsFraction,
                                    centerPrimaryValue = "${data.dailyGoal.progressPercent}%",
                                    centerSubtitle = if (data.dailyGoal.isGoalReached) "TARGET HIT" else "DAILY RING",
                                    ringSize = if (isWide) 184.dp else 156.dp
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RingLegendRow(
                                        dotColor = NeonLime,
                                        title = "Active Minutes",
                                        value = "${data.dailyGoal.completedMinutesToday} / ${if (data.dailyGoal.isFreeMode) "∞" else "${data.dailyGoal.goalMinutes}"} min"
                                    )
                                    RingLegendRow(
                                        dotColor = EmeraldPulse,
                                        title = "Calories Burned",
                                        value = "${data.dailyGoal.caloriesToday} kcal"
                                    )
                                    RingLegendRow(
                                        dotColor = ElectricCyan,
                                        title = "Steps Counter",
                                        value = "${data.dailyGoal.stepsToday} steps"
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.08f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.LocalFireDepartment,
                                                contentDescription = null,
                                                tint = NeonLime,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Streak ${data.streak.currentStreak} วัน",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.testTag("streak_count_text")
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = !data.dailyGoal.isFreeMode && data.dailyGoal.isGoalReached) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = NeonLime.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "พิชิตเป้าหมาย ${data.dailyGoal.goalMinutes} นาทีวันนี้สำเร็จแล้ว!",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = NeonLime,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Calories, Steps & Active Minutes Glass Metric Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PulseKpiGlassTile(
                        icon = Icons.Rounded.LocalFireDepartment,
                        label = "Calories",
                        value = "${data.dailyGoal.caloriesToday}",
                        unit = "kcal",
                        accent = NeonLime,
                        modifier = Modifier.weight(1f)
                    )
                    PulseKpiGlassTile(
                        icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                        label = "Steps",
                        value = "${data.dailyGoal.stepsToday}",
                        unit = "steps",
                        accent = EmeraldPulse,
                        modifier = Modifier.weight(1f)
                    )
                    PulseKpiGlassTile(
                        icon = Icons.Rounded.Timer,
                        label = "Active",
                        value = "${data.dailyGoal.completedMinutesToday}",
                        unit = "min",
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3.5 Primary Hero CTA Button
            item {
                NeonPulseButton(
                    text = "เริ่มออกกำลังกายเลย",
                    onClick = onStartExerciseClick,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = ObsidianBlack,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_start_workout_cta")
                )
            }

            // 4. Daily Workout Recommendation (Realistic Athlete Photography)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DAILY RECOMMENDATION",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonLime,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "โปรแกรมแนะนำสำหรับวันนี้",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = onStartExerciseClick) {
                            Text("ดูทั้งหมด", color = EmeraldPulse, fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.45f)),
                        colors = CardDefaults.cardColors(containerColor = ObsidianElevated),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(226.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .bounceClickable { onOpenProgramDetail(recommendedProgram) }
                            .testTag("daily_recommendation_card")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = painterResource(id = recommendedProgram.imageRes),
                                contentDescription = recommendedProgram.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                ObsidianBlack.copy(alpha = 0.25f),
                                                ObsidianBlack.copy(alpha = 0.55f),
                                                ObsidianBlack.copy(alpha = 0.94f)
                                            )
                                        )
                                    )
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = NeonLime
                                ) {
                                    Text(
                                        text = "READINESS ${data.muscleRecovery.overallReadinessPercent}% • ${recommendedProgram.category.title.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ObsidianBlack,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = ObsidianBlack.copy(alpha = 0.72f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = "${recommendedProgram.durationMinutes} min • ${recommendedProgram.difficulty.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = recommendedProgram.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "${recommendedProgram.coachFocus} • ~${recommendedProgram.estimatedCalories} kcal",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.82f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .shadow(
                                            elevation = 10.dp,
                                            shape = RoundedCornerShape(999.dp),
                                            ambientColor = NeonLime,
                                            spotColor = EmeraldPulse
                                        )
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Brush.horizontalGradient(listOf(EmeraldPulse, NeonLime)))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "เริ่มฝึก",
                                            tint = ObsidianBlack,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "เริ่มฝึก",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = ObsidianBlack,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Smart Hubs: Nearby Workout Spots & Navigation + Recovery AI
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassHubTile(
                        icon = Icons.Rounded.Explore,
                        title = "สถานที่ใกล้ฉัน",
                        subtitle = "สวน ยิม & นำทาง GPS",
                        accent = EmeraldPulse,
                        onClick = onOpenNearbySpots,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("topic_nearby_spots_card")
                    )
                    GlassHubTile(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "ฟื้นฟูกล้ามเนื้อ",
                        subtitle = "พร้อม ${data.muscleRecovery.overallReadinessPercent}% • แนะนำ",
                        accent = NeonLime,
                        onClick = onOpenRecoveryPlanPage,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("topic_recovery_plan_card")
                    )
                }
            }

            // 6. Quick Start Live Tracking Sports
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "QUICK LIVE TRACKING",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPulse,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            listOf(
                                SportType.RUNNING,
                                SportType.GENERAL,
                                SportType.JUMP_ROPE,
                                SportType.CYCLING,
                                SportType.WALKING
                            ),
                            key = { it.name }
                        ) { sport ->
                            GlassPulseCard(
                                cornerRadius = 18.dp,
                                modifier = Modifier
                                    .width(138.dp)
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
                                            .background(Brush.linearGradient(listOf(EmeraldPulse, NeonLime))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = sport.icon,
                                            contentDescription = sport.thaiName,
                                            tint = ObsidianBlack,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = sport.thaiName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${sport.kcalPerMinute.toInt()} kcal/น.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonLime
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
private fun RingLegendRow(
    dotColor: Color,
    title: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PulseKpiGlassTile(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    GlassPulseCard(
        cornerRadius = 22.dp,
        accentBorder = accent.copy(alpha = 0.28f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
private fun GlassHubTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPulseCard(
        highlightGlow = true,
        cornerRadius = 22.dp,
        accentBorder = accent.copy(alpha = 0.65f),
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = accent.copy(alpha = 0.35f),
                spotColor = accent.copy(alpha = 0.45f)
            )
            .bounceClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(accent, EmeraldPulse))),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = ObsidianBlack, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.18f),
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingDraftBanner(
    draft: ActiveWorkoutDraft,
    onResume: () -> Unit,
    onDiscard: () -> Unit
) {
    GlassPulseCard(
        cornerRadius = 22.dp,
        accentBorder = NeonLime.copy(alpha = 0.45f),
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
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = NeonLime)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "มีกิจกรรมค้างอยู่: ${draft.sportType.thaiName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "เวลา ${DateStreakUtils.formatDurationClock(draft.elapsedSeconds)} • ${draft.caloriesExact.toInt()} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonLime
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonPulseButton(
                    text = "ทำต่อ",
                    onClick = onResume,
                    modifier = Modifier.weight(1f).height(48.dp)
                )
                OutlinedButton(
                    onClick = onDiscard,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ยกเลิก")
                }
            }
        }
    }
}
