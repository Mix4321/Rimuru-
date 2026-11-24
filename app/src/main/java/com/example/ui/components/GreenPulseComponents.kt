package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DeepForestGreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.GlassBorderEmerald
import com.example.ui.theme.NeonLime
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Translucent sports-tech glassmorphism container with emerald rim lighting.
 */
@Composable
fun GlassPulseCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    accentBorder: Color = GlassBorderEmerald,
    highlightGlow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val bgBrush = if (highlightGlow) {
        Brush.linearGradient(
            colors = listOf(
                DeepForestGreen.copy(alpha = 0.88f),
                ObsidianSurface.copy(alpha = 0.94f),
                ObsidianBlack.copy(alpha = 0.96f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                ObsidianElevated.copy(alpha = 0.78f),
                ObsidianSurface.copy(alpha = 0.88f)
            )
        )
    }

    Surface(
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, accentBorder),
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
    ) {
        Column(content = content)
    }
}

/**
 * Apple Fitness & Nike Training inspired concentric triple activity progress rings:
 * - Outer Ring: Active Minutes (#B7FF45 Neon Lime)
 * - Middle Ring: Calories Burned (#00C978 Emerald Green)
 * - Inner Ring: Steps Counter (#38F2C7 Mint Cyan)
 */
@Composable
fun MultiActivityRings(
    minutesFraction: Float,
    caloriesFraction: Float,
    stepsFraction: Float,
    centerPrimaryValue: String,
    centerSubtitle: String,
    modifier: Modifier = Modifier,
    ringSize: Dp = 178.dp
) {
    val sweepMinutes by animateFloatAsState(
        targetValue = minutesFraction.coerceIn(0f, 1f) * 360f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "ring_minutes"
    )
    val sweepCalories by animateFloatAsState(
        targetValue = caloriesFraction.coerceIn(0f, 1f) * 360f,
        animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing),
        label = "ring_calories"
    )
    val sweepSteps by animateFloatAsState(
        targetValue = stepsFraction.coerceIn(0f, 1f) * 360f,
        animationSpec = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
        label = "ring_steps"
    )

    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val strokeW = 13.dp.toPx()
            val gap = 4.dp.toPx()

            fun drawRing(inset: Float, sweep: Float, primary: Color, secondary: Color) {
                val diameter = size.minDimension - inset * 2f
                if (diameter <= strokeW) return
                val topLeft = Offset(
                    x = (size.width - diameter) / 2f,
                    y = (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)

                // Track
                drawArc(
                    color = primary.copy(alpha = 0.14f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                if (sweep > 0.5f) {
                    // Ambient Glow
                    drawArc(
                        color = primary.copy(alpha = 0.22f),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW * 1.45f, cap = StrokeCap.Round)
                    )
                    // Gradient Arc
                    drawArc(
                        brush = Brush.sweepGradient(listOf(secondary, primary, NeonLime.copy(alpha = 0.9f), secondary)),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    // Specular tip dot
                    val rad = Math.toRadians((-90f + sweep).toDouble())
                    val radius = diameter / 2f
                    val cx = size.width / 2f + (radius * cos(rad)).toFloat()
                    val cy = size.height / 2f + (radius * sin(rad)).toFloat()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.92f),
                        radius = strokeW * 0.24f,
                        center = Offset(cx, cy)
                    )
                }
            }

            val firstInset = strokeW / 2f
            val secondInset = firstInset + strokeW + gap
            val thirdInset = secondInset + strokeW + gap

            drawRing(firstInset, sweepMinutes, NeonLime, EmeraldPulse)
            drawRing(secondInset, sweepCalories, EmeraldPulse, DeepForestGreen)
            drawRing(thirdInset, sweepSteps, ElectricCyan, EmeraldPulse)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerPrimaryValue,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = centerSubtitle,
                style = MaterialTheme.typography.labelSmall,
                color = NeonLime,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * High-energy Emerald-to-Neon-Lime primary gradient CTA button.
 */
@Composable
fun NeonPulseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = ObsidianBlack,
            disabledContainerColor = ObsidianElevated,
            disabledContentColor = Color.White.copy(alpha = 0.45f)
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(EmeraldPulse, NeonLime))
                } else {
                    Brush.horizontalGradient(listOf(ObsidianElevated, ObsidianElevated))
                }
            )
            .shimmerSweepEffect(enabled = enabled, shimmerAlpha = 0.22f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingContent != null) {
                leadingContent()
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (enabled) ObsidianBlack else Color.White.copy(alpha = 0.45f)
            )
        }
    }
}

data class CalorieBarPoint(
    val label: String,
    val calories: Int,
    val minutes: Int,
    val isCurrent: Boolean
)

/**
 * Glowing gradient bar chart specifically designed for Calories Burned (kcal) visualization.
 */
@Composable
fun CaloriesBurnedChart(
    bars: List<CalorieBarPoint>,
    modifier: Modifier = Modifier
) {
    val maxCal = max(250, bars.maxOfOrNull { it.calories } ?: 250)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { bar ->
                val targetRatio = (bar.calories.toFloat() / maxCal.toFloat()).coerceIn(0f, 1f)
                val animRatio by animateFloatAsState(
                    targetValue = targetRatio,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                    label = "cal_bar"
                )
                val animCal by animateIntAsState(
                    targetValue = bar.calories,
                    animationSpec = tween(durationMillis = 650),
                    label = "cal_num"
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = if (animCal > 0) "$animCal" else "0",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bar.isCurrent) NeonLime else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (bar.isCurrent) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth(0.58f)
                            .weight(1f)
                    ) {
                        val radius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.06f),
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = radius
                        )
                        val fillH = (size.height * animRatio).coerceAtLeast(
                            if (bar.calories > 0) 10.dp.toPx() else 0f
                        )
                        if (fillH > 0f) {
                            val topY = size.height - fillH
                            val brush = if (bar.isCurrent) {
                                Brush.verticalGradient(listOf(NeonLime, EmeraldPulse), startY = topY, endY = size.height)
                            } else {
                                Brush.verticalGradient(
                                    listOf(EmeraldPulse.copy(alpha = 0.8f), DeepForestGreen),
                                    startY = topY,
                                    endY = size.height
                                )
                            }
                            drawRoundRect(
                                brush = brush,
                                topLeft = Offset(0f, topY),
                                size = Size(size.width, fillH),
                                cornerRadius = radius
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
                    color = if (bar.isCurrent) NeonLime else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (bar.isCurrent) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Smooth gradient trend line chart for weight tracking.
 */
@Composable
fun WeightTrendSparkline(
    weightsKg: List<Float>,
    modifier: Modifier = Modifier
) {
    if (weightsKg.isEmpty()) return
    val minW = (weightsKg.minOrNull() ?: 60f) - 1.5f
    val maxW = (weightsKg.maxOrNull() ?: 70f) + 1.5f
    val range = (maxW - minW).coerceAtLeast(2f)

    Canvas(modifier = modifier.fillMaxWidth().height(84.dp)) {
        val count = weightsKg.size
        val stepX = if (count > 1) size.width / (count - 1).toFloat() else size.width
        val points = weightsKg.mapIndexed { idx, w ->
            val x = if (count > 1) idx * stepX else size.width / 2f
            val normY = ((w - minW) / range).coerceIn(0f, 1f)
            val y = size.height - (normY * (size.height * 0.78f)) - (size.height * 0.11f)
            Offset(x, y)
        }

        if (points.size >= 2) {
            val fillPath = Path().apply {
                moveTo(points.first().x, size.height)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, size.height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(EmeraldPulse.copy(alpha = 0.34f), Color.Transparent)
                )
            )

            val strokePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val cx = (prev.x + curr.x) / 2f
                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                }
            }
            drawPath(
                path = strokePath,
                brush = Brush.horizontalGradient(listOf(EmeraldPulse, NeonLime)),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        points.forEachIndexed { idx, pt ->
            val isLast = idx == points.lastIndex
            drawCircle(
                color = if (isLast) NeonLime else EmeraldPulse,
                radius = if (isLast) 6.dp.toPx() else 3.5.dp.toPx(),
                center = pt
            )
            if (isLast) {
                drawCircle(
                    color = ObsidianBlack,
                    radius = 2.5.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}
