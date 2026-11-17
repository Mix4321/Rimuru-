package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.ChartBarPoint
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.MintBright
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun DailyGoalRing(
    progressFraction: Float,
    progressPercent: Int,
    completedMinutes: Int,
    goalMinutes: Int,
    modifier: Modifier = Modifier,
    ringSize: Dp = 176.dp
) {
    val animatedSweep by animateFloatAsState(
        targetValue = (progressFraction.coerceIn(0f, 1f)) * 360f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "goal_ring_sweep"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MintBright

    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val strokeWidth = 18.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (animatedSweep > 0.5f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryColor, EmeraldVibrant, accentColor, primaryColor)
                    ),
                    startAngle = -90f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (goalMinutes <= 0) {
                Text(
                    text = if (completedMinutes > 0) "${completedMinutes}น." else "อิสระ",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "เวลาอิสระวันนี้",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$completedMinutes / $goalMinutes นาที",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActivityBarChart(
    bars: List<ChartBarPoint>,
    modifier: Modifier = Modifier
) {
    val maxMinutes = max(30, bars.maxOfOrNull { it.minutes } ?: 30)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { bar ->
                val targetHeightRatio = (bar.minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
                val animatedRatio by animateFloatAsState(
                    targetValue = targetHeightRatio,
                    animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
                    label = "bar_height"
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = if (bar.minutes > 0) "${bar.minutes}" else "0",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bar.isCurrentPeriod) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .weight(1f)
                    ) {
                        val barRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = barRadius
                        )
                        val filledH = (size.height * animatedRatio).coerceAtLeast(
                            if (bar.minutes > 0) 8.dp.toPx() else 0f
                        )
                        if (filledH > 0f) {
                            drawRoundRect(
                                color = if (bar.isCurrentPeriod) activeColor else inactiveColor,
                                topLeft = Offset(0f, size.height - filledH),
                                size = Size(size.width, filledH),
                                cornerRadius = barRadius
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bar.isCurrentPeriod) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (bar.isCurrentPeriod) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalSettingDialog(
    currentGoalMinutes: Int,
    onDismiss: () -> Unit,
    onSaveGoal: (Int) -> Unit
) {
    var sliderValue by remember(currentGoalMinutes) {
        mutableFloatStateOf(currentGoalMinutes.coerceIn(0, 180).toFloat())
    }
    val presets = listOf(0, 10, 20, 45, 60, 90)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "ตั้งเป้าหมายรายวัน",
                style = MaterialTheme.typography.headlineMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "เลือกเวลาอิสระ (ไม่จำกัดเวลา) หรือกำหนดนาทีเป้าหมายต่อวันที่ต้องการ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val selectedMins = sliderValue.roundToInt()
                Text(
                    text = if (selectedMins == 0) "โหมดเวลาอิสระ (ไม่กำหนดขั้นต่ำ)" else "$selectedMins นาที / วัน",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = ((it / 5f).roundToInt() * 5).coerceIn(0, 180).toFloat() },
                    valueRange = 0f..180f,
                    colors = SliderDefaults.colors(
                        thumbColor = ForestGreen,
                        activeTrackColor = ForestGreen
                    )
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val selected = selectedMins == preset
                        FilterChip(
                            selected = selected,
                            onClick = { sliderValue = preset.toFloat() },
                            label = { Text(if (preset == 0) "เวลาอิสระ" else "$preset นาที") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("goal_preset_$preset")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveGoal(sliderValue.roundToInt()) },
                modifier = Modifier.testTag("save_goal_button")
            ) {
                Text("บันทึกเป้าหมาย")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        }
    )
}
