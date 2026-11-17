package com.example

import com.example.data.model.SportType
import com.example.data.model.WorkoutSession
import com.example.domain.DateStreakUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateStreakUtilsTest {

    private fun workoutOn(date: String, seconds: Int, calories: Int = 120): WorkoutSession {
        return WorkoutSession(
            id = "s_${date}_$seconds",
            userId = "u1",
            sportType = SportType.RUNNING.name,
            durationSeconds = seconds,
            caloriesBurned = calories,
            steps = 1500,
            distanceMeters = 1500,
            activityDate = date,
            startedAtMillis = 1700000000000L,
            endedAtMillis = 1700000000000L + seconds * 1000L
        )
    }

    @Test
    fun streakIncrementsWhenConsecutiveDaysIncludingToday() {
        val today = "2026-11-16"
        val workouts = listOf(
            workoutOn("2026-11-14", 900),
            workoutOn("2026-11-15", 900),
            workoutOn("2026-11-16", 900)
        )
        val status = DateStreakUtils.calculateStreak(workouts, storedLongestStreak = 2, today = today)
        assertEquals(3, status.currentStreak)
        assertEquals(3, status.longestStreak)
        assertTrue(status.hasCompletedToday)
    }

    @Test
    fun streakPreservesYesterdayWithoutIncrementingWhenTodayNotDoneYet() {
        val today = "2026-11-16"
        val workouts = listOf(
            workoutOn("2026-11-14", 900),
            workoutOn("2026-11-15", 900)
        )
        val status = DateStreakUtils.calculateStreak(workouts, storedLongestStreak = 2, today = today)
        assertEquals(2, status.currentStreak)
        assertFalse(status.hasCompletedToday)
    }

    @Test
    fun streakResetsAfterGapAndStartsAtOneToday() {
        val today = "2026-11-16"
        val workouts = listOf(
            workoutOn("2026-11-12", 900),
            workoutOn("2026-11-13", 900),
            workoutOn("2026-11-16", 900)
        )
        val status = DateStreakUtils.calculateStreak(workouts, storedLongestStreak = 2, today = today)
        assertEquals(1, status.currentStreak)
        assertEquals(2, status.longestStreak)
        assertTrue(status.hasCompletedToday)
    }

    @Test
    fun dailyGoalCalculatesProgressAndCompletionCorrectly() {
        val today = "2026-11-16"
        val workouts = listOf(
            workoutOn("2026-11-16", 900, calories = 150),
            workoutOn("2026-11-16", 900, calories = 140)
        )
        val summary = DateStreakUtils.calculateDailyGoalSummary(workouts, goalMinutes = 30, today = today)
        assertEquals(30, summary.completedMinutesToday)
        assertEquals(0, summary.remainingMinutesToday)
        assertEquals(100, summary.progressPercent)
        assertTrue(summary.isGoalReached)
        assertEquals(290, summary.caloriesToday)
    }
}
