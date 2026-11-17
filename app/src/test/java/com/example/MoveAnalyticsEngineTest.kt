package com.example

import com.example.data.model.SportType
import com.example.data.model.WorkoutSession
import com.example.domain.MoveAnalyticsEngine
import com.example.domain.MuscleGroup
import com.example.domain.RecoveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveAnalyticsEngineTest {

    @Test
    fun `empty workouts show 100 percent readiness and fresh muscles`() {
        val report = MoveAnalyticsEngine.calculateMuscleRecovery(emptyList(), nowMillis = 1_000_000L)
        assertEquals(100, report.overallReadinessPercent)
        assertTrue(report.muscles.values.all { it.recoveryState == RecoveryState.FRESH })
    }

    @Test
    fun `heavy recent running fatigues calves and recommends upper-body or low-impact sport`() {
        val now = 10_000_000_000L
        val workouts = listOf(
            WorkoutSession(
                id = "w1",
                sportType = SportType.RUNNING.name,
                durationSeconds = 3600, // 60 mins
                caloriesBurned = 520,
                steps = 8500,
                distanceMeters = 7200,
                startedAtMillis = now - 2 * 3600_000L, // 2 hours ago
                endedAtMillis = now - 3600_000L,
                activityDate = "2026-11-16"
            )
        )

        val report = MoveAnalyticsEngine.calculateMuscleRecovery(workouts, nowMillis = now)
        val calves = report.muscles.getValue(MuscleGroup.CALVES_ANKLES)
        assertEquals(RecoveryState.FATIGUED, calves.recoveryState)
        assertTrue(report.overallReadinessPercent < 90)
        assertTrue(report.recommendedSport != SportType.RUNNING)
    }

    @Test
    fun `ghost rival selects best qualifying workout and interpolates linearly`() {
        val sessions = listOf(
            WorkoutSession(
                id = "short",
                sportType = SportType.RUNNING.name,
                durationSeconds = 300, // under 15-min minimum
                distanceMeters = 5000,
                activityDate = "2026-11-10"
            ),
            WorkoutSession(
                id = "good1",
                sportType = SportType.RUNNING.name,
                durationSeconds = 1200,
                distanceMeters = 2400,
                steps = 2800,
                caloriesBurned = 200,
                activityDate = "2026-11-12"
            ),
            WorkoutSession(
                id = "best",
                sportType = SportType.RUNNING.name,
                durationSeconds = 1800, // 30 mins
                distanceMeters = 4500,
                steps = 5200,
                caloriesBurned = 360,
                activityDate = "2026-11-15"
            )
        )

        val rival = MoveAnalyticsEngine.findBestGhostRival(sessions, SportType.RUNNING)
        assertTrue(rival.hasRival)
        assertEquals("2026-11-15", rival.rivalDateLabel)

        val halfway = rival.expectedProgressAt(elapsedSeconds = 900)
        assertEquals(2250, halfway.meters)
        assertEquals(2600, halfway.steps)
        assertEquals(180, halfway.calories)

        val noBadmintonRival = MoveAnalyticsEngine.findBestGhostRival(sessions, SportType.BADMINTON)
        assertFalse(noBadmintonRival.hasRival)
    }
}
