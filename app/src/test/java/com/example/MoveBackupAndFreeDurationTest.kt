package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.MoveBackupManager
import com.example.data.local.MoveRepository
import com.example.data.local.MoveRoomDatabase
import com.example.data.model.RoutePoint
import com.example.data.model.SportType
import com.example.data.model.TrackingMode
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.domain.DateStreakUtils
import com.example.ui.components.RouteJsonCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MoveBackupAndFreeDurationTest {

    private lateinit var db: MoveRoomDatabase
    private lateinit var repository: MoveRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MoveRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MoveRepository(db.moveDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun flexibleShortWorkoutAndRoutePointsSaveAndExportRoundTrip() = runBlocking {
        repository.ensureUserProfile()
        repository.updateDailyGoal(0) // Free duration mode

        val route = listOf(
            RoutePoint(lat = 13.7563, lng = 100.5018, speedKmh = 7.5f, elapsedSec = 5),
            RoutePoint(lat = 13.7570, lng = 100.5025, speedKmh = 9.2f, elapsedSec = 25)
        )
        val session = WorkoutSession(
            id = "free_short_1",
            sportType = SportType.RUNNING.name,
            durationSeconds = 25, // < 15 mins, valid under free duration (>= 5s)
            caloriesBurned = 8,
            steps = 45,
            distanceMeters = 95,
            trackingMode = TrackingMode.SENSOR_GPS.name,
            motionActiveSeconds = 24,
            activityDate = DateStreakUtils.todayDateString(),
            avgFormScore = 91,
            avgSymmetryPercent = 96,
            routeJson = RouteJsonCodec.encode(route),
            workoutSubType = "HIIT"
        )

        val outcome = repository.saveCompletedWorkoutAndSyncStreak(session.id, session).getOrThrow()
        assertEquals(1, outcome.updatedStreak.currentStreak)

        val profile = repository.getUserProfileOnce()
        assertEquals(0, profile.dailyGoalMinutes)

        val storedWorkouts = repository.getAllWorkoutsOnce()
        assertEquals(1, storedWorkouts.size)
        assertEquals(91, storedWorkouts.first().avgFormScore)
        assertEquals(2, RouteJsonCodec.decode(storedWorkouts.first().routeJson).size)

        // JSON Backup & Restore round-trip
        val backupJson = MoveBackupManager.exportToJson(profile, storedWorkouts)
        val parsed = MoveBackupManager.parseBackupJson(backupJson).getOrThrow()
        assertEquals(1, parsed.workouts.size)
        assertEquals("free_short_1", parsed.workouts.first().id)

        // CSV Export contains UTF-8 BOM and header + row
        val csv = MoveBackupManager.exportToCsv(storedWorkouts)
        assertTrue(csv.startsWith("\uFEFFid,activityDate,sportType"))
        assertTrue(csv.contains("free_short_1"))
    }
}
