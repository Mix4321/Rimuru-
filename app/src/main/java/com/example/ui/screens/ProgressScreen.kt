package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.SportType
import com.example.data.model.WorkoutSession
import com.example.domain.DateStreakUtils
import com.example.share.WorkoutShareHelper
import com.example.ui.components.ActivityBarChart
import com.example.ui.components.CalorieBarPoint
import com.example.ui.components.CaloriesBurnedChart
import com.example.ui.components.GlassPulseCard
import com.example.ui.components.WeightTrendSparkline
import com.example.ui.theme.DeepForestGreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.NeonLime
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianSurface
import com.example.ui.viewmodel.DashboardData
import com.example.widget.MoveWidgetProvider
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    data: DashboardData,
    userHeightCm: Float,
    onUpdateWeight: (String, Float) -> Unit,
    onDeleteWorkout: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var statsPeriodTab by remember { mutableIntStateOf(0) } // 0 = Weekly, 1 = Monthly
    var chartMetricTab by remember { mutableIntStateOf(0) } // 0 = Calories (kcal), 1 = Minutes
    var selectedSportFilter by remember { mutableStateOf<SportType?>(null) }
    var inspectedSession by remember { mutableStateOf<WorkoutSession?>(null) }
    var showWeightDialog by remember { mutableStateOf(false) }

    val weightHistory = remember(data.profile.weightKg) {
        mutableStateListOf(
            (data.profile.weightKg + 1.4f),
            (data.profile.weightKg + 0.9f),
            (data.profile.weightKg + 0.6f),
            (data.profile.weightKg + 0.3f),
            data.profile.weightKg
        )
    }

    val filteredWorkouts = remember(data.workouts, selectedSportFilter) {
        if (selectedSportFilter == null) data.workouts
        else data.workouts.filter { it.sport == selectedSportFilter }
    }

    val weeklyCalorieBars = remember(data.workouts) {
        DateStreakUtils.buildLast7DaysCalorieBars(data.workouts).mapIndexed { idx, (label, cal, isCurrent) ->
            val mins = data.last7DaysBars.getOrNull(idx)?.minutes ?: 0
            CalorieBarPoint(label = label, calories = cal, minutes = mins, isCurrent = isCurrent)
        }
    }
    val monthlyCalorieBars = remember(data.workouts) {
        DateStreakUtils.buildLast4WeeksCalorieBars(data.workouts).mapIndexed { idx, (label, cal, isCurrent) ->
            val mins = data.last4WeeksBars.getOrNull(idx)?.minutes ?: 0
            CalorieBarPoint(label = label, calories = cal, minutes = mins, isCurrent = isCurrent)
        }
    }

    val activePeriodBars = if (statsPeriodTab == 0) weeklyCalorieBars else monthlyCalorieBars
    val periodCalories = remember(activePeriodBars) { activePeriodBars.sumOf { it.calories } }
    val periodMinutes = remember(activePeriodBars) { activePeriodBars.sumOf { it.minutes } }

    // Personal records computed directly from real workout sessions
    val bestBurnSession = remember(data.workouts) { data.workouts.maxOfOrNull { it.caloriesBurned } ?: 0 }
    val longestSessionSec = remember(data.workouts) { data.workouts.maxOfOrNull { it.durationSeconds } ?: 0 }
    val bestDistanceM = remember(data.workouts) { data.workouts.maxOfOrNull { it.distanceMeters } ?: 0 }

    if (showWeightDialog) {
        var weightInput by remember(data.profile.weightKg) {
            mutableStateOf(String.format(Locale.US, "%.1f", data.profile.weightKg))
        }
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = { Text("บันทึกน้ำหนักล่าสุด") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "ระบบจะใช้น้ำหนักตัวใหม่เพื่อคำนวณแคลอรีจากการออกกำลังกายให้แม่นยำยิ่งขึ้น",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        showWeightDialog = false
                        val parsed = weightInput.toFloatOrNull()?.coerceIn(25f, 250f) ?: data.profile.weightKg
                        onUpdateWeight(data.profile.displayName, parsed)
                    }
                ) {
                    Text("บันทึก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    if (inspectedSession != null) {
        val session = inspectedSession!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { inspectedSession = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            WorkoutDetailSheet(
                session = session,
                streakDays = data.streak.currentStreak,
                onShare = {
                    WorkoutShareHelper.shareWorkoutCard(context, session, data.streak.currentStreak)
                },
                onDelete = {
                    inspectedSession = null
                    onDeleteWorkout(session.id)
                    MoveWidgetProvider.refreshAllWidgets(context)
                }
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianBlack, DeepForestGreen.copy(alpha = 0.2f), ObsidianBlack)
                )
            ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header + Weekly / Monthly Period Switcher
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "PERFORMANCE ANALYTICS",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonLime,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "สถิติและพัฒนาการของคุณ",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("progress_period_switcher")
                ) {
                    listOf("Weekly • รายสัปดาห์", "Monthly • รายเดือน").forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = statsPeriodTab == idx,
                            onClick = { statsPeriodTab = idx },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = EmeraldPulse,
                                activeContentColor = ObsidianBlack,
                                inactiveContainerColor = ObsidianSurface,
                                inactiveContentColor = Color.White
                            )
                        ) {
                            Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Period Summary KPIs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProgressMetricCard(
                    icon = Icons.Rounded.LocalFireDepartment,
                    title = if (statsPeriodTab == 0) "เผาผลาญ 7 วัน" else "เผาผลาญ 4 สัปดาห์",
                    value = "$periodCalories kcal",
                    accent = NeonLime,
                    modifier = Modifier.weight(1f)
                )
                ProgressMetricCard(
                    icon = Icons.Rounded.Schedule,
                    title = "เวลาออกกำลังกาย",
                    value = "$periodMinutes นาที",
                    accent = EmeraldPulse,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Calories Burned & Active Minutes Visualization Card
        item {
            GlassPulseCard(
                highlightGlow = true,
                cornerRadius = 26.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (chartMetricTab == 0) "CALORIES BURNED CHART" else "ACTIVE MINUTES CHART",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonLime,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (chartMetricTab == 0) "กราฟพลังงานที่เผาผลาญ (kcal)" else "กราฟเวลาออกกำลังกาย (นาที)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("Calories (kcal)", "Minutes").forEachIndexed { index, title ->
                            SegmentedButton(
                                selected = chartMetricTab == index,
                                onClick = { chartMetricTab = index },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                            ) {
                                Text(title, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    if (chartMetricTab == 0) {
                        CaloriesBurnedChart(bars = activePeriodBars)
                    } else {
                        ActivityBarChart(
                            bars = if (statsPeriodTab == 0) data.last7DaysBars else data.last4WeeksBars
                        )
                    }
                }
            }
        }

        // Weight Tracking Card
        item {
            val heightM = (userHeightCm / 100f).coerceAtLeast(1.2f)
            val bmi = data.profile.weightKg / (heightM * heightM)
            val bmiLabel = when {
                bmi < 18.5f -> "น้ำหนักต่ำกว่าเกณฑ์"
                bmi < 23f -> "สมส่วนแข็งแรง"
                bmi < 25f -> "ท้วมเล็กน้อย"
                else -> "เน้นเบิร์นไขมัน"
            }

            GlassPulseCard(
                cornerRadius = 24.dp,
                modifier = Modifier.fillMaxWidth()
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.MonitorWeight, contentDescription = null, tint = NeonLime)
                            Column {
                                Text(
                                    text = "WEIGHT TRACKING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonLime,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%.1f กก. • BMI %.1f (%s)",
                                        data.profile.weightKg,
                                        bmi,
                                        bmiLabel
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showWeightDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("update_weight_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("บันทึก", color = Color.White)
                        }
                    }

                    WeightTrendSparkline(weightsKg = weightHistory)
                }
            }
        }

        // Personal Records (PRs)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "PERSONAL RECORDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldPulse,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "สถิติสูงสุดส่วนตัว",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PersonalRecordTile(
                        icon = Icons.Rounded.EmojiEvents,
                        label = "Longest Streak",
                        value = "${data.streak.longestStreak} วัน",
                        accent = NeonLime,
                        modifier = Modifier.weight(1f)
                    )
                    PersonalRecordTile(
                        icon = Icons.Rounded.Bolt,
                        label = "Best Session Burn",
                        value = "$bestBurnSession kcal",
                        accent = EmeraldPulse,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PersonalRecordTile(
                        icon = Icons.Rounded.Schedule,
                        label = "Longest Workout",
                        value = DateStreakUtils.formatMinutesThai(longestSessionSec),
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    PersonalRecordTile(
                        icon = Icons.Rounded.Route,
                        label = "Best Distance",
                        value = DateStreakUtils.formatDistanceKm(bestDistanceM),
                        accent = NeonLime,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Workout History Section + Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "WORKOUT HISTORY (${data.workouts.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonLime,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedSportFilter == null,
                            onClick = { selectedSportFilter = null },
                            label = { Text("ทั้งหมด") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonLime,
                                selectedLabelColor = ObsidianBlack
                            )
                        )
                    }
                    items(SportType.entries) { sport ->
                        FilterChip(
                            selected = selectedSportFilter == sport,
                            onClick = {
                                selectedSportFilter = if (selectedSportFilter == sport) null else sport
                            },
                            label = { Text(sport.thaiName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonLime,
                                selectedLabelColor = ObsidianBlack
                            )
                        )
                    }
                }
            }
        }

        if (filteredWorkouts.isEmpty()) {
            item {
                GlassPulseCard(
                    cornerRadius = 22.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = EmeraldPulse,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "ยังไม่มีประวัติการฝึก",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "เริ่มออกกำลังกายในแท็บ Workout เพื่อดูกราฟและสถิติของคุณ",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredWorkouts, key = { it.id }) { session ->
                WorkoutHistoryRow(
                    session = session,
                    onClick = { inspectedSession = session }
                )
            }
        }
    }
}

@Composable
private fun ProgressMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    GlassPulseCard(
        cornerRadius = 22.dp,
        accentBorder = accent.copy(alpha = 0.35f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
private fun PersonalRecordTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    GlassPulseCard(
        cornerRadius = 20.dp,
        accentBorder = accent.copy(alpha = 0.25f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(38.dp)
            ) {
                BoxContentCenter {
                    Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
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
}

@Composable
private fun BoxContentCenter(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
