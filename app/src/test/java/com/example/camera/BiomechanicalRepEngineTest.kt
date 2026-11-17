package com.example.camera

import android.graphics.PointF
import com.example.data.local.DetectionSensitivity
import com.example.data.model.SportType
import com.google.mlkit.vision.pose.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BiomechanicalRepEngineTest {

    private fun uprightPose(
        hipY: Float = 360f,
        shoulderY: Float = 220f,
        kneeY: Float = 470f,
        ankleY: Float = 570f,
        lWristOffsetY: Float = 140f,
        rWristOffsetY: Float = 140f,
        ankleSpreadHalf: Float = 28f,
        shoulderHalfW: Float = 42f,
        hipHalfW: Float = 32f
    ): Map<Int, PointF> {
        val cx = 240f
        return mapOf(
            PoseLandmark.NOSE to PointF(cx, shoulderY - 50f),
            PoseLandmark.LEFT_SHOULDER to PointF(cx - shoulderHalfW, shoulderY),
            PoseLandmark.RIGHT_SHOULDER to PointF(cx + shoulderHalfW, shoulderY),
            PoseLandmark.LEFT_ELBOW to PointF(cx - shoulderHalfW - 12f, shoulderY + lWristOffsetY * 0.5f),
            PoseLandmark.RIGHT_ELBOW to PointF(cx + shoulderHalfW + 12f, shoulderY + rWristOffsetY * 0.5f),
            PoseLandmark.LEFT_WRIST to PointF(cx - shoulderHalfW - 18f, shoulderY + lWristOffsetY),
            PoseLandmark.RIGHT_WRIST to PointF(cx + shoulderHalfW + 18f, shoulderY + rWristOffsetY),
            PoseLandmark.LEFT_HIP to PointF(cx - hipHalfW, hipY),
            PoseLandmark.RIGHT_HIP to PointF(cx + hipHalfW, hipY),
            PoseLandmark.LEFT_KNEE to PointF(cx - ankleSpreadHalf * 0.85f, kneeY),
            PoseLandmark.RIGHT_KNEE to PointF(cx + ankleSpreadHalf * 0.85f, kneeY),
            PoseLandmark.LEFT_ANKLE to PointF(cx - ankleSpreadHalf, ankleY),
            PoseLandmark.RIGHT_ANKLE to PointF(cx + ankleSpreadHalf, ankleY)
        )
    }

    private fun sidePushUpPose(
        shoulderDropY: Float,
        elbowDropY: Float,
        hipSagY: Float = 0f
    ): Map<Int, PointF> {
        // Horizontal plank facing left: hands at (120, 480), shoulders above hands, hips/ankles stretched right
        val wristX = 125f
        val wristY = 480f
        val shoulderX = 135f
        val shoulderY = 365f + shoulderDropY // 365 (top) -> 455 (bottom)
        val elbowX = shoulderX + elbowDropY * 0.65f
        val elbowY = (shoulderY + wristY) * 0.5f + 8f
        val hipX = 285f
        val hipY = 390f + shoulderDropY * 0.45f + hipSagY
        val kneeX = 375f
        val kneeY = 435f + shoulderDropY * 0.2f
        val ankleX = 445f
        val ankleY = 472f
        return mapOf(
            PoseLandmark.NOSE to PointF(100f, shoulderY - 12f),
            PoseLandmark.LEFT_SHOULDER to PointF(shoulderX, shoulderY),
            PoseLandmark.RIGHT_SHOULDER to PointF(shoulderX + 6f, shoulderY + 4f),
            PoseLandmark.LEFT_ELBOW to PointF(elbowX, elbowY),
            PoseLandmark.RIGHT_ELBOW to PointF(elbowX + 4f, elbowY + 4f),
            PoseLandmark.LEFT_WRIST to PointF(wristX, wristY),
            PoseLandmark.RIGHT_WRIST to PointF(wristX + 5f, wristY),
            PoseLandmark.LEFT_HIP to PointF(hipX, hipY),
            PoseLandmark.RIGHT_HIP to PointF(hipX + 5f, hipY + 3f),
            PoseLandmark.LEFT_KNEE to PointF(kneeX, kneeY),
            PoseLandmark.RIGHT_KNEE to PointF(kneeX + 4f, kneeY + 3f),
            PoseLandmark.LEFT_ANKLE to PointF(ankleX, ankleY),
            PoseLandmark.RIGHT_ANKLE to PointF(ankleX + 3f, ankleY + 2f)
        )
    }

    private fun frontFloorPushUpPose(
        shoulderDropY: Float,
        elbowFlareX: Float
    ): Map<Int, PointF> {
        // Phone on ground facing athlete: wide shoulders & wrists on floor, hips directly behind shoulders
        val cx = 240f
        val shoulderY = 320f + shoulderDropY // 320 (top) -> 410 (bottom)
        val wristY = 450f
        val elbowY = (shoulderY + wristY) * 0.5f - 8f
        return mapOf(
            PoseLandmark.NOSE to PointF(cx, shoulderY - 24f),
            PoseLandmark.LEFT_SHOULDER to PointF(cx - 56f, shoulderY),
            PoseLandmark.RIGHT_SHOULDER to PointF(cx + 56f, shoulderY),
            PoseLandmark.LEFT_ELBOW to PointF(cx - 72f - elbowFlareX, elbowY),
            PoseLandmark.RIGHT_ELBOW to PointF(cx + 72f + elbowFlareX, elbowY),
            PoseLandmark.LEFT_WRIST to PointF(cx - 66f, wristY),
            PoseLandmark.RIGHT_WRIST to PointF(cx + 66f, wristY),
            // Hips behind shoulders at nearly same vertical band on floor camera
            PoseLandmark.LEFT_HIP to PointF(cx - 34f, shoulderY + 38f - shoulderDropY * 0.25f),
            PoseLandmark.RIGHT_HIP to PointF(cx + 34f, shoulderY + 38f - shoulderDropY * 0.25f),
            PoseLandmark.LEFT_KNEE to PointF(cx - 26f, shoulderY + 70f),
            PoseLandmark.RIGHT_KNEE to PointF(cx + 26f, shoulderY + 70f),
            PoseLandmark.LEFT_ANKLE to PointF(cx - 20f, shoulderY + 95f),
            PoseLandmark.RIGHT_ANKLE to PointF(cx + 20f, shoulderY + 95f)
        )
    }

    @Test
    fun `waving both hands up and down while standing still counts 0 jumps in JUMP_ROPE`() {
        val engine = BiomechanicalRepEngine()
        var reps = 0
        var t = 1_000L

        // Repeat 4 full hand-waving cycles (wrists high overhead -> down by thighs) while body does NOT jump
        repeat(4) {
            // Arms down
            repeat(5) {
                t += 80L
                val pts = uprightPose(lWristOffsetY = 145f, rWristOffsetY = 145f)
                val out = engine.evaluate(SportType.JUMP_ROPE, GeneralExerciseFocus.AUTO, pts, 140f, false, DetectionSensitivity.BALANCED, t)
                reps += out.repDelta
            }
            // Arms wave way up overhead (plus slight bodyScale fluctuation)
            repeat(5) {
                t += 80L
                val pts = uprightPose(lWristOffsetY = -125f, rWristOffsetY = -125f)
                val out = engine.evaluate(SportType.JUMP_ROPE, GeneralExerciseFocus.AUTO, pts, 122f, false, DetectionSensitivity.BALANCED, t)
                reps += out.repDelta
            }
        }
        assertEquals("Waving arms without jumping must count 0 reps", 0, reps)
    }

    @Test
    fun `real jump rope hops lift hips shoulders and legs together and count exact reps`() {
        val engine = BiomechanicalRepEngine()
        var reps = 0
        var t = 5_000L

        // Establish 6 standing baseline frames
        repeat(6) {
            t += 50L
            val pts = uprightPose()
            reps += engine.evaluate(SportType.JUMP_ROPE, GeneralExerciseFocus.AUTO, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
        }
        assertEquals(0, reps)

        // 3 real jumps (lifting entire body by 26px = ~18% of 140px torso)
        repeat(3) {
            // Takeoff peak
            repeat(3) {
                t += 60L
                val pts = uprightPose(hipY = 334f, shoulderY = 194f, kneeY = 444f, ankleY = 544f)
                reps += engine.evaluate(SportType.JUMP_ROPE, GeneralExerciseFocus.AUTO, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Land back on ground
            repeat(4) {
                t += 60L
                val pts = uprightPose()
                reps += engine.evaluate(SportType.JUMP_ROPE, GeneralExerciseFocus.AUTO, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
        }
        assertEquals("Real whole-body jump rope hops should count all 3 reps", 3, reps)
    }

    @Test
    fun `waving arms up and down without spreading legs counts 0 jumping jacks in GENERAL`() {
        val engine = BiomechanicalRepEngine()
        var reps = 0
        var t = 10_000L

        repeat(4) {
            repeat(4) {
                t += 100L
                val pts = uprightPose(lWristOffsetY = 140f, rWristOffsetY = 140f, ankleSpreadHalf = 26f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            repeat(4) {
                t += 100L
                val pts = uprightPose(lWristOffsetY = -115f, rWristOffsetY = -115f, ankleSpreadHalf = 26f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
        }
        assertEquals("Arm waving with narrow legs must count 0 jumping jacks", 0, reps)
    }

    @Test
    fun `real jumping jacks with overhead arms and wide leg spread count exact reps`() {
        val engine = BiomechanicalRepEngine()
        var reps = 0
        var t = 15_000L

        repeat(3) {
            // Closed stance
            repeat(4) {
                t += 100L
                val pts = uprightPose(lWristOffsetY = 135f, rWristOffsetY = 135f, ankleSpreadHalf = 26f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Open jumping jack (hands overhead AND wide leg jump)
            repeat(4) {
                t += 100L
                val pts = uprightPose(lWristOffsetY = -110f, rWristOffsetY = -110f, ankleSpreadHalf = 92f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Close back down
            repeat(4) {
                t += 100L
                val pts = uprightPose(lWristOffsetY = 135f, rWristOffsetY = 135f, ankleSpreadHalf = 26f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
        }
        assertEquals("Real jumping jacks should count all 3 reps", 3, reps)
    }

    @Test
    fun `side-view push-ups count accurately in GENERAL AUTO mode`() {
        val engine = BiomechanicalRepEngine()
        var reps = 0
        var t = 20_000L

        repeat(3) {
            // Top plank (arms straight)
            repeat(4) {
                t += 120L
                val pts = sidePushUpPose(shoulderDropY = 0f, elbowDropY = 4f)
                val out = engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 152f, true, DetectionSensitivity.BALANCED, t)
                assertTrue("Side plank should be detected as horizontal plank", out.isHorizontalPlank)
                reps += out.repDelta
            }
            // Lower chest to floor (elbows sharply bent ~85-95 deg)
            repeat(4) {
                t += 120L
                val pts = sidePushUpPose(shoulderDropY = 82f, elbowDropY = 85f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 152f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Push back up to top plank
            repeat(4) {
                t += 120L
                val pts = sidePushUpPose(shoulderDropY = 0f, elbowDropY = 4f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 152f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
        }
        assertEquals("Side-view push-ups should count all 3 reps", 3, reps)
    }

    @Test
    fun `front floor-camera push-ups count accurately in GENERAL AUTO and PUSHUP focus`() {
        val engine = BiomechanicalRepEngine()
        var reps = 0
        var t = 30_000L

        repeat(2) {
            // Top plank facing front
            repeat(4) {
                t += 120L
                val pts = frontFloorPushUpPose(shoulderDropY = 0f, elbowFlareX = 6f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 156f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Bottom push-up facing front (elbows flared & bent < 100 deg, shoulders lowered toward wrists)
            repeat(4) {
                t += 120L
                val pts = frontFloorPushUpPose(shoulderDropY = 86f, elbowFlareX = 52f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 156f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Push back up
            repeat(4) {
                t += 120L
                val pts = frontFloorPushUpPose(shoulderDropY = 0f, elbowFlareX = 6f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 156f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
        }
        assertEquals("Front-view push-ups should count all 2 reps", 2, reps)
    }

    @Test
    fun `upright standing arm curls do NOT trigger push-up posture or false reps`() {
        val engine = BiomechanicalRepEngine()
        var reps = 0
        var t = 40_000L

        repeat(4) {
            val ptsExtended = uprightPose(lWristOffsetY = 120f, rWristOffsetY = 120f)
            assertFalse(engine.isPushUpPlankPosture(ptsExtended, 140f, strict = false))
            t += 120L
            reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.PUSHUP, ptsExtended, 140f, true, DetectionSensitivity.BALANCED, t).repDelta

            // Curl wrists up near shoulders while standing
            val ptsCurled = uprightPose(lWristOffsetY = 12f, rWristOffsetY = 12f)
            assertFalse(engine.isPushUpPlankPosture(ptsCurled, 140f, strict = false))
            t += 120L
            reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.PUSHUP, ptsCurled, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
        }
        assertEquals("Standing arm bending must count 0 push-ups", 0, reps)
    }

    @Test
    fun `real squats with hip drop and knee flexion count exact reps`() {
        val engine = BiomechanicalRepEngine()
        var reps = 0
        var t = 50_000L

        repeat(2) {
            // Standing
            repeat(4) {
                t += 120L
                val pts = uprightPose(hipY = 360f, kneeY = 470f, ankleY = 570f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Deep squat: hips drop down to knee height & knees bend forward/outward (~95 deg)
            repeat(4) {
                t += 120L
                val cx = 240f
                val pts = uprightPose(
                    hipY = 460f,
                    shoulderY = 335f,
                    kneeY = 475f,
                    ankleY = 570f
                ).toMutableMap().apply {
                    // Push knees forward/out in screen X to model real knee angle bending < 110 deg
                    this[PoseLandmark.LEFT_KNEE] = PointF(cx - 75f, 475f)
                    this[PoseLandmark.RIGHT_KNEE] = PointF(cx + 75f, 475f)
                }
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Stand back up
            repeat(4) {
                t += 120L
                val pts = uprightPose(hipY = 360f, kneeY = 470f, ankleY = 570f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.AUTO, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
        }
        assertEquals("Real squats should count all 2 reps", 2, reps)
    }
}
