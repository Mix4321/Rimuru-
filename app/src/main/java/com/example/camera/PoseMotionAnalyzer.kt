package com.example.camera

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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

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
    val visibleJointCount: Int = 0,
    val confidencePercent: Int = 0,
    val postureHint: String = "หันกล้องให้เห็นร่างกายอย่างน้อยครึ่งตัว"
)

class PoseMotionAnalyzer(
    private val sportProvider: () -> SportType,
    private val sensitivityProvider: () -> DetectionSensitivity,
    private val isFrontCameraProvider: () -> Boolean,
    private val onMotionAnalyzed: (PoseSkeletonSnapshot) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private val stateLock = Any()
    private var lastAnalyzedSport: SportType? = null
    private val smoothedJoints = mutableMapOf<Int, PointF>()
    private val missedFrames = mutableMapOf<Int, Int>()
    private var prevSmoothedJoints: Map<Int, PointF> = emptyMap()
    private var activeMotionStreak: Int = 0
    private var lastMotionSeenMillis: Long = 0L

    // Sport-specific finite-state machine variables
    private var baselineTorsoY: Float? = null
    private var jumpState: Int = 0 // 0 = grounded, 1 = airborne
    private var lastRepMillis: Long = 0L

    private var prevGaitSign: Int = 0
    private var lastStepMillis: Long = 0L

    private var squatState: Int = 0 // 0 = standing, 1 = deep squat
    private var standingKneeHipRatio: Float? = null
    private var jackState: Int = 0 // 0 = arms down, 1 = arms overhead
    private var swingState: Int = 0 // 0 = ready, 1 = overhead prep
    private var pedalState: Int = 0 // 0 = low, 1 = high knee crank

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
        val mirrorX = isFrontCameraProvider()
        val sport = sportProvider()
        val sensitivity = sensitivityProvider()

        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                val snapshot = evaluatePoseAndMotion(pose, imgW, imgH, mirrorX, sport, sensitivity)
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
                        postureHint = "ไม่พบร่างกายในกล้อง • ลองปรับแสงหรือมุมกล้อง"
                    )
                )
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun evaluatePoseAndMotion(
        pose: Pose,
        imgW: Float,
        imgH: Float,
        mirrorX: Boolean,
        sport: SportType,
        sensitivity: DetectionSensitivity
    ): PoseSkeletonSnapshot = synchronized(stateLock) {
        if (lastAnalyzedSport != sport) {
            resetInternalState()
            lastAnalyzedSport = sport
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

        val currentPoints = mutableMapOf<Int, PointF>()
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
                val px = landmark.position.x
                val py = landmark.position.y
                val inBounds = px >= -0.04f * imgW && px <= 1.04f * imgW &&
                    py >= -0.04f * imgH && py <= 1.04f * imgH
                if (inBounds) {
                    val prev = smoothedJoints[type]
                    val smoothed = if (prev == null) {
                        PointF(px, py)
                    } else {
                        val alpha = if (type in coreTypes) 0.44f else 0.56f
                        PointF(
                            prev.x * (1f - alpha) + px * alpha,
                            prev.y * (1f - alpha) + py * alpha
                        )
                    }
                    smoothedJoints[type] = smoothed
                    missedFrames[type] = 0
                    currentPoints[type] = smoothed
                    confidenceSum += landmark.inFrameLikelihood
                    confidenceCount++
                } else {
                    handleMissingJoint(type)
                }
            } else {
                handleMissingJoint(type)
            }
        }

        val hasShoulders = currentPoints.containsKey(PoseLandmark.LEFT_SHOULDER) ||
            currentPoints.containsKey(PoseLandmark.RIGHT_SHOULDER)
        val hasHips = currentPoints.containsKey(PoseLandmark.LEFT_HIP) ||
            currentPoints.containsKey(PoseLandmark.RIGHT_HIP)
        val isBodyVisible = currentPoints.size >= 5 && (hasShoulders || hasHips)
        val avgConfPct = if (confidenceCount > 0) ((confidenceSum / confidenceCount) * 100f).roundToInt().coerceIn(0, 99) else 0

        if (!isBodyVisible) {
            prevSmoothedJoints = emptyMap()
            activeMotionStreak = 0
            baselineTorsoY = null
            jumpState = 0
            squatState = 0
            jackState = 0
            swingState = 0
            pedalState = 0
            return@synchronized PoseSkeletonSnapshot(
                rawPoints = emptyMap(),
                imageWidth = imgW,
                imageHeight = imgH,
                isMirrored = mirrorX,
                isBodyVisible = false,
                isMoving = false,
                motionIntensity = 0f,
                visibleJointCount = currentPoints.size,
                confidencePercent = avgConfPct,
                postureHint = "ยังไม่พบตัวในกล้อง • ถอยหลังให้เห็นร่างกายชัดเจน"
            )
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

        var relativeNormDispSum = 0f
        var rawNormDispSum = 0f
        for (type in matchedKeys) {
            val pt = currentPoints.getValue(type)
            val prev = prevSmoothedJoints.getValue(type)
            val dx = pt.x - prev.x
            val dy = pt.y - prev.y
            rawNormDispSum += hypot(dx, dy) / bodyScale
            relativeNormDispSum += hypot(dx - medDx, dy - medDy) / bodyScale
        }
        prevSmoothedJoints = currentPoints.toMap()

        val pairCount = matchedKeys.size
        val avgRelDisp = if (pairCount > 0) relativeNormDispSum / pairCount else 0f
        val avgRawDisp = if (pairCount > 0) rawNormDispSum / pairCount else 0f
        // For full-body vertical jumps (Jump Rope), global vertical translation counts too, while horizontal camera jitter is suppressed
        val effectiveDisp = if (sport == SportType.JUMP_ROPE) {
            max(avgRelDisp, avgRawDisp * 0.72f)
        } else {
            avgRelDisp
        }

        val now = System.currentTimeMillis()
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

        // Dispatch to Sport-Specific Biomechanical Detector
        var stepDelta = 0
        var repDelta = 0
        var sportHint = ""

        when (sport) {
            SportType.JUMP_ROPE -> {
                val res = detectJumpRope(currentPoints, bodyScale, sensitivity, now)
                repDelta = res.first
                sportHint = res.second
            }

            SportType.RUNNING, SportType.FOOTBALL -> {
                stepDelta = detectAlternatingSteps(
                    points = currentPoints,
                    bodyScale = bodyScale,
                    isMoving = isMoving,
                    minNormDiff = 0.088f * sensitivity.motionDeadzoneScale,
                    minIntervalMs = 200L,
                    now = now
                )
                sportHint = if (isMoving) "${sport.thaiName}: จับจังหวะก้าววิ่งจริง" else "${sport.thaiName}: พร้อมจับก้าววิ่ง"
            }

            SportType.WALKING -> {
                stepDelta = detectAlternatingSteps(
                    points = currentPoints,
                    bodyScale = bodyScale,
                    isMoving = isMoving,
                    minNormDiff = 0.068f * sensitivity.motionDeadzoneScale,
                    minIntervalMs = 340L,
                    now = now
                )
                sportHint = if (isMoving) "เดิน: กำลังนับก้าวตามจังหวะเดิน" else "เดิน: พร้อมนับก้าวจริง"
            }

            SportType.BASKETBALL -> {
                stepDelta = detectAlternatingSteps(
                    points = currentPoints,
                    bodyScale = bodyScale,
                    isMoving = isMoving,
                    minNormDiff = 0.085f * sensitivity.motionDeadzoneScale,
                    minIntervalMs = 220L,
                    now = now
                )
                val jumpRes = detectJumpRope(currentPoints, bodyScale, sensitivity, now)
                repDelta = jumpRes.first
                sportHint = if (isMoving) "บาสเกตบอล: จับฟุตเวิร์ก & จังหวะกระโดด" else "บาสเกตบอล: พร้อมจับท่าทาง"
            }

            SportType.BADMINTON -> {
                stepDelta = detectAlternatingSteps(
                    points = currentPoints,
                    bodyScale = bodyScale,
                    isMoving = isMoving,
                    minNormDiff = 0.075f * sensitivity.motionDeadzoneScale,
                    minIntervalMs = 220L,
                    now = now
                )
                repDelta = detectRacketSwing(currentPoints, bodyScale, sensitivity, now)
                sportHint = if (isMoving) "แบดมินตัน: จับฟุตเวิร์ก & วงสวิงไม้" else "แบดมินตัน: พร้อมจับสวิงและก้าว"
            }

            SportType.CYCLING -> {
                repDelta = detectCyclingPedal(currentPoints, bodyScale, isMoving, sensitivity, now)
                sportHint = if (isMoving) "ปั่นจักรยาน: จับรอบปั่นเข่าจริง" else "ปั่นจักรยาน: หันข้างเข้ากล้องเพื่อจับรอบขา"
            }

            SportType.GENERAL -> {
                val res = detectGeneralBodyweightReps(currentPoints, bodyScale, sensitivity, now)
                repDelta = res.first
                sportHint = res.second
            }
        }

        val displayHint = "$sportHint • แม่นยำ $avgConfPct%"

        PoseSkeletonSnapshot(
            rawPoints = currentPoints,
            imageWidth = imgW,
            imageHeight = imgH,
            isMirrored = mirrorX,
            isBodyVisible = true,
            isMoving = isMoving,
            motionIntensity = if (isMoving) poseIntensity.coerceAtLeast(0.20f) else 0f,
            stepDelta = stepDelta,
            repDelta = repDelta,
            visibleJointCount = currentPoints.size,
            confidencePercent = avgConfPct,
            postureHint = displayHint
        )
    }

    private fun detectJumpRope(
        points: Map<Int, PointF>,
        bodyScale: Float,
        sensitivity: DetectionSensitivity,
        now: Long
    ): Pair<Int, String> {
        val anchorY = computeVerticalAnchor(points) ?: return 0 to "กระโดดเชือก: รอจับลำตัว"
        val normY = anchorY / bodyScale
        val prevBase = baselineTorsoY ?: normY
        // Floor baseline adapts slowly downward/neutral so airborne peaks stand out cleanly
        val updatedBase = if (jumpState == 0) prevBase * 0.90f + normY * 0.10f else prevBase * 0.985f + normY * 0.015f
        baselineTorsoY = updatedBase

        // Note: In image coordinates, jumping UP means smaller Y!
        val liftNorm = updatedBase - normY
        val liftThreshold = 0.072f * sensitivity.motionDeadzoneScale
        val landThreshold = 0.030f * sensitivity.motionDeadzoneScale

        var rep = 0
        if (jumpState == 0 && liftNorm > liftThreshold && (now - lastRepMillis) >= 240L) {
            jumpState = 1
        } else if (jumpState == 1 && liftNorm < landThreshold) {
            jumpState = 0
            rep = 1
            lastRepMillis = now
        }
        val stateText = if (jumpState == 1) "กระโดดเชือก: กำลังลอยตัว!" else "กระโดดเชือก: จับจังหวะกระโดด"
        return rep to stateText
    }

    private fun detectAlternatingSteps(
        points: Map<Int, PointF>,
        bodyScale: Float,
        isMoving: Boolean,
        minNormDiff: Float,
        minIntervalMs: Long,
        now: Long
    ): Int {
        if (!isMoving) return 0
        val leftLeg = points[PoseLandmark.LEFT_ANKLE] ?: points[PoseLandmark.LEFT_KNEE]
        val rightLeg = points[PoseLandmark.RIGHT_ANKLE] ?: points[PoseLandmark.RIGHT_KNEE]
        if (leftLeg == null || rightLeg == null) return 0

        val normDiff = (leftLeg.y - rightLeg.y) / bodyScale
        val sign = when {
            normDiff > minNormDiff -> 1
            normDiff < -minNormDiff -> -1
            else -> prevGaitSign
        }
        var step = 0
        if (sign != 0 && sign != prevGaitSign && (now - lastStepMillis) >= minIntervalMs) {
            step = 1
            lastStepMillis = now
        }
        prevGaitSign = sign
        return step
    }

    private fun detectRacketSwing(
        points: Map<Int, PointF>,
        bodyScale: Float,
        sensitivity: DetectionSensitivity,
        now: Long
    ): Int {
        val shoulderMid = midpoint(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER]) ?: return 0
        val lWrist = points[PoseLandmark.LEFT_WRIST]
        val rWrist = points[PoseLandmark.RIGHT_WRIST]
        val topWristY = minOf(lWrist?.y ?: Float.MAX_VALUE, rWrist?.y ?: Float.MAX_VALUE)
        if (topWristY == Float.MAX_VALUE) return 0

        val upThresh = shoulderMid.y - 0.14f * bodyScale * sensitivity.motionDeadzoneScale
        val followThroughThresh = shoulderMid.y + 0.16f * bodyScale

        var rep = 0
        if (swingState == 0 && topWristY < upThresh && (now - lastRepMillis) >= 360L) {
            swingState = 1
        } else if (swingState == 1 && topWristY > followThroughThresh) {
            swingState = 0
            rep = 1
            lastRepMillis = now
        }
        return rep
    }

    private fun detectCyclingPedal(
        points: Map<Int, PointF>,
        bodyScale: Float,
        isMoving: Boolean,
        sensitivity: DetectionSensitivity,
        now: Long
    ): Int {
        if (!isMoving) return 0
        val hipMid = midpoint(points[PoseLandmark.LEFT_HIP], points[PoseLandmark.RIGHT_HIP]) ?: return 0
        val lKnee = points[PoseLandmark.LEFT_KNEE]
        val rKnee = points[PoseLandmark.RIGHT_KNEE]
        val highKneeY = minOf(lKnee?.y ?: Float.MAX_VALUE, rKnee?.y ?: Float.MAX_VALUE)
        if (highKneeY == Float.MAX_VALUE) return 0

        val kneeHeightRelHip = (hipMid.y - highKneeY) / bodyScale
        val upThresh = -0.18f
        val downThresh = -0.45f

        var rep = 0
        if (pedalState == 0 && kneeHeightRelHip > upThresh && (now - lastRepMillis) >= 280L) {
            pedalState = 1
        } else if (pedalState == 1 && kneeHeightRelHip < downThresh) {
            pedalState = 0
            rep = 1
            lastRepMillis = now
        }
        return rep
    }

    private fun detectGeneralBodyweightReps(
        points: Map<Int, PointF>,
        bodyScale: Float,
        sensitivity: DetectionSensitivity,
        now: Long
    ): Pair<Int, String> {
        var rep = 0
        var label = "ทั่วไป: จับท่าบอดี้เวท"

        // 1) Squat / Lunge detector via vertical hip-to-knee compression
        val hipMid = midpoint(points[PoseLandmark.LEFT_HIP], points[PoseLandmark.RIGHT_HIP])
        val kneeMid = midpoint(points[PoseLandmark.LEFT_KNEE], points[PoseLandmark.RIGHT_KNEE])
        if (hipMid != null && kneeMid != null) {
            val hipToKneeNorm = (kneeMid.y - hipMid.y) / bodyScale
            val baseStanding = standingKneeHipRatio ?: hipToKneeNorm.coerceAtLeast(0.65f)
            if (squatState == 0 && hipToKneeNorm > standingKneeHipRatioOrDefault(baseStanding) * 0.88f) {
                standingKneeHipRatio = baseStanding * 0.90f + hipToKneeNorm * 0.10f
            }
            val deepThresh = baseStanding * (0.64f * sensitivity.motionDeadzoneScale).coerceIn(0.48f, 0.76f)
            val standThresh = baseStanding * 0.86f

            if (squatState == 0 && hipToKneeNorm < deepThresh && (now - lastRepMillis) >= 380L) {
                squatState = 1
                label = "สควอท: ย่อตัวลงแล้ว (ดันตัวขึ้นเพื่อครบ 1 ครั้ง)"
            } else if (squatState == 1 && hipToKneeNorm > standThresh) {
                squatState = 0
                rep = 1
                lastRepMillis = now
                label = "สควอท: นับ +1 ครั้ง!"
            }
        }

        // 2) Overhead Raise / Jumping Jack detector (only when not mid-squat)
        val shoulderMid = midpoint(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER])
        val lWrist = points[PoseLandmark.LEFT_WRIST]
        val rWrist = points[PoseLandmark.RIGHT_WRIST]
        if (rep == 0 && squatState == 0 && shoulderMid != null && lWrist != null && rWrist != null) {
            val bothUp = lWrist.y < shoulderMid.y - 0.14f * bodyScale &&
                rWrist.y < shoulderMid.y - 0.14f * bodyScale
            val bothDown = lWrist.y > shoulderMid.y + 0.18f * bodyScale &&
                rWrist.y > shoulderMid.y + 0.18f * bodyScale

            if (jackState == 0 && bothUp && (now - lastRepMillis) >= 380L) {
                jackState = 1
                label = "ยกแขน/กระโดดตบ: ยกแขนขึ้นแล้ว"
            } else if (jackState == 1 && bothDown) {
                jackState = 0
                rep = 1
                lastRepMillis = now
                label = "กระโดดตบ/ยกแขน: นับ +1 ครั้ง!"
            }
        }

        return rep to label
    }

    private fun standingKneeHipRatioOrDefault(v: Float): Float = v.coerceIn(0.45f, 1.35f)

    private fun medianOrZero(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
    }

    private fun handleMissingJoint(type: Int) {
        val count = (missedFrames[type] ?: 0) + 1
        missedFrames[type] = count
        if (count > 4) {
            smoothedJoints.remove(type)
        }
    }

    private fun estimateBodyScalePx(points: Map<Int, PointF>, imgH: Float): Float {
        val lShoulder = points[PoseLandmark.LEFT_SHOULDER]
        val rShoulder = points[PoseLandmark.RIGHT_SHOULDER]
        val lHip = points[PoseLandmark.LEFT_HIP]
        val rHip = points[PoseLandmark.RIGHT_HIP]

        val shoulderMid = midpoint(lShoulder, rShoulder)
        val hipMid = midpoint(lHip, rHip)

        if (shoulderMid != null && hipMid != null) {
            val torso = hypot(shoulderMid.x - hipMid.x, shoulderMid.y - hipMid.y)
            if (torso > 24f) return torso
        }
        if (lShoulder != null && rShoulder != null) {
            val width = hypot(lShoulder.x - rShoulder.x, lShoulder.y - rShoulder.y)
            if (width > 20f) return width * 1.45f
        }
        return (imgH * 0.25f).coerceAtLeast(80f)
    }

    private fun computeVerticalAnchor(points: Map<Int, PointF>): Float? {
        val hipMid = midpoint(points[PoseLandmark.LEFT_HIP], points[PoseLandmark.RIGHT_HIP])
        if (hipMid != null) return hipMid.y
        val shoulderMid = midpoint(points[PoseLandmark.LEFT_SHOULDER], points[PoseLandmark.RIGHT_SHOULDER])
        return shoulderMid?.y
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
        smoothedJoints.clear()
        missedFrames.clear()
        prevSmoothedJoints = emptyMap()
        activeMotionStreak = 0
        lastMotionSeenMillis = 0L
        baselineTorsoY = null
        jumpState = 0
        lastRepMillis = 0L
        prevGaitSign = 0
        lastStepMillis = 0L
        squatState = 0
        standingKneeHipRatio = null
        jackState = 0
        swingState = 0
        pedalState = 0
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
        !snapshot.isBodyVisible -> Color(0xFFFBBF24)
        snapshot.isMoving -> Color(0xFF34D399)
        else -> Color(0xFFA7F3D0)
    }

    Canvas(modifier = modifier.clipToBounds()) {
        val pts = snapshot.rawPoints
        if (pts.isEmpty() || snapshot.imageWidth <= 0f || snapshot.imageHeight <= 0f) return@Canvas

        // Exact PreviewView.ScaleType.FILL_CENTER projection (uniform aspect scale + center crop)
        val scale = max(size.width / snapshot.imageWidth, size.height / snapshot.imageHeight)
        val offsetX = (size.width - snapshot.imageWidth * scale) / 2f
        val offsetY = (size.height - snapshot.imageHeight * scale) / 2f

        fun toScreen(p: PointF): Offset {
            val x = if (snapshot.isMirrored) snapshot.imageWidth - p.x else p.x
            return Offset(x * scale + offsetX, p.y * scale + offsetY)
        }

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
