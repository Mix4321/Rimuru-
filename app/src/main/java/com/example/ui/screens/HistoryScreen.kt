package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.MetricTile
import com.example.ui.components.RouteJsonCodec
import com.example.ui.components.RouteMapCard
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.MintBright
import com.example.ui.viewmodel.DashboardData
import com.example.widget.MoveWidgetProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    data: DashboardData,
    onDeleteWorkout: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var historyTopicTab by remember { mutableIntStateOf(0) } // 0 = รายการกิจกรรม, 1 = กราฟสถิติ
    var selectedChartTab by remember { mutableIntStateOf(0) } // 0 = รายวัน, 1 = รายสัปดาห์
    var selectedSportFilter by remember { mutableStateOf<SportType?>(null) }
    var inspectedSession by remember { mutableStateOf<WorkoutSession?>(null) }

    val filteredWorkouts = remember(data.workouts, selectedSportFilter) {
        if (selectedSportFilter == null) data.workouts
        else data.workouts.filter { it.sport == selectedSportFilter }
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
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreenDeep),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ForestGreenDeep, ForestGreen)
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ประวัติและสถิติกิจกรรม",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EmojiEvents,
                                    contentDescription = null,
                                    tint = AmberWarm,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Lv.${data.progression.level}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MintBright,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("ทั้งหมด", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f))
                                Text("${data.workouts.size} ครั้ง", style = MaterialTheme.typography.titleMedium, color = MintBright, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("เวลารวม", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f))
                                Text(DateStreakUtils.formatMinutesThai(data.workouts.sumOf { it.durationSeconds }), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("เผาผลาญ", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f))
                                Text("${data.workouts.sumOf { it.caloriesBurned }} kcal", style = MaterialTheme.typography.titleMedium, color = AmberWarm, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Topic Switcher: Logs vs Charts
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_topic_switcher")
            ) {
                SegmentedButton(
                    selected = historyTopicTab == 0,
                    onClick = { historyTopicTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Rounded.ListAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                ) {
                    Text("รายการกิจกรรม (${data.workouts.size})")
                }
                SegmentedButton(
                    selected = historyTopicTab == 1,
                    onClick = { historyTopicTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Rounded.BarChart, contentDescription = null, modifier = Modifier.size(18.dp)) }
                ) {
                    Text("กราฟสถิติ")
                }
            }
        }

        if (historyTopicTab == 1) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                            Text(
                                text = if (selectedChartTab == 0) "สถิติรายวัน (นาที)" else "สถิติรายสัปดาห์ (นาที)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            SingleChoiceSegmentedButtonRow {
                                listOf("รายวัน", "รายสัปดาห์").forEachIndexed { index, label ->
                                    SegmentedButton(
                                        selected = selectedChartTab == index,
                                        onClick = { selectedChartTab = index },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        ActivityBarChart(
                            bars = if (selectedChartTab == 0) data.last7DaysBars else data.last4WeeksBars
                        )
                    }
                }
            }

            item {
                val totalSteps = remember(data.workouts) { data.workouts.sumOf { it.steps } }
                val totalMeters = remember(data.workouts) { data.workouts.sumOf { it.distanceMeters } }
                val totalReps = remember(data.workouts) { data.workouts.sumOf { it.repsCount } }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                        label = "ก้าวสะสม",
                        value = "$totalSteps",
                        accentColor = EmeraldVibrant,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        icon = Icons.Rounded.Repeat,
                        label = "ครั้งสะสม",
                        value = "$totalReps",
                        accentColor = AmberWarm,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        icon = Icons.Rounded.Route,
                        label = "ระยะทางรวม",
                        value = DateStreakUtils.formatDistanceKm(totalMeters),
                        accentColor = ForestGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // Sport filter chips
            item {
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
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
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
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            if (filteredWorkouts.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "ยังไม่มีประวัติการออกกำลังกาย",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "เริ่มต้นออกกำลังกายครั้งแรกแบบเวลาอิสระ เพื่อบันทึกสถิติแรกของคุณ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

@Composable
fun WorkoutHistoryRow(
    session: WorkoutSession,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("history_item_${session.id}")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(EmeraldVibrant, ForestGreen)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = session.sport.icon,
                    contentDescription = session.sport.thaiName,
                    tint = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.sport.thaiName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = DateStreakUtils.formatDateTimeThai(session.startedAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            text = session.mode.thaiLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (session.repsCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldVibrant.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "${session.repsCount} ครั้ง",
                                style = MaterialTheme.typography.labelSmall,
                                color = ForestGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (session.avgFormScore > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AmberWarm.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "ฟอร์ม ${session.avgFormScore}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberWarm,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = DateStreakUtils.formatMinutesThai(session.durationSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${session.caloriesBurned} kcal",
                    style = MaterialTheme.typography.labelLarge,
                    color = AmberWarm
                )
            }
        }
    }
}

@Composable
fun WorkoutDetailSheet(
    session: WorkoutSession,
    streakDays: Int,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val routePoints = remember(session.routeJson) { RouteJsonCodec.decode(session.routeJson) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = session.sport.icon,
                    contentDescription = session.sport.thaiName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column {
                Text(
                    text = session.sport.thaiName,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "${DateStreakUtils.formatDateTimeThai(session.startedAtMillis)} • ${
                        if (session.workoutSubType.equals("HIIT", ignoreCase = true)) "โหมด HIIT" else "เวลาอิสระ"
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                icon = Icons.Rounded.Schedule,
                label = "ระยะเวลา",
                value = DateStreakUtils.formatDurationClock(session.durationSeconds),
                accentColor = ForestGreen,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Rounded.LocalFireDepartment,
                label = "แคลอรี",
                value = "${session.caloriesBurned} kcal",
                accentColor = AmberWarm,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (session.sport.supportsSteps || session.steps > 0) {
                MetricTile(
                    icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                    label = "จำนวนก้าว",
                    value = "${session.steps}",
                    accentColor = EmeraldVibrant,
                    modifier = Modifier.weight(1f)
                )
            }
            if (session.sport.supportsReps || session.repsCount > 0) {
                MetricTile(
                    icon = Icons.Rounded.Repeat,
                    label = if (session.sport == SportType.JUMP_ROPE) "กระโดด" else "จำนวนครั้ง",
                    value = "${session.repsCount}",
                    accentColor = EmeraldVibrant,
                    modifier = Modifier.weight(1f)
                )
            }
            if (session.sport.supportsDistance || session.distanceMeters > 0) {
                MetricTile(
                    icon = Icons.Rounded.Route,
                    label = "ระยะทาง",
                    value = DateStreakUtils.formatDistanceKm(session.distanceMeters),
                    accentColor = ForestGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (session.avgFormScore > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "AI ฟอร์มเฉลี่ย",
                    value = "${session.avgFormScore}%",
                    accentColor = EmeraldVibrant,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    icon = Icons.Rounded.Balance,
                    label = "สมดุลซ้าย-ขวา",
                    value = "${session.avgSymmetryPercent}%",
                    accentColor = ForestGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (routePoints.isNotEmpty()) {
            RouteMapCard(
                routePoints = routePoints,
                distanceMeters = session.distanceMeters,
                durationSeconds = session.durationSeconds,
                mapHeight = 200.dp
            )
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "รูปแบบการติดตาม: ${session.mode.thaiLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "เวลาที่ตรวจจับการเคลื่อนไหวต่อเนื่อง: ${
                        DateStreakUtils.formatMinutesThai(session.motionActiveSeconds)
                    }${if (streakDays > 0) " • Streak ปัจจุบัน $streakDays วัน" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = onShare,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("history_share_workout_button")
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("แชร์การ์ดสรุปผล (PNG)", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onDelete,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("delete_workout_button")
        ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = CoralError)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ลบประวัติกิจกรรมนี้", color = CoralError)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
