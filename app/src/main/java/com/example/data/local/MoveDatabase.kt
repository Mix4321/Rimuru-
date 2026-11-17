package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.domain.DateStreakUtils
import com.example.domain.StreakStatus
import kotlinx.coroutines.flow.Flow

data class SaveWorkoutOutcome(
    val alreadyExisted: Boolean,
    val updatedStreak: StreakStatus
)

@Dao
interface MoveDao {
    @Query("SELECT * FROM user_profile WHERE id = :id LIMIT 1")
    fun observeUserProfile(id: String = UserProfile.LOCAL_PROFILE_ID): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = :id LIMIT 1")
    suspend fun getUserProfileOnce(id: String = UserProfile.LOCAL_PROFILE_ID): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(profile: UserProfile)

    @Query("SELECT * FROM workouts ORDER BY startedAtMillis DESC")
    fun observeAllWorkouts(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workouts ORDER BY startedAtMillis DESC")
    suspend fun getAllWorkoutsOnce(): List<WorkoutSession>

    @Query("SELECT * FROM workouts WHERE id = :workoutId LIMIT 1")
    suspend fun getWorkoutById(workoutId: String): WorkoutSession?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWorkoutIgnoreDuplicate(workout: WorkoutSession): Long

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    suspend fun deleteWorkoutById(workoutId: String)
}

@Database(
    entities = [UserProfile::class, WorkoutSession::class],
    version = 3,
    exportSchema = false
)
abstract class MoveRoomDatabase : RoomDatabase() {
    abstract fun moveDao(): MoveDao

    companion object {
        @Volatile
        private var INSTANCE: MoveRoomDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profile ADD COLUMN weightKg REAL NOT NULL DEFAULT 65.0")
                db.execSQL("ALTER TABLE workouts ADD COLUMN repsCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN avgFormScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workouts ADD COLUMN avgSymmetryPercent INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE workouts ADD COLUMN routeJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workouts ADD COLUMN workoutSubType TEXT NOT NULL DEFAULT 'FREE'")
            }
        }

        fun getInstance(context: Context): MoveRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoveRoomDatabase::class.java,
                    "move_activity_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class MoveRepository(private val dao: MoveDao) {

    fun observeUserProfile(): Flow<UserProfile?> = dao.observeUserProfile()

    fun observeWorkouts(): Flow<List<WorkoutSession>> = dao.observeAllWorkouts()

    suspend fun getUserProfileOnce(): UserProfile = dao.getUserProfileOnce() ?: UserProfile()

    suspend fun getAllWorkoutsOnce(): List<WorkoutSession> = dao.getAllWorkoutsOnce()

    suspend fun ensureUserProfile(): Result<Unit> = runCatching {
        if (dao.getUserProfileOnce() == null) {
            dao.upsertUserProfile(UserProfile())
        }
    }

    suspend fun updateProfile(newName: String, weightKg: Float): Result<Unit> = runCatching {
        val safeName = newName.trim().take(80).ifBlank { "นักกีฬา MOVE" }
        val safeWeight = weightKg.coerceIn(25f, 250f)
        val current = dao.getUserProfileOnce() ?: UserProfile()
        dao.upsertUserProfile(
            current.copy(
                displayName = safeName,
                weightKg = safeWeight,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateDailyGoal(goalMinutes: Int): Result<Unit> = runCatching {
        val clamped = goalMinutes.coerceIn(0, 300)
        val current = dao.getUserProfileOnce() ?: UserProfile()
        dao.upsertUserProfile(
            current.copy(
                dailyGoalMinutes = clamped,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveCompletedWorkoutAndSyncStreak(
        sessionId: String,
        session: WorkoutSession
    ): Result<SaveWorkoutOutcome> = runCatching {
        require(session.durationSeconds >= DateStreakUtils.MIN_WORKOUT_SECONDS) {
            "กรุณาออกกำลังกายอย่างน้อย ${DateStreakUtils.MIN_WORKOUT_SECONDS} วินาที จึงจะบันทึกได้"
        }

        if (dao.getUserProfileOnce() == null) {
            dao.upsertUserProfile(UserProfile())
        }

        val existing = dao.getWorkoutById(sessionId)
        val alreadyExisted = if (existing != null) {
            true
        } else {
            val safeDate = session.activityDate.ifBlank {
                DateStreakUtils.dateStringFromMillis(session.startedAtMillis)
            }
            val rowId = dao.insertWorkoutIgnoreDuplicate(
                session.copy(
                    id = sessionId,
                    userId = UserProfile.LOCAL_PROFILE_ID,
                    activityDate = safeDate
                )
            )
            rowId == -1L
        }

        val streakStatus = recalculateAndPersistStreak()
        SaveWorkoutOutcome(
            alreadyExisted = alreadyExisted,
            updatedStreak = streakStatus
        )
    }

    suspend fun deleteWorkout(workoutId: String): Result<Unit> = runCatching {
        dao.deleteWorkoutById(workoutId)
        recalculateAndPersistStreak()
    }

    suspend fun importWorkoutsFromBackup(
        sessions: List<WorkoutSession>,
        importedProfile: UserProfile?
    ): Result<Int> = runCatching {
        if (importedProfile != null) {
            val existingProfile = dao.getUserProfileOnce()
            if (existingProfile == null) {
                dao.upsertUserProfile(importedProfile.copy(id = UserProfile.LOCAL_PROFILE_ID, userId = UserProfile.LOCAL_PROFILE_ID))
            } else {
                dao.upsertUserProfile(
                    existingProfile.copy(
                        displayName = importedProfile.displayName.ifBlank { existingProfile.displayName },
                        weightKg = importedProfile.weightKg.coerceIn(25f, 250f),
                        dailyGoalMinutes = importedProfile.dailyGoalMinutes.coerceIn(0, 300),
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
            }
        } else if (dao.getUserProfileOnce() == null) {
            dao.upsertUserProfile(UserProfile())
        }

        var inserted = 0
        for (raw in sessions) {
            if (raw.id.isBlank() || raw.durationSeconds < DateStreakUtils.MIN_WORKOUT_SECONDS) continue
            val safeDate = raw.activityDate.ifBlank {
                if (raw.startedAtMillis > 0L) DateStreakUtils.dateStringFromMillis(raw.startedAtMillis) else DateStreakUtils.todayDateString()
            }
            val rowId = dao.insertWorkoutIgnoreDuplicate(
                raw.copy(
                    userId = UserProfile.LOCAL_PROFILE_ID,
                    activityDate = safeDate
                )
            )
            if (rowId != -1L) inserted++
        }
        recalculateAndPersistStreak()
        inserted
    }

    private suspend fun recalculateAndPersistStreak(): StreakStatus {
        val allWorkouts = dao.getAllWorkoutsOnce()
        val currentProfile = dao.getUserProfileOnce() ?: UserProfile()
        val streakStatus = DateStreakUtils.calculateStreak(
            workouts = allWorkouts,
            storedLongestStreak = currentProfile.longestStreak
        )
        dao.upsertUserProfile(
            currentProfile.copy(
                currentStreak = streakStatus.currentStreak,
                longestStreak = streakStatus.longestStreak,
                lastActiveDate = streakStatus.lastActiveDate,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        return streakStatus
    }
}
