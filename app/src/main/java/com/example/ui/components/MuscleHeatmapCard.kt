package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SportType
import com.example.domain.MuscleGroup
import com.example.domain.MuscleRecoveryReport
import com.example.domain.RecoveryState
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.ForestGreenMedium
import com.example.ui.theme.MintBright

private fun RecoveryState.heatmapColor(): Color = when (this) {
    RecoveryState.FRESH -> MintBright
    RecoveryState.MODERATE -> AmberWarm
    RecoveryState.FATIGUED -> Color(0xFFEF4444)
}

@Composable
fun MuscleRecoveryHeatmapCard(
    report: MuscleRecoveryReport,
    onStartRecommendedSport: (SportType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "แผนที่ฟื้นฟูกล้ามเนื้ออัจฉริยะ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "วิเคราะห์จากกิจกรรมย้อนหลัง 72 ชม.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = EmeraldVibrant.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "พร้อม ${report.overallReadinessPercent}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldVibrant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Human Body Muscle Heatmap Diagram
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ForestGreenDeep,
                    modifier = Modifier
                        .width(126.dp)
                        .height(198.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(198.dp)) {
                        drawBodyHeatmap(report)
                    }
                }

                // Individual Muscle Breakdown List
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    MuscleGroup.entries.forEach { group ->
                        val status = report.muscles[group]
                        val state = status?.recoveryState ?: RecoveryState.FRESH
                        val color = state.heatmapColor()
                        val pct = status?.fatiguePercent ?: 0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = group.thaiName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (pct == 0) "100% สด" else "ล้า $pct%",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state == RecoveryState.FRESH) EmeraldVibrant else color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Smart Recommendation Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "แนะนำวันนี้: ${report.recommendedSport.thaiName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = report.recommendationReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Button(
                        onClick = { onStartRecommendedSport(report.recommendedSport) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldVibrant),
                        modifier = Modifier.testTag("start_recommended_sport_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "เริ่มกีฬาแนะนำ",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("เริ่ม")
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBodyHeatmap(report: MuscleRecoveryReport) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(ForestGreenMedium.copy(alpha = 0.45f), ForestGreenDeep)
        )
    )

    fun colorFor(group: MuscleGroup): Color =
        (report.muscles[group]?.recoveryState ?: RecoveryState.FRESH).heatmapColor()

    // Head
    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = w * 0.10f,
        center = Offset(cx, h * 0.12f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.5f),
        radius = w * 0.10f,
        center = Offset(cx, h * 0.12f),
        style = Stroke(width = 2f)
    )

    // Neck & Shoulders
    val shoulderColor = colorFor(MuscleGroup.NECK_SHOULDERS)
    val shoulderY = h * 0.23f
    val shoulderSpan = w * 0.24f
    drawLine(
        color = shoulderColor,
        start = Offset(cx - shoulderSpan, shoulderY),
        end = Offset(cx + shoulderSpan, shoulderY),
        strokeWidth = 14f,
        cap = StrokeCap.Round
    )

    // Arms & Wrists
    val armColor = colorFor(MuscleGroup.ARMS_WRISTS)
    drawLine(
        color = armColor,
        start = Offset(cx - shoulderSpan, shoulderY),
        end = Offset(cx - shoulderSpan * 1.28f, h * 0.47f),
        strokeWidth = 10f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = armColor,
        start = Offset(cx + shoulderSpan, shoulderY),
        end = Offset(cx + shoulderSpan * 1.28f, h * 0.47f),
        strokeWidth = 10f,
        cap = StrokeCap.Round
    )

    // Core & Hips torso polygon
    val coreColor = colorFor(MuscleGroup.CORE_HIPS)
    val torsoPath = Path().apply {
        moveTo(cx - shoulderSpan * 0.72f, shoulderY + 4f)
        lineTo(cx + shoulderSpan * 0.72f, shoulderY + 4f)
        lineTo(cx + w * 0.14f, h * 0.50f)
        lineTo(cx - w * 0.14f, h * 0.50f)
        close()
    }
    drawPath(path = torsoPath, color = coreColor.copy(alpha = 0.88f))

    // Quads (outer thigh) & Hamstrings (inner/upper posterior highlight)
    val quadColor = colorFor(MuscleGroup.QUADS)
    val hamColor = colorFor(MuscleGroup.HAMSTRINGS)
    val hipY = h * 0.51f
    val kneeY = h * 0.71f
    val ankleY = h * 0.89f
    val legOffset = w * 0.11f

    // Thighs (Quads outer, Hamstrings inner glow)
    drawLine(
        color = quadColor,
        start = Offset(cx - legOffset, hipY),
        end = Offset(cx - legOffset * 1.05f, kneeY),
        strokeWidth = 13f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = quadColor,
        start = Offset(cx + legOffset, hipY),
        end = Offset(cx + legOffset * 1.05f, kneeY),
        strokeWidth = 13f,
        cap = StrokeCap.Round
    )
    drawCircle(color = hamColor.copy(alpha = 0.65f), radius = 8f, center = Offset(cx - legOffset * 0.7f, h * 0.59f))
    drawCircle(color = hamColor.copy(alpha = 0.65f), radius = 8f, center = Offset(cx + legOffset * 0.7f, h * 0.59f))

    // Calves & Ankles
    val calfColor = colorFor(MuscleGroup.CALVES_ANKLES)
    drawLine(
        color = calfColor,
        start = Offset(cx - legOffset * 1.05f, kneeY + 4f),
        end = Offset(cx - legOffset * 1.1f, ankleY),
        strokeWidth = 10f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = calfColor,
        start = Offset(cx + legOffset * 1.05f, kneeY + 4f),
        end = Offset(cx + legOffset * 1.1f, ankleY),
        strokeWidth = 10f,
        cap = StrokeCap.Round
    )
}
