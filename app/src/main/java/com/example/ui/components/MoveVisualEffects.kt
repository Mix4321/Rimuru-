package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.MintBright
import com.example.ui.theme.MintSoft
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Physical spring bounce + Material ripple on clickable surfaces.
 */
fun Modifier.bounceClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = onClick
        )
}

/**
 * Subtle infinite diagonal specular shimmer sweep for primary hero CTAs & achievement badges.
 */
fun Modifier.shimmerSweepEffect(
    enabled: Boolean = true,
    shimmerAlpha: Float = 0.22f,
    durationMillis: Int = 2600
): Modifier = composed {
    if (!enabled) return@composed this
    val transition = rememberInfiniteTransition(label = "shimmer_sweep")
    val progress by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )
    this.drawWithContent {
        drawContent()
        val bandWidth = size.width * 0.42f
        val startX = size.width * progress - bandWidth * 0.5f
        val endX = startX + bandWidth
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = shimmerAlpha),
                    Color.Transparent
                ),
                start = Offset(startX, 0f),
                end = Offset(endX, size.height)
            )
        )
    }
}

/**
 * Soft breathing radial glow orbs behind dark emerald Hero cards or screens.
 */
fun Modifier.auroraGlowBackdrop(
    primaryGlow: Color = MintBright,
    secondaryGlow: Color = EmeraldVibrant
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "aurora_glow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_phase"
    )
    this.drawBehind {
        val driftX = cos(phase) * (size.width * 0.06f)
        val driftY = sin(phase) * (size.height * 0.08f)

        val orb1Center = Offset(size.width * 0.85f + driftX, size.height * 0.18f - driftY)
        val orb1Radius = size.maxDimension * 0.58f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryGlow.copy(alpha = 0.24f),
                    primaryGlow.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = orb1Center,
                radius = orb1Radius
            ),
            radius = orb1Radius,
            center = orb1Center
        )

        val orb2Center = Offset(size.width * 0.12f - driftX, size.height * 0.92f + driftY)
        val orb2Radius = size.maxDimension * 0.52f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryGlow.copy(alpha = 0.26f),
                    secondaryGlow.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = orb2Center,
                radius = orb2Radius
            ),
            radius = orb2Radius,
            center = orb2Center
        )
    }
}

/**
 * Concentric sonar pulse dot to indicate active live GPS/AI tracking or active status.
 */
@Composable
fun LivePulseBeacon(
    color: Color,
    isActive: Boolean,
    size: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "live_pulse")
    val pulseFraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_fraction"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxR = this.size.minDimension / 2f
            val coreR = maxR * 0.45f
            if (isActive) {
                val ringR = coreR + (maxR - coreR) * pulseFraction
                val alpha = (1f - pulseFraction) * 0.65f
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = ringR
                )
            }
            drawCircle(
                color = color,
                radius = coreR
            )
        }
    }
}

/**
 * Staggered smooth entrance for grid items, lists, and cards (< 300ms per item).
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    delayPerItemMs: Long = 35L,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val cappedDelay = (index.coerceIn(0, 8) * delayPerItemMs)
        if (cappedDelay > 0L) delay(cappedDelay)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 240)) +
            slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it / 6 }
            ) +
            scaleIn(
                initialScale = 0.96f,
                animationSpec = tween(durationMillis = 240)
            ),
        modifier = modifier
    ) {
        content()
    }
}

private data class ConfettiParticle(
    val startXFraction: Float,
    val velocityX: Float,
    val velocityY: Float,
    val sizeDp: Float,
    val spinSpeed: Float,
    val color: Color,
    val isCircle: Boolean
)

/**
 * Lightweight 60fps Canvas celebration particle burst for post-workout summary & goal milestones.
 */
@Composable
fun ConfettiCelebrationOverlay(
    triggerKey: Any,
    modifier: Modifier = Modifier
) {
    val particles = remember(triggerKey) {
        val rnd = Random(triggerKey.hashCode())
        val palette = listOf(MintBright, AmberWarm, EmeraldVibrant, MintSoft, Color.White)
        List(36) {
            ConfettiParticle(
                startXFraction = 0.15f + rnd.nextFloat() * 0.70f,
                velocityX = (rnd.nextFloat() - 0.5f) * 380f,
                velocityY = -(240f + rnd.nextFloat() * 440f),
                sizeDp = 5f + rnd.nextFloat() * 5f,
                spinSpeed = (rnd.nextFloat() - 0.5f) * 540f,
                color = palette[rnd.nextInt(palette.size)],
                isCircle = rnd.nextBoolean()
            )
        }
    }

    var started by remember(triggerKey) { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 2300, easing = LinearEasing),
        label = "confetti_progress"
    )

    LaunchedEffect(triggerKey) {
        started = true
    }

    if (animProgress >= 0.99f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = animProgress
        val gravity = 860f
        val fade = when {
            t < 0.72f -> 1f
            else -> (1f - (t - 0.72f) / 0.28f).coerceIn(0f, 1f)
        }
        val originY = size.height * 0.24f

        particles.forEach { p ->
            val x = size.width * p.startXFraction + p.velocityX * t
            val y = originY + p.velocityY * t + 0.5f * gravity * t * t
            if (y in -40f..(size.height + 40f)) {
                val pxSize = p.sizeDp.dp.toPx()
                val col = p.color.copy(alpha = fade * 0.9f)
                if (p.isCircle) {
                    drawCircle(
                        color = col,
                        radius = pxSize * 0.5f,
                        center = Offset(x, y)
                    )
                } else {
                    rotate(degrees = p.spinSpeed * t, pivot = Offset(x, y)) {
                        drawRect(
                            color = col,
                            topLeft = Offset(x - pxSize * 0.5f, y - pxSize * 0.3f),
                            size = Size(pxSize, pxSize * 0.6f)
                        )
                    }
                }
            }
        }
    }
}
