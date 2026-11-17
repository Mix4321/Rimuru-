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
import kotlin.math.sqrt

data class SensorMotionSnapshot(
    val isMoving: Boolean = false,
    val motionIntensity: Float = 0f,
    val hasHardwareStepSensor: Boolean = false,
    val sensorSourceLabel: String = "เซนเซอร์ตรวจจับการเคลื่อนไหว"
)

class DeviceMotionTracker(
    private val context: Context,
    private val onStepDetected: (Int) -> Unit,
    private val onJumpRepDetected: (Int) -> Unit,
    private val onMotionStateChanged: (SensorMotionSnapshot) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val linearAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rawAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var isStarted = false
    private var usingHardwareStepSensor = false
    private var lastCounterTotal: Float? = null

    // Gravity low-pass filter state when falling back to raw TYPE_ACCELEROMETER
    private val gravity = FloatArray(3)
    private var gravityInitialized = false

    // Accelerometer peak detection for fallback steps & jump reps
    private var wasBelowStepThreshold = true
    private var lastAccelStepMillis = 0L
    private var wasBelowJumpThreshold = true
    private var lastJumpMillis = 0L

    // Rolling RMS intensity smoothing
    private var smoothedAccelMag = 0f
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
        wasBelowStepThreshold = true
        wasBelowJumpThreshold = true

        val canUseStepSensor = hasActivityRecognitionPermission()
        usingHardwareStepSensor = false

        if (canUseStepSensor) {
            when {
                stepDetectorSensor != null -> {
                    usingHardwareStepSensor = sensorManager.registerListener(
                        this,
                        stepDetectorSensor,
                        SensorManager.SENSOR_DELAY_GAME
                    )
                }
                stepCounterSensor != null -> {
                    usingHardwareStepSensor = sensorManager.registerListener(
                        this,
                        stepCounterSensor,
                        SensorManager.SENSOR_DELAY_UI
                    )
                }
            }
        }

        val motionSensor = linearAccelSensor ?: rawAccelSensor
        if (motionSensor != null) {
            sensorManager.registerListener(this, motionSensor, SensorManager.SENSOR_DELAY_GAME)
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

        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                val count = event.values.size.coerceAtLeast(1)
                onStepDetected(count)
            }

            Sensor.TYPE_STEP_COUNTER -> {
                val total = event.values[0]
                val prev = lastCounterTotal
                lastCounterTotal = total
                if (prev != null) {
                    val delta = (total - prev).toInt()
                    if (delta in 1..100) {
                        onStepDetected(delta)
                    }
                }
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val mag = sqrt(ax * ax + ay * ay + az * az)
                processLinearAccelerationMagnitude(mag, now)
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
                val lx = event.values[0] - gravity[0]
                val ly = event.values[1] - gravity[1]
                val lz = event.values[2] - gravity[2]
                val mag = sqrt(lx * lx + ly * ly + lz * lz)
                processLinearAccelerationMagnitude(mag, now)
            }
        }
    }

    private fun processLinearAccelerationMagnitude(mag: Float, now: Long) {
        // Exponential smoothing to track sustained movement vs single micro-noise
        smoothedAccelMag = smoothedAccelMag * 0.82f + mag * 0.18f

        // Fallback step detection using dynamic acceleration peaks when hardware pedometer isn't active
        if (!usingHardwareStepSensor) {
            if (mag < 1.15f) {
                wasBelowStepThreshold = true
            } else if (wasBelowStepThreshold && mag > 2.65f && (now - lastAccelStepMillis) >= 270L) {
                wasBelowStepThreshold = false
                lastAccelStepMillis = now
                onStepDetected(1)
            }
        }

        // Jump / bounce rep detection for Jump Rope / Cardio via accelerometer peak
        if (mag < 1.8f) {
            wasBelowJumpThreshold = true
        } else if (wasBelowJumpThreshold && mag > 5.2f && (now - lastJumpMillis) >= 280L) {
            wasBelowJumpThreshold = false
            lastJumpMillis = now
            onJumpRepDetected(1)
        }

        if (now - lastMotionPublishMillis >= 220L) {
            lastMotionPublishMillis = now
            // Phone resting on table has smoothedAccelMag ~ 0.02..0.25
            val isMoving = smoothedAccelMag > 0.65f
            val intensity = ((smoothedAccelMag - 0.35f) / 4.5f).coerceIn(0f, 1f)
            publishSnapshot(isMoving = isMoving, intensity = if (isMoving) intensity else 0f)
        }
    }

    private fun publishSnapshot(isMoving: Boolean, intensity: Float) {
        val label = when {
            usingHardwareStepSensor -> "เซนเซอร์นับก้าวฮาร์ดแวร์ + วัดความเร่ง"
            linearAccelSensor != null || rawAccelSensor != null -> "เซนเซอร์วัดความเร่ง (Accelerometer)"
            else -> "ไม่พบเซนเซอร์เคลื่อนไหวในอุปกรณ์"
        }
        onMotionStateChanged(
            SensorMotionSnapshot(
                isMoving = isMoving,
                motionIntensity = intensity,
                hasHardwareStepSensor = usingHardwareStepSensor,
                sensorSourceLabel = label
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
