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
    version = 2,
    exportSchema = false
)
abstract class MoveRoomDatabase : RoomDatabase() {
    abstract fun moveDao(): MoveDao

    companion object {
        @Volatile
        private var INSTANCE: MoveRoomDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profile ADD COLUMN weightKg REAL NOT NULL DEFAULT 65.0")
                db.execSQL("ALTER TABLE workouts ADD COLUMN repsCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): MoveRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoveRoomDatabase::class.java,
                    "move_activity_db"
                )
                    .addMigrations(MIGRATION_1_2)
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
        val clamped = goalMinutes.coerceIn(5, 300)
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
            "ต้องออกกำลังกายอย่างน้อย 15 นาที (900 วินาที) จึงจะบันทึกได้"
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

        SaveWorkoutOutcome(
            alreadyExisted = alreadyExisted,
            updatedStreak = streakStatus
        )
    }

    suspend fun deleteWorkout(workoutId: String): Result<Unit> = runCatching {
        dao.deleteWorkoutById(workoutId)
        val remaining = dao.getAllWorkoutsOnce()
        val currentProfile = dao.getUserProfileOnce() ?: UserProfile()
        val streakStatus = DateStreakUtils.calculateStreak(
            workouts = remaining,
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
    }
}
