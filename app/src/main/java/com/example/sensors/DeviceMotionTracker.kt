package com.example.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.local.DetectionSensitivity
import com.example.data.model.SportType
import kotlin.math.abs
import kotlin.math.sqrt

data class SensorMotionSnapshot(
    val isMoving: Boolean = false,
    val motionIntensity: Float = 0f,
    val hasHardwareStepSensor: Boolean = false,
    val hasGyroShakeGuard: Boolean = false,
    val sensorSourceLabel: String = "เซนเซอร์ตรวจจับการเคลื่อนไหว"
)

class DeviceMotionTracker(
    private val context: Context,
    private val sportProvider: () -> SportType,
    private val sensitivityProvider: () -> DetectionSensitivity,
    private val onStepDetected: (Int) -> Unit,
    private val onJumpRepDetected: (Int) -> Unit,
    private val onMotionStateChanged: (SensorMotionSnapshot) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val linearAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rawAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val gyroscopeSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var isStarted = false
    private var usingHardwareStepSensor = false
    private var usingGyroGuard = false
    private var lastCounterTotal: Float? = null

    // Gravity orientation vector so vertical jump/gait impulses are separated from pocket sway
    private val gravity = floatArrayOf(0f, 9.81f, 0f)
    private var gravityInitialized = false

    private var wasBelowStepThreshold = true
    private var lastAccelStepMillis = 0L
    private var wasBelowRepThreshold = true
    private var lastRepMillis = 0L
    private var lastHighGyroTwistMillis = 0L

    private var smoothedAccelMag = 0f
    private var verticalDcFloor = 0f
    private var lastMotionPublishMillis = 0L

    fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun start() {
        stop()
        lastCounterTotal = null
        gravityInitialized = false
        smoothedAccelMag = 0f
        verticalDcFloor = 0f
        lastHighGyroTwistMillis = 0L
        wasBelowStepThreshold = true
        wasBelowRepThreshold = true

        val sport = sportProvider()
        val canUseStepSensor = hasActivityRecognitionPermission() && sport.supportsSteps
        usingHardwareStepSensor = false

        if (canUseStepSensor) {
            when {
                stepDetectorSensor != null -> {
                    // maxReportLatencyUs = 0 disables hardware FIFO batching for zero-lag step counting
                    usingHardwareStepSensor = sensorManager.registerListener(
                        this,
                        stepDetectorSensor,
                        SensorManager.SENSOR_DELAY_FASTEST,
                        0
                    )
                }
                stepCounterSensor != null -> {
                    usingHardwareStepSensor = sensorManager.registerListener(
                        this,
                        stepCounterSensor,
                        SensorManager.SENSOR_DELAY_GAME,
                        0
                    )
                }
            }
        }

        if (gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME, 0)
        }
        usingGyroGuard = if (gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME, 0)
        } else {
            false
        }
        val motionSensor = linearAccelSensor ?: rawAccelSensor
        if (motionSensor != null) {
            sensorManager.registerListener(this, motionSensor, SensorManager.SENSOR_DELAY_GAME, 0)
        }
        // Always register raw accelerometer gently if using linearAccel without dedicated gravity sensor
        if (linearAccelSensor != null && gravitySensor == null && rawAccelSensor != null) {
            sensorManager.registerListener(this, rawAccelSensor, SensorManager.SENSOR_DELAY_UI)
        }

        isStarted = true
        publishSnapshot(isMoving = false, intensity = 0f)
    }

    fun stop() {
        if (isStarted) {
            sensorManager.unregisterListener(this)
            isStarted = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isStarted) return
        val now = System.currentTimeMillis()
        val sport = sportProvider()
        val sensitivity = sensitivityProvider()

        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                if (sport.supportsSteps) {
                    val count = event.values.size.coerceAtLeast(1)
                    onStepDetected(count)
                }
            }

            Sensor.TYPE_STEP_COUNTER -> {
                val total = event.values[0]
                val prev = lastCounterTotal
                lastCounterTotal = total
                if (sport.supportsSteps && prev != null) {
                    val delta = (total - prev).toInt()
                    if (delta in 1..100) {
                        onStepDetected(delta)
                    }
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val wx = event.values[0]
                val wy = event.values[1]
                val wz = event.values[2]
                val gyroMag = sqrt(wx * wx + wy * wy + wz * wz)
                // Suppress sudden hand twist / pickup spikes (> ~205 deg/s) from faking steps or jumps
                if (gyroMag > 3.6f) {
                    lastHighGyroTwistMillis = now
                }
            }

            Sensor.TYPE_GRAVITY -> {
                gravity[0] = event.values[0]
                gravity[1] = event.values[1]
                gravity[2] = event.values[2]
                gravityInitialized = true
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val totalMag = sqrt(ax * ax + ay * ay + az * az)
                val verticalAxisMag = projectOntoVertical(ax, ay, az)
                processAcceleration(totalMag, verticalAxisMag, sport, sensitivity, now)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val alpha = 0.85f
                if (!gravityInitialized) {
                    gravity[0] = event.values[0]
                    gravity[1] = event.values[1]
                    gravity[2] = event.values[2]
                    gravityInitialized = true
                } else {
                    gravity[0] = alpha * gravity[0] + (1f - alpha) * event.values[0]
                    gravity[1] = alpha * gravity[1] + (1f - alpha) * event.values[1]
                    gravity[2] = alpha * gravity[2] + (1f - alpha) * event.values[2]
                }
                // Only drive motion loop from raw accelerometer if hardware linearAccel isn't registered
                if (linearAccelSensor == null) {
                    val lx = event.values[0] - gravity[0]
                    val ly = event.values[1] - gravity[1]
                    val lz = event.values[2] - gravity[2]
                    val totalMag = sqrt(lx * lx + ly * ly + lz * lz)
                    val verticalAxisMag = projectOntoVertical(lx, ly, lz)
                    processAcceleration(totalMag, verticalAxisMag, sport, sensitivity, now)
                }
            }
        }
    }

    private fun projectOntoVertical(lx: Float, ly: Float, lz: Float): Float {
        val gMag = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
        if (gMag < 1f) return sqrt(lx * lx + ly * ly + lz * lz)
        val dot = (lx * gravity[0] + ly * gravity[1] + lz * gravity[2]) / gMag
        return abs(dot)
    }

    private fun processAcceleration(
        totalMag: Float,
        verticalMag: Float,
        sport: SportType,
        sensitivity: DetectionSensitivity,
        now: Long
    ) {
        smoothedAccelMag = smoothedAccelMag * 0.70f + totalMag * 0.30f
        verticalDcFloor = verticalDcFloor * 0.96f + verticalMag.coerceAtMost(1.2f) * 0.04f
        val dynamicVertical = (verticalMag - verticalDcFloor * 0.65f).coerceAtLeast(0f)
        val scale = sensitivity.motionDeadzoneScale
        val gyroCalm = !usingGyroGuard || (now - lastHighGyroTwistMillis) > 360L

        // Fallback step detection ONLY for step-supported sports when hardware pedometer isn't active
        if (!usingHardwareStepSensor && sport.supportsSteps) {
            val minIntervalMs = when (sport) {
                SportType.WALKING -> 310L
                SportType.RUNNING -> 190L
                else -> 220L
            }
            val peakThreshold = when (sport) {
                SportType.WALKING -> 1.75f * scale
                else -> 2.35f * scale
            }
            val lowThreshold = 0.85f * scale

            if (dynamicVertical < lowThreshold) {
                wasBelowStepThreshold = true
            } else if (wasBelowStepThreshold && gyroCalm && dynamicVertical > peakThreshold && (now - lastAccelStepMillis) >= minIntervalMs) {
                wasBelowStepThreshold = false
                lastAccelStepMillis = now
                onStepDetected(1)
            }
        }

        // Sport-specific rep / jump peak detection using gravity-aligned vertical axis + Gyro shake guard
        if (sport.supportsReps) {
            val (lowThresh, highThresh, minRepMs) = when (sport) {
                SportType.JUMP_ROPE -> Triple(1.35f * scale, 4.4f * scale, 220L)
                SportType.BADMINTON -> Triple(1.20f * scale, 3.7f * scale, 310L)
                else -> Triple(1.30f * scale, 3.6f * scale, 360L)
            }
            if (dynamicVertical < lowThresh) {
                wasBelowRepThreshold = true
            } else if (wasBelowRepThreshold && gyroCalm && dynamicVertical > highThresh && (now - lastRepMillis) >= minRepMs) {
                wasBelowRepThreshold = false
                lastRepMillis = now
                onJumpRepDetected(1)
            }
        }

        if (now - lastMotionPublishMillis >= 150L) {
            lastMotionPublishMillis = now
            val movingFloor = when (sport) {
                SportType.CYCLING -> 0.35f * scale
                SportType.WALKING -> 0.44f * scale
                else -> 0.54f * scale
            }
            val isMoving = smoothedAccelMag > movingFloor && gyroCalm
            val intensity = ((smoothedAccelMag - movingFloor * 0.6f) / 4.2f).coerceIn(0f, 1f)
            publishSnapshot(isMoving = isMoving, intensity = if (isMoving) intensity else 0f)
        }
    }

    private fun publishSnapshot(isMoving: Boolean, intensity: Float) {
        val sport = sportProvider()
        val gyroSuffix = if (usingGyroGuard) " + กันสั่น Gyro" else ""
        val label = when {
            sport == SportType.CYCLING -> "GPS จักรยาน + วัดจังหวะปั่น$gyroSuffix"
            usingHardwareStepSensor -> "นับก้าวฮาร์ดแวร์แม่นยำสูง$gyroSuffix"
            linearAccelSensor != null || rawAccelSensor != null -> "วัดแรงแกนดิ่ง (${sport.thaiName})$gyroSuffix"
            else -> "ไม่พบเซนเซอร์เคลื่อนไหวในอุปกรณ์"
        }
        onMotionStateChanged(
            SensorMotionSnapshot(
                isMoving = isMoving,
                motionIntensity = intensity,
                hasHardwareStepSensor = usingHardwareStepSensor,
                hasGyroShakeGuard = usingGyroGuard,
                sensorSourceLabel = label
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
