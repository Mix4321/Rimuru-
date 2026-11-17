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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.data.model.StretchAnimationType
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.ForestGreenMedium
import com.example.ui.theme.MintBright
import com.example.ui.theme.MintSoft
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val SkinBase = Color(0xFFF3C6A5)
private val SkinShadow = Color(0xFFD8A07A)
private val HairDark = Color(0xFF1A2822)
private val JerseyTop = Color(0xFF22A06B)
private val JerseyBottom = Color(0xFF146B46)
private val ShortsDark = Color(0xFF0B2F23)
private val ShortsTrim = Color(0xFF34D399)
private val ShoeUpper = Color(0xFFF8FAFC)
private val ShoeSole = Color(0xFF34D399)

private data class HumanRig(
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
    val facingDirection: Float = 0f, // 0f = front-facing, +1f = facing right, -1f = facing left
    val targetHighlights: List<Offset> = emptyList(),
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
    val transition = rememberInfiniteTransition(label = "human_stretch_motion")
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

        // Studio backdrop
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(ForestGreenMedium, ForestGreenDeep),
                center = Offset(cx, h * 0.44f),
                radius = maxOf(w, h) * 0.72f
            )
        )

        val unit = minOf(w, h) * 0.36f
        val rig = buildHumanRig(
            type = animationType,
            phase = phase,
            cx = cx,
            floorY = floorY,
            unit = unit,
            flipSide = !isLeftSideActive
        )

        // Floor exercise mat + grounded drop shadow
        drawRoundRect(
            color = Color(0xFF082218),
            topLeft = Offset(w * 0.17f, floorY - 5f),
            size = Size(w * 0.66f, 12f),
            style = Fill
        )
        drawLine(
            color = MintBright.copy(alpha = 0.42f),
            start = Offset(w * 0.22f, floorY - 1f),
            end = Offset(w * 0.78f, floorY - 1f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawOval(
            color = Color.Black.copy(alpha = 0.38f),
            topLeft = Offset(cx - unit * 0.52f, floorY - 4f),
            size = Size(unit * 1.04f, 14f)
        )

        // Motion path guides (behind body)
        if (rig.motionArcCenter != null && rig.motionArcRadius > 0f) {
            drawCircle(
                color = MintBright.copy(alpha = 0.45f),
                radius = rig.motionArcRadius,
                center = rig.motionArcCenter,
                style = Stroke(
                    width = 3.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), phase * 40f)
                )
            )
        }
        if (rig.motionArrowStart != null && rig.motionArrowEnd != null) {
            drawDirectionalCue(rig.motionArrowStart, rig.motionArrowEnd, MintBright.copy(alpha = 0.8f))
        }

        // Sculpted full human figure
        drawHumanFigure(rig, unit, floorY)

        // Highlight targeted muscle groups with gentle breathing pulse
        val s = (unit / 120f).coerceIn(0.75f, 1.55f)
        for (target in rig.targetHighlights) {
            val outerR = (20f + pulse * 13f) * s
            drawCircle(
                color = AmberWarm.copy(alpha = 0.24f + pulse * 0.14f),
                radius = outerR,
                center = target
            )
            drawCircle(
                color = AmberWarm.copy(alpha = 0.78f),
                radius = outerR * 0.56f,
                center = target,
                style = Stroke(width = 3f)
            )
        }
    }
}

private fun DrawScope.drawHumanFigure(rig: HumanRig, unit: Float, floorY: Float) {
    val s = (unit / 120f).coerceIn(0.75f, 1.55f)
    val isSide = abs(rig.facingDirection) > 0.2f

    // Back leg first when in side profile so front leg layers cleanly in front
    if (isSide) {
        drawHumanLeg(rig.lHip, rig.lKnee, rig.lAnkle, rig.facingDirection, floorY, s, isBackLimb = true)
        drawHumanArm(rig.lShoulder, rig.lElbow, rig.lWrist, s, isBackLimb = true)
    }

    // Neck
    drawTaperedLimb(rig.head, rig.neck, 10.5f * s, 11.5f * s, SkinShadow)

    // Legs (front-facing draws both symmetrically)
    if (!isSide) {
        drawHumanLeg(rig.lHip, rig.lKnee, rig.lAnkle, -0.35f, floorY, s, isBackLimb = false)
        drawHumanLeg(rig.rHip, rig.rKnee, rig.rAnkle, 0.35f, floorY, s, isBackLimb = false)
    }

    // Torso athletic jersey + shorts pelvis
    drawTorsoAndShorts(rig, s)

    // Arms
    if (!isSide) {
        drawHumanArm(rig.lShoulder, rig.lElbow, rig.lWrist, s, isBackLimb = false)
        drawHumanArm(rig.rShoulder, rig.rElbow, rig.rWrist, s, isBackLimb = false)
    }

    // Front leg & front arm when in side profile
    if (isSide) {
        drawHumanLeg(rig.rHip, rig.rKnee, rig.rAnkle, rig.facingDirection, floorY, s, isBackLimb = false)
        drawHumanArm(rig.rShoulder, rig.rElbow, rig.rWrist, s, isBackLimb = false)
    }

    // Head, hair & facial features
    drawHumanHead(rig.head, rig.neck, rig.facingDirection, s)
}

private fun DrawScope.drawTorsoAndShorts(rig: HumanRig, s: Float) {
    val shoulderMid = Offset(
        (rig.lShoulder.x + rig.rShoulder.x) / 2f,
        (rig.lShoulder.y + rig.rShoulder.y) / 2f
    )
    val waistL = Offset(
        rig.lShoulder.x * 0.26f + rig.lHip.x * 0.74f,
        rig.lShoulder.y * 0.24f + rig.lHip.y * 0.76f
    )
    val waistR = Offset(
        rig.rShoulder.x * 0.26f + rig.rHip.x * 0.74f,
        rig.rShoulder.y * 0.24f + rig.rHip.y * 0.76f
    )

    // Ensure torso always has realistic thickness even in side-view poses
    val minTorsoHalfW = 14f * s
    val topW = hypot(rig.rShoulder.x - rig.lShoulder.x, rig.rShoulder.y - rig.lShoulder.y)
    val shoulderL = if (topW < minTorsoHalfW * 2f) Offset(shoulderMid.x - minTorsoHalfW, rig.lShoulder.y) else rig.lShoulder
    val shoulderR = if (topW < minTorsoHalfW * 2f) Offset(shoulderMid.x + minTorsoHalfW, rig.rShoulder.y) else rig.rShoulder

    val hipW = hypot(rig.rHip.x - rig.lHip.x, rig.rHip.y - rig.lHip.y)
    val pelvisL = if (hipW < minTorsoHalfW * 1.7f) Offset(rig.hipCenter.x - minTorsoHalfW * 0.9f, rig.lHip.y) else rig.lHip
    val pelvisR = if (hipW < minTorsoHalfW * 1.7f) Offset(rig.hipCenter.x + minTorsoHalfW * 0.9f, rig.rHip.y) else rig.rHip

    val torsoPath = Path().apply {
        moveTo(shoulderL.x, shoulderL.y - 3f * s)
        quadraticTo(shoulderMid.x, rig.neck.y - 2f * s, shoulderR.x, shoulderR.y - 3f * s)
        lineTo(waistR.x.coerceAtLeast(pelvisR.x - 2f * s), waistR.y)
        lineTo(pelvisR.x, pelvisR.y)
        lineTo(pelvisL.x, pelvisL.y)
        lineTo(waistL.x.coerceAtMost(pelvisL.x + 2f * s), waistL.y)
        close()
    }

    drawPath(
        path = torsoPath,
        brush = Brush.verticalGradient(
            colors = listOf(JerseyTop, JerseyBottom),
            startY = minOf(shoulderL.y, shoulderR.y),
            endY = maxOf(pelvisL.y, pelvisR.y)
        )
    )
    drawPath(
        path = torsoPath,
        color = ForestGreenDeep.copy(alpha = 0.55f),
        style = Stroke(width = 2.2f * s, join = StrokeJoin.Round)
    )

    // Athletic shorts hip band
    val shortsBottomY = rig.hipCenter.y + 13f * s
    val shortsPath = Path().apply {
        moveTo(pelvisL.x - 2f * s, pelvisL.y - 2f * s)
        lineTo(pelvisR.x + 2f * s, pelvisR.y - 2f * s)
        lineTo(pelvisR.x + 3f * s, shortsBottomY)
        lineTo(pelvisL.x - 3f * s, shortsBottomY)
        close()
    }
    drawPath(path = shortsPath, color = ShortsDark)
    drawLine(
        color = ShortsTrim,
        start = Offset(pelvisL.x - 1f * s, pelvisL.y),
        end = Offset(pelvisR.x + 1f * s, pelvisR.y),
        strokeWidth = 3f * s,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawHumanArm(
    shoulder: Offset,
    elbow: Offset,
    wrist: Offset,
    s: Float,
    isBackLimb: Boolean
) {
    val skinColor = if (isBackLimb) SkinShadow else SkinBase
    val sleeveColor = if (isBackLimb) ForestGreen else JerseyTop

    // Short sleeve covering upper 42% of upper arm
    val sleeveEnd = Offset(
        shoulder.x + (elbow.x - shoulder.x) * 0.42f,
        shoulder.y + (elbow.y - shoulder.y) * 0.42f
    )
    drawTaperedLimb(shoulder, elbow, 8.8f * s, 7.0f * s, skinColor)
    drawTaperedLimb(shoulder, sleeveEnd, 9.6f * s, 8.5f * s, sleeveColor)
    drawTaperedLimb(elbow, wrist, 6.8f * s, 5.0f * s, skinColor)

    // Elbow contour + palm/hand
    drawCircle(color = skinColor, radius = 6.2f * s, center = elbow)
    drawCircle(color = skinColor, radius = 6.5f * s, center = wrist)
    drawCircle(
        color = SkinShadow.copy(alpha = 0.65f),
        radius = 6.5f * s,
        center = wrist,
        style = Stroke(width = 1.6f * s)
    )
}

private fun DrawScope.drawHumanLeg(
    hip: Offset,
    knee: Offset,
    ankle: Offset,
    toeDirSign: Float,
    floorY: Float,
    s: Float,
    isBackLimb: Boolean
) {
    val skinColor = if (isBackLimb) SkinShadow else SkinBase
    val shortHem = Offset(
        hip.x + (knee.x - hip.x) * 0.42f,
        hip.y + (knee.y - hip.y) * 0.42f
    )

    // Thigh + shorts leg
    drawTaperedLimb(hip, knee, 12.2f * s, 9.0f * s, skinColor)
    drawTaperedLimb(hip, shortHem, 13.0f * s, 11.0f * s, ShortsDark)

    // Knee cap & lower calf
    drawCircle(color = skinColor, radius = 8.4f * s, center = knee)
    drawTaperedLimb(knee, ankle, 8.8f * s, 5.8f * s, skinColor)

    // Athletic Sneaker
    val shoeDir = if (toeDirSign >= 0f) 1f else -1f
    val heel = Offset(ankle.x - shoeDir * 7f * s, minOf(ankle.y + 7f * s, floorY - 2f * s))
    val toe = Offset(ankle.x + shoeDir * 19f * s, minOf(ankle.y + 7f * s, floorY - 2f * s))
    val shoePath = Path().apply {
        moveTo(heel.x, ankle.y - 3f * s)
        lineTo(ankle.x + shoeDir * 6f * s, ankle.y - 2f * s)
        quadraticTo(toe.x, ankle.y + 2f * s, toe.x, heel.y)
        lineTo(heel.x, heel.y)
        close()
    }
    drawPath(
        path = shoePath,
        color = if (isBackLimb) Color(0xFFCBD5E1) else ShoeUpper
    )
    drawLine(
        color = ShoeSole,
        start = Offset(heel.x, heel.y),
        end = Offset(toe.x, toe.y),
        strokeWidth = 3.8f * s,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawHumanHead(
    head: Offset,
    neck: Offset,
    facingDirection: Float,
    s: Float
) {
    val headR = 18.5f * s

    // Head base
    drawCircle(
        color = SkinBase,
        radius = headR,
        center = head
    )
    drawCircle(
        color = SkinShadow.copy(alpha = 0.6f),
        radius = headR,
        center = head,
        style = Stroke(width = 1.8f * s)
    )

    // Sculpted hair cap angled slightly toward facing direction
    val hairOffset = facingDirection * 2.5f * s
    val hairPath = Path().apply {
        moveTo(head.x - headR * 0.96f, head.y - headR * 0.08f)
        quadraticTo(
            head.x - headR * 0.75f + hairOffset,
            head.y - headR * 1.18f,
            head.x + hairOffset,
            head.y - headR * 1.05f
        )
        quadraticTo(
            head.x + headR * 0.85f + hairOffset,
            head.y - headR * 1.12f,
            head.x + headR * 0.95f,
            head.y - headR * 0.08f
        )
        quadraticTo(
            head.x + headR * 0.35f,
            head.y - headR * 0.45f,
            head.x - headR * 0.96f,
            head.y - headR * 0.08f
        )
        close()
    }
    drawPath(path = hairPath, color = HairDark)

    // Athletic headband
    drawLine(
        color = MintBright,
        start = Offset(head.x - headR * 0.88f, head.y - headR * 0.25f),
        end = Offset(head.x + headR * 0.88f, head.y - headR * 0.25f),
        strokeWidth = 3.2f * s,
        cap = StrokeCap.Round
    )

    // Subtle eyes/brow cue for clear human expression
    if (abs(facingDirection) < 0.25f) {
        drawCircle(color = HairDark, radius = 2.0f * s, center = Offset(head.x - 5.5f * s, head.y + 1.5f * s))
        drawCircle(color = HairDark, radius = 2.0f * s, center = Offset(head.x + 5.5f * s, head.y + 1.5f * s))
    } else {
        val eyeX = head.x + facingDirection * 7.5f * s
        drawCircle(color = HairDark, radius = 2.1f * s, center = Offset(eyeX, head.y + 1.5f * s))
    }
}

private fun DrawScope.drawTaperedLimb(
    a: Offset,
    b: Offset,
    radiusA: Float,
    radiusB: Float,
    color: Color
) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = hypot(dx, dy)
    if (len < 1f) {
        drawCircle(color = color, radius = maxOf(radiusA, radiusB), center = a)
        return
    }
    val nx = -dy / len
    val ny = dx / len

    val path = Path().apply {
        moveTo(a.x + nx * radiusA, a.y + ny * radiusA)
        lineTo(b.x + nx * radiusB, b.y + ny * radiusB)
        lineTo(b.x - nx * radiusB, b.y - ny * radiusB)
        lineTo(a.x - nx * radiusA, a.y - ny * radiusA)
        close()
    }
    drawPath(path = path, color = color)
    drawCircle(color = color, radius = radiusA, center = a)
    drawCircle(color = color, radius = radiusB, center = b)
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

private fun buildHumanRig(
    type: StretchAnimationType,
    phase: Float,
    cx: Float,
    floorY: Float,
    unit: Float,
    flipSide: Boolean
): HumanRig {
    val angle = phase * 2f * PI.toFloat()
    val wave = (1f - cos(angle)) * 0.5f
    val signedWave = sin(angle)
    val sideSign = if (flipSide) -1f else 1f

    val ankleY = floorY - unit * 0.08f
    val kneeY = floorY - unit * 0.54f
    val hipY = floorY - unit * 1.03f
    val shoulderY = floorY - unit * 1.65f
    val neckY = floorY - unit * 1.72f
    val headY = floorY - unit * 1.96f

    val shoulderW = unit * 0.25f
    val hipW = unit * 0.16f

    var head = Offset(cx, headY)
    var neck = Offset(cx, neckY)
    var lShoulder = Offset(cx - shoulderW, shoulderY)
    var rShoulder = Offset(cx + shoulderW, shoulderY)
    var lElbow = Offset(cx - shoulderW * 1.38f, shoulderY + unit * 0.34f)
    var rElbow = Offset(cx + shoulderW * 1.38f, shoulderY + unit * 0.34f)
    var lWrist = Offset(cx - shoulderW * 1.48f, shoulderY + unit * 0.65f)
    var rWrist = Offset(cx + shoulderW * 1.48f, shoulderY + unit * 0.65f)
    var hipCenter = Offset(cx, hipY)
    var lHip = Offset(cx - hipW, hipY)
    var rHip = Offset(cx + hipW, hipY)
    var lKnee = Offset(cx - hipW * 1.05f, kneeY)
    var rKnee = Offset(cx + hipW * 1.05f, kneeY)
    var lAnkle = Offset(cx - hipW * 1.1f, ankleY)
    var rAnkle = Offset(cx + hipW * 1.1f, ankleY)

    var facing = 0f
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
            lElbow = Offset(lShoulder.x - unit * 0.10f, lShoulder.y + unit * 0.33f)
            rElbow = Offset(rShoulder.x + unit * 0.10f, rShoulder.y + unit * 0.33f)
            lWrist = Offset(lElbow.x - unit * 0.04f, lElbow.y + unit * 0.31f)
            rWrist = Offset(rElbow.x + unit * 0.04f, rElbow.y + unit * 0.31f)
            head = Offset(cx + signedWave * unit * 0.09f, headY + abs(signedWave) * unit * 0.03f)
            targets = listOf(neck, lShoulder, rShoulder)
            arcCenter = lShoulder
            arcRadius = unit * 0.14f
        }

        StretchAnimationType.ARM_ACROSS_CHEST -> {
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
            val pullLift = wave * unit * 0.09f
            if (sideSign > 0f) {
                rKnee = Offset(rHip.x + unit * 0.03f, kneeY + unit * 0.04f)
                rAnkle = Offset(rHip.x + unit * 0.22f, hipY + unit * 0.20f - pullLift)
                rElbow = Offset(rShoulder.x + unit * 0.14f, shoulderY + unit * 0.36f)
                rWrist = rAnkle
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
            facing = sideSign
            val bend = 0.28f + wave * 0.55f
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

            val frontFootX = cx + unit * 0.40f * sideSign
            val backFootX = cx - unit * 0.16f * sideSign
            if (sideSign > 0f) {
                rKnee = Offset((rHip.x + frontFootX) * 0.5f, (rHip.y + ankleY) * 0.5f)
                rAnkle = Offset(frontFootX, ankleY - unit * 0.02f)
                lKnee = Offset(backFootX - unit * 0.04f, kneeY + unit * 0.04f)
                lAnkle = Offset(backFootX, ankleY)
                rElbow = Offset((rShoulder.x + frontFootX) * 0.52f, rShoulder.y + unit * 0.30f)
                rWrist = Offset(frontFootX - unit * 0.06f, ankleY - unit * 0.22f - (1f - wave) * unit * 0.15f)
                lElbow = Offset(rElbow.x - unit * 0.06f, rElbow.y + unit * 0.03f)
                lWrist = Offset(rWrist.x - unit * 0.06f, rWrist.y + unit * 0.03f)
                targets = listOf(rKnee, rHip)
                arrowStart = rWrist
                arrowEnd = rAnkle
            } else {
                lKnee = Offset((lHip.x + frontFootX) * 0.5f, (lHip.y + ankleY) * 0.5f)
                lAnkle = Offset(frontFootX, ankleY - unit * 0.02f)
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
            facing = sideSign
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
            val lLift = (-signedWave).coerceAtLeast(0f)
            val rLift = signedWave.coerceAtLeast(0f)

            lKnee = Offset(lHip.x - unit * 0.03f, kneeY - lLift * unit * 0.44f)
            lAnkle = Offset(lHip.x - unit * 0.03f, ankleY - lLift * unit * 0.48f)
            rKnee = Offset(rHip.x + unit * 0.03f, kneeY - rLift * unit * 0.44f)
            rAnkle = Offset(rHip.x + unit * 0.03f, ankleY - rLift * unit * 0.48f)

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

    return HumanRig(
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
        facingDirection = facing,
        targetHighlights = targets,
        motionArcCenter = arcCenter,
        motionArcRadius = arcRadius,
        motionArrowStart = arrowStart,
        motionArrowEnd = arrowEnd
    )
}
