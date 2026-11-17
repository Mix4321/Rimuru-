package com.example.camera

import android.graphics.PointF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.abs
import kotlin.math.hypot

data class PoseSkeletonSnapshot(
    val normalizedPoints: Map<Int, PointF> = emptyMap(),
    val isBodyVisible: Boolean = false,
    val isMoving: Boolean = false,
    val motionIntensity: Float = 0f,
    val stepImpulse: Boolean = false
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

    private var prevJoints: Map<Int, PointF> = emptyMap()
    private var prevLumaSample: IntArray? = null
    private var prevAnkleDiffSign: Int = 0
    private var lastStepMillis: Long = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val lumaEnergy = computeSubsampledLumaEnergy(imageProxy)
        val rotation = imageProxy.imageInfo.rotationDegrees
        val isRotated = rotation == 90 || rotation == 270
        val imgW = if (isRotated) imageProxy.height.toFloat() else imageProxy.width.toFloat()
        val imgH = if (isRotated) imageProxy.width.toFloat() else imageProxy.height.toFloat()
        val mirrorX = isFrontCameraProvider()

        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                val snapshot = evaluatePoseAndMotion(pose, imgW, imgH, mirrorX, lumaEnergy)
                onMotionAnalyzed(snapshot)
            }
            .addOnFailureListener {
                val movingFromLuma = lumaEnergy > 0.045f
                onMotionAnalyzed(
                    PoseSkeletonSnapshot(
                        isBodyVisible = false,
                        isMoving = movingFromLuma,
                        motionIntensity = (lumaEnergy * 6f).coerceIn(0f, 1f),
                        stepImpulse = false
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
        lumaEnergy: Float
    ): PoseSkeletonSnapshot {
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
            if (landmark != null && landmark.inFrameLikelihood >= 0.45f && imgW > 0f && imgH > 0f) {
                var nx = (landmark.position.x / imgW).coerceIn(0f, 1f)
                val ny = (landmark.position.y / imgH).coerceIn(0f, 1f)
                if (mirrorX) nx = 1f - nx
                currentPoints[type] = PointF(nx, ny)
            }
        }

        val isBodyVisible = currentPoints.size >= 4
        var totalDisp = 0f
        var pairCount = 0
        for ((type, pt) in currentPoints) {
            val prev = prevJoints[type]
            if (prev != null) {
                totalDisp += hypot(pt.x - prev.x, pt.y - prev.y)
                pairCount++
            }
        }
        prevJoints = currentPoints

        val avgDisp = if (pairCount > 0) totalDisp / pairCount else 0f
        val poseIntensity = (avgDisp * 28f).coerceIn(0f, 1f)
        val combinedIntensity = maxOf(poseIntensity, (lumaEnergy * 5.5f).coerceIn(0f, 1f))
        val isMoving = (isBodyVisible && avgDisp > 0.0085f) || lumaEnergy > 0.045f

        var stepImpulse = false
        val now = System.currentTimeMillis()
        val leftAnkle = currentPoints[PoseLandmark.LEFT_ANKLE] ?: currentPoints[PoseLandmark.LEFT_HIP]
        val rightAnkle = currentPoints[PoseLandmark.RIGHT_ANKLE] ?: currentPoints[PoseLandmark.RIGHT_HIP]
        if (leftAnkle != null && rightAnkle != null && isMoving) {
            val diff = leftAnkle.y - rightAnkle.y
            val sign = when {
                diff > 0.015f -> 1
                diff < -0.015f -> -1
                else -> prevAnkleDiffSign
            }
            if (sign != 0 && sign != prevAnkleDiffSign && (now - lastStepMillis) > 260L) {
                stepImpulse = true
                lastStepMillis = now
            }
            prevAnkleDiffSign = sign
        }

        return PoseSkeletonSnapshot(
            normalizedPoints = currentPoints,
            isBodyVisible = isBodyVisible,
            isMoving = isMoving,
            motionIntensity = combinedIntensity,
            stepImpulse = stepImpulse
        )
    }

    private fun computeSubsampledLumaEnergy(imageProxy: ImageProxy): Float {
        val plane = imageProxy.planes.firstOrNull() ?: return 0f
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val width = imageProxy.width
        val height = imageProxy.height
        val grid = 12
        val sample = IntArray(grid * grid)
        var idx = 0
        val stepX = (width / grid).coerceAtLeast(1)
        val stepY = (height / grid).coerceAtLeast(1)

        for (gy in 0 until grid) {
            val y = (gy * stepY).coerceAtMost(height - 1)
            for (gx in 0 until grid) {
                val x = (gx * stepX).coerceAtMost(width - 1)
                val pos = y * rowStride + x
                sample[idx++] = if (pos in 0 until buffer.limit()) buffer.get(pos).toInt() and 0xFF else 0
            }
        }

        val prev = prevLumaSample
        prevLumaSample = sample
        if (prev == null || prev.size != sample.size) return 0f

        var diffSum = 0
        for (i in sample.indices) {
            diffSum += abs(sample[i] - prev[i])
        }
        return diffSum.toFloat() / (sample.size * 255f)
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
    val activeColor = if (snapshot.isMoving) Color(0xFF34D399) else Color(0xFFFBBF24)
    Canvas(modifier = modifier) {
        val pts = snapshot.normalizedPoints
        if (pts.isEmpty()) return@Canvas

        for ((startType, endType) in POSE_CONNECTIONS) {
            val a = pts[startType]
            val b = pts[endType]
            if (a != null && b != null) {
                drawLine(
                    color = activeColor.copy(alpha = 0.85f),
                    start = Offset(a.x * size.width, a.y * size.height),
                    end = Offset(b.x * size.width, b.y * size.height),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }

        for ((_, p) in pts) {
            val center = Offset(p.x * size.width, p.y * size.height)
            drawCircle(
                color = Color.White,
                radius = 9f,
                center = center
            )
            drawCircle(
                color = activeColor,
                radius = 6f,
                center = center
            )
        }
    }
}
