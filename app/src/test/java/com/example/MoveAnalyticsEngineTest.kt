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
                id = "too_short",
                sportType = SportType.RUNNING.name,
                durationSeconds = 3, // under 5-sec minimum
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

    @Test
    fun `adaptive daily plan recommends HIIT when fresh and active recovery when fatigued`() {
        val freshReport = MoveAnalyticsEngine.calculateMuscleRecovery(emptyList(), nowMillis = 1_000_000L)
        val freshPlan = MoveAnalyticsEngine.buildAdaptiveDailyPlan(freshReport, emptyList())
        assertTrue(freshPlan.isHiitRecommended)

        val now = 10_000_000_000L
        val heavyWorkouts = listOf(
            WorkoutSession(id = "h1", sportType = SportType.RUNNING.name, durationSeconds = 3600, startedAtMillis = now - 3600_000L, endedAtMillis = now),
            WorkoutSession(id = "h2", sportType = SportType.BADMINTON.name, durationSeconds = 3600, startedAtMillis = now - 7200_000L, endedAtMillis = now)
        )
        val fatiguedReport = MoveAnalyticsEngine.calculateMuscleRecovery(heavyWorkouts, nowMillis = now)
        val recoveryPlan = MoveAnalyticsEngine.buildAdaptiveDailyPlan(fatiguedReport, heavyWorkouts)
        assertFalse(recoveryPlan.isHiitRecommended)
        assertEquals(SportType.WALKING, recoveryPlan.suggestedSport)
    }

    @Test
    fun `athlete progression computes XP level and unlocks earned badges`() {
        val workouts = listOf(
            WorkoutSession(
                id = "w1",
                sportType = SportType.GENERAL.name,
                trackingMode = "CAMERA_AI",
                workoutSubType = "HIIT",
                durationSeconds = 600,
                caloriesBurned = 550,
                repsCount = 60,
                distanceMeters = 3200,
                avgFormScore = 94,
                activityDate = "2026-11-15"
            ),
            WorkoutSession(
                id = "w2",
                sportType = SportType.RUNNING.name,
                trackingMode = "CAMERA_AI",
                workoutSubType = "FREE",
                durationSeconds = 300,
                caloriesBurned = 90,
                activityDate = "2026-11-16"
            )
        )
        val streak = com.example.domain.StreakStatus(
            currentStreak = 3,
            longestStreak = 3,
            hasCompletedToday = true,
            lastActiveDate = "2026-11-16",
            statusMessageThai = "ok"
        )
        val prog = MoveAnalyticsEngine.buildAthleteProgression(workouts, streak)
        assertTrue(prog.level >= 2)
        assertTrue(prog.totalXp > 250)
        assertEquals(8, prog.badges.size)
        assertTrue(prog.badges.first { it.id == "first_step" }.unlocked)
        assertTrue(prog.badges.first { it.id == "streak_3" }.unlocked)
        assertTrue(prog.badges.first { it.id == "kcal_500" }.unlocked)
        assertTrue(prog.badges.first { it.id == "ai_form_90" }.unlocked)
        assertTrue(prog.badges.first { it.id == "distance_3km" }.unlocked)
        assertTrue(prog.badges.first { it.id == "rep_master_50" }.unlocked)
        assertTrue(prog.badges.first { it.id == "hiit_challenger" }.unlocked)
    }
}
