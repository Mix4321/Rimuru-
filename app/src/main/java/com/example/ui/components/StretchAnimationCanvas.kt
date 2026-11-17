package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.data.model.StretchAnimationType
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.ForestGreenMedium
import com.example.ui.theme.MintBright
import com.example.ui.theme.MintSoft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class StretchRig(
    val head: Offset,
    val neck: Offset,
    val lShoulder: Offset,
    val rShoulder: Offset,
    val lElbow: Offset,
    val rElbow: Offset,
    val lWrist: Offset,
    val rWrist: Offset,
    val hipCenter: Offset,
    val lHip: Offset,
    val rHip: Offset,
    val lKnee: Offset,
    val rKnee: Offset,
    val lAnkle: Offset,
    val rAnkle: Offset,
    val targetHighlights: List<Offset>,
    val motionArcCenter: Offset? = null,
    val motionArcRadius: Float = 0f,
    val motionArrowStart: Offset? = null,
    val motionArrowEnd: Offset? = null
)

@Composable
fun StretchAnimationCanvas(
    animationType: StretchAnimationType,
    isPlaying: Boolean,
    isLeftSideActive: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "stretch_motion")
    val animatedCycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycle_phase"
    )
    val pulsePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_phase"
    )

    val phase = if (isPlaying) animatedCycle else 0.35f
    val pulse = if (isPlaying) pulsePhase else 0.5f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val floorY = h * 0.87f

        // Radial studio spotlight background
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(ForestGreenMedium, ForestGreenDeep),
                center = Offset(cx, h * 0.45f),
                radius = maxOf(w, h) * 0.72f
            )
        )

        // Subtle floor guide mat
        drawLine(
            color = MintSoft.copy(alpha = 0.25f),
            start = Offset(w * 0.16f, floorY),
            end = Offset(w * 0.84f, floorY),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = MintBright.copy(alpha = 0.45f),
            start = Offset(w * 0.28f, floorY),
            end = Offset(w * 0.72f, floorY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        val unit = minOf(w, h) * 0.36f
        val rig = buildRig(
            type = animationType,
            phase = phase,
            cx = cx,
            floorY = floorY,
            unit = unit,
            flipSide = !isLeftSideActive
        )

        // Highlight targeted muscle joints with soft breathing glow
        for (target in rig.targetHighlights) {
            val outerR = (18f + pulse * 14f) * (unit / 120f).coerceIn(0.75f, 1.5f)
            drawCircle(
                color = AmberWarm.copy(alpha = 0.22f + pulse * 0.14f),
                radius = outerR,
                center = target
            )
            drawCircle(
                color = AmberWarm.copy(alpha = 0.75f),
                radius = outerR * 0.55f,
                center = target,
                style = Stroke(width = 3f)
            )
        }

        // Optional circular motion cue (for rotations)
        if (rig.motionArcCenter != null && rig.motionArcRadius > 0f) {
            drawCircle(
                color = MintBright.copy(alpha = 0.45f),
                radius = rig.motionArcRadius,
                center = rig.motionArcCenter,
                style = Stroke(
                    width = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), phase * 40f)
                )
            )
        }

        // Optional directional arrow cue
        if (rig.motionArrowStart != null && rig.motionArrowEnd != null) {
            drawDirectionalCue(rig.motionArrowStart, rig.motionArrowEnd, MintBright.copy(alpha = 0.75f))
        }

        drawAthleteRig(rig, unit)
    }
}

private fun DrawScope.drawAthleteRig(rig: StretchRig, unit: Float) {
    val scale = (unit / 120f).coerceIn(0.75f, 1.6f)
    val haloWidth = 17f * scale
    val boneWidth = 10.5f * scale

    val bones = listOf(
        rig.neck to rig.lShoulder,
        rig.neck to rig.rShoulder,
        rig.lShoulder to rig.rShoulder,
        rig.neck to rig.hipCenter,
        rig.lShoulder to rig.lHip,
        rig.rShoulder to rig.rHip,
        rig.lHip to rig.rHip,
        rig.lShoulder to rig.lElbow,
        rig.lElbow to rig.lWrist,
        rig.rShoulder to rig.rElbow,
        rig.rElbow to rig.rWrist,
        rig.lHip to rig.lKnee,
        rig.lKnee to rig.lAnkle,
        rig.rHip to rig.rKnee,
        rig.rKnee to rig.rAnkle
    )

    // Back halo shadow for crisp athletic figure contrast
    for ((a, b) in bones) {
        drawLine(
            color = Color(0xFF061A13).copy(alpha = 0.65f),
            start = a,
            end = b,
            strokeWidth = haloWidth,
            cap = StrokeCap.Round
        )
    }
    // Neck
    drawLine(
        color = MintSoft,
        start = rig.head,
        end = rig.neck,
        strokeWidth = boneWidth * 0.85f,
        cap = StrokeCap.Round
    )
    // Main luminous body segments
    for ((a, b) in bones) {
        drawLine(
            color = MintBright,
            start = a,
            end = b,
            strokeWidth = boneWidth,
            cap = StrokeCap.Round
        )
    }

    // Head with clean athletic outline
    val headRadius = 17f * scale
    drawCircle(
        color = ForestGreenDeep,
        radius = headRadius,
        center = rig.head
    )
    drawCircle(
        color = MintSoft,
        radius = headRadius,
        center = rig.head,
        style = Stroke(width = 4.5f * scale)
    )

    // Crisp joint dots
    val joints = listOf(
        rig.lShoulder, rig.rShoulder,
        rig.lElbow, rig.rElbow,
        rig.lWrist, rig.rWrist,
        rig.lHip, rig.rHip,
        rig.lKnee, rig.rKnee,
        rig.lAnkle, rig.rAnkle
    )
    for (pt in joints) {
        drawCircle(
            color = ForestGreenDeep,
            radius = 6.5f * scale,
            center = pt
        )
        drawCircle(
            color = Color.White,
            radius = 4.2f * scale,
            center = pt
        )
    }
}

private fun DrawScope.drawDirectionalCue(start: Offset, end: Offset, color: Color) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = 4f,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
    )
    drawCircle(
        color = color,
        radius = 6f,
        center = end
    )
}

private fun buildRig(
    type: StretchAnimationType,
    phase: Float,
    cx: Float,
    floorY: Float,
    unit: Float,
    flipSide: Boolean
): StretchRig {
    val angle = phase * 2f * PI.toFloat()
    val wave = (1f - cos(angle)) * 0.5f // smooth 0 -> 1 -> 0 stretch wave
    val signedWave = sin(angle)         // -1 -> +1 oscillating wave
    val sideSign = if (flipSide) -1f else 1f

    // Neutral standing proportions anchored at floorY
    val ankleY = floorY - unit * 0.04f
    val kneeY = floorY - unit * 0.54f
    val hipY = floorY - unit * 1.04f
    val shoulderY = floorY - unit * 1.66f
    val neckY = floorY - unit * 1.71f
    val headY = floorY - unit * 1.94f

    val shoulderW = unit * 0.25f
    val hipW = unit * 0.16f

    var head = Offset(cx, headY)
    var neck = Offset(cx, neckY)
    var lShoulder = Offset(cx - shoulderW, shoulderY)
    var rShoulder = Offset(cx + shoulderW, shoulderY)
    var lElbow = Offset(cx - shoulderW * 1.35f, shoulderY + unit * 0.34f)
    var rElbow = Offset(cx + shoulderW * 1.35f, shoulderY + unit * 0.34f)
    var lWrist = Offset(cx - shoulderW * 1.45f, shoulderY + unit * 0.66f)
    var rWrist = Offset(cx + shoulderW * 1.45f, shoulderY + unit * 0.66f)
    var hipCenter = Offset(cx, hipY)
    var lHip = Offset(cx - hipW, hipY)
    var rHip = Offset(cx + hipW, hipY)
    var lKnee = Offset(cx - hipW * 1.05f, kneeY)
    var rKnee = Offset(cx + hipW * 1.05f, kneeY)
    var lAnkle = Offset(cx - hipW * 1.1f, ankleY)
    var rAnkle = Offset(cx + hipW * 1.1f, ankleY)

    var targets: List<Offset> = emptyList()
    var arcCenter: Offset? = null
    var arcRadius = 0f
    var arrowStart: Offset? = null
    var arrowEnd: Offset? = null

    when (type) {
        StretchAnimationType.NECK_SHOULDER_CIRCLE -> {
            val sx = cos(angle) * unit * 0.06f
            val sy = sin(angle) * unit * 0.08f
            lShoulder = Offset(cx - shoulderW - sx, shoulderY + sy)
            rShoulder = Offset(cx + shoulderW + sx, shoulderY + sy)
            lElbow = Offset(lShoulder.x - unit * 0.08f, lShoulder.y + unit * 0.33f)
            rElbow = Offset(rShoulder.x + unit * 0.08f, rShoulder.y + unit * 0.33f)
            lWrist = Offset(lElbow.x - unit * 0.04f, lElbow.y + unit * 0.31f)
            rWrist = Offset(rElbow.x + unit * 0.04f, rElbow.y + unit * 0.31f)
            head = Offset(cx + signedWave * unit * 0.09f, headY + kotlin.math.abs(signedWave) * unit * 0.03f)
            targets = listOf(neck, lShoulder, rShoulder)
            arcCenter = lShoulder
            arcRadius = unit * 0.14f
        }

        StretchAnimationType.ARM_ACROSS_CHEST -> {
            // Pull active arm across chest toward opposite shoulder
            val pull = wave * unit * 0.12f
            if (sideSign > 0f) {
                rElbow = Offset(cx - unit * 0.04f - pull, shoulderY + unit * 0.06f)
                rWrist = Offset(cx - shoulderW * 1.3f - pull, shoulderY + unit * 0.02f)
                lElbow = Offset(cx - unit * 0.28f, shoulderY + unit * 0.30f)
                lWrist = Offset(rElbow.x + unit * 0.03f, rElbow.y + unit * 0.02f)
                targets = listOf(rShoulder, rElbow)
                arrowStart = rElbow
                arrowEnd = Offset(cx - shoulderW * 1.1f, shoulderY + unit * 0.05f)
            } else {
                lElbow = Offset(cx + unit * 0.04f + pull, shoulderY + unit * 0.06f)
                lWrist = Offset(cx + shoulderW * 1.3f + pull, shoulderY + unit * 0.02f)
                rElbow = Offset(cx + unit * 0.28f, shoulderY + unit * 0.30f)
                rWrist = Offset(lElbow.x - unit * 0.03f, lElbow.y + unit * 0.02f)
                targets = listOf(lShoulder, lElbow)
                arrowStart = lElbow
                arrowEnd = Offset(cx + shoulderW * 1.1f, shoulderY + unit * 0.05f)
            }
        }

        StretchAnimationType.OVERHEAD_TRICEPS -> {
            val lean = wave * unit * 0.12f * -sideSign
            head = Offset(cx + lean, headY + wave * unit * 0.03f)
            neck = Offset(cx + lean * 0.8f, neckY + wave * unit * 0.02f)
            lShoulder = Offset(cx - shoulderW + lean * 0.7f, shoulderY)
            rShoulder = Offset(cx + shoulderW + lean * 0.7f, shoulderY)
            if (sideSign > 0f) {
                rElbow = Offset(rShoulder.x - unit * 0.06f + lean, shoulderY - unit * 0.34f)
                rWrist = Offset(neck.x - unit * 0.04f, shoulderY - unit * 0.06f)
                lElbow = Offset(lShoulder.x - unit * 0.12f, shoulderY - unit * 0.22f)
                lWrist = rElbow
                targets = listOf(rElbow, rShoulder)
                arrowStart = rElbow
                arrowEnd = Offset(rElbow.x - unit * 0.16f, rElbow.y + unit * 0.08f)
            } else {
                lElbow = Offset(lShoulder.x + unit * 0.06f + lean, shoulderY - unit * 0.34f)
                lWrist = Offset(neck.x + unit * 0.04f, shoulderY - unit * 0.06f)
                rElbow = Offset(rShoulder.x + unit * 0.12f, shoulderY - unit * 0.22f)
                rWrist = lElbow
                targets = listOf(lElbow, lShoulder)
                arrowStart = lElbow
                arrowEnd = Offset(lElbow.x + unit * 0.16f, lElbow.y + unit * 0.08f)
            }
        }

        StretchAnimationType.WRIST_FOREARM -> {
            val flex = wave * unit * 0.08f
            if (sideSign > 0f) {
                rElbow = Offset(cx + unit * 0.26f, shoulderY + unit * 0.10f)
                rWrist = Offset(cx + unit * 0.42f, shoulderY + unit * 0.06f)
                lElbow = Offset(cx - unit * 0.04f, shoulderY + unit * 0.28f)
                lWrist = Offset(rWrist.x - flex, rWrist.y - unit * 0.08f - flex)
                targets = listOf(rWrist, rElbow)
                arcCenter = rWrist
                arcRadius = unit * 0.14f
            } else {
                lElbow = Offset(cx - unit * 0.26f, shoulderY + unit * 0.10f)
                lWrist = Offset(cx - unit * 0.42f, shoulderY + unit * 0.06f)
                rElbow = Offset(cx + unit * 0.04f, shoulderY + unit * 0.28f)
                rWrist = Offset(lWrist.x + flex, lWrist.y - unit * 0.08f - flex)
                targets = listOf(lWrist, lElbow)
                arcCenter = lWrist
                arcRadius = unit * 0.14f
            }
        }

        StretchAnimationType.HIP_ROTATION -> {
            val hx = cos(angle) * unit * 0.16f
            val hy = sin(angle) * unit * 0.06f
            hipCenter = Offset(cx + hx, hipY + hy)
            lHip = Offset(hipCenter.x - hipW, hipCenter.y)
            rHip = Offset(hipCenter.x + hipW, hipCenter.y)
            // Hands on hips
            lElbow = Offset(lShoulder.x - unit * 0.18f, shoulderY + unit * 0.30f)
            rElbow = Offset(rShoulder.x + unit * 0.18f, shoulderY + unit * 0.30f)
            lWrist = lHip
            rWrist = rHip
            lKnee = Offset(cx - hipW * 1.15f + hx * 0.4f, kneeY)
            rKnee = Offset(cx + hipW * 1.15f + hx * 0.4f, kneeY)
            targets = listOf(lHip, rHip)
            arcCenter = Offset(cx, hipY)
            arcRadius = unit * 0.24f
        }

        StretchAnimationType.QUAD_PULL -> {
            // Standing on one leg, pulling the other ankle behind glutes
            val pullLift = wave * unit * 0.09f
            if (sideSign > 0f) {
                rKnee = Offset(rHip.x + unit * 0.03f, kneeY + unit * 0.04f)
                rAnkle = Offset(rHip.x + unit * 0.22f, hipY + unit * 0.20f - pullLift)
                rElbow = Offset(rShoulder.x + unit * 0.14f, shoulderY + unit * 0.36f)
                rWrist = rAnkle
                // Balance arm out slightly
                lElbow = Offset(lShoulder.x - unit * 0.22f, shoulderY + unit * 0.18f)
                lWrist = Offset(lElbow.x - unit * 0.20f, lElbow.y + unit * 0.10f)
                targets = listOf(rHip, rKnee)
                arrowStart = rAnkle
                arrowEnd = Offset(rHip.x + unit * 0.08f, rHip.y + unit * 0.04f)
            } else {
                lKnee = Offset(lHip.x - unit * 0.03f, kneeY + unit * 0.04f)
                lAnkle = Offset(lHip.x - unit * 0.22f, hipY + unit * 0.20f - pullLift)
                lElbow = Offset(lShoulder.x - unit * 0.14f, shoulderY + unit * 0.36f)
                lWrist = lAnkle
                rElbow = Offset(rShoulder.x + unit * 0.22f, shoulderY + unit * 0.18f)
                rWrist = Offset(rElbow.x + unit * 0.20f, rElbow.y + unit * 0.10f)
                targets = listOf(lHip, lKnee)
                arrowStart = lAnkle
                arrowEnd = Offset(lHip.x - unit * 0.08f, lHip.y + unit * 0.04f)
            }
        }

        StretchAnimationType.HAMSTRING_REACH -> {
            // Side profile-ish forward hinge reaching down toward front extended leg
            val bend = 0.28f + wave * 0.55f // torso forward hinge amount
            val torsoLen = unit * 0.65f
            val hipX = cx - unit * 0.10f * sideSign
            hipCenter = Offset(hipX, hipY + unit * 0.04f)
            lHip = Offset(hipX - unit * 0.07f, hipCenter.y)
            rHip = Offset(hipX + unit * 0.07f, hipCenter.y)

            val torsoAngle = (-PI.toFloat() / 2f) + bend * 1.15f * sideSign
            neck = Offset(
                hipCenter.x + cos(torsoAngle) * torsoLen,
                hipCenter.y + sin(torsoAngle) * torsoLen
            )
            head = Offset(
                neck.x + cos(torsoAngle) * (unit * 0.22f),
                neck.y + sin(torsoAngle) * (unit * 0.22f)
            )
            lShoulder = Offset(neck.x - unit * 0.08f, neck.y + unit * 0.04f)
            rShoulder = Offset(neck.x + unit * 0.08f, neck.y + unit * 0.04f)

            // Front leg extended forward, back leg slightly bent
            val frontFootX = cx + unit * 0.40f * sideSign
            val backFootX = cx - unit * 0.16f * sideSign
            if (sideSign > 0f) {
                rKnee = Offset((rHip.x + frontFootX) * 0.5f, (rHip.y + ankleY) * 0.5f)
                rAnkle = Offset(frontFootX, ankleY - unit * 0.04f)
                lKnee = Offset(backFootX - unit * 0.04f, kneeY + unit * 0.04f)
                lAnkle = Offset(backFootX, ankleY)
                // Arms reach down toward front foot
                rElbow = Offset((rShoulder.x + frontFootX) * 0.52f, rShoulder.y + unit * 0.30f)
                rWrist = Offset(frontFootX - unit * 0.06f, ankleY - unit * 0.22f - (1f - wave) * unit * 0.15f)
                lElbow = Offset(rElbow.x - unit * 0.06f, rElbow.y + unit * 0.03f)
                lWrist = Offset(rWrist.x - unit * 0.06f, rWrist.y + unit * 0.03f)
                targets = listOf(rKnee, rHip)
                arrowStart = rWrist
                arrowEnd = rAnkle
            } else {
                lKnee = Offset((lHip.x + frontFootX) * 0.5f, (lHip.y + ankleY) * 0.5f)
                lAnkle = Offset(frontFootX, ankleY - unit * 0.04f)
                rKnee = Offset(backFootX + unit * 0.04f, kneeY + unit * 0.04f)
                rAnkle = Offset(backFootX, ankleY)
                lElbow = Offset((lShoulder.x + frontFootX) * 0.52f, lShoulder.y + unit * 0.30f)
                lWrist = Offset(frontFootX + unit * 0.06f, ankleY - unit * 0.22f - (1f - wave) * unit * 0.15f)
                rElbow = Offset(lElbow.x + unit * 0.06f, lElbow.y + unit * 0.03f)
                rWrist = Offset(lWrist.x + unit * 0.06f, lWrist.y + unit * 0.03f)
                targets = listOf(lKnee, lHip)
                arrowStart = lWrist
                arrowEnd = lAnkle
            }
        }

        StretchAnimationType.CALF_LUNGE -> {
            // Split stance lunge: front knee bends forward, back leg stays straight with heel planted
            val shift = wave * unit * 0.12f * sideSign
            hipCenter = Offset(cx + shift, hipY + wave * unit * 0.10f)
            lHip = Offset(hipCenter.x - unit * 0.08f, hipCenter.y)
            rHip = Offset(hipCenter.x + unit * 0.08f, hipCenter.y)
            neck = Offset(hipCenter.x + unit * 0.10f * sideSign + shift * 0.5f, neckY + wave * unit * 0.10f)
            head = Offset(neck.x + unit * 0.04f * sideSign, neck.y - unit * 0.22f)
            lShoulder = Offset(neck.x - unit * 0.10f, neck.y + unit * 0.05f)
            rShoulder = Offset(neck.x + unit * 0.10f, neck.y + unit * 0.05f)

            lElbow = Offset(lShoulder.x + unit * 0.14f * sideSign, lShoulder.y + unit * 0.22f)
            rElbow = Offset(rShoulder.x + unit * 0.14f * sideSign, rShoulder.y + unit * 0.22f)
            lWrist = Offset(lElbow.x + unit * 0.16f * sideSign, lElbow.y - unit * 0.08f)
            rWrist = Offset(rElbow.x + unit * 0.16f * sideSign, rElbow.y - unit * 0.08f)

            val frontFootX = cx + unit * 0.38f * sideSign
            val backFootX = cx - unit * 0.42f * sideSign
            if (sideSign > 0f) {
                rKnee = Offset(frontFootX + shift * 0.6f, kneeY + unit * 0.06f)
                rAnkle = Offset(frontFootX, ankleY)
                lKnee = Offset((lHip.x + backFootX) * 0.5f, (lHip.y + ankleY) * 0.5f)
                lAnkle = Offset(backFootX, ankleY)
                targets = listOf(lAnkle, lKnee)
                arrowStart = hipCenter
                arrowEnd = Offset(hipCenter.x + unit * 0.18f, hipCenter.y)
            } else {
                lKnee = Offset(frontFootX + shift * 0.6f, kneeY + unit * 0.06f)
                lAnkle = Offset(frontFootX, ankleY)
                rKnee = Offset((rHip.x + backFootX) * 0.5f, (rHip.y + ankleY) * 0.5f)
                rAnkle = Offset(backFootX, ankleY)
                targets = listOf(rAnkle, rKnee)
                arrowStart = hipCenter
                arrowEnd = Offset(hipCenter.x - unit * 0.18f, hipCenter.y)
            }
        }

        StretchAnimationType.HIGH_KNEES_MARCH -> {
            // Alternating high knee lifts with swinging arms
            val lLift = (-signedWave).coerceAtLeast(0f)
            val rLift = signedWave.coerceAtLeast(0f)

            lKnee = Offset(lHip.x - unit * 0.03f, kneeY - lLift * unit * 0.44f)
            lAnkle = Offset(lHip.x - unit * 0.03f, ankleY - lLift * unit * 0.48f)
            rKnee = Offset(rHip.x + unit * 0.03f, kneeY - rLift * unit * 0.44f)
            rAnkle = Offset(rHip.x + unit * 0.03f, ankleY - rLift * unit * 0.48f)

            // Opposite arm swings forward/up
            lElbow = Offset(lShoulder.x - unit * 0.14f, shoulderY + unit * 0.30f - rLift * unit * 0.08f)
            lWrist = Offset(lElbow.x + unit * 0.04f, lElbow.y + unit * 0.22f - rLift * unit * 0.32f)
            rElbow = Offset(rShoulder.x + unit * 0.14f, shoulderY + unit * 0.30f - lLift * unit * 0.08f)
            rWrist = Offset(rElbow.x - unit * 0.04f, rElbow.y + unit * 0.22f - lLift * unit * 0.32f)
            targets = listOf(lHip, rHip, if (lLift >= rLift) lKnee else rKnee)
        }

        StretchAnimationType.ANKLE_CIRCLES -> {
            val circleX = cos(angle) * unit * 0.09f
            val circleY = sin(angle) * unit * 0.05f
            lElbow = Offset(lShoulder.x - unit * 0.18f, shoulderY + unit * 0.30f)
            rElbow = Offset(rShoulder.x + unit * 0.18f, shoulderY + unit * 0.30f)
            lWrist = lHip
            rWrist = rHip
            if (sideSign > 0f) {
                rKnee = Offset(rHip.x + unit * 0.08f, kneeY - unit * 0.08f)
                rAnkle = Offset(rKnee.x + circleX, ankleY - unit * 0.16f + circleY)
                targets = listOf(rAnkle)
                arcCenter = Offset(rKnee.x, ankleY - unit * 0.16f)
                arcRadius = unit * 0.12f
            } else {
                lKnee = Offset(lHip.x - unit * 0.08f, kneeY - unit * 0.08f)
                lAnkle = Offset(lKnee.x + circleX, ankleY - unit * 0.16f + circleY)
                targets = listOf(lAnkle)
                arcCenter = Offset(lKnee.x, ankleY - unit * 0.16f)
                arcRadius = unit * 0.12f
            }
        }

        StretchAnimationType.DEEP_SQUAT_HIP_OPENER -> {
            val drop = wave * unit * 0.36f
            hipCenter = Offset(cx, hipY + drop)
            lHip = Offset(cx - hipW * (1f + wave * 0.25f), hipCenter.y)
            rHip = Offset(cx + hipW * (1f + wave * 0.25f), hipCenter.y)
            neck = Offset(cx, neckY + drop * 0.85f)
            head = Offset(cx, headY + drop * 0.85f)
            lShoulder = Offset(cx - shoulderW, shoulderY + drop * 0.85f)
            rShoulder = Offset(cx + shoulderW, shoulderY + drop * 0.85f)

            // Arms counter-balance forward/inside knees
            lElbow = Offset(lShoulder.x - unit * 0.06f, lShoulder.y + unit * 0.26f)
            rElbow = Offset(rShoulder.x + unit * 0.06f, rShoulder.y + unit * 0.26f)
            lWrist = Offset(cx - unit * 0.06f, lElbow.y + unit * 0.06f)
            rWrist = Offset(cx + unit * 0.06f, rElbow.y + unit * 0.06f)

            val kneeOut = unit * (0.20f + wave * 0.18f)
            lKnee = Offset(cx - kneeOut, kneeY + drop * 0.38f)
            rKnee = Offset(cx + kneeOut, kneeY + drop * 0.38f)
            lAnkle = Offset(cx - unit * 0.24f, ankleY)
            rAnkle = Offset(cx + unit * 0.24f, ankleY)
            targets = listOf(lHip, rHip, lKnee, rKnee)
            arrowStart = hipCenter
            arrowEnd = Offset(cx, hipY + unit * 0.36f)
        }
    }

    return StretchRig(
        head = head,
        neck = neck,
        lShoulder = lShoulder,
        rShoulder = rShoulder,
        lElbow = lElbow,
        rElbow = rElbow,
        lWrist = lWrist,
        rWrist = rWrist,
        hipCenter = hipCenter,
        lHip = lHip,
        rHip = rHip,
        lKnee = lKnee,
        rKnee = rKnee,
        lAnkle = lAnkle,
        rAnkle = rAnkle,
        targetHighlights = targets,
        motionArcCenter = arcCenter,
        motionArcRadius = arcRadius,
        motionArrowStart = arrowStart,
        motionArrowEnd = arrowEnd
    )
}
