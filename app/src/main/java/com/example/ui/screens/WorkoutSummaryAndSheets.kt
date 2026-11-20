package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SportType
import com.example.domain.DateStreakUtils
import com.example.share.WorkoutShareHelper
import com.example.ui.components.MetricTile
import com.example.ui.components.RouteJsonCodec
import com.example.ui.components.RouteMapCard
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.MintBright
import com.example.ui.viewmodel.HiitPreset
import com.example.ui.viewmodel.HiitPresets
import com.example.ui.viewmodel.WorkoutTimerStyle
import com.example.ui.viewmodel.WorkoutUiState
import com.example.widget.MoveWidgetProvider

@Composable
fun WorkoutAdvancedOptionsSheet(
    uiState: WorkoutUiState,
    onSelectTimerStyle: (WorkoutTimerStyle) -> Unit,
    onApplyHiitPreset: (HiitPreset) -> Unit,
    onUpdateCustomHiit: (workSec: Int, restSec: Int, rounds: Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "ตั้งค่าการฝึก & ข้อมูลเชิงลึก",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "รูปแบบเวลาออกกำลังกาย",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                WorkoutTimerStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = uiState.timerStyle == style,
                        onClick = { onSelectTimerStyle(style) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = WorkoutTimerStyle.entries.size
                        ),
                        icon = {
                            Icon(
                                imageVector = if (style == WorkoutTimerStyle.FREE) Icons.Rounded.Timer else Icons.Rounded.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text(style.thaiLabel)
                    }
                }
            }
        }

        if (uiState.timerStyle == WorkoutTimerStyle.HIIT) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "เลือกโปรแกรม HIIT / Circuit",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(HiitPresets.ALL, key = { it.id }) { preset ->
                        val active = uiState.hiitWorkSeconds == preset.workSec &&
                            uiState.hiitRestSeconds == preset.restSec &&
                            uiState.hiitTotalRounds == preset.rounds
                        FilterChip(
                            selected = active,
                            onClick = { onApplyHiitPreset(preset) },
                            label = { Text(preset.titleThai, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {
                                onUpdateCustomHiit(
                                    uiState.hiitWorkSeconds + 10,
                                    uiState.hiitRestSeconds,
                                    uiState.hiitTotalRounds
                                )
                            },
                            label = { Text("+10วิ ช่วงฝึก") }
                        )
                    }
                }
            }
        }

        if (uiState.ghostRival.hasRival) {
            GhostPacerDuelCard(uiState = uiState)
        }

        if (uiState.routePoints.isNotEmpty()) {
            RouteMapCard(
                routePoints = uiState.routePoints,
                distanceMeters = uiState.distanceMeters,
                durationSeconds = uiState.elapsedSeconds,
                mapHeight = 190.dp
            )
        }

        Button(
            onClick = onClose,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("กลับไปหน้าออกกำลังกาย", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun GhostPacerDuelCard(uiState: WorkoutUiState) {
    val ghostAtNow = uiState.ghostRival.expectedProgressAt(uiState.elapsedSeconds)
    val sport = uiState.sportType

    val (currentVal, ghostVal, unitLabel) = when {
        sport.supportsDistance && uiState.ghostRival.targetTotalMeters > 0 ->
            Triple(uiState.distanceMeters, ghostAtNow.meters, "ม.")
        sport.supportsReps && uiState.ghostRival.targetTotalReps > 0 ->
            Triple(uiState.repsCount, ghostAtNow.reps, "ครั้ง")
        sport.supportsSteps && uiState.ghostRival.targetTotalSteps > 0 ->
            Triple(uiState.steps, ghostAtNow.steps, "ก้าว")
        else ->
            Triple(uiState.caloriesBurned, ghostAtNow.calories, "kcal")
    }

    val diff = currentVal - ghostVal
    val isAhead = diff >= 0
    val duelRatio = when {
        ghostVal <= 0 && currentVal <= 0 -> 0.5f
        ghostVal <= 0 -> 0.85f
        else -> (0.5f + ((diff.toFloat() / maxOf(ghostVal, 10)) * 0.45f)).coerceIn(0.08f, 0.96f)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ForestGreenDeep),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ghost_pacer_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(ForestGreenDeep, ForestGreen)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = MintBright,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Ghost Pacer • แข่งกับเงาสถิติสูงสุด (${uiState.ghostRival.rivalDateLabel})",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isAhead) "คุณนำหน้าเงาอยู่ +$diff $unitLabel" else "ตามหลังเงา ${kotlin.math.abs(diff)} $unitLabel",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isAhead) MintBright else AmberWarm,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "เงาตอนนี้: $ghostVal $unitLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }

            LinearProgressIndicator(
                progress = { duelRatio },
                color = if (isAhead) MintBright else AmberWarm,
                trackColor = Color.White.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
            )
        }
    }
}

@Composable
fun PostWorkoutSummaryScreen(
    uiState: WorkoutUiState,
    onStartCoolDown: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val session = uiState.savedSession!!
    val outcome = uiState.saveOutcome!!
    val routePoints = remember(session.routeJson) { RouteJsonCodec.decode(session.routeJson) }

    LaunchedEffect(session.id) {
        MoveWidgetProvider.refreshAllWidgets(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_goal_badge),
                    contentDescription = stringResource(R.string.goal_badge_desc),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(22.dp))
                )

                Text(
                    text = "บันทึกกิจกรรมสำเร็จ!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
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

                if (session.avgFormScore > 0 || session.repsCount > 0 || session.distanceMeters > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (session.repsCount > 0) {
                            MetricTile(
                                icon = Icons.Rounded.Repeat,
                                label = if (session.sport == SportType.JUMP_ROPE) "กระโดด" else "จำนวนครั้ง",
                                value = "${session.repsCount}",
                                accentColor = ForestGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (session.distanceMeters > 0) {
                            MetricTile(
                                icon = Icons.Rounded.Route,
                                label = "ระยะทาง",
                                value = DateStreakUtils.formatDistanceKm(session.distanceMeters),
                                accentColor = ForestGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (session.avgFormScore > 0) {
                            MetricTile(
                                icon = Icons.Rounded.AutoAwesome,
                                label = "AI ฟอร์มเฉลี่ย",
                                value = "${session.avgFormScore}%",
                                accentColor = EmeraldVibrant,
                                modifier = Modifier.weight(1f)
                            )
                        }
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

                Text(
                    text = outcome.updatedStreak.statusMessageThai,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        WorkoutShareHelper.shareWorkoutCard(
                            context = context,
                            session = session,
                            streakDays = outcome.updatedStreak.currentStreak
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("share_workout_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "แชร์การ์ดสรุปผล (PNG)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onStartCoolDown,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldVibrant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("post_workout_cooldown_button")
                ) {
                    Icon(Icons.Rounded.AccessibilityNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ยืดคลายกล้ามเนื้อหลังเล่น (Cool-Down)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onDone,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("post_workout_done_button")
                ) {
                    Text("กลับหน้าหลัก", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
