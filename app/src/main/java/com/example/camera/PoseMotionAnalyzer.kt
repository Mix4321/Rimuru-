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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

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
    val postureHint: String = "หันกล้องให้เห็นร่างกายอย่างน้อยครึ่งตัว"
)

class PoseMotionAnalyzer(
    private val isFrontCameraProvider: () -> Boolean,
    private val onMotionAnalyzed: (PoseSkeletonSnapshot) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private val stateLock = Any()
    private val smoothedJoints = mutableMapOf<Int, PointF>()
    private val missedFrames = mutableMapOf<Int, Int>()
    private var prevSmoothedJoints: Map<Int, PointF> = emptyMap()
    private var lastMotionSeenMillis: Long = 0L

    // Scale-normalized rep (jump/squat) & alternating step detector state
    private var baselineCenterY: Float? = null
    private var repState: Int = 0 // 0 = neutral, 1 = excursion active
    private var lastRepMillis: Long = 0L
    private var prevAnkleSign: Int = 0
    private var lastStepMillis: Long = 0L

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

        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                val snapshot = evaluatePoseAndMotion(pose, imgW, imgH, mirrorX)
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
        mirrorX: Boolean
    ): PoseSkeletonSnapshot = synchronized(stateLock) {
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
        for (type in trackedTypes) {
            val landmark = pose.getPoseLandmark(type)
            val minConfidence = if (type in coreTypes) 0.65f else 0.56f
            if (landmark != null && landmark.inFrameLikelihood >= minConfidence && imgW > 0f && imgH > 0f) {
                val px = landmark.position.x
                val py = landmark.position.y
                // Reject hallucinated landmarks projected far outside the visible camera frame
                val inBounds = px >= -0.06f * imgW && px <= 1.06f * imgW &&
                    py >= -0.06f * imgH && py <= 1.06f * imgH
                if (inBounds) {
                    val prev = smoothedJoints[type]
                    val smoothed = if (prev == null) {
                        PointF(px, py)
                    } else {
                        val alpha = if (type in coreTypes) 0.42f else 0.54f
                        PointF(
                            prev.x * (1f - alpha) + px * alpha,
                            prev.y * (1f - alpha) + py * alpha
                        )
                    }
                    smoothedJoints[type] = smoothed
                    missedFrames[type] = 0
                    currentPoints[type] = smoothed
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

        if (!isBodyVisible) {
            prevSmoothedJoints = emptyMap()
            baselineCenterY = null
            repState = 0
            return@synchronized PoseSkeletonSnapshot(
                rawPoints = emptyMap(),
                imageWidth = imgW,
                imageHeight = imgH,
                isMirrored = mirrorX,
                isBodyVisible = false,
                isMoving = false,
                motionIntensity = 0f,
                visibleJointCount = currentPoints.size,
                postureHint = "ยังไม่พบตัวในกล้อง • ถอยหลังให้เห็นครึ่งตัวหรือทั้งตัว"
            )
        }

        val bodyScale = estimateBodyScalePx(currentPoints, imgH)

        var totalNormalizedDisp = 0f
        var pairCount = 0
        for ((type, pt) in currentPoints) {
            val prev = prevSmoothedJoints[type]
            if (prev != null) {
                val distPx = hypot(pt.x - prev.x, pt.y - prev.y)
                totalNormalizedDisp += distPx / bodyScale
                pairCount++
            }
        }
        prevSmoothedJoints = currentPoints.toMap()

        val avgNormDisp = if (pairCount > 0) totalNormalizedDisp / pairCount else 0f
        val now = System.currentTimeMillis()
        // Deadzone filters camera sensor noise while catching genuine body movement at any distance
        val instantMoving = avgNormDisp > 0.022f
        if (instantMoving) {
            lastMotionSeenMillis = now
        }
        val isMoving = instantMoving || (now - lastMotionSeenMillis) <= 550L
        val poseIntensity = ((avgNormDisp - 0.014f) / 0.14f).coerceIn(0f, 1f)

        // Real vertical oscillation rep detector (jumps, squats, burpees)
        var repDelta = 0
        val refY = computeVerticalAnchor(currentPoints)
        if (refY != null) {
            val normY = refY / bodyScale
            val prevBaseline = baselineCenterY ?: normY
            val updatedBaseline = prevBaseline * 0.92f + normY * 0.08f
            baselineCenterY = updatedBaseline

            val excursion = abs(normY - updatedBaseline)
            if (repState == 0 && excursion > 0.11f && (now - lastRepMillis) >= 280L) {
                repState = 1
            } else if (repState == 1 && excursion < 0.055f) {
                repState = 0
                repDelta = 1
                lastRepMillis = now
            }
        }

        // Real alternating leg/ankle lift detector (steps in place / running / footwork)
        var stepDelta = 0
        val leftLimb = currentPoints[PoseLandmark.LEFT_ANKLE] ?: currentPoints[PoseLandmark.LEFT_KNEE]
        val rightLimb = currentPoints[PoseLandmark.RIGHT_ANKLE] ?: currentPoints[PoseLandmark.RIGHT_KNEE]
        if (leftLimb != null && rightLimb != null && isMoving) {
            val normDiff = (leftLimb.y - rightLimb.y) / bodyScale
            val sign = when {
                normDiff > 0.085f -> 1
                normDiff < -0.085f -> -1
                else -> prevAnkleSign
            }
            if (sign != 0 && sign != prevAnkleSign && (now - lastStepMillis) >= 260L) {
                stepDelta = 1
                lastStepMillis = now
            }
            prevAnkleSign = sign
        }

        val hint = when {
            isMoving && currentPoints.size >= 9 -> "จับตัวเต็มชัดเจน • กำลังเคลื่อนไหว"
            isMoving -> "จับท่าทางได้ • กำลังเคลื่อนไหว"
            currentPoints.size >= 9 -> "พบโครงกระดูกเต็มตัว • ขยับร่างกายเพื่อนับ"
            else -> "พบโครงกระดูก (${currentPoints.size} จุด) • ขยับตัวเพื่อนับ"
        }

        PoseSkeletonSnapshot(
            rawPoints = currentPoints,
            imageWidth = imgW,
            imageHeight = imgH,
            isMirrored = mirrorX,
            isBodyVisible = true,
            isMoving = isMoving,
            motionIntensity = if (isMoving) poseIntensity.coerceAtLeast(0.18f) else 0f,
            stepDelta = stepDelta,
            repDelta = repDelta,
            visibleJointCount = currentPoints.size,
            postureHint = hint
        )
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

    fun resetTracking() {
        synchronized(stateLock) {
            smoothedJoints.clear()
            missedFrames.clear()
            prevSmoothedJoints = emptyMap()
            baselineCenterY = null
            repState = 0
            prevAnkleSign = 0
            lastMotionSeenMillis = 0L
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

        // Draw neck & head circle if nose and at least one shoulder are visible
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

        // Draw anatomical bones with dark contrast halo so skeleton stays visible on bright scenes
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

        // Draw crisp joint landmarks
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
