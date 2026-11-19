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

    @Test
    fun `plank hold validates straight alignment and pauses when hips sag`() {
        val engine = BiomechanicalRepEngine()
        var t = 60_000L

        val straightPlank = sidePushUpPose(shoulderDropY = 4f, elbowDropY = 8f, hipSagY = 0f)
        val validOut = engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.PLANK, straightPlank, 152f, true, DetectionSensitivity.BALANCED, t)
        assertTrue("Straight plank must be valid hold", validOut.isPlankHoldingValid)
        assertTrue("Straight plank must be horizontal plank", validOut.isHorizontalPlank)

        t += 200L
        val saggingPlank = sidePushUpPose(shoulderDropY = 4f, elbowDropY = 8f, hipSagY = 95f)
        val sagOut = engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.PLANK, saggingPlank, 152f, true, DetectionSensitivity.BALANCED, t)
        assertFalse("Sagging hip plank must fail alignment check", sagOut.isPlankHoldingValid)
    }

    @Test
    fun `sit-ups in floor knee-bent posture count accurate curl reps`() {
        val engine = BiomechanicalRepEngine()
        var reps = 0
        var t = 70_000L

        fun sitUpPose(shoulderX: Float, shoulderY: Float): Map<Int, PointF> {
            return mapOf(
                PoseLandmark.NOSE to PointF(shoulderX - 18f, shoulderY - 12f),
                PoseLandmark.LEFT_SHOULDER to PointF(shoulderX, shoulderY),
                PoseLandmark.RIGHT_SHOULDER to PointF(shoulderX + 6f, shoulderY + 4f),
                PoseLandmark.LEFT_ELBOW to PointF(shoulderX + 20f, shoulderY - 15f),
                PoseLandmark.RIGHT_ELBOW to PointF(shoulderX + 24f, shoulderY - 12f),
                PoseLandmark.LEFT_WRIST to PointF(shoulderX + 10f, shoulderY - 22f),
                PoseLandmark.RIGHT_WRIST to PointF(shoulderX + 14f, shoulderY - 20f),
                PoseLandmark.LEFT_HIP to PointF(260f, 440f),
                PoseLandmark.RIGHT_HIP to PointF(265f, 442f),
                PoseLandmark.LEFT_KNEE to PointF(355f, 385f),
                PoseLandmark.RIGHT_KNEE to PointF(360f, 387f),
                PoseLandmark.LEFT_ANKLE to PointF(415f, 445f),
                PoseLandmark.RIGHT_ANKLE to PointF(420f, 447f)
            )
        }

        repeat(2) {
            // Lying back (>125 deg torso-hip-knee)
            repeat(4) {
                t += 120L
                val pts = sitUpPose(shoulderX = 120f, shoulderY = 435f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.SITUP, pts, 142f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Crunch up toward knees (<75 deg)
            repeat(4) {
                t += 120L
                val pts = sitUpPose(shoulderX = 285f, shoulderY = 305f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.SITUP, pts, 142f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Lower back down
            repeat(4) {
                t += 120L
                val pts = sitUpPose(shoulderX = 120f, shoulderY = 435f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.SITUP, pts, 142f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
        }
        assertEquals("Sit-up curl reps should count 2", 2, reps)
    }

    @Test
    fun `split-stance lunges count accurate reps`() {
        val engine = BiomechanicalRepEngine()
        var reps = 0
        var t = 80_000L

        fun lungePose(hipDrop: Float, frontKneeBend: Boolean): Map<Int, PointF> {
            val shoulderY = 210f + hipDrop
            val hipY = 350f + hipDrop
            val kneeY = if (frontKneeBend) 435f else 460f
            val lKneeX = if (frontKneeBend) 170f else 195f
            return mapOf(
                PoseLandmark.NOSE to PointF(240f, shoulderY - 45f),
                PoseLandmark.LEFT_SHOULDER to PointF(215f, shoulderY),
                PoseLandmark.RIGHT_SHOULDER to PointF(265f, shoulderY),
                PoseLandmark.LEFT_ELBOW to PointF(205f, shoulderY + 60f),
                PoseLandmark.RIGHT_ELBOW to PointF(275f, shoulderY + 60f),
                PoseLandmark.LEFT_WRIST to PointF(200f, shoulderY + 115f),
                PoseLandmark.RIGHT_WRIST to PointF(280f, shoulderY + 115f),
                PoseLandmark.LEFT_HIP to PointF(220f, hipY),
                PoseLandmark.RIGHT_HIP to PointF(260f, hipY),
                PoseLandmark.LEFT_KNEE to PointF(lKneeX, kneeY),
                PoseLandmark.RIGHT_KNEE to PointF(300f, 475f),
                PoseLandmark.LEFT_ANKLE to PointF(168f, 560f),
                PoseLandmark.RIGHT_ANKLE to PointF(345f, 560f)
            )
        }

        repeat(2) {
            repeat(4) {
                t += 120L
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.LUNGE, lungePose(0f, false), 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            repeat(4) {
                t += 120L
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.LUNGE, lungePose(65f, true), 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            repeat(4) {
                t += 120L
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.LUNGE, lungePose(0f, false), 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
        }
        assertEquals("Real split-stance lunges should count 2 reps", 2, reps)
    }

    @Test
    fun `front-facing squats with Z-foreshortened 2D knee angle count exact reps`() {
        val engine = BiomechanicalRepEngine()
        var reps = 0
        var t = 90_000L

        repeat(2) {
            // Standing upright
            repeat(4) {
                t += 120L
                val pts = uprightPose(hipY = 360f, kneeY = 470f, ankleY = 570f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.SQUAT, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Front-facing squat bottom: hips drop right down to knee height, while 2D knee angle stays ~145 deg
            repeat(4) {
                t += 120L
                val pts = uprightPose(
                    shoulderY = 315f,
                    hipY = 445f,
                    kneeY = 472f,
                    ankleY = 570f
                )
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.SQUAT, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Return to standing
            repeat(4) {
                t += 120L
                val pts = uprightPose(hipY = 360f, kneeY = 470f, ankleY = 570f)
                reps += engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.SQUAT, pts, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
        }
        assertEquals("Front-view squats must count all 2 reps", 2, reps)
    }

    @Test
    fun `fast jump rope cadence every 300ms counts all jumps without skipping`() {
        val engine = BiomechanicalRepEngine()
        var reps = 0
        var t = 100_000L

        // Establish baseline
        repeat(5) {
            t += 50L
            reps += engine.evaluate(SportType.JUMP_ROPE, GeneralExerciseFocus.AUTO, uprightPose(), 140f, true, DetectionSensitivity.BALANCED, t).repDelta
        }

        // 4 fast bounces at ~300ms per cycle (200 BPM cadence)
        repeat(4) {
            // Airborne peak (~150ms)
            repeat(2) {
                t += 75L
                val peak = uprightPose(hipY = 342f, shoulderY = 202f, kneeY = 452f, ankleY = 552f)
                reps += engine.evaluate(SportType.JUMP_ROPE, GeneralExerciseFocus.AUTO, peak, 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
            // Quick ground contact (~150ms)
            repeat(2) {
                t += 75L
                reps += engine.evaluate(SportType.JUMP_ROPE, GeneralExerciseFocus.AUTO, uprightPose(), 140f, true, DetectionSensitivity.BALANCED, t).repDelta
            }
        }
        assertEquals("Fast jump rope cadence should count all 4 jumps", 4, reps)
    }

    @Test
    fun `one-euro filter suppresses stationary micro jitter yet tracks fast jump with minimal lag`() {
        val filter = OneEuroFilter3D(minCutoff = 1.5f, beta = 0.045f)
        val dt = 0.033f
        val bodyScale = 140f

        // Stationary jitter +/- 4px around 300f -> output stays within tight < 2.2px band
        var maxDeviation = 0f
        repeat(20) { i ->
            val rawY = 300f + if (i % 2 == 0) 4f else -4f
            val out = filter.filter(Point3D(240f, rawY, 0f), dt, bodyScale)
            if (i >= 4) {
                maxDeviation = kotlin.math.max(maxDeviation, kotlin.math.abs(out.y - 300f))
            }
        }
        assertTrue("Stationary jitter should be heavily damped (<2.5px), got $maxDeviation", maxDeviation < 2.5f)

        // Sudden explosive jump upward by 80px -> within 2 frames responds strongly (>65% of jump)
        val f1 = filter.filter(Point3D(240f, 220f, 0f), dt, bodyScale)
        val f2 = filter.filter(Point3D(240f, 220f, 0f), dt, bodyScale)
        assertTrue("First explosive jump frame should move rapidly below 275f, got ${f1.y}", f1.y < 275f)
        assertTrue("Second explosive jump frame should track near 220f (<248f), got ${f2.y}", f2.y < 248f)
    }

    @Test
    fun `3D joint angle detects 90 degree squat knee bend even when 2D front view appears straight`() {
        val engine = BiomechanicalRepEngine()
        val standing2d = uprightPose(hipY = 360f, kneeY = 470f, ankleY = 570f)
        val standing3d = standing2d.mapValues { Point3D(it.value.x, it.value.y, 0f) }

        // Knee pushed toward camera (-95 in Z) while 2D x,y look nearly vertical (175 deg in 2D!)
        val squatBottom3d = standing2d.mapValues { (k, p) ->
            when (k) {
                PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE -> Point3D(p.x, p.y - 10f, -105f)
                PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP -> Point3D(p.x, p.y + 85f, 35f)
                else -> Point3D(p.x, p.y, 0f)
            }
        }

        val angle2dOnly = engine.measureKneeAngleDeg(standing2d)!!
        val angle3dBent = engine.measureKneeAngleDeg(standing2d, squatBottom3d)!!

        assertTrue("2D alone looks >165 deg straight: $angle2dOnly", angle2dOnly > 165f)
        assertTrue("3D geometry must capture true deep knee flexion (<105 deg): $angle3dBent", angle3dBent < 105f)

        // Verify rep phase depth progress is > 0.75 when deep in squat
        var t = 120_000L
        repeat(4) {
            t += 100L
            engine.evaluate(SportType.GENERAL, GeneralExerciseFocus.SQUAT, standing2d, 140f, true, DetectionSensitivity.BALANCED, t, standing3d)
        }
        t += 100L
        val deepEval = engine.evaluate(
            SportType.GENERAL, GeneralExerciseFocus.SQUAT,
            uprightPose(shoulderY = 300f, hipY = 435f, kneeY = 460f, ankleY = 570f),
            140f, true, DetectionSensitivity.BALANCED, t, squatBottom3d
        )
        assertTrue("Deep squat should report high repPhaseProgress >= 0.75, got ${deepEval.repPhaseProgress}", deepEval.repPhaseProgress >= 0.75f)
    }

    @Test
    fun `occluded far-side arm noise does not corrupt near-side push-up elbow angle`() {
        val engine = BiomechanicalRepEngine()
        // Left arm (near camera) clearly bent at 85 deg; Right arm (occluded behind torso) locked straight 175 deg with low conf
        val pts = mapOf(
            PoseLandmark.LEFT_SHOULDER to PointF(150f, 360f),
            PoseLandmark.LEFT_ELBOW to PointF(210f, 415f),
            PoseLandmark.LEFT_WRIST to PointF(145f, 475f),
            PoseLandmark.RIGHT_SHOULDER to PointF(160f, 365f),
            PoseLandmark.RIGHT_ELBOW to PointF(162f, 420f),
            PoseLandmark.RIGHT_WRIST to PointF(164f, 475f)
        )
        val confidences = mapOf(
            PoseLandmark.LEFT_SHOULDER to 0.94f,
            PoseLandmark.LEFT_ELBOW to 0.91f,
            PoseLandmark.LEFT_WRIST to 0.90f,
            PoseLandmark.RIGHT_SHOULDER to 0.48f,
            PoseLandmark.RIGHT_ELBOW to 0.41f,
            PoseLandmark.RIGHT_WRIST to 0.39f
        )

        val measured = engine.measureElbowAngleDeg(pts, confidences = confidences)!!
        assertTrue("Clear near-side 85deg arm must win over occluded far-side (<98 deg), got $measured", measured < 98f)
    }
}
