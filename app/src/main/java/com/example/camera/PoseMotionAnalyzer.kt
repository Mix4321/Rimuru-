package com.example.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.data.local.DetectionSensitivity
import com.example.data.model.SportType
import com.example.data.model.StretchAnimationType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class GeneralExerciseFocus(val thaiLabel: String) {
    AUTO("AI อัตโนมัติ"),
    PUSHUP("วิดพื้น"),
    SQUAT("สควอท"),
    LUNGE("ลันจ์"),
    SITUP("ซิทอัพ"),
    PLANK("แพลงก์จับเวลา"),
    JUMPING_JACK("กระโดดตบ")
}

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Adaptive One-Euro Filter (Casiez et al.) tuned per body-scale unit:
 * eliminates stationary landmark jitter while keeping zero phase lag on explosive reps & jumps.
 */
internal class OneEuroFilter1D(
    private val minCutoff: Float = 1.6f,
    private val beta: Float = 0.045f,
    private val dCutoff: Float = 1.0f
) {
    private var hasPrev = false
    private var xPrev = 0f
    private var dxPrev = 0f

    fun filter(value: Float, dtSec: Float, speedScale: Float = 1f): Float {
        if (!hasPrev) {
            hasPrev = true
            xPrev = value
            dxPrev = 0f
            return value
        }
        val te = dtSec.coerceIn(0.005f, 0.25f)
        val dx = (value - xPrev) / te
        val aD = smoothingAlpha(te, dCutoff)
        val dxHat = aD * dx + (1f - aD) * dxPrev
        val normSpeed = abs(dxHat) / speedScale.coerceAtLeast(1f)
        val cutoff = minCutoff + beta * normSpeed
        val a = smoothingAlpha(te, cutoff)
        val xHat = a * value + (1f - a) * xPrev
        xPrev = xHat
        dxPrev = dxHat
        return xHat
    }

    fun reset() {
        hasPrev = false
        xPrev = 0f
        dxPrev = 0f
    }

    private fun smoothingAlpha(te: Float, cutoff: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff.coerceAtLeast(0.05f))
        return 1f / (1f + tau / te)
    }
}

internal class OneEuroFilter3D(
    minCutoff: Float = 1.6f,
    beta: Float = 0.045f,
    dCutoff: Float = 1.0f
) {
    private val fx = OneEuroFilter1D(minCutoff, beta, dCutoff)
    private val fy = OneEuroFilter1D(minCutoff, beta, dCutoff)
    private val fz = OneEuroFilter1D(minCutoff * 0.85f, beta * 0.85f, dCutoff)

    fun filter(p: Point3D, dtSec: Float, bodyScale: Float): Point3D {
        // Scale speed sensitivity by normalized body scale (in px) * 100 so beta behaves identically near & far
        val normDivisor = (bodyScale * 0.01f).coerceAtLeast(0.5f)
        return Point3D(
            x = fx.filter(p.x, dtSec, normDivisor),
            y = fy.filter(p.y, dtSec, normDivisor),
            z = fz.filter(p.z, dtSec, normDivisor)
        )
    }
}

data class PoseSkeletonSnapshot(
    val rawPoints: Map<Int, PointF> = emptyMap(),
    val imageWidth: Float = 480f,
    val imageHeight: Float = 640f,
    val isMirrored: Boolean = true,
    val isBodyVisible: Boolean = false,
    val isMoving: Boolean = false,
    val motionIntensity: Float = 0f,
    val stepDelta: Int = 0,
    val repDelta: Int = 0,
    val repPhaseProgress: Float = 0f,
    val repPhaseLabel: String = "",
    val isPlankHoldingValid: Boolean = false,
    val visibleJointCount: Int = 0,
    val confidencePercent: Int = 0,
    val symmetryPercent: Int = 100,
    val formScorePercent: Int = 0,
    val symmetryHint: String = "",
    val fatigueWarning: Boolean = false,
    val fatigueHint: String = "",
    val isSubjectLocked: Boolean = false,
    val isForeignPersonBlocked: Boolean = false,
    val lockCenterX: Float = 0f,
    val lockCenterY: Float = 0f,
    val lockRadiusX: Float = 0f,
    val lockRadiusY: Float = 0f,
    val detectedExerciseLabel: String = "",
    val postureHint: String = "หันกล้องให้เห็นร่างกายอย่างน้อยครึ่งตัว"
)

data class StretchFormFeedback(
    val snapshot: PoseSkeletonSnapshot = PoseSkeletonSnapshot(),
    val isPostureCorrect: Boolean = false,
    val formScore: Int = 0,
    val feedbackText: String = "จัดตัวให้อยู่ในกรอบกล้องเพื่อตรวจท่ายืด"
)

internal data class RoiCropRequest(
    val left: Int,
    val top: Int,
    val bitmap: Bitmap
)

/**
 * Shared biomechanical Subject Anchor Lock + ROI anti-hijack helper.
 * Prevents passersby or moving objects crossing in front of the camera from
 * stealing ML Kit single-pose focus away from the active athlete.
 */
internal class SubjectAnchorTracker {
    var isLocked: Boolean = false
        private set
    var anchorCenterX: Float = 0f
        private set
    var anchorCenterY: Float = 0f
        private set
    var anchorScale: Float = 0f
        private set
    var roiFocusFramesRemaining: Int = 0
        private set

    private var candidateStreak: Int = 0
    private var lastCandidateX: Float = 0f
    private var lastCandidateY: Float = 0f
    private var lastCandidateScale: Float = 0f
    private var lastValidSubjectSeenMs: Long = 0L
    private var lastForeignBlockedMs: Long = 0L

    private var preferredNormX: Float? = null
    private var preferredNormY: Float? = null

    fun lockAtScreenTap(tapX: Float, tapY: Float, viewW: Float, viewH: Float, isMirrored: Boolean, imgW: Float, imgH: Float) {
        if (viewW <= 0f || viewH <= 0f || imgW <= 0f || imgH <= 0f) return
        val scale = max(viewW / imgW, viewH / imgH)
        val offsetX = (viewW - imgW * scale) / 2f
        val offsetY = (viewH - imgH * scale) / 2f
        val viewImgX = ((tapX - offsetX) / scale).coerceIn(0f, imgW)
        val unmirroredX = if (isMirrored) (imgW - viewImgX) else viewImgX
        val unmirroredY = ((tapY - offsetY) / scale).coerceIn(0f, imgH)

        preferredNormX = (unmirroredX / imgW).coerceIn(0.12f, 0.88f)
        preferredNormY = (unmirroredY / imgH).coerceIn(0.12f, 0.88f)
        // Reset current lock and force ROI cropping immediately around user's tap point
        isLocked = false
        candidateStreak = 0
        anchorCenterX = unmirroredX
        anchorCenterY = unmirroredY
        if (anchorScale <= 24f) {
            anchorScale = (imgH * 0.24f).coerceAtLeast(70f)
        }
        roiFocusFramesRemaining = 28
    }

    fun clearLock() {
        isLocked = false
        candidateStreak = 0
        roiFocusFramesRemaining = 0
        preferredNormX = null
        preferredNormY = null
        lastValidSubjectSeenMs = 0L
        lastForeignBlockedMs = 0L
    }

    fun shouldUseRoiCrop(): Boolean {
        return (isLocked || preferredNormX != null) && roiFocusFramesRemaining > 0
    }

    fun computeRoiRect(imgW: Float, imgH: Float): FloatArray? {
        val cx = when {
            isLocked -> anchorCenterX
            preferredNormX != null -> preferredNormX!! * imgW
            else -> return null
        }
        val cy = when {
            isLocked -> anchorCenterY
            preferredNormY != null -> preferredNormY!! * imgH
            else -> return null
        }
        val refScale = if (anchorScale > 24f) anchorScale else (imgH * 0.25f)
        val halfW = (refScale * 1.48f).coerceIn(imgW * 0.24f, imgW * 0.45f)
        val halfH = (refScale * 2.10f).coerceIn(imgH * 0.35f, imgH * 0.49f)
        val left = (cx - halfW).coerceIn(0f, (imgW - halfW * 2f).coerceAtLeast(0f))
        val top = (cy - halfH).coerceIn(0f, (imgH - halfH * 2f).coerceAtLeast(0f))
        val right = (left + halfW * 2f).coerceAtMost(imgW)
        val bottom = (top + halfH * 2f).coerceAtMost(imgH)
        return floatArrayOf(left, top, right, bottom)
    }

    fun consumeRoiFrameTick() {
        if (roiFocusFramesRemaining > 0) {
            roiFocusFramesRemaining--
        }
    }

    /**
     * Validates whether the incoming skeleton belongs to the locked user.
     * Returns true if accepted, false if it looks like a passerby/hijack.
     */
    fun validateAndTrack(
        centerX: Float,
        centerY: Float,
        bodyScale: Float,
        imgW: Float,
        imgH: Float,
        now: Long
    ): Boolean {
        // User tapped a preferred spot while acquiring lock
        val prefX = preferredNormX
        val prefY = preferredNormY
        if (!isLocked && prefX != null && prefY != null) {
            val tapPxX = prefX * imgW
            val tapPxY = prefY * imgH
            val distToTap = hypot(centerX - tapPxX, centerY - tapPxY)
            if (distToTap > max(bodyScale * 1.65f, imgW * 0.36f)) {
                lastForeignBlockedMs = now
                roiFocusFramesRemaining = max(roiFocusFramesRemaining, 16)
                return false
            }
        }

        if (!isLocked) {
            if (candidateStreak == 0) {
                lastCandidateX = centerX
                lastCandidateY = centerY
                lastCandidateScale = bodyScale
                candidateStreak = 1
            } else {
                val jump = hypot(centerX - lastCandidateX, centerY - lastCandidateY)
                val scaleRatio = bodyScale / lastCandidateScale.coerceAtLeast(24f)
                if (jump < bodyScale * 0.60f && scaleRatio in 0.65f..1.55f) {
                    lastCandidateX = lastCandidateX * 0.6f + centerX * 0.4f
                    lastCandidateY = lastCandidateY * 0.6f + centerY * 0.4f
                    lastCandidateScale = lastCandidateScale * 0.6f + bodyScale * 0.4f
                    candidateStreak++
                } else {
                    lastCandidateX = centerX
                    lastCandidateY = centerY
                    lastCandidateScale = bodyScale
                    candidateStreak = 1
                }
            }

            if (candidateStreak >= 5 || (prefX != null && candidateStreak >= 2)) {
                isLocked = true
                anchorCenterX = lastCandidateX
                anchorCenterY = lastCandidateY
                anchorScale = lastCandidateScale
                lastValidSubjectSeenMs = now
                preferredNormX = null
                preferredNormY = null
            }
            return true
        }

        // Already locked: check continuity against locked anchor!
        val distFromAnchor = hypot(centerX - anchorCenterX, centerY - anchorCenterY)
        val scaleRatio = bodyScale / anchorScale.coerceAtLeast(24f)
        val dxFromAnchor = abs(centerX - anchorCenterX)
        val dyFromAnchor = abs(centerY - anchorCenterY)

        // Allow vertical posture transitions (standing -> squat / floor push-up / sit-up) in the same horizontal lane
        val sameHorizontalLane = dxFromAnchor <= max(anchorScale * 1.15f, imgW * 0.18f)
        val maxHorizontalDrift = max(anchorScale * 1.45f, imgW * 0.24f)
        val maxVerticalDrift = if (sameHorizontalLane) {
            max(anchorScale * 2.75f, imgH * 0.45f)
        } else {
            max(anchorScale * 1.65f, imgH * 0.24f)
        }
        val minScaleAllowed = if (sameHorizontalLane) 0.34f else 0.56f
        val maxScaleAllowed = 1.68f

        val suddenTeleport = dxFromAnchor > maxHorizontalDrift ||
            dyFromAnchor > maxVerticalDrift ||
            (!sameHorizontalLane && distFromAnchor > max(anchorScale * 1.65f, imgW * 0.24f)) ||
            scaleRatio !in minScaleAllowed..maxScaleAllowed

        if (suddenTeleport) {
            // Foreign person or large object hijacked ML Kit!
            // If locked athlete has been gone for > 2.4s continuously, allow re-acquiring cleanly.
            if (lastValidSubjectSeenMs > 0L && (now - lastValidSubjectSeenMs) > 2400L) {
                isLocked = false
                candidateStreak = 1
                lastCandidateX = centerX
                lastCandidateY = centerY
                lastCandidateScale = bodyScale
                roiFocusFramesRemaining = 0
                return true
            }
            lastForeignBlockedMs = now
            // Engage active ROI crop around the locked athlete for the next ~20 frames
            roiFocusFramesRemaining = 20
            return false
        }

        // Valid locked user frame: track vertical posture changes slightly faster than horizontal drift
        anchorCenterX = anchorCenterX * 0.86f + centerX * 0.14f
        anchorCenterY = anchorCenterY * 0.80f + centerY * 0.20f
        anchorScale = anchorScale * 0.90f + bodyScale * 0.10f
        lastValidSubjectSeenMs = now
        return true
    }

    fun isRecentlyBlockingForeign(now: Long): Boolean {
        return lastForeignBlockedMs > 0L && (now - lastForeignBlockedMs) <= 1400L
    }

    fun lockRadiusX(imgW: Float): Float = (anchorScale * 1.25f).coerceIn(imgW * 0.18f, imgW * 0.42f)
    fun lockRadiusY(imgH: Float): Float = (anchorScale * 1.80f).coerceIn(imgH * 0.28f, imgH * 0.47f)
}

internal fun extractUprightRoiCrop(
    imageProxy: ImageProxy,
    rotation: Int,
    roiRect: FloatArray
): RoiCropRequest? {
    return runCatching {
        val rawBmp = imageProxy.toBitmap()
        val uprightBmp = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, false).also {
                if (it !== rawBmp) rawBmp.recycle()
            }
        } else {
            rawBmp
        }

        val maxW = uprightBmp.width
        val maxH = uprightBmp.height
        if (maxW < 100 || maxH < 100) {
            uprightBmp.recycle()
            return null
        }

        val left = roiRect[0].roundToInt().coerceIn(0, maxW - 96)
        val top = roiRect[1].roundToInt().coerceIn(0, maxH - 96)
        val right = roiRect[2].roundToInt().coerceIn(left + 96, maxW)
        val bottom = roiRect[3].roundToInt().coerceIn(top + 96, maxH)
        val cropW = (right - left).coerceAtLeast(96)
        val cropH = (bottom - top).coerceAtLeast(96)

        val cropped = Bitmap.createBitmap(uprightBmp, left, top, cropW, cropH)
        if (cropped !== uprightBmp) uprightBmp.recycle()
        RoiCropRequest(left = left, top = top, bitmap = cropped)
    }.getOrNull()
}

internal data class RepEvalOutput(
    val stepDelta: Int = 0,
    val repDelta: Int = 0,
    val repPhaseProgress: Float = 0f,
    val repPhaseLabel: String = "",
    val postureHint: String = "",
    val exerciseTag: String = "",
    val repAmplitude: Float = 0f,
    val isHorizontalPlank: Boolean = false,
    val isPlankHoldingValid: Boolean = false
)

/**
 * Pure biomechanical finite-state rep & gait engine.
 * Designed with strict joint-angle, multi-segment lift, and plank-orientation gates
 * so hand-waving cannot trigger jumps/jacks and both side-view and front-view push-ups count accurately.
 */
internal class BiomechanicalRepEngine {
    // Jump state
    private var baselineHipY: Float? = null
    private var baselineShoulderY: Float? = null
    private var baselineLegY: Float? = null
    private var stableJumpScale: Float? = null
    private var prevHipY: Float? = null
    private var jumpState: Int = 0
    private var jumpTakeoffMillis: Long = 0L

    // Push-up state
    private var pushupArmed: Boolean = false
    private var pushupState: Int = 0
    private var pushupTopClearance: Float? = null
    private var pushupPeakDepth: Float = 0f

    // Squat state
    private var squatArmed: Boolean = false
    private var squatState: Int = 0
    private var squatTopHipY: Float? = null
    private var standingKneeHipRatio: Float? = null

    // Lunge state
    private var lungeArmed: Boolean = false
    private var lungeState: Int = 0
    private var lungeTopHipY: Float? = null

    // Sit-up state
    private var situpArmed: Boolean = false
    private var situpState: Int = 0

    // Jumping Jack state
    private var jackState: Int = 0
    private var baselineLegSpread: Float? = null

    // Gait / Swing / Cycling state
    private var prevGaitSign: Int = 0
    private var lastStepMillis: Long = 0L
    private var prevLeftLegY: Float? = null
    private var prevRightLegY: Float? = null
    private var swingState: Int = 0
    private var pedalState: Int = 0

    // Sticky AUTO posture hysteresis (prevents flickering mid-rep between exercises)
    private var activeAutoTag: String = ""
    private var pendingAutoTag: String = ""
    private var pendingAutoStreak: Int = 0

    var lastRepMillis: Long = 0L
        private set
    var currentRepPeakAmplitude: Float = 0f
        private set
    val stepIntervalHistory = mutableListOf<Long>()

    fun reset() {
        baselineHipY = null
        baselineShoulderY = null
        baselineLegY = null
        stableJumpScale = null
        prevHipY = null
        jumpState = 0
        jumpTakeoffMillis = 0L
        pushupArmed = false
        pushupState = 0
        pushupTopClearance = null
        pushupPeakDepth = 0f
        squatArmed = false
        squatState = 0
        squatTopHipY = null
        standingKneeHipRatio = null
        lungeArmed = false
        lungeState = 0
        lungeTopHipY = null
        situpArmed = false
        situpState = 0
        jackState = 0
        baselineLegSpread = null
        prevGaitSign = 0
        lastStepMillis = 0L
        prevLeftLegY = null
        prevRightLegY = null
        swingState = 0
        pedalState = 0
        activeAutoTag = ""
        pendingAutoTag = ""
        pendingAutoStreak = 0
        lastRepMillis = 0L
        currentRepPeakAmplitude = 0f
        stepIntervalHistory.clear()
    }

    fun resetBodyLost() {
        baselineHipY = null
        baselineShoulderY = null
        baselineLegY = null
        prevHipY = null
        jumpState = 0
        pushupState = 0
        squatState = 0
        lungeState = 0
        situpState = 0
        jackState = 0
        swingState = 0
        pedalState = 0
        activeAutoTag = ""
        pendingAutoTag = ""
        pendingAutoStreak = 0
    }

    fun evaluate(
        sport: SportType,
        focus: GeneralExerciseFocus,
        points: Map<Int, PointF>,
        bodyScale: Float,
        isWholeBodyMoving: Boolean,
        sensitivity: DetectionSensitivity,
        now: Long,
        points3D: Map<Int, Point3D> = emptyMap(),
        confidences: Map<Int, Float> = emptyMap()
    ): RepEvalOutput {
        val plankDetected = isPushUpPlankPosture(points, bodyScale, strict = (focus != GeneralExerciseFocus.PUSHUP))
        return when (sport) {
            SportType.JUMP_ROPE -> {
                val (rep, hint, progress) = detectJumpRopeDetailed(points, bodyScale, sensitivity, now)
                RepEvalOutput(
                    repDelta = rep,
                    repPhaseProgress = progress,
                    repPhaseLabel = if (jumpState == 1) "ลอยตัว!" else if (progress > 0.3f) "กำลังดีดตัว" else "",
                    postureHint = hint,
                    exerciseTag = "กระโดดเชือก",
                    repAmplitude = currentRepPeakAmplitude
                )
            }

            SportType.RUNNING, SportType.FOOTBALL -> {
                val steps = detectAlternatingSteps(
                    points = points,
                    bodyScale = bodyScale,
                    isMoving = isWholeBodyMoving,
                    minNormDiff = 0.088f * sensitivity.motionDeadzoneScale,
                    minIntervalMs = 200L,
                    now = now
                )
                val hint = if (isWholeBodyMoving) "${sport.thaiName}: จับจังหวะก้าววิ่งจริง" else "${sport.thaiName}: พร้อมจับก้าววิ่ง"
                RepEvalOutput(stepDelta = steps, postureHint = hint, exerciseTag = sport.thaiName)
            }

            SportType.WALKING -> {
                val steps = detectAlternatingSteps(
                    points = points,
                    bodyScale = bodyScale,
                    isMoving = isWholeBodyMoving,
                    minNormDiff = 0.068f * sensitivity.motionDeadzoneScale,
                    minIntervalMs = 340L,
                    now = now
                )
                val hint = if (isWholeBodyMoving) "เดิน: กำลังนับก้าวตามจังหวะเดิน" else "เดิน: พร้อมนับก้าวจริง"
                RepEvalOutput(stepDelta = steps, postureHint = hint, exerciseTag = "เดิน")
            }

            SportType.BASKETBALL -> {
                val steps = detectAlternatingSteps(
                    points = points,
                    bodyScale = bodyScale,
                    isMoving = isWholeBodyMoving,
                    minNormDiff = 0.085f * sensitivity.motionDeadzoneScale,
                    minIntervalMs = 220L,
                    now = now
                )
                val (jumpRep, _, progress) = detectJumpRopeDetailed(points, bodyScale, sensitivity, now)
                val hint = if (isWholeBodyMoving) "บาสเกตบอล: จับฟุตเวิร์ก & จังหวะกระโดด" else "บาสเกตบอล: พร้อมจับท่าทาง"
                RepEvalOutput(stepDelta = steps, repDelta = jumpRep, repPhaseProgress = progress, postureHint = hint, exerciseTag = "บาสเกตบอล")
            }

            SportType.BADMINTON -> {
                val steps = detectAlternatingSteps(
                    points = points,
                    bodyScale = bodyScale,
                    isMoving = isWholeBodyMoving,
                    minNormDiff = 0.075f * sensitivity.motionDeadzoneScale,
                    minIntervalMs = 220L,
                    now = now
                )
                val (swing, swingProgress) = detectRacketSwingDetailed(points, bodyScale, sensitivity, now)
                val hint = if (isWholeBodyMoving) "แบดมินตัน: จับฟุตเวิร์ก & วงสวิงไม้" else "แบดมินตัน: พร้อมจับสวิงและก้าว"
                RepEvalOutput(stepDelta = steps, repDelta = swing, repPhaseProgress = swingProgress, postureHint = hint, exerciseTag = "แบดมินตัน")
            }

            SportType.CYCLING -> {
                val (pedal, pedalProgress) = detectCyclingPedalDetailed(points, bodyScale, isWholeBodyMoving, now)
                val hint = if (isWholeBodyMoving) "ปั่นจักรยาน: จับรอบปั่นเข่าจริง" else "ปั่นจักรยาน: หันข้างเข้ากล้องเพื่อจับรอบขา"
                RepEvalOutput(repDelta = pedal, repPhaseProgress = pedalProgress, postureHint = hint, exerciseTag = "ปั่นจักรยาน")
            }

            SportType.GENERAL -> evaluateGeneral(focus, points, bodyScale, sensitivity, plankDetected, now, points3D, confidences)
        }
    }

    private fun evaluateGeneral(
        focus: GeneralExerciseFocus,
        points: Map<Int, PointF>,
        bodyScale: Float,
        sensitivity: DetectionSensitivity,
        plankDetected: Boolean,
        now: Long,
        points3D: Map<Int, Point3D>,
        confidences: Map<Int, Float>
    ): RepEvalOutput {
        return when (focus) {
            GeneralExerciseFocus.PUSHUP -> {
                val res = detectPushUpDetailed(points, bodyScale, sensitivity, strictPosture = false, now = now, points3D = points3D, confidences = confidences)
                res.copy(isHorizontalPlank = true)
            }

            GeneralExerciseFocus.SQUAT -> detectSquatDetailed(points, bodyScale, sensitivity, now, points3D, confidences)

            GeneralExerciseFocus.LUNGE -> detectLungeDetailed(points, bodyScale, sensitivity, now, points3D, confidences)

            GeneralExerciseFocus.SITUP -> detectSitUpDetailed(points, bodyScale, sensitivity, now, points3D, confidences)

            GeneralExerciseFocus.PLANK -> {
                val (holdingValid, hint) = detectPlankHold(points, bodyScale)
                RepEvalOutput(
                    repDelta = 0,
                    repPhaseProgress = if (holdingValid) 1f else 0f,
                    repPhaseLabel = if (holdingValid) "ค้างท่าตรง" else "จัดแกนตัว",
                    postureHint = hint,
                    exerciseTag = "แพลงก์",
                    isHorizontalPlank = true,
                    isPlankHoldingValid = holdingValid
                )
            }

            GeneralExerciseFocus.JUMPING_JACK -> detectJumpingJackDetailed(points, bodyScale, sensitivity, now)

            GeneralExerciseFocus.AUTO -> evaluateAutoWithHysteresis(
                points = points,
                bodyScale = bodyScale,
                sensitivity = sensitivity,
                plankDetected = plankDetected,
                now = now,
                points3D = points3D,
                confidences = confidences
            )
        }
    }

    private fun evaluateAutoWithHysteresis(
        points: Map<Int, PointF>,
        bodyScale: Float,
        sensitivity: DetectionSensitivity,
        plankDetected: Boolean,
        now: Long,
        points3D: Map<Int, Point3D>,
        confidences: Map<Int, Float>
    ): RepEvalOutput {
        // Always finish any rep currently in progress before considering posture switches!
        when {
            pushupState == 1 -> {
                activeAutoTag = "วิดพื้น"
                pendingAutoTag = ""
                pendingAutoStreak = 0
                return detectPushUpDetailed(points, bodyScale, sensitivity, strictPosture = true, now = now, points3D = points3D, confidences = confidences).copy(isHorizontalPlank = true)
            }
            situpState == 1 -> {
                activeAutoTag = "ซิทอัพ"
                pendingAutoTag = ""
                pendingAutoStreak = 0
                return detectSitUpDetailed(points, bodyScale, sensitivity, now, points3D, confidences)
            }
            lungeState == 1 -> {
                activeAutoTag = "ลันจ์"
                pendingAutoTag = ""
                pendingAutoStreak = 0
                return detectLungeDetailed(points, bodyScale, sensitivity, now, points3D, confidences)
            }
            squatState == 1 -> {
                activeAutoTag = "สควอท"
                pendingAutoTag = ""
                pendingAutoStreak = 0
                return detectSquatDetailed(points, bodyScale, sensitivity, now, points3D, confidences)
            }
            jackState == 1 -> {
                activeAutoTag = "กระโดดตบ"
                pendingAutoTag = ""
                pendingAutoStreak = 0
                return detectJumpingJackDetailed(points, bodyScale, sensitivity, now)
            }
        }

        // Stateless posture classification so inactive state machines are never mutated prematurely
        val candidateTag = classifyAutoPosture(points, bodyScale, plankDetected, points3D)

        if (activeAutoTag.isBlank()) {
            activeAutoTag = candidateTag
            pendingAutoTag = ""
            pendingAutoStreak = 0
        } else if (candidateTag.isNotBlank() && candidateTag != activeAutoTag) {
            if (pendingAutoTag == candidateTag) {
                pendingAutoStreak++
            } else {
                pendingAutoTag = candidateTag
                pendingAutoStreak = 1
            }
            if (pendingAutoStreak >= 3) {
                activeAutoTag = candidateTag
                pendingAutoTag = ""
                pendingAutoStreak = 0
                clearMidRepFlags()
            }
        } else {
            pendingAutoTag = ""
            pendingAutoStreak = 0
        }

        return when (activeAutoTag) {
            "วิดพื้น" -> detectPushUpDetailed(points, bodyScale, sensitivity, strictPosture = true, now = now, points3D = points3D, confidences = confidences).copy(isHorizontalPlank = true)
            "ซิทอัพ" -> detectSitUpDetailed(points, bodyScale, sensitivity, now, points3D, confidences)
            "ลันจ์" -> detectLungeDetailed(points, bodyScale, sensitivity, now, points3D, confidences)
            "สควอท" -> detectSquatDetailed(points, bodyScale, sensitivity, now, points3D, confidences)
            "กระโดดตบ" -> detectJumpingJackDetailed(points, bodyScale, sensitivity, now)
            else -> RepEvalOutput(
                repDelta = 0,
                postureHint = "ทั่วไป AI: พร้อมจับ วิดพื้น / สควอท / ลันจ์ / ซิทอัพ / กระโดดตบ",
                exerciseTag = "บอดี้เวท"
            )
        }
    }

    private fun clearMidRepFlags() {
        pushupState = 0
        squatState = 0
        lungeState = 0
        situpState = 0
        jackState = 0
    }

    private fun classifyAutoPosture(
        points: Map<Int, PointF>,
        bodyScale: Float,
        plankDetected: Boolean,
        points3D: Map<Int, Point3D>
    ): String {
        if (plankDetected) return "วิดพื้น"

        val shoulderMid = mid(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER]) ?: return ""
        val hipMid = mid(points[PoseLandmark.LEFT_HIP], points[PoseLandmark.RIGHT_HIP]) ?: return ""
        val scale = bodyScale.coerceAtLeast(36f)

        val ankleMid = mid(points[PoseLandmark.LEFT_ANKLE], points[PoseLandmark.RIGHT_ANKLE])
        val kneeMid = mid(points[PoseLandmark.LEFT_KNEE], points[PoseLandmark.RIGHT_KNEE])
        val isFloorSeated = kneeMid != null && (
            (ankleMid != null && (ankleMid.y - hipMid.y) <= 0.44f * scale) ||
                (ankleMid == null && abs(kneeMid.x - hipMid.x) >= 0.40f * scale)
            )
        if (isFloorSeated) return "ซิทอัพ"

        // Jumping jack: both wrists clearly lifted above shoulders while upright
        val lWrist = points[PoseLandmark.LEFT_WRIST]
        val rWrist = points[PoseLandmark.RIGHT_WRIST]
        val lElbow = points[PoseLandmark.LEFT_ELBOW]
        val rElbow = points[PoseLandmark.RIGHT_ELBOW]
        val handsOverhead = lWrist != null && rWrist != null &&
            (lWrist.y < shoulderMid.y - 0.14f * scale || (lElbow != null && lElbow.y < shoulderMid.y - 0.12f * scale)) &&
            (rWrist.y < shoulderMid.y - 0.14f * scale || (rElbow != null && rElbow.y < shoulderMid.y - 0.12f * scale))
        if (handsOverhead && hipMid.y > shoulderMid.y + 0.25f * scale) {
            return "กระโดดตบ"
        }

        if (hipMid.y > shoulderMid.y + 0.25f * scale && kneeMid != null) {
            if (isAsymmetricSplitLunge(points, scale, points3D)) {
                return "ลันจ์"
            }
            return "สควอท"
        }
        return ""
    }

    private fun isAsymmetricSplitLunge(
        points: Map<Int, PointF>,
        scale: Float,
        points3D: Map<Int, Point3D>
    ): Boolean {
        val lKnee = points[PoseLandmark.LEFT_KNEE] ?: return false
        val rKnee = points[PoseLandmark.RIGHT_KNEE] ?: return false
        val lAnkle = points[PoseLandmark.LEFT_ANKLE] ?: return false
        val rAnkle = points[PoseLandmark.RIGHT_ANKLE] ?: return false
        val ankleSplit = max(abs(lAnkle.x - rAnkle.x), abs(lAnkle.y - rAnkle.y)) / scale
        if (ankleSplit < 0.52f) return false
        val ankleHeightDiff = abs(lAnkle.y - rAnkle.y) / scale
        val kneeHeightDiff = abs(lKnee.y - rKnee.y) / scale
        val lDeg = tripletJointAngleDeg(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE, points, points3D)
        val rDeg = tripletJointAngleDeg(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, points, points3D)
        val kneeAsym = if (lDeg != null && rDeg != null) abs(lDeg - rDeg) >= 16f else false
        return ankleHeightDiff >= 0.10f || kneeHeightDiff >= 0.10f || kneeAsym || ankleSplit >= 0.78f
    }

    /**
     * Strict Jump Rope detector:
     * Uses pixel-delta against stable baseline (immune to bodyScale jitter),
     * requires visible hips + shoulders + legs, and requires multi-segment upward lift
     * so waving arms while standing/sitting NEVER counts.
     */
    private fun detectJumpRopeDetailed(
        points: Map<Int, PointF>,
        bodyScale: Float,
        sensitivity: DetectionSensitivity,
        now: Long
    ): Triple<Int, String, Float> {
        val shoulderMid = mid(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER])
        val hipMid = mid(points[PoseLandmark.LEFT_HIP], points[PoseLandmark.RIGHT_HIP])
        val lLeg = points[PoseLandmark.LEFT_ANKLE] ?: points[PoseLandmark.LEFT_KNEE]
        val rLeg = points[PoseLandmark.RIGHT_ANKLE] ?: points[PoseLandmark.RIGHT_KNEE]
        val legMid = mid(lLeg, rLeg)

        if (shoulderMid == null || hipMid == null || legMid == null) {
            jumpState = 0
            return Triple(0, "กระโดดเชือก: ถอยให้เห็นสะโพกและขาเพื่อกันนับมือหลอก", 0f)
        }

        // Must be standing upright (knees/ankles clearly below hips), not sitting folded or horizontal
        val legDropRatio = (legMid.y - hipMid.y) / bodyScale.coerceAtLeast(30f)
        if (legDropRatio < 0.35f) {
            jumpState = 0
            return Triple(0, "กระโดดเชือก: ยืนตรงให้เห็นช่วงขาชัดเจน", 0f)
        }

        val curScale = stableJumpScale ?: bodyScale
        if (jumpState == 0) {
            stableJumpScale = curScale * 0.90f + bodyScale * 0.10f
        }
        val scale = (stableJumpScale ?: bodyScale).coerceAtLeast(36f)

        // Update steady baseline ONLY while grounded and hips are calm (prevents slow lean/drift false triggers)
        val prevHip = prevHipY
        prevHipY = hipMid.y
        val hipFrameDeltaNorm = if (prevHip != null) abs(hipMid.y - prevHip) / scale else 0f

        if (baselineHipY == null || baselineShoulderY == null || baselineLegY == null) {
            baselineHipY = hipMid.y
            baselineShoulderY = shoulderMid.y
            baselineLegY = legMid.y
        } else if (jumpState == 0 && hipFrameDeltaNorm < 0.042f) {
            baselineHipY = baselineHipY!! * 0.88f + hipMid.y * 0.12f
            baselineShoulderY = baselineShoulderY!! * 0.88f + shoulderMid.y * 0.12f
            baselineLegY = baselineLegY!! * 0.88f + legMid.y * 0.12f
        }

        // True pixel lift relative to baseline (y decreases as body moves upward)
        val hipLiftNorm = (baselineHipY!! - hipMid.y) / scale
        val shoulderLiftNorm = (baselineShoulderY!! - shoulderMid.y) / scale
        val legLiftNorm = (baselineLegY!! - legMid.y) / scale

        if (jumpState == 1) {
            currentRepPeakAmplitude = max(currentRepPeakAmplitude, hipLiftNorm)
            // Timeout stuck airborne state (> 1.2s)
            if ((now - jumpTakeoffMillis) > 1200L) {
                jumpState = 0
                baselineHipY = hipMid.y
                baselineShoulderY = shoulderMid.y
                baselineLegY = legMid.y
                return Triple(0, "กระโดดเชือก: ปรับระดับพื้นใหม่ พร้อมจับจังหวะ", 0f)
            }
        }

        val sens = sensitivity.motionDeadzoneScale
        val hipTakeoffThresh = 0.045f * sens
        val shoulderTakeoffThresh = 0.025f * sens
        val legTakeoffThresh = 0.013f * sens
        val landThresh = 0.018f * sens

        val wholeBodyLifted = hipLiftNorm > hipTakeoffThresh &&
            shoulderLiftNorm > shoulderTakeoffThresh &&
            (legLiftNorm > legTakeoffThresh || hipLiftNorm > hipTakeoffThresh * 1.45f)

        var rep = 0
        // Do NOT gate takeoff on lastRepMillis (fast jump rope bounces every ~300ms would skip every 2nd jump!)
        if (jumpState == 0 && wholeBodyLifted) {
            jumpState = 1
            jumpTakeoffMillis = now
            currentRepPeakAmplitude = hipLiftNorm
        } else if (jumpState == 1 && hipLiftNorm < landThresh && (now - lastRepMillis) >= 180L) {
            jumpState = 0
            rep = 1
            lastRepMillis = now
        }

        val progress = if (jumpState == 1) 1f else (hipLiftNorm / hipTakeoffThresh).coerceIn(0f, 0.99f)
        val stateText = if (jumpState == 1) {
            "กระโดดเชือก: ลอยตัวทั้งตัวจริง!"
        } else {
            "กระโดดเชือก: จับการลอยตัวจริง (ขยับเฉพาะมือไม่นับ)"
        }
        return Triple(rep, stateText, progress)
    }

    /**
     * Detects plank/push-up body orientation in either Side/Diagonal view OR Front floor-camera view.
     */
    internal fun isPushUpPlankPosture(
        points: Map<Int, PointF>,
        bodyScale: Float,
        strict: Boolean
    ): Boolean {
        val shoulderMid = mid(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER]) ?: return false
        val hipMid = mid(points[PoseLandmark.LEFT_HIP], points[PoseLandmark.RIGHT_HIP]) ?: return false
        val wristMid = mid(points[PoseLandmark.LEFT_WRIST], points[PoseLandmark.RIGHT_WRIST]) ?: return false
        val elbowMid = mid(points[PoseLandmark.LEFT_ELBOW], points[PoseLandmark.RIGHT_ELBOW]) ?: return false

        // Hands must be below shoulders supporting weight on floor (y increases downward)
        val minWristBelowShoulder = if (strict) 0.12f * bodyScale else 0.06f * bodyScale
        if (wristMid.y <= shoulderMid.y + minWristBelowShoulder) return false
        if (elbowMid.y <= shoulderMid.y - 0.15f * bodyScale) return false

        val torsoDx = abs(shoulderMid.x - hipMid.x)
        val torsoDy = abs(shoulderMid.y - hipMid.y)

        // Side or 45-deg diagonal plank: torso is inclined/horizontal
        val sideRatio = if (strict) 0.68f else 0.48f
        val isSideOrDiagPlank = torsoDx >= torsoDy * sideRatio && torsoDx > bodyScale * 0.25f

        // Front floor-camera plank: hips are behind shoulders within vertical band (NOT hanging down below like standing/sitting)
        val shoulderWidth = points[PoseLandmark.LEFT_SHOULDER]?.let { ls ->
            points[PoseLandmark.RIGHT_SHOULDER]?.let { rs -> hypot(ls.x - rs.x, ls.y - rs.y) }
        } ?: (bodyScale * 0.65f)
        val maxFrontTorsoDrop = if (strict) 0.58f * bodyScale else 0.72f * bodyScale
        val wristSpread = points[PoseLandmark.LEFT_WRIST]?.let { lw ->
            points[PoseLandmark.RIGHT_WRIST]?.let { rw -> abs(lw.x - rw.x) }
        } ?: shoulderWidth
        val isFrontFloorPlank = torsoDy <= maxFrontTorsoDrop &&
            wristSpread >= shoulderWidth * 0.62f &&
            (hipMid.y - shoulderMid.y) <= maxFrontTorsoDrop &&
            wristMid.y >= hipMid.y - 0.08f * bodyScale

        // Guard against upright standing: if ankles are directly vertically beneath hips by full leg length, NOT a plank
        val ankleMid = mid(points[PoseLandmark.LEFT_ANKLE], points[PoseLandmark.RIGHT_ANKLE])
        if (ankleMid != null) {
            val legVerticalDrop = (ankleMid.y - hipMid.y) / bodyScale.coerceAtLeast(30f)
            if (legVerticalDrop > 1.05f && !isFrontFloorPlank) return false
        }

        return isSideOrDiagPlank || isFrontFloorPlank
    }

    private fun detectPushUpDetailed(
        points: Map<Int, PointF>,
        bodyScale: Float,
        sensitivity: DetectionSensitivity,
        strictPosture: Boolean,
        now: Long,
        points3D: Map<Int, Point3D>,
        confidences: Map<Int, Float>
    ): RepEvalOutput {
        val shoulderMid = mid(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER])
        val wristMid = mid(points[PoseLandmark.LEFT_WRIST], points[PoseLandmark.RIGHT_WRIST])
        val elbowAngle = measureElbowAngleDeg(points, points3D, confidences)

        if (shoulderMid == null || wristMid == null || elbowAngle == null) {
            return RepEvalOutput(
                postureHint = "วิดพื้น: จัดกล้องให้เห็นไหล่ ศอก และข้อมือชัดเจน",
                exerciseTag = "วิดพื้น"
            )
        }

        val inPlank = isPushUpPlankPosture(points, bodyScale, strict = strictPosture)
        if (!inPlank && pushupState == 0) {
            pushupArmed = false
            return RepEvalOutput(
                postureHint = "วิดพื้น: เตรียมท่าวิดพื้นยันมือกับพื้น (หันข้างหรือหันหน้าเข้ากล้อง)",
                exerciseTag = "วิดพื้น"
            )
        }

        val sens = sensitivity.motionDeadzoneScale
        val topAngleThresh = when {
            sens <= 0.85f -> 126f
            sens >= 1.15f -> 140f
            else -> 134f
        }
        val bottomAngleThresh = when {
            sens <= 0.85f -> 120f
            sens >= 1.15f -> 100f
            else -> 112f
        }

        val shoulderClearance = ((wristMid.y - shoulderMid.y) / bodyScale.coerceAtLeast(36f)).coerceAtLeast(0f)
        if (pushupState == 0 && elbowAngle >= topAngleThresh && shoulderClearance >= 0.14f) {
            val prevTop = pushupTopClearance ?: shoulderClearance
            pushupTopClearance = prevTop * 0.78f + shoulderClearance * 0.22f
            pushupArmed = true
        }

        val topRef = (pushupTopClearance ?: shoulderClearance).coerceAtLeast(shoulderClearance)
        val dropFromTop = (topRef - shoulderClearance).coerceAtLeast(0f)
        val elbowInt = elbowAngle.roundToInt()

        val rawAngleProg = ((topAngleThresh + 14f - elbowAngle) / ((topAngleThresh + 14f) - bottomAngleThresh).coerceAtLeast(15f))
            .coerceIn(0f, 1f)
        val rawDropProg = if (topRef >= 0.24f) (dropFromTop / (0.18f * sens)).coerceIn(0f, 1f) else 0f
        val depthProg = max(rawAngleProg, rawDropProg)

        var rep = 0
        var hint: String

        if (pushupState == 0) {
            val sufficientDrop = dropFromTop >= 0.06f * sens || shoulderClearance <= 0.42f
            val angleOrChestDepth = (elbowAngle <= bottomAngleThresh && sufficientDrop) ||
                (topRef >= 0.24f && dropFromTop >= 0.18f * sens && elbowAngle <= bottomAngleThresh + 12f)
            if (pushupArmed && angleOrChestDepth && (now - lastRepMillis) >= 340L) {
                pushupState = 1
                pushupPeakDepth = max(dropFromTop, (topAngleThresh - elbowAngle) / 100f)
                currentRepPeakAmplitude = pushupPeakDepth
                hint = "วิดพื้น: ลงลึกดี (${elbowInt}°) • ดันตัวขึ้นให้สุด!"
            } else if (pushupArmed) {
                hint = "วิดพื้น: พร้อมแล้ว ย่อศอกลง (มุมศอก ${elbowInt}°)"
            } else {
                hint = "วิดพื้น: เหยียดแขนตรงเตรียมท่าวิดพื้น (${elbowInt}°)"
            }
        } else {
            val bendMetric = max(dropFromTop, (topAngleThresh - elbowAngle) / 100f)
            pushupPeakDepth = max(pushupPeakDepth, bendMetric)
            currentRepPeakAmplitude = pushupPeakDepth

            if (elbowAngle >= (topAngleThresh - 4f) && dropFromTop <= max(0.15f, topRef * 0.36f)) {
                pushupState = 0
                rep = 1
                lastRepMillis = now
                hint = "วิดพื้น: นับ +1 ครั้ง! (เหยียดสุด ${elbowInt}°)"
            } else {
                hint = "วิดพื้น: กำลังดันขึ้น (${elbowInt}°)"
            }
        }

        val finalProg = if (pushupState == 1) max(depthProg, 0.85f) else depthProg
        val phaseLbl = when {
            rep > 0 -> "ครบ +1!"
            pushupState == 1 -> "ดันขึ้น!"
            finalProg >= 0.35f -> "ลง ${(finalProg * 100).roundToInt()}%"
            else -> ""
        }
        return RepEvalOutput(
            repDelta = rep,
            repPhaseProgress = finalProg,
            repPhaseLabel = phaseLbl,
            postureHint = hint,
            exerciseTag = "วิดพื้น",
            repAmplitude = currentRepPeakAmplitude
        )
    }

    private fun detectSquatDetailed(
        points: Map<Int, PointF>,
        bodyScale: Float,
        sensitivity: DetectionSensitivity,
        now: Long,
        points3D: Map<Int, Point3D>,
        confidences: Map<Int, Float>
    ): RepEvalOutput {
        val shoulderMid = mid(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER])
        val hipMid = mid(points[PoseLandmark.LEFT_HIP], points[PoseLandmark.RIGHT_HIP])
        val kneeMid = mid(points[PoseLandmark.LEFT_KNEE], points[PoseLandmark.RIGHT_KNEE])
        if (shoulderMid == null || hipMid == null || kneeMid == null) {
            return RepEvalOutput(
                postureHint = "สควอท: ถอยหลังให้เห็นสะโพกและหัวเข่าชัดเจน",
                exerciseTag = "สควอท"
            )
        }

        // Must be upright (shoulders above hips)
        if (hipMid.y <= shoulderMid.y + 0.25f * bodyScale) {
            squatArmed = false
            return RepEvalOutput(
                postureHint = "สควอท: ยืนตัวตรงให้เห็นช่วงขา",
                exerciseTag = "สควอท"
            )
        }

        val kneeAngle = measureKneeAngleDeg(points, points3D, confidences)
        val hipToKneeNorm = (kneeMid.y - hipMid.y) / bodyScale.coerceAtLeast(36f)
        val baseStanding = standingKneeHipRatio ?: hipToKneeNorm.coerceAtLeast(0.62f)
        val sens = sensitivity.motionDeadzoneScale

        val isStandingStraight = (kneeAngle == null || kneeAngle >= 150f) && hipToKneeNorm > baseStanding * 0.84f
        if (squatState == 0 && isStandingStraight) {
            standingKneeHipRatio = baseStanding * 0.88f + hipToKneeNorm * 0.12f
            val prevTopHip = squatTopHipY ?: hipMid.y
            squatTopHipY = prevTopHip * 0.85f + hipMid.y * 0.15f
            squatArmed = true
        }

        val topHipRef = squatTopHipY ?: hipMid.y
        val hipDropNorm = ((hipMid.y - topHipRef) / bodyScale.coerceAtLeast(36f)).coerceAtLeast(0f)
        val kneeDownThresh = when {
            sens <= 0.85f -> 128f
            sens >= 1.15f -> 108f
            else -> 118f
        }
        val ratioDownThresh = baseStanding * (0.70f * sens).coerceIn(0.50f, 0.78f)
        // Support both 3D/side knee angle AND front-facing Z-foreshortened hip-to-knee drop
        val deepSquatReached = (kneeAngle != null && kneeAngle <= kneeDownThresh && hipDropNorm >= 0.09f) ||
            (hipToKneeNorm < ratioDownThresh && hipDropNorm >= 0.15f)

        val squatDepth = max(hipDropNorm, (baseStanding - hipToKneeNorm).coerceAtLeast(0f))
        if (squatState == 1) {
            currentRepPeakAmplitude = max(currentRepPeakAmplitude, squatDepth)
        }

        val angleProg = if (kneeAngle != null) {
            ((160f - kneeAngle) / (160f - kneeDownThresh).coerceAtLeast(15f)).coerceIn(0f, 1f)
        } else 0f
        val dropProg = (hipDropNorm / 0.18f).coerceIn(0f, 1f)
        val rawProg = max(angleProg, dropProg)

        var rep = 0
        var label = "สควอท: ย่อสะโพกลงให้เข่างอ (${kneeAngle?.roundToInt() ?: 0}°)"
        if (squatState == 0 && squatArmed && deepSquatReached && (now - lastRepMillis) >= 340L) {
            squatState = 1
            currentRepPeakAmplitude = squatDepth
            label = "สควอท: ย่อลึกดีแล้ว! ดันตัวขึ้น"
        } else if (squatState == 1 && (kneeAngle == null || kneeAngle >= 142f) && hipToKneeNorm > baseStanding * 0.80f) {
            squatState = 0
            rep = 1
            lastRepMillis = now
            label = "สควอท: นับ +1 ครั้ง!"
        }

        val finalProg = if (squatState == 1) max(rawProg, 0.85f) else rawProg
        val phaseLbl = when {
            rep > 0 -> "ครบ +1!"
            squatState == 1 -> "ดันขึ้น!"
            finalProg >= 0.25f -> "ลึก ${(finalProg * 100).roundToInt()}%"
            else -> ""
        }
        return RepEvalOutput(
            repDelta = rep,
            repPhaseProgress = finalProg,
            repPhaseLabel = phaseLbl,
            postureHint = label,
            exerciseTag = "สควอท",
            repAmplitude = currentRepPeakAmplitude
        )
    }

    /**
     * Plank Hold evaluator: validates forearm or high plank orientation AND spinal/hip alignment.
     * Returns isPlankHoldingValid = true only while posture stays straight (time clock pauses if broken).
     */
    private fun detectPlankHold(
        points: Map<Int, PointF>,
        bodyScale: Float
    ): Pair<Boolean, String> {
        val shoulderMid = mid(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER])
        val hipMid = mid(points[PoseLandmark.LEFT_HIP], points[PoseLandmark.RIGHT_HIP])
        val ankleMid = mid(points[PoseLandmark.LEFT_ANKLE], points[PoseLandmark.RIGHT_ANKLE])
        val elbowMid = mid(points[PoseLandmark.LEFT_ELBOW], points[PoseLandmark.RIGHT_ELBOW])
        val wristMid = mid(points[PoseLandmark.LEFT_WRIST], points[PoseLandmark.RIGHT_WRIST])

        if (shoulderMid == null || hipMid == null) {
            return false to "แพลงก์: หันข้างให้เห็นหัวไหล่ สะโพก และลำตัวชัดเจน"
        }

        val torsoDx = abs(shoulderMid.x - hipMid.x)
        val torsoDy = abs(shoulderMid.y - hipMid.y)
        val armsSupporting = (elbowMid != null && elbowMid.y > shoulderMid.y + 0.06f * bodyScale) ||
            (wristMid != null && wristMid.y > shoulderMid.y + 0.08f * bodyScale)

        val isHorizontal = torsoDx >= torsoDy * 0.72f && torsoDx >= bodyScale * 0.28f
        if (!isHorizontal || !armsSupporting) {
            return false to "แพลงก์: ลงท่าแพลงก์ยันศอก/มือ ลำตัวขนานพื้น"
        }

        if (ankleMid != null) {
            val s2aDx = ankleMid.x - shoulderMid.x
            val s2aDy = ankleMid.y - shoulderMid.y
            val s2aLen = hypot(s2aDx, s2aDy).coerceAtLeast(1f)
            val signedDist = ((hipMid.x - shoulderMid.x) * s2aDy - (hipMid.y - shoulderMid.y) * s2aDx) / s2aLen
            val absNorm = abs(signedDist) / bodyScale.coerceAtLeast(36f)
            if (absNorm > 0.26f) {
                return false to "แพลงก์: สะโพกหย่อนหรือโด่งเกินไป — เกร็งหน้าท้องให้ตัวตรง!"
            }
        }
        return true to "แพลงก์: ฟอร์มตรงเยี่ยม! กำลังนับเวลาค้างท่าจริง"
    }

    /**
     * Lunge detector: requires split stance leg separation + vertical hip drop + knee flexion < 124 deg.
     */
    private fun detectLungeDetailed(
        points: Map<Int, PointF>,
        bodyScale: Float,
        sensitivity: DetectionSensitivity,
        now: Long,
        points3D: Map<Int, Point3D>,
        confidences: Map<Int, Float>
    ): RepEvalOutput {
        val shoulderMid = mid(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER])
        val hipMid = mid(points[PoseLandmark.LEFT_HIP], points[PoseLandmark.RIGHT_HIP])
        val lKnee = points[PoseLandmark.LEFT_KNEE]
        val rKnee = points[PoseLandmark.RIGHT_KNEE]
        val lAnkle = points[PoseLandmark.LEFT_ANKLE]
        val rAnkle = points[PoseLandmark.RIGHT_ANKLE]

        if (shoulderMid == null || hipMid == null || lKnee == null || rKnee == null || lAnkle == null || rAnkle == null) {
            return RepEvalOutput(postureHint = "ลันจ์: ถอยให้เห็นขาก้าวแยกหน้า-หลังชัดเจน", exerciseTag = "ลันจ์")
        }
        if (hipMid.y <= shoulderMid.y + 0.25f * bodyScale) {
            lungeArmed = false
            return RepEvalOutput(postureHint = "ลันจ์: ยืนลำตัวตรง", exerciseTag = "ลันจ์")
        }

        val scale = bodyScale.coerceAtLeast(36f)
        val ankleSplit = max(abs(lAnkle.x - rAnkle.x), abs(lAnkle.y - rAnkle.y)) / scale
        val isSplitStance = isAsymmetricSplitLunge(points, scale, points3D) || ankleSplit >= 0.56f

        val lKneeDeg = tripletJointAngleDeg(
            PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE, points, points3D
        )
        val rKneeDeg = tripletJointAngleDeg(
            PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, points, points3D
        )
        val minKneeDeg = minOf(lKneeDeg ?: 180f, rKneeDeg ?: 180f)
        val maxKneeDeg = maxOf(lKneeDeg ?: 180f, rKneeDeg ?: 180f)

        if (lungeState == 0 && maxKneeDeg >= 148f) {
            val prevTop = lungeTopHipY ?: hipMid.y
            lungeTopHipY = prevTop * 0.85f + hipMid.y * 0.15f
            lungeArmed = true
        }
        val topRef = lungeTopHipY ?: hipMid.y
        val hipDrop = (hipMid.y - topRef) / scale
        val sens = sensitivity.motionDeadzoneScale
        val downKneeThresh = (122f / sens).coerceIn(106f, 132f)

        val kneeProg = ((156f - minKneeDeg) / (156f - downKneeThresh).coerceAtLeast(15f)).coerceIn(0f, 1f)
        val rawProg = if (isSplitStance) max(kneeProg, (hipDrop / 0.12f).coerceIn(0f, 1f)) else 0f

        var rep = 0
        var hint = "ลันจ์: ก้าวขาแยกหน้า-หลังแล้วย่อเข่าลง (${minKneeDeg.roundToInt()}°)"
        if (lungeState == 0 && lungeArmed && isSplitStance && minKneeDeg <= downKneeThresh && hipDrop >= 0.08f && (now - lastRepMillis) >= 380L) {
            lungeState = 1
            currentRepPeakAmplitude = max(hipDrop, (150f - minKneeDeg) / 90f)
            hint = "ลันจ์: ย่อได้ลึกดี (${minKneeDeg.roundToInt()}°) • ดันตัวขึ้น"
        } else if (lungeState == 1 && minKneeDeg >= 146f) {
            lungeState = 0
            rep = 1
            lastRepMillis = now
            hint = "ลันจ์: นับ +1 ครั้ง!"
        }
        val finalProg = if (lungeState == 1) max(rawProg, 0.85f) else rawProg
        return RepEvalOutput(
            repDelta = rep,
            repPhaseProgress = finalProg,
            repPhaseLabel = if (lungeState == 1) "ดันขึ้น!" else if (finalProg >= 0.3f) "ลึก ${(finalProg * 100).roundToInt()}%" else "",
            postureHint = hint,
            exerciseTag = "ลันจ์",
            repAmplitude = currentRepPeakAmplitude
        )
    }

    /**
     * Sit-up / Crunch detector: requires floor posture (knees flexed, hips near floor band) and
     * measures shoulder-hip-knee torso curl angle dropping below ~86 deg then extending back > 118 deg.
     */
    private fun detectSitUpDetailed(
        points: Map<Int, PointF>,
        bodyScale: Float,
        sensitivity: DetectionSensitivity,
        now: Long,
        points3D: Map<Int, Point3D>,
        confidences: Map<Int, Float>
    ): RepEvalOutput {
        val shoulderMid = mid(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER])
        val hipMid = mid(points[PoseLandmark.LEFT_HIP], points[PoseLandmark.RIGHT_HIP])
        val kneeMid = mid(points[PoseLandmark.LEFT_KNEE], points[PoseLandmark.RIGHT_KNEE])
        if (shoulderMid == null || hipMid == null || kneeMid == null) {
            return RepEvalOutput(postureHint = "ซิทอัพ: หันข้างให้เห็นหัวไหล่ สะโพก และเข่างอ", exerciseTag = "ซิทอัพ")
        }

        val scale = bodyScale.coerceAtLeast(36f)
        // Guard against upright standing: during floor sit-up, knee Y is at/above ankle or close to hip band horizontally
        val ankleMid = mid(points[PoseLandmark.LEFT_ANKLE], points[PoseLandmark.RIGHT_ANKLE])
        val kneeToHipDx = abs(kneeMid.x - hipMid.x) / scale
        val kneeDropBelowHip = (kneeMid.y - hipMid.y) / scale
        val looksFloorSeated = kneeToHipDx >= 0.34f || kneeDropBelowHip <= 0.42f ||
            (ankleMid != null && kneeMid.y < hipMid.y + 0.15f * scale)
        if (!looksFloorSeated) {
            situpArmed = false
            return RepEvalOutput(postureHint = "ซิทอัพ: นอนชันเข่าหันข้างเข้ากล้อง", exerciseTag = "ซิทอัพ")
        }

        val s3d = mid3D(points3D[PoseLandmark.LEFT_SHOULDER], points3D[PoseLandmark.RIGHT_SHOULDER])
        val h3d = mid3D(points3D[PoseLandmark.LEFT_HIP], points3D[PoseLandmark.RIGHT_HIP])
        val k3d = mid3D(points3D[PoseLandmark.LEFT_KNEE], points3D[PoseLandmark.RIGHT_KNEE])
        val torsoHipKneeAngle = if (s3d != null && h3d != null && k3d != null) {
            tripletAngle3DIfValid(s3d, h3d, k3d) ?: tripletAngleIfValid(shoulderMid, hipMid, kneeMid)
        } else {
            tripletAngleIfValid(shoulderMid, hipMid, kneeMid)
        } ?: return RepEvalOutput(postureHint = "ซิทอัพ: จัดกล้องให้เห็นแนวลำตัวชัดเจน", exerciseTag = "ซิทอัพ")

        val sens = sensitivity.motionDeadzoneScale
        val downAngleThresh = (112f / sens).coerceIn(102f, 126f)
        val upAngleThresh = (92f * sens).coerceIn(78f, 102f)

        if (situpState == 0 && torsoHipKneeAngle >= downAngleThresh) {
            situpArmed = true
        }

        val curlProg = ((downAngleThresh - torsoHipKneeAngle) / (downAngleThresh - upAngleThresh).coerceAtLeast(12f))
            .coerceIn(0f, 1f)

        var rep = 0
        var hint = "ซิทอัพ: ยกอกเข้าหาเข่า (${torsoHipKneeAngle.roundToInt()}°)"
        if (situpState == 0 && situpArmed && torsoHipKneeAngle <= upAngleThresh && (now - lastRepMillis) >= 380L) {
            situpState = 1
            currentRepPeakAmplitude = ((downAngleThresh - torsoHipKneeAngle) / 60f).coerceAtLeast(0.25f)
            hint = "ซิทอัพ: เกร็งหน้าท้องดีมาก! ผ่อนตัวลง"
        } else if (situpState == 1 && torsoHipKneeAngle >= downAngleThresh) {
            situpState = 0
            rep = 1
            lastRepMillis = now
            hint = "ซิทอัพ: นับ +1 ครั้ง!"
        }
        val finalProg = if (situpState == 1) max(curlProg, 0.85f) else curlProg
        return RepEvalOutput(
            repDelta = rep,
            repPhaseProgress = finalProg,
            repPhaseLabel = if (situpState == 1) "ผ่อนลง!" else if (finalProg >= 0.25f) "ยกตัว ${(finalProg * 100).roundToInt()}%" else "",
            postureHint = hint,
            exerciseTag = "ซิทอัพ",
            repAmplitude = currentRepPeakAmplitude
        )
    }

    /**
     * Strict Jumping Jack detector:
     * MUST show BOTH overhead arms AND wide leg stance simultaneously on the open phase,
     * and hands-down + feet-together on the close phase. Arm waving while feet stay narrow gives 0 reps!
     */
    private fun detectJumpingJackDetailed(
        points: Map<Int, PointF>,
        bodyScale: Float,
        sensitivity: DetectionSensitivity,
        now: Long
    ): RepEvalOutput {
        val shoulderMid = mid(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER])
        val lWrist = points[PoseLandmark.LEFT_WRIST]
        val rWrist = points[PoseLandmark.RIGHT_WRIST]
        val lAnkle = points[PoseLandmark.LEFT_ANKLE]
        val rAnkle = points[PoseLandmark.RIGHT_ANKLE]
        val lKnee = points[PoseLandmark.LEFT_KNEE]
        val rKnee = points[PoseLandmark.RIGHT_KNEE]

        if (shoulderMid == null || lWrist == null || rWrist == null) {
            return RepEvalOutput(exerciseTag = "กระโดดตบ")
        }

        val legSpreadPx = when {
            lAnkle != null && rAnkle != null -> abs(lAnkle.x - rAnkle.x)
            lKnee != null && rKnee != null -> abs(lKnee.x - rKnee.x)
            else -> {
                jackState = 0
                return RepEvalOutput(
                    postureHint = "กระโดดตบ: ถอยให้เห็นขากางออกด้วย จึงจะนับครั้ง",
                    exerciseTag = "กระโดดตบ"
                )
            }
        }

        val lHip = points[PoseLandmark.LEFT_HIP]
        val rHip = points[PoseLandmark.RIGHT_HIP]
        val hipWidth = if (lHip != null && rHip != null) abs(lHip.x - rHip.x) else bodyScale * 0.40f
        val baseScale = bodyScale.coerceAtLeast(36f)

        val lElbow = points[PoseLandmark.LEFT_ELBOW]
        val rElbow = points[PoseLandmark.RIGHT_ELBOW]
        val bothHandsUp = (lWrist.y < shoulderMid.y - 0.14f * baseScale ||
            (lElbow != null && lElbow.y < shoulderMid.y - 0.12f * baseScale)) &&
            (rWrist.y < shoulderMid.y - 0.14f * baseScale ||
                (rElbow != null && rElbow.y < shoulderMid.y - 0.12f * baseScale))
        val bothHandsDown = lWrist.y > shoulderMid.y + 0.14f * baseScale &&
            rWrist.y > shoulderMid.y + 0.14f * baseScale

        if (jackState == 0 && bothHandsDown) {
            val prevSpread = baselineLegSpread ?: legSpreadPx
            baselineLegSpread = prevSpread * 0.85f + legSpreadPx * 0.15f
        }
        val baseSpread = baselineLegSpread ?: hipWidth
        val sens = sensitivity.motionDeadzoneScale
        val wideSpreadTarget = max(
            hipWidth * (1.28f * sens).coerceIn(1.16f, 1.48f),
            max(baseScale * 0.42f, baseSpread * 1.28f)
        )

        val legsSpreadWide = legSpreadPx > wideSpreadTarget
        val legsTogether = legSpreadPx < max(hipWidth * 1.22f, baseSpread * 1.20f)

        val avgLift = ((shoulderMid.y - lWrist.y) + (shoulderMid.y - rWrist.y)) / (2f * baseScale)
        if (jackState == 1) {
            currentRepPeakAmplitude = max(currentRepPeakAmplitude, avgLift)
        }

        val armProg = ((avgLift + 0.14f) / 0.34f).coerceIn(0f, 1f)
        val legProg = ((legSpreadPx - baseSpread) / (wideSpreadTarget - baseSpread).coerceAtLeast(10f)).coerceIn(0f, 1f)
        val rawProg = (armProg * 0.5f + legProg * 0.5f).coerceIn(0f, 1f)

        var rep = 0
        var hint = ""
        if (jackState == 0 && bothHandsUp) {
            if (legsSpreadWide && (now - lastRepMillis) >= 340L) {
                jackState = 1
                currentRepPeakAmplitude = avgLift
                hint = "กระโดดตบ: กางแขนขาครบ! หุบลงเพื่อครบ 1 ครั้ง"
            } else {
                // Reject arm-only waving clearly with coaching message
                hint = "กระโดดตบ: ต้องกระโดดกางขาพร้อมยกแขนขึ้นด้วย (ยกแขนอย่างเดียวไม่นับ)"
            }
        } else if (jackState == 1 && bothHandsDown && legsTogether) {
            jackState = 0
            rep = 1
            lastRepMillis = now
            hint = "กระโดดตบ: นับ +1 ครั้ง!"
        } else if (jackState == 1) {
            hint = "กระโดดตบ: หุบขาและลดแขนลง"
        }

        val finalProg = if (jackState == 1) max(rawProg, 0.85f) else rawProg
        return RepEvalOutput(
            repDelta = rep,
            repPhaseProgress = finalProg,
            repPhaseLabel = if (jackState == 1) "หุบลง!" else if (finalProg >= 0.35f) "กาง ${(finalProg * 100).roundToInt()}%" else "",
            postureHint = hint,
            exerciseTag = "กระโดดตบ",
            repAmplitude = currentRepPeakAmplitude
        )
    }

    private fun detectAlternatingSteps(
        points: Map<Int, PointF>,
        bodyScale: Float,
        isMoving: Boolean,
        minNormDiff: Float,
        minIntervalMs: Long,
        now: Long
    ): Int {
        val leftLeg = points[PoseLandmark.LEFT_ANKLE] ?: points[PoseLandmark.LEFT_KNEE]
        val rightLeg = points[PoseLandmark.RIGHT_ANKLE] ?: points[PoseLandmark.RIGHT_KNEE]
        if (leftLeg == null || rightLeg == null) return 0

        val prevL = prevLeftLegY
        val prevR = prevRightLegY
        prevLeftLegY = leftLeg.y
        prevRightLegY = rightLeg.y

        if (!isMoving) return 0
        // Guard against counting steps when legs are completely planted (e.g. waving arms)
        val legMoved = if (prevL != null && prevR != null) {
            (abs(leftLeg.y - prevL) + abs(rightLeg.y - prevR)) / bodyScale.coerceAtLeast(36f) > 0.014f
        } else {
            true
        }
        if (!legMoved) return 0

        val normDiff = (leftLeg.y - rightLeg.y) / bodyScale.coerceAtLeast(36f)
        val sign = when {
            normDiff > minNormDiff -> 1
            normDiff < -minNormDiff -> -1
            else -> prevGaitSign
        }
        var step = 0
        if (sign != 0 && sign != prevGaitSign && (now - lastStepMillis) >= minIntervalMs) {
            if (lastStepMillis > 0L) {
                stepIntervalHistory.add((now - lastStepMillis).coerceIn(150L, 3000L))
                if (stepIntervalHistory.size > 40) stepIntervalHistory.removeAt(0)
            }
            step = 1
            lastStepMillis = now
        }
        prevGaitSign = sign
        return step
    }

    private fun detectRacketSwingDetailed(
        points: Map<Int, PointF>,
        bodyScale: Float,
        sensitivity: DetectionSensitivity,
        now: Long
    ): Pair<Int, Float> {
        val shoulderMid = mid(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER]) ?: return 0 to 0f
        val lWrist = points[PoseLandmark.LEFT_WRIST]
        val rWrist = points[PoseLandmark.RIGHT_WRIST]
        val topWristY = minOf(lWrist?.y ?: Float.MAX_VALUE, rWrist?.y ?: Float.MAX_VALUE)
        if (topWristY == Float.MAX_VALUE) return 0 to 0f

        val liftAboveShoulder = (shoulderMid.y - topWristY) / bodyScale
        if (swingState == 1) {
            currentRepPeakAmplitude = max(currentRepPeakAmplitude, liftAboveShoulder)
        }
        val upThresh = shoulderMid.y - 0.14f * bodyScale * sensitivity.motionDeadzoneScale
        val followThroughThresh = shoulderMid.y + 0.16f * bodyScale

        var rep = 0
        if (swingState == 0 && topWristY < upThresh && (now - lastRepMillis) >= 360L) {
            swingState = 1
            currentRepPeakAmplitude = liftAboveShoulder
        } else if (swingState == 1 && topWristY > followThroughThresh) {
            swingState = 0
            rep = 1
            lastRepMillis = now
        }
        val prog = if (swingState == 1) 1f else ((liftAboveShoulder + 0.16f) / 0.30f).coerceIn(0f, 0.99f)
        return rep to prog
    }

    private fun detectCyclingPedalDetailed(
        points: Map<Int, PointF>,
        bodyScale: Float,
        isMoving: Boolean,
        now: Long
    ): Pair<Int, Float> {
        if (!isMoving) return 0 to 0f
        val hipMid = mid(points[PoseLandmark.LEFT_HIP], points[PoseLandmark.RIGHT_HIP]) ?: return 0 to 0f
        val lKnee = points[PoseLandmark.LEFT_KNEE]
        val rKnee = points[PoseLandmark.RIGHT_KNEE]
        val highKneeY = minOf(lKnee?.y ?: Float.MAX_VALUE, rKnee?.y ?: Float.MAX_VALUE)
        if (highKneeY == Float.MAX_VALUE) return 0 to 0f

        val kneeHeightRelHip = (hipMid.y - highKneeY) / bodyScale
        if (pedalState == 1) {
            currentRepPeakAmplitude = max(currentRepPeakAmplitude, kneeHeightRelHip + 0.5f)
        }
        val upThresh = -0.18f
        val downThresh = -0.45f

        var rep = 0
        if (pedalState == 0 && kneeHeightRelHip > upThresh && (now - lastRepMillis) >= 280L) {
            pedalState = 1
            currentRepPeakAmplitude = kneeHeightRelHip + 0.5f
        } else if (pedalState == 1 && kneeHeightRelHip < downThresh) {
            pedalState = 0
            rep = 1
            lastRepMillis = now
        }
        val prog = ((kneeHeightRelHip - downThresh) / (upThresh - downThresh)).coerceIn(0f, 1f)
        return rep to prog
    }

    internal fun measureElbowAngleDeg(
        points: Map<Int, PointF>,
        points3D: Map<Int, Point3D> = emptyMap(),
        confidences: Map<Int, Float> = emptyMap()
    ): Float? {
        val leftAngle = tripletJointAngleDeg(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST, points, points3D
        )
        val rightAngle = tripletJointAngleDeg(
            PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST, points, points3D
        )
        val leftConf = sideConfidence(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST, confidences
        )
        val rightConf = sideConfidence(
            PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST, confidences
        )
        return mergeBilateralAngles(leftAngle, leftConf, rightAngle, rightConf)
    }

    internal fun measureKneeAngleDeg(
        points: Map<Int, PointF>,
        points3D: Map<Int, Point3D> = emptyMap(),
        confidences: Map<Int, Float> = emptyMap()
    ): Float? {
        val leftAngle = tripletJointAngleDeg(
            PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE, points, points3D
        )
        val rightAngle = tripletJointAngleDeg(
            PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, points, points3D
        )
        val leftConf = sideConfidence(
            PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE, confidences
        )
        val rightConf = sideConfidence(
            PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, confidences
        )
        return mergeBilateralAngles(leftAngle, leftConf, rightAngle, rightConf)
    }

    private fun sideConfidence(t1: Int, t2: Int, t3: Int, confidences: Map<Int, Float>): Float? {
        if (confidences.isEmpty()) return null
        val c1 = confidences[t1] ?: return null
        val c2 = confidences[t2] ?: return null
        val c3 = confidences[t3] ?: return null
        return minOf(c1, c2, c3) * 0.5f + ((c1 + c2 + c3) / 3f) * 0.5f
    }

    private fun mergeBilateralAngles(
        leftAngle: Float?,
        leftConf: Float?,
        rightAngle: Float?,
        rightConf: Float?
    ): Float? {
        if (leftAngle == null) return rightAngle
        if (rightAngle == null) return leftAngle
        if (leftConf == null || rightConf == null) return (leftAngle + rightAngle) / 2f
        val diff = abs(leftConf - rightConf)
        return when {
            diff >= 0.14f -> if (leftConf > rightConf) leftAngle else rightAngle
            else -> {
                val sum = (leftConf + rightConf).coerceAtLeast(0.01f)
                (leftAngle * leftConf + rightAngle * rightConf) / sum
            }
        }
    }

    private fun tripletJointAngleDeg(
        t1: Int,
        t2: Int,
        t3: Int,
        points: Map<Int, PointF>,
        points3D: Map<Int, Point3D>
    ): Float? {
        val a3 = points3D[t1]
        val b3 = points3D[t2]
        val c3 = points3D[t3]
        if (a3 != null && b3 != null && c3 != null) {
            val angle3d = tripletAngle3DIfValid(a3, b3, c3)
            if (angle3d != null) return angle3d
        }
        return tripletAngleIfValid(points[t1], points[t2], points[t3])
    }

    private fun tripletAngle3DIfValid(a: Point3D, b: Point3D, c: Point3D): Float? {
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v1z = a.z - b.z
        val v2x = c.x - b.x
        val v2y = c.y - b.y
        val v2z = c.z - b.z
        val m1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
        val m2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)
        if (m1 < 12f || m2 < 12f) return null
        val cosTheta = ((v1x * v2x + v1y * v2y + v1z * v2z) / (m1 * m2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosTheta.toDouble())).toFloat()
    }

    private fun tripletAngleIfValid(a: PointF?, b: PointF?, c: PointF?): Float? {
        if (a == null || b == null || c == null) return null
        val len1 = hypot(a.x - b.x, a.y - b.y)
        val len2 = hypot(c.x - b.x, c.y - b.y)
        if (len1 < 12f || len2 < 12f) return null
        return calculateJointAngleDeg(a, b, c)
    }

    private fun calculateJointAngleDeg(a: PointF, b: PointF, c: PointF): Float {
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v2x = c.x - b.x
        val v2y = c.y - b.y
        val m1 = hypot(v1x, v1y)
        val m2 = hypot(v2x, v2y)
        if (m1 < 1e-3f || m2 < 1e-3f) return 180f
        val cosTheta = ((v1x * v2x + v1y * v2y) / (m1 * m2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosTheta.toDouble())).toFloat()
    }

    private fun mid3D(a: Point3D?, b: Point3D?): Point3D? = when {
        a != null && b != null -> Point3D((a.x + b.x) / 2f, (a.y + b.y) / 2f, (a.z + b.z) / 2f)
        a != null -> a
        b != null -> b
        else -> null
    }

    private fun mid(a: PointF?, b: PointF?): PointF? = when {
        a != null && b != null -> PointF((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        a != null -> a
        b != null -> b
        else -> null
    }
}

class PoseMotionAnalyzer(
    private val sportProvider: () -> SportType,
    private val sensitivityProvider: () -> DetectionSensitivity,
    private val isFrontCameraProvider: () -> Boolean,
    private val exerciseFocusProvider: () -> GeneralExerciseFocus = { GeneralExerciseFocus.AUTO },
    private val onMotionAnalyzed: (PoseSkeletonSnapshot) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .setPreferredHardwareConfigs(AccuratePoseDetectorOptions.CPU_GPU)
            .build()
    )

    private val stateLock = Any()
    private val anchorTracker = SubjectAnchorTracker()
    private val repEngine = BiomechanicalRepEngine()
    private var lastAnalyzedSport: SportType? = null
    private var lastExerciseFocus: GeneralExerciseFocus? = null
    private val oneEuroFilters = mutableMapOf<Int, OneEuroFilter3D>()
    private val smoothedJoints = mutableMapOf<Int, PointF>()
    private val smoothedJoints3D = mutableMapOf<Int, Point3D>()
    private val missedFrames = mutableMapOf<Int, Int>()
    private var prevSmoothedJoints: Map<Int, PointF> = emptyMap()
    private var lastFrameMillis: Long = 0L
    private var activeMotionStreak: Int = 0
    private var lastMotionSeenMillis: Long = 0L

    // Last full-frame dimensions for Tap-to-Lock coordinate conversion
    @Volatile
    private var lastImgW: Float = 480f
    @Volatile
    private var lastImgH: Float = 640f

    // Live Symmetry, Form & Fatigue Guard state
    private var smoothedSymmetry: Float = 96f
    private var smoothedFormScore: Float = 80f
    private val repAmplitudeHistory = mutableListOf<Float>()
    private var lastFatigueWarning: Boolean = false
    private var lastFatigueHint: String = ""

    fun lockSubjectAtTap(tapX: Float, tapY: Float, viewW: Float, viewH: Float) {
        synchronized(stateLock) {
            oneEuroFilters.clear()
            smoothedJoints.clear()
            smoothedJoints3D.clear()
            missedFrames.clear()
            prevSmoothedJoints = emptyMap()
            anchorTracker.lockAtScreenTap(
                tapX = tapX,
                tapY = tapY,
                viewW = viewW,
                viewH = viewH,
                isMirrored = isFrontCameraProvider(),
                imgW = lastImgW,
                imgH = lastImgH
            )
        }
    }

    fun relockPrimarySubject() {
        synchronized(stateLock) {
            oneEuroFilters.clear()
            smoothedJoints.clear()
            smoothedJoints3D.clear()
            missedFrames.clear()
            prevSmoothedJoints = emptyMap()
            activeMotionStreak = 0
            anchorTracker.clearLock()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val isRotated = rotation == 90 || rotation == 270
        val imgW = if (isRotated) imageProxy.height.toFloat() else imageProxy.width.toFloat()
        val imgH = if (isRotated) imageProxy.width.toFloat() else imageProxy.height.toFloat()
        lastImgW = imgW
        lastImgH = imgH

        val mirrorX = isFrontCameraProvider()
        val sport = sportProvider()
        val focus = exerciseFocusProvider()
        val sensitivity = sensitivityProvider()

        val roiCrop = synchronized(stateLock) {
            if (anchorTracker.shouldUseRoiCrop()) {
                anchorTracker.consumeRoiFrameTick()
                anchorTracker.computeRoiRect(imgW, imgH)?.let { rect ->
                    extractUprightRoiCrop(imageProxy, rotation, rect)
                }
            } else {
                null
            }
        }

        val inputImage = if (roiCrop != null) {
            InputImage.fromBitmap(roiCrop.bitmap, 0)
        } else {
            InputImage.fromMediaImage(mediaImage, rotation)
        }
        val offsetX = roiCrop?.left?.toFloat() ?: 0f
        val offsetY = roiCrop?.top?.toFloat() ?: 0f

        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                val snapshot = evaluatePoseAndMotion(
                    pose = pose,
                    imgW = imgW,
                    imgH = imgH,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    mirrorX = mirrorX,
                    sport = sport,
                    focus = focus,
                    sensitivity = sensitivity
                )
                onMotionAnalyzed(snapshot)
            }
            .addOnFailureListener {
                onMotionAnalyzed(
                    PoseSkeletonSnapshot(
                        imageWidth = imgW,
                        imageHeight = imgH,
                        isMirrored = mirrorX,
                        isBodyVisible = false,
                        isMoving = false,
                        confidencePercent = 0,
                        isSubjectLocked = anchorTracker.isLocked,
                        postureHint = "ไม่พบร่างกายในกล้อง • ลองปรับแสงหรือมุมกล้อง"
                    )
                )
            }
            .addOnCompleteListener {
                runCatching { roiCrop?.bitmap?.recycle() }
                imageProxy.close()
            }
    }

    private fun evaluatePoseAndMotion(
        pose: Pose,
        imgW: Float,
        imgH: Float,
        offsetX: Float,
        offsetY: Float,
        mirrorX: Boolean,
        sport: SportType,
        focus: GeneralExerciseFocus,
        sensitivity: DetectionSensitivity
    ): PoseSkeletonSnapshot = synchronized(stateLock) {
        if (lastAnalyzedSport != sport) {
            resetInternalState()
            lastAnalyzedSport = sport
            lastExerciseFocus = focus
        } else if (lastExerciseFocus != focus) {
            repEngine.reset()
            lastExerciseFocus = focus
        }

        val coreTypes = setOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP
        )
        val trackedTypes = listOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW,
            PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE
        )

        // Step 1: Extract raw unsmoothed 2D + 3D landmarks and per-joint confidences
        val rawCandidatePoints = mutableMapOf<Int, PointF>()
        val rawCandidatePoints3D = mutableMapOf<Int, Point3D>()
        val jointConfidences = mutableMapOf<Int, Float>()
        var confidenceSum = 0f
        var confidenceCount = 0

        for (type in trackedTypes) {
            val landmark = pose.getPoseLandmark(type)
            val minConfidence = if (type in coreTypes) {
                sensitivity.coreConfidence
            } else {
                sensitivity.limbConfidence
            }
            if (landmark != null && landmark.inFrameLikelihood >= minConfidence && imgW > 0f && imgH > 0f) {
                val px = landmark.position.x + offsetX
                val py = landmark.position.y + offsetY
                val inBounds = px >= -0.06f * imgW && px <= 1.06f * imgW &&
                    py >= -0.10f * imgH && py <= 1.10f * imgH
                if (inBounds) {
                    rawCandidatePoints[type] = PointF(px, py)
                    val p3d = landmark.position3D
                    rawCandidatePoints3D[type] = Point3D(p3d.x + offsetX, p3d.y + offsetY, p3d.z)
                    jointConfidences[type] = landmark.inFrameLikelihood
                    confidenceSum += landmark.inFrameLikelihood
                    confidenceCount++
                }
            }
        }

        val hasShoulders = rawCandidatePoints.containsKey(PoseLandmark.LEFT_SHOULDER) ||
            rawCandidatePoints.containsKey(PoseLandmark.RIGHT_SHOULDER)
        val hasHips = rawCandidatePoints.containsKey(PoseLandmark.LEFT_HIP) ||
            rawCandidatePoints.containsKey(PoseLandmark.RIGHT_HIP)
        val hasRawBody = rawCandidatePoints.size >= 5 && (hasShoulders || hasHips)
        val avgConfPct = if (confidenceCount > 0) ((confidenceSum / confidenceCount) * 100f).roundToInt().coerceIn(0, 99) else 0
        val now = System.currentTimeMillis()
        val dtSec = if (lastFrameMillis > 0L) ((now - lastFrameMillis) / 1000f).coerceIn(0.01f, 0.25f) else 0.033f
        lastFrameMillis = now

        if (!hasRawBody) {
            prevSmoothedJoints = emptyMap()
            activeMotionStreak = 0
            repEngine.resetBodyLost()
            return@synchronized PoseSkeletonSnapshot(
                rawPoints = emptyMap(),
                imageWidth = imgW,
                imageHeight = imgH,
                isMirrored = mirrorX,
                isBodyVisible = false,
                isMoving = false,
                motionIntensity = 0f,
                visibleJointCount = rawCandidatePoints.size,
                confidencePercent = avgConfPct,
                symmetryPercent = smoothedSymmetry.roundToInt(),
                formScorePercent = 0,
                fatigueWarning = lastFatigueWarning,
                fatigueHint = lastFatigueHint,
                isSubjectLocked = anchorTracker.isLocked,
                isForeignPersonBlocked = anchorTracker.isRecentlyBlockingForeign(now),
                lockCenterX = anchorTracker.anchorCenterX,
                lockCenterY = anchorTracker.anchorCenterY,
                lockRadiusX = anchorTracker.lockRadiusX(imgW),
                lockRadiusY = anchorTracker.lockRadiusY(imgH),
                postureHint = if (anchorTracker.isLocked) {
                    "ล็อกตำแหน่งตัวคุณไว้แล้ว • กำลังรอคุณกลับเข้ากรอบ"
                } else {
                    "ยังไม่พบตัวในกล้อง • ถอยหลังให้เห็นร่างกายชัดเจน"
                }
            )
        }

        // Step 2: Subject Anchor Lock Gate BEFORE updating filtered joints / rep counters!
        val candidateTorsoCenter = estimateTorsoCenter(rawCandidatePoints)
        val candidateScale = estimateBodyScalePx(rawCandidatePoints, imgH)
        val isAcceptedSubject = anchorTracker.validateAndTrack(
            centerX = candidateTorsoCenter.x,
            centerY = candidateTorsoCenter.y,
            bodyScale = candidateScale,
            imgW = imgW,
            imgH = imgH,
            now = now
        )

        if (!isAcceptedSubject) {
            activeMotionStreak = 0
            return@synchronized PoseSkeletonSnapshot(
                rawPoints = smoothedJoints.toMap(),
                imageWidth = imgW,
                imageHeight = imgH,
                isMirrored = mirrorX,
                isBodyVisible = false,
                isMoving = false,
                motionIntensity = 0f,
                visibleJointCount = 0,
                confidencePercent = avgConfPct,
                symmetryPercent = smoothedSymmetry.roundToInt(),
                formScorePercent = smoothedFormScore.roundToInt(),
                fatigueWarning = lastFatigueWarning,
                fatigueHint = lastFatigueHint,
                isSubjectLocked = anchorTracker.isLocked,
                isForeignPersonBlocked = true,
                lockCenterX = anchorTracker.anchorCenterX,
                lockCenterY = anchorTracker.anchorCenterY,
                lockRadiusX = anchorTracker.lockRadiusX(imgW),
                lockRadiusY = anchorTracker.lockRadiusY(imgH),
                postureHint = "พบคน/สิ่งอื่นผ่านหน้ากล้อง • ล็อกตัวคุณไว้แล้ว"
            )
        }

        // Step 3: Adaptive One-Euro 3D joint filtering (zero jitter at rest, zero lag during fast jumps/reps)
        val refScale = candidateScale.coerceAtLeast(48f)
        val currentPoints = mutableMapOf<Int, PointF>()
        val currentPoints3D = mutableMapOf<Int, Point3D>()
        for (type in trackedTypes) {
            val raw3D = rawCandidatePoints3D[type]
            if (raw3D != null) {
                val filter = oneEuroFilters.getOrPut(type) {
                    if (type in coreTypes) OneEuroFilter3D(minCutoff = 1.45f, beta = 0.040f)
                    else OneEuroFilter3D(minCutoff = 1.75f, beta = 0.055f)
                }
                val filtered3D = filter.filter(raw3D, dtSec, refScale)
                val filtered2D = PointF(filtered3D.x, filtered3D.y)
                smoothedJoints[type] = filtered2D
                smoothedJoints3D[type] = filtered3D
                missedFrames[type] = 0
                currentPoints[type] = filtered2D
                currentPoints3D[type] = filtered3D
            } else {
                handleMissingJoint(type)
            }
        }

        val bodyScale = estimateBodyScalePx(currentPoints, imgH)

        // Camera-shake rejection: subtract median global translation across visible joints
        val dxList = mutableListOf<Float>()
        val dyList = mutableListOf<Float>()
        val matchedKeys = mutableListOf<Int>()
        for ((type, pt) in currentPoints) {
            val prev = prevSmoothedJoints[type]
            if (prev != null) {
                dxList.add(pt.x - prev.x)
                dyList.add(pt.y - prev.y)
                matchedKeys.add(type)
            }
        }

        val medDx = medianOrZero(dxList)
        val medDy = medianOrZero(dyList)

        val coreAndLegTypes = setOf(
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE
        )

        var relativeNormDispSum = 0f
        var rawNormDispSum = 0f
        var coreLegDispSum = 0f
        var coreLegCount = 0
        for (type in matchedKeys) {
            val pt = currentPoints.getValue(type)
            val prev = prevSmoothedJoints.getValue(type)
            val dx = pt.x - prev.x
            val dy = pt.y - prev.y
            val rawD = hypot(dx, dy) / bodyScale
            val relD = hypot(dx - medDx, dy - medDy) / bodyScale
            rawNormDispSum += rawD
            relativeNormDispSum += relD
            if (type in coreAndLegTypes) {
                coreLegDispSum += max(relD, rawD * 0.65f)
                coreLegCount++
            }
        }
        prevSmoothedJoints = currentPoints.toMap()

        val pairCount = matchedKeys.size
        val avgRelDisp = if (pairCount > 0) relativeNormDispSum / pairCount else 0f
        val avgRawDisp = if (pairCount > 0) rawNormDispSum / pairCount else 0f
        val avgCoreLegDisp = if (coreLegCount > 0) coreLegDispSum / coreLegCount else 0f

        // For lower-body / jump sports, ignore wrist-only arm waving in whole-body motion check!
        val requiresWholeBodyMotion = sport in setOf(
            SportType.JUMP_ROPE,
            SportType.RUNNING,
            SportType.WALKING,
            SportType.FOOTBALL,
            SportType.BASKETBALL,
            SportType.CYCLING
        )
        val effectiveDisp = when {
            requiresWholeBodyMotion -> avgCoreLegDisp
            sport == SportType.BADMINTON || sport == SportType.GENERAL -> max(avgRelDisp, avgRawDisp * 0.55f)
            else -> avgRelDisp
        }

        val motionThreshold = 0.022f * sensitivity.motionDeadzoneScale
        if (effectiveDisp > motionThreshold) {
            activeMotionStreak = (activeMotionStreak + 1).coerceAtMost(10)
        } else {
            activeMotionStreak = 0
        }

        val instantConfirmedMoving = activeMotionStreak >= 2
        if (instantConfirmedMoving) {
            lastMotionSeenMillis = now
        }
        val isMoving = instantConfirmedMoving || (now - lastMotionSeenMillis) <= 520L
        val poseIntensity = ((effectiveDisp - motionThreshold * 0.65f) / 0.13f).coerceIn(0f, 1f)

        // Dispatch to strict 3D biomechanical detector BEFORE final symmetry/hint assembly
        val eval = repEngine.evaluate(
            sport = sport,
            focus = focus,
            points = currentPoints,
            bodyScale = bodyScale,
            isWholeBodyMoving = isMoving && avgCoreLegDisp > motionThreshold * 0.72f,
            sensitivity = sensitivity,
            now = now,
            points3D = currentPoints3D,
            confidences = jointConfidences
        )

        val effectiveMoving = if (requiresWholeBodyMotion) {
            isMoving && (avgCoreLegDisp > motionThreshold * 0.75f || eval.stepDelta > 0 || eval.repDelta > 0)
        } else {
            isMoving || eval.repDelta > 0
        }

        // Real-time Biomechanical Symmetry & Form Scoring (plank-aware!)
        val (instSymmetry, symHint) = evaluateSymmetry(currentPoints, bodyScale, isPlank = eval.isHorizontalPlank)
        smoothedSymmetry = smoothedSymmetry * 0.82f + instSymmetry * 0.18f
        val symPct = smoothedSymmetry.roundToInt().coerceIn(45, 100)

        val completeness = (currentPoints.size / 13f).coerceIn(0f, 1f)
        val rawForm = avgConfPct * 0.35f + symPct * 0.45f + completeness * 100f * 0.20f
        smoothedFormScore = smoothedFormScore * 0.80f + rawForm * 0.20f
        val formPct = smoothedFormScore.roundToInt().coerceIn(40, 100)

        evaluateFatigueGuard(stepDelta = eval.stepDelta, repDelta = eval.repDelta, symPct = symPct)

        val lockPrefix = if (anchorTracker.isLocked) "ล็อกตัวแล้ว • " else ""
        val displayHint = "$lockPrefix${eval.postureHint} • ฟอร์ม $formPct%"

        PoseSkeletonSnapshot(
            rawPoints = currentPoints,
            imageWidth = imgW,
            imageHeight = imgH,
            isMirrored = mirrorX,
            isBodyVisible = true,
            isMoving = effectiveMoving || eval.isPlankHoldingValid,
            motionIntensity = if (effectiveMoving) poseIntensity.coerceAtLeast(0.20f) else if (eval.isPlankHoldingValid) 0.48f else 0f,
            stepDelta = eval.stepDelta,
            repDelta = eval.repDelta,
            repPhaseProgress = eval.repPhaseProgress,
            repPhaseLabel = eval.repPhaseLabel,
            isPlankHoldingValid = eval.isPlankHoldingValid,
            visibleJointCount = currentPoints.size,
            confidencePercent = avgConfPct,
            symmetryPercent = symPct,
            formScorePercent = formPct,
            symmetryHint = symHint,
            fatigueWarning = lastFatigueWarning,
            fatigueHint = lastFatigueHint,
            isSubjectLocked = anchorTracker.isLocked,
            isForeignPersonBlocked = anchorTracker.isRecentlyBlockingForeign(now),
            lockCenterX = anchorTracker.anchorCenterX,
            lockCenterY = anchorTracker.anchorCenterY,
            lockRadiusX = anchorTracker.lockRadiusX(imgW),
            lockRadiusY = anchorTracker.lockRadiusY(imgH),
            detectedExerciseLabel = eval.exerciseTag,
            postureHint = displayHint
        )
    }

    private fun estimateTorsoCenter(points: Map<Int, PointF>): PointF {
        val lShoulder = points[PoseLandmark.LEFT_SHOULDER]
        val rShoulder = points[PoseLandmark.RIGHT_SHOULDER]
        val lHip = points[PoseLandmark.LEFT_HIP]
        val rHip = points[PoseLandmark.RIGHT_HIP]
        val sMid = midpoint(lShoulder, rShoulder)
        val hMid = midpoint(lHip, rHip)
        return when {
            sMid != null && hMid != null -> PointF((sMid.x + hMid.x) / 2f, (sMid.y + hMid.y) / 2f)
            sMid != null -> sMid
            hMid != null -> hMid
            else -> points.values.firstOrNull() ?: PointF(0f, 0f)
        }
    }

    private fun evaluateSymmetry(
        points: Map<Int, PointF>,
        bodyScale: Float,
        isPlank: Boolean
    ): Pair<Float, String> {
        var penalty = 0f
        var worstHint = "สมดุลซ้าย-ขวาดีเยี่ยม"

        val lShoulder = points[PoseLandmark.LEFT_SHOULDER]
        val rShoulder = points[PoseLandmark.RIGHT_SHOULDER]
        if (lShoulder != null && rShoulder != null) {
            val shoulderTilt = abs(lShoulder.y - rShoulder.y) / bodyScale
            if (shoulderTilt > 0.11f) {
                penalty += ((shoulderTilt - 0.11f) * 135f).coerceAtMost(24f)
                worstHint = "หัวไหล่เอียงเล็กน้อย — รักษาไหล่ให้เสมอกัน"
            }
        }

        val lHip = points[PoseLandmark.LEFT_HIP]
        val rHip = points[PoseLandmark.RIGHT_HIP]
        if (lHip != null && rHip != null) {
            val hipTilt = abs(lHip.y - rHip.y) / bodyScale
            if (hipTilt > 0.10f) {
                penalty += ((hipTilt - 0.10f) * 145f).coerceAtMost(24f)
                if (worstHint == "สมดุลซ้าย-ขวาดีเยี่ยม") {
                    worstHint = "สะโพกเอียง — ลงน้ำหนักให้เท่ากันทั้งสองข้าง"
                }
            }
        }

        val shoulderMid = midpoint(lShoulder, rShoulder)
        val hipMid = midpoint(lHip, rHip)
        val ankleMid = midpoint(points[PoseLandmark.LEFT_ANKLE], points[PoseLandmark.RIGHT_ANKLE])

        if (isPlank && shoulderMid != null && hipMid != null && ankleMid != null) {
            // Check straight plank alignment shoulder -> hip -> ankle (no hip sag / pike)
            val s2aDx = ankleMid.x - shoulderMid.x
            val s2aDy = ankleMid.y - shoulderMid.y
            val s2aLen = hypot(s2aDx, s2aDy)
            if (s2aLen > bodyScale * 0.6f) {
                val cross = abs((hipMid.x - shoulderMid.x) * s2aDy - (hipMid.y - shoulderMid.y) * s2aDx) / s2aLen
                val sagNorm = cross / bodyScale
                if (sagNorm > 0.22f) {
                    penalty += ((sagNorm - 0.22f) * 95f).coerceAtMost(22f)
                    worstHint = "สะโพกหย่อนหรือโก่งเกินไป — เกร็งแกนกลางให้ตัวตรง"
                }
            }
        } else if (!isPlank && shoulderMid != null && hipMid != null) {
            val torsoSpanX = abs(shoulderMid.x - hipMid.x) / bodyScale
            if (torsoSpanX > 0.22f) {
                penalty += ((torsoSpanX - 0.22f) * 90f).coerceAtMost(18f)
                if (worstHint == "สมดุลซ้าย-ขวาดีเยี่ยม") {
                    worstHint = "ลำตัวเอนข้าง — เกร็งแกนกลางให้ลำตัวตรง"
                }
            }
        }

        return (100f - penalty).coerceIn(48f, 100f) to worstHint
    }

    private fun evaluateFatigueGuard(stepDelta: Int, repDelta: Int, symPct: Int) {
        if (repDelta > 0 && repEngine.currentRepPeakAmplitude > 0f) {
            repAmplitudeHistory.add(repEngine.currentRepPeakAmplitude)
            if (repAmplitudeHistory.size >= 8) {
                val baseline = repAmplitudeHistory.take(4).average().toFloat().coerceAtLeast(0.05f)
                val recent = repAmplitudeHistory.takeLast(3).average().toFloat()
                val dropRatio = (baseline - recent) / baseline
                if (dropRatio >= 0.25f) {
                    lastFatigueWarning = true
                    lastFatigueHint = "แรงยก/ความลึกท่าลดลง ${(dropRatio * 100).roundToInt()}% — พักหายใจ 15 วิ หรือเช็กฟอร์ม"
                } else if (dropRatio < 0.14f) {
                    lastFatigueWarning = false
                    lastFatigueHint = ""
                }
            }
        }

        if (stepDelta > 0 && repEngine.stepIntervalHistory.size >= 24 && symPct < 68) {
            lastFatigueWarning = true
            lastFatigueHint = "สมดุลซ้าย-ขวาตกลง ($symPct%) — ลดสปีดลงเล็กน้อยและผ่อนไหล่"
        } else if (symPct >= 78 && repAmplitudeHistory.size < 8) {
            lastFatigueWarning = false
            lastFatigueHint = ""
        }
    }

    private fun medianOrZero(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
    }

    private fun handleMissingJoint(type: Int) {
        val count = (missedFrames[type] ?: 0) + 1
        missedFrames[type] = count
        if (count > 2) {
            smoothedJoints.remove(type)
            smoothedJoints3D.remove(type)
            oneEuroFilters.remove(type)
        }
    }

    private fun estimateBodyScalePx(points: Map<Int, PointF>, imgH: Float): Float {
        val lShoulder = points[PoseLandmark.LEFT_SHOULDER]
        val rShoulder = points[PoseLandmark.RIGHT_SHOULDER]
        val lHip = points[PoseLandmark.LEFT_HIP]
        val rHip = points[PoseLandmark.RIGHT_HIP]

        val shoulderMid = midpoint(lShoulder, rShoulder)
        val hipMid = midpoint(lHip, rHip)

        val torso = if (shoulderMid != null && hipMid != null) {
            hypot(shoulderMid.x - hipMid.x, shoulderMid.y - hipMid.y)
        } else {
            0f
        }
        val shoulderWidth = if (lShoulder != null && rShoulder != null) {
            hypot(lShoulder.x - rShoulder.x, lShoulder.y - rShoulder.y) * 1.40f
        } else {
            0f
        }
        val combined = max(torso, shoulderWidth)
        if (combined > 24f) return combined
        return (imgH * 0.25f).coerceAtLeast(80f)
    }

    private fun midpoint(a: PointF?, b: PointF?): PointF? {
        return when {
            a != null && b != null -> PointF((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            a != null -> a
            b != null -> b
            else -> null
        }
    }

    private fun resetInternalState() {
        oneEuroFilters.clear()
        smoothedJoints.clear()
        smoothedJoints3D.clear()
        missedFrames.clear()
        prevSmoothedJoints = emptyMap()
        lastFrameMillis = 0L
        activeMotionStreak = 0
        lastMotionSeenMillis = 0L
        repEngine.reset()
        smoothedSymmetry = 96f
        smoothedFormScore = 80f
        repAmplitudeHistory.clear()
        lastFatigueWarning = false
        lastFatigueHint = ""
        anchorTracker.clearLock()
    }

    fun resetTracking() {
        synchronized(stateLock) {
            resetInternalState()
        }
    }

    fun close() {
        detector.close()
    }
}

class StretchPoseAnalyzer(
    private val stretchTypeProvider: () -> StretchAnimationType,
    private val sensitivityProvider: () -> DetectionSensitivity,
    private val isFrontCameraProvider: () -> Boolean,
    private val onStretchEvaluated: (StretchFormFeedback) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .setPreferredHardwareConfigs(AccuratePoseDetectorOptions.CPU_GPU)
            .build()
    )

    private val stateLock = Any()
    private val anchorTracker = SubjectAnchorTracker()
    private val oneEuroFilters = mutableMapOf<Int, OneEuroFilter3D>()
    private val smoothedJoints = mutableMapOf<Int, PointF>()
    private var smoothedScore = 60f
    private var lastFrameMillis = 0L

    @Volatile
    private var lastImgW: Float = 480f
    @Volatile
    private var lastImgH: Float = 640f

    fun lockSubjectAtTap(tapX: Float, tapY: Float, viewW: Float, viewH: Float) {
        synchronized(stateLock) {
            oneEuroFilters.clear()
            smoothedJoints.clear()
            anchorTracker.lockAtScreenTap(
                tapX = tapX,
                tapY = tapY,
                viewW = viewW,
                viewH = viewH,
                isMirrored = isFrontCameraProvider(),
                imgW = lastImgW,
                imgH = lastImgH
            )
        }
    }

    fun relockPrimarySubject() {
        synchronized(stateLock) {
            oneEuroFilters.clear()
            smoothedJoints.clear()
            anchorTracker.clearLock()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val isRotated = rotation == 90 || rotation == 270
        val imgW = if (isRotated) imageProxy.height.toFloat() else imageProxy.width.toFloat()
        val imgH = if (isRotated) imageProxy.width.toFloat() else imageProxy.height.toFloat()
        lastImgW = imgW
        lastImgH = imgH

        val mirrorX = isFrontCameraProvider()
        val stretchType = stretchTypeProvider()
        val sensitivity = sensitivityProvider()

        val roiCrop = synchronized(stateLock) {
            if (anchorTracker.shouldUseRoiCrop()) {
                anchorTracker.consumeRoiFrameTick()
                anchorTracker.computeRoiRect(imgW, imgH)?.let { rect ->
                    extractUprightRoiCrop(imageProxy, rotation, rect)
                }
            } else {
                null
            }
        }

        val inputImage = if (roiCrop != null) {
            InputImage.fromBitmap(roiCrop.bitmap, 0)
        } else {
            InputImage.fromMediaImage(mediaImage, rotation)
        }
        val offsetX = roiCrop?.left?.toFloat() ?: 0f
        val offsetY = roiCrop?.top?.toFloat() ?: 0f

        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                onStretchEvaluated(
                    evaluateStretchPose(pose, imgW, imgH, offsetX, offsetY, mirrorX, stretchType, sensitivity)
                )
            }
            .addOnFailureListener {
                onStretchEvaluated(
                    StretchFormFeedback(
                        snapshot = PoseSkeletonSnapshot(imageWidth = imgW, imageHeight = imgH, isMirrored = mirrorX),
                        isPostureCorrect = false,
                        formScore = 0,
                        feedbackText = "ไม่พบตัวในกล้อง • ถอยหลังให้เห็นร่างกายชัดเจน"
                    )
                )
            }
            .addOnCompleteListener {
                runCatching { roiCrop?.bitmap?.recycle() }
                imageProxy.close()
            }
    }

    private fun evaluateStretchPose(
        pose: Pose,
        imgW: Float,
        imgH: Float,
        offsetX: Float,
        offsetY: Float,
        mirrorX: Boolean,
        stretchType: StretchAnimationType,
        sensitivity: DetectionSensitivity
    ): StretchFormFeedback = synchronized(stateLock) {
        val trackedTypes = listOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW,
            PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE
        )

        val rawPoints = mutableMapOf<Int, PointF>()
        for (t in trackedTypes) {
            val lm = pose.getPoseLandmark(t)
            if (lm != null && lm.inFrameLikelihood >= sensitivity.limbConfidence) {
                rawPoints[t] = PointF(lm.position.x + offsetX, lm.position.y + offsetY)
            }
        }

        val lS = rawPoints[PoseLandmark.LEFT_SHOULDER]
        val rS = rawPoints[PoseLandmark.RIGHT_SHOULDER]
        val lH = rawPoints[PoseLandmark.LEFT_HIP]
        val rH = rawPoints[PoseLandmark.RIGHT_HIP]
        val isVisible = rawPoints.size >= 5 && (lS != null || rS != null)
        val now = System.currentTimeMillis()

        if (!isVisible) {
            return@synchronized StretchFormFeedback(
                snapshot = PoseSkeletonSnapshot(
                    rawPoints = emptyMap(),
                    imageWidth = imgW,
                    imageHeight = imgH,
                    isMirrored = mirrorX,
                    isBodyVisible = false,
                    isSubjectLocked = anchorTracker.isLocked,
                    isForeignPersonBlocked = anchorTracker.isRecentlyBlockingForeign(now),
                    lockCenterX = anchorTracker.anchorCenterX,
                    lockCenterY = anchorTracker.anchorCenterY,
                    lockRadiusX = anchorTracker.lockRadiusX(imgW),
                    lockRadiusY = anchorTracker.lockRadiusY(imgH)
                ),
                isPostureCorrect = false,
                formScore = 0,
                feedbackText = "ยังไม่พบตัวในกล้อง • ถอยหลังให้เห็นท่ายืดชัดเจน"
            )
        }

        val sMidRaw = mid(lS, rS)
        val hMidRaw = mid(lH, rH)
        val torsoCenter = when {
            sMidRaw != null && hMidRaw != null -> PointF((sMidRaw.x + hMidRaw.x) / 2f, (sMidRaw.y + hMidRaw.y) / 2f)
            sMidRaw != null -> sMidRaw
            hMidRaw != null -> hMidRaw
            else -> rawPoints.values.first()
        }
        val bodyScale = if (sMidRaw != null && hMidRaw != null) {
            hypot(sMidRaw.x - hMidRaw.x, sMidRaw.y - hMidRaw.y).coerceAtLeast(36f)
        } else {
            (imgH * 0.25f).coerceAtLeast(60f)
        }

        if (!anchorTracker.validateAndTrack(torsoCenter.x, torsoCenter.y, bodyScale, imgW, imgH, now)) {
            return@synchronized StretchFormFeedback(
                snapshot = PoseSkeletonSnapshot(
                    rawPoints = smoothedJoints.toMap(),
                    imageWidth = imgW,
                    imageHeight = imgH,
                    isMirrored = mirrorX,
                    isBodyVisible = false,
                    isSubjectLocked = anchorTracker.isLocked,
                    isForeignPersonBlocked = true,
                    lockCenterX = anchorTracker.anchorCenterX,
                    lockCenterY = anchorTracker.anchorCenterY,
                    lockRadiusX = anchorTracker.lockRadiusX(imgW),
                    lockRadiusY = anchorTracker.lockRadiusY(imgH)
                ),
                isPostureCorrect = false,
                formScore = smoothedScore.roundToInt(),
                feedbackText = "พบคน/สิ่งอื่นผ่านหน้ากล้อง • ล็อกตัวคุณไว้แล้ว"
            )
        }

        val dtSec = if (lastFrameMillis > 0L) ((now - lastFrameMillis) / 1000f).coerceIn(0.01f, 0.25f) else 0.033f
        lastFrameMillis = now

        val points = mutableMapOf<Int, PointF>()
        for ((t, rawPt) in rawPoints) {
            val filter = oneEuroFilters.getOrPut(t) { OneEuroFilter3D(minCutoff = 1.4f, beta = 0.038f) }
            val filtered = filter.filter(Point3D(rawPt.x, rawPt.y, 0f), dtSec, bodyScale)
            val pt = PointF(filtered.x, filtered.y)
            smoothedJoints[t] = pt
            points[t] = pt
        }

        val lShoulder = points[PoseLandmark.LEFT_SHOULDER]
        val rShoulder = points[PoseLandmark.RIGHT_SHOULDER]
        val lHip = points[PoseLandmark.LEFT_HIP]
        val rHip = points[PoseLandmark.RIGHT_HIP]
        val shoulderMid = mid(lShoulder, rShoulder)
        val hipMid = mid(lHip, rHip)
        val torsoPx = if (shoulderMid != null && hipMid != null) {
            hypot(shoulderMid.x - hipMid.x, shoulderMid.y - hipMid.y).coerceAtLeast(36f)
        } else {
            (imgH * 0.25f).coerceAtLeast(60f)
        }

        val lWrist = points[PoseLandmark.LEFT_WRIST]
        val rWrist = points[PoseLandmark.RIGHT_WRIST]
        val lElbow = points[PoseLandmark.LEFT_ELBOW]
        val rElbow = points[PoseLandmark.RIGHT_ELBOW]
        val lKnee = points[PoseLandmark.LEFT_KNEE]
        val rKnee = points[PoseLandmark.RIGHT_KNEE]
        val lAnkle = points[PoseLandmark.LEFT_ANKLE]
        val rAnkle = points[PoseLandmark.RIGHT_ANKLE]

        val (rawScore, tip) = when (stretchType) {
            StretchAnimationType.NECK_SHOULDER_CIRCLE -> {
                val levelDiff = if (lShoulder != null && rShoulder != null) abs(lShoulder.y - rShoulder.y) / torsoPx else 0f
                val ok = levelDiff < 0.22f
                (if (ok) 92f else 68f) to if (ok) "ท่าดีเยี่ยม! หมุนหัวไหล่และคลายคอช้าๆ" else "ตั้งศีรษะและหัวไหล่ให้สมดุลกัน"
            }

            StretchAnimationType.ARM_ACROSS_CHEST -> {
                val midX = shoulderMid?.x ?: (imgW * 0.5f)
                val crossed = (lWrist != null && shoulderMid != null && abs(lWrist.y - shoulderMid.y) < torsoPx * 0.45f && abs(lWrist.x - midX) < torsoPx * 0.6f) ||
                    (rWrist != null && shoulderMid != null && abs(rWrist.y - shoulderMid.y) < torsoPx * 0.45f && abs(rWrist.x - midX) < torsoPx * 0.6f)
                (if (crossed) 94f else 64f) to if (crossed) "แขนพาดอกในระดับไหล่ถูกต้อง!" else "ยกแขนพาดขวางหน้าอกในระดับหัวไหล่"
            }

            StretchAnimationType.OVERHEAD_TRICEPS -> {
                val up = (shoulderMid != null && ((lElbow != null && lElbow.y < shoulderMid.y - torsoPx * 0.1f) || (rElbow != null && rElbow.y < shoulderMid.y - torsoPx * 0.1f)))
                (if (up) 94f else 62f) to if (up) "ศอกยกเหนือไหล่ถูกต้อง ค้างท่าไว้" else "ยกแขนขึ้นเหนือศีรษะแล้วพับศอกไปด้านหลัง"
            }

            StretchAnimationType.WRIST_FOREARM -> {
                val extended = (shoulderMid != null && ((lWrist != null && abs(lWrist.y - shoulderMid.y) < torsoPx * 0.55f) || (rWrist != null && abs(rWrist.y - shoulderMid.y) < torsoPx * 0.55f)))
                (if (extended) 90f else 65f) to if (extended) "ยืดข้อมือด้านหน้าได้ดีมาก" else "เหยียดแขนระดับอกเพื่อยืดข้อมือให้ชัดเจน"
            }

            StretchAnimationType.HIP_ROTATION -> {
                val wideStance = if (lAnkle != null && rAnkle != null) abs(lAnkle.x - rAnkle.x) > torsoPx * 0.28f else true
                (if (wideStance) 91f else 70f) to if (wideStance) "ยืนมั่นคง หมุนสะโพกช้าๆ เป็นวงกลม" else "แยกเท้ากว้างกว่าช่วงสะโพกเล็กน้อย"
            }

            StretchAnimationType.QUAD_PULL -> {
                val oneLegBent = (lAnkle != null && rAnkle != null && abs(lAnkle.y - rAnkle.y) > torsoPx * 0.22f) ||
                    (lKnee != null && rKnee != null && abs(lKnee.y - rKnee.y) > torsoPx * 0.10f)
                (if (oneLegBent) 93f else 60f) to if (oneLegBent) "พับขาหลังดึงหน้าขาได้ดีเยี่ยม!" else "พับขาข้างหนึ่งไปด้านหลังแล้วจับข้อเท้าดึงเบาๆ"
            }

            StretchAnimationType.HAMSTRING_REACH -> {
                val folded = if (shoulderMid != null && hipMid != null) {
                    val verticalSpan = abs(hipMid.y - shoulderMid.y) / torsoPx
                    verticalSpan < 0.85f || (lWrist != null && lWrist.y > hipMid.y) || (rWrist != null && rWrist.y > hipMid.y)
                } else false
                (if (folded) 93f else 62f) to if (folded) "พับตัวจากสะโพกยืดหลังขาถูกต้อง!" else "พับลำตัวลงจากสะโพก เอื้อมมือหาหน้าแข้ง"
            }

            StretchAnimationType.CALF_LUNGE -> {
                val staggered = (lAnkle != null && rAnkle != null && hypot(lAnkle.x - rAnkle.x, lAnkle.y - rAnkle.y) > torsoPx * 0.35f) ||
                    (lKnee != null && rKnee != null && abs(lKnee.x - rKnee.x) > torsoPx * 0.25f)
                (if (staggered) 92f else 64f) to if (staggered) "ก้าวแยกขากดส้นเท้าหลังยืดน่องได้ดี" else "ก้าวขาหนึ่งข้างไปด้านหน้าและงอเข่าหน้า"
            }

            StretchAnimationType.HIGH_KNEES_MARCH -> {
                val highKnee = hipMid != null && ((lKnee != null && lKnee.y < hipMid.y + torsoPx * 0.25f) || (rKnee != null && rKnee.y < hipMid.y + torsoPx * 0.25f))
                (if (highKnee) 94f else 66f) to if (highKnee) "ยกเข่าสูงระดับสะโพกดีมาก!" else "ยกเข่าขึ้นมาให้ใกล้ระดับสะโพกสลับซ้าย-ขวา"
            }

            StretchAnimationType.ANKLE_CIRCLES -> {
                val legVisible = lAnkle != null || rAnkle != null
                (if (legVisible) 90f else 68f) to if (legVisible) "หมุนข้อเท้าเป็นวงกลมช้าๆ ต่อเนื่อง" else "จัดเฟรมให้เห็นข้อเท้าทั้งสองข้าง"
            }

            StretchAnimationType.DEEP_SQUAT_HIP_OPENER -> {
                val kneeMid = mid(lKnee, rKnee)
                val lowSquat = hipMid != null && kneeMid != null && (kneeMid.y - hipMid.y) / torsoPx < 0.78f
                (if (lowSquat) 95f else 63f) to if (lowSquat) "ย่อเปิดสะโพกได้ลึกและตรงแนวเข่าดีเยี่ยม!" else "ค่อยๆ ย่อสะโพกลงเปิดข้อสะโพก"
            }
        }

        smoothedScore = smoothedScore * 0.72f + rawScore * 0.28f
        val finalScore = smoothedScore.roundToInt().coerceIn(0, 100)
        val correct = finalScore >= 80

        StretchFormFeedback(
            snapshot = PoseSkeletonSnapshot(
                rawPoints = points,
                imageWidth = imgW,
                imageHeight = imgH,
                isMirrored = mirrorX,
                isBodyVisible = true,
                isMoving = correct,
                visibleJointCount = points.size,
                formScorePercent = finalScore,
                isSubjectLocked = anchorTracker.isLocked,
                isForeignPersonBlocked = anchorTracker.isRecentlyBlockingForeign(now),
                lockCenterX = anchorTracker.anchorCenterX,
                lockCenterY = anchorTracker.anchorCenterY,
                lockRadiusX = anchorTracker.lockRadiusX(imgW),
                lockRadiusY = anchorTracker.lockRadiusY(imgH)
            ),
            isPostureCorrect = correct,
            formScore = finalScore,
            feedbackText = tip
        )
    }

    private fun mid(a: PointF?, b: PointF?): PointF? = when {
        a != null && b != null -> PointF((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        a != null -> a
        b != null -> b
        else -> null
    }

    fun reset() {
        synchronized(stateLock) {
            oneEuroFilters.clear()
            smoothedJoints.clear()
            smoothedScore = 60f
            lastFrameMillis = 0L
            anchorTracker.clearLock()
        }
    }

    fun close() {
        detector.close()
    }
}

private val POSE_CONNECTIONS = listOf(
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
    PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
    PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
    PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
    PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE
)

@Composable
fun PoseOverlayCanvas(
    snapshot: PoseSkeletonSnapshot,
    modifier: Modifier = Modifier
) {
    val activeColor = when {
        snapshot.isForeignPersonBlocked -> Color(0xFFF59E0B)
        !snapshot.isBodyVisible -> Color(0xFFFBBF24)
        snapshot.fatigueWarning -> Color(0xFFF59E0B)
        snapshot.isMoving -> Color(0xFF34D399)
        else -> Color(0xFFA7F3D0)
    }

    Canvas(modifier = modifier.clipToBounds()) {
        if (snapshot.imageWidth <= 0f || snapshot.imageHeight <= 0f) return@Canvas

        // Exact PreviewView.ScaleType.FILL_CENTER projection (uniform aspect scale + center crop)
        val scale = max(size.width / snapshot.imageWidth, size.height / snapshot.imageHeight)
        val offsetX = (size.width - snapshot.imageWidth * scale) / 2f
        val offsetY = (size.height - snapshot.imageHeight * scale) / 2f

        fun toScreen(p: PointF): Offset {
            val x = if (snapshot.isMirrored) snapshot.imageWidth - p.x else p.x
            return Offset(x * scale + offsetX, p.y * scale + offsetY)
        }

        // Draw subtle Subject Anchor Lock corner brackets around the locked athlete
        if (snapshot.isSubjectLocked && snapshot.lockRadiusX > 0f && snapshot.lockRadiusY > 0f) {
            val lockCenter = toScreen(PointF(snapshot.lockCenterX, snapshot.lockCenterY))
            val rx = snapshot.lockRadiusX * scale
            val ry = snapshot.lockRadiusY * scale
            val left = (lockCenter.x - rx).coerceIn(8f, size.width - 40f)
            val top = (lockCenter.y - ry).coerceIn(8f, size.height - 40f)
            val right = (lockCenter.x + rx).coerceIn(left + 40f, size.width - 8f)
            val bottom = (lockCenter.y + ry).coerceIn(top + 40f, size.height - 8f)
            val bracketLen = ((right - left) * 0.16f).coerceIn(18f, 34f)
            val bracketColor = if (snapshot.isForeignPersonBlocked) {
                Color(0xFFF59E0B).copy(alpha = 0.92f)
            } else {
                Color(0xFF34D399).copy(alpha = 0.65f)
            }

            // Top-left
            drawLine(bracketColor, Offset(left, top), Offset(left + bracketLen, top), 5f, StrokeCap.Round)
            drawLine(bracketColor, Offset(left, top), Offset(left, top + bracketLen), 5f, StrokeCap.Round)
            // Top-right
            drawLine(bracketColor, Offset(right, top), Offset(right - bracketLen, top), 5f, StrokeCap.Round)
            drawLine(bracketColor, Offset(right, top), Offset(right, top + bracketLen), 5f, StrokeCap.Round)
            // Bottom-left
            drawLine(bracketColor, Offset(left, bottom), Offset(left + bracketLen, bottom), 5f, StrokeCap.Round)
            drawLine(bracketColor, Offset(left, bottom), Offset(left, bottom - bracketLen), 5f, StrokeCap.Round)
            // Bottom-right
            drawLine(bracketColor, Offset(right, bottom), Offset(right - bracketLen, bottom), 5f, StrokeCap.Round)
            drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - bracketLen), 5f, StrokeCap.Round)
        }

        val pts = snapshot.rawPoints
        if (pts.isEmpty()) return@Canvas

        val nose = pts[PoseLandmark.NOSE]?.let(::toScreen)
        val lShoulder = pts[PoseLandmark.LEFT_SHOULDER]?.let(::toScreen)
        val rShoulder = pts[PoseLandmark.RIGHT_SHOULDER]?.let(::toScreen)
        val shoulderMid = when {
            lShoulder != null && rShoulder != null -> Offset(
                (lShoulder.x + rShoulder.x) / 2f,
                (lShoulder.y + rShoulder.y) / 2f
            )
            lShoulder != null -> lShoulder
            rShoulder != null -> rShoulder
            else -> null
        }

        if (nose != null && shoulderMid != null) {
            drawLine(
                color = Color(0xFF0A291F).copy(alpha = 0.65f),
                start = nose,
                end = shoulderMid,
                strokeWidth = 10f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = activeColor.copy(alpha = 0.9f),
                start = nose,
                end = shoulderMid,
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            val headRadius = if (lShoulder != null && rShoulder != null) {
                (hypot(lShoulder.x - rShoulder.x, lShoulder.y - rShoulder.y) * 0.28f).coerceIn(16f, 44f)
            } else {
                24f
            }
            drawCircle(
                color = activeColor.copy(alpha = 0.85f),
                radius = headRadius,
                center = nose,
                style = Stroke(width = 4f)
            )
        }

        for ((startType, endType) in POSE_CONNECTIONS) {
            val aRaw = pts[startType]
            val bRaw = pts[endType]
            if (aRaw != null && bRaw != null) {
                val a = toScreen(aRaw)
                val b = toScreen(bRaw)
                drawLine(
                    color = Color(0xFF0A291F).copy(alpha = 0.65f),
                    start = a,
                    end = b,
                    strokeWidth = 11f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = activeColor.copy(alpha = 0.92f),
                    start = a,
                    end = b,
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }

        for ((type, p) in pts) {
            if (type == PoseLandmark.NOSE) continue
            val center = toScreen(p)
            drawCircle(
                color = Color(0xFF0A291F),
                radius = 10f,
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 7f,
                center = center
            )
            drawCircle(
                color = activeColor,
                radius = 4.5f,
                center = center
            )
        }
    }
}
