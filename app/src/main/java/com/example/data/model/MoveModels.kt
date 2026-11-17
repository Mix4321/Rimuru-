package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TrackingMode(val thaiLabel: String) {
    CAMERA_AI("ตรวจจับด้วยกล้อง AI"),
    MANUAL("บันทึกด้วยตนเอง");

    companion object {
        fun fromNameSafe(raw: String?): TrackingMode =
            entries.firstOrNull { it.name == raw } ?: MANUAL
    }
}

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: String = LOCAL_PROFILE_ID,
    val userId: String = LOCAL_PROFILE_ID,
    val displayName: String = "นักกีฬา MOVE",
    val dailyGoalMinutes: Int = 30,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastActiveDate: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val LOCAL_PROFILE_ID = "local_user"
    }
}

@Entity(tableName = "workouts")
data class WorkoutSession(
    @PrimaryKey val id: String = "",
    val userId: String = UserProfile.LOCAL_PROFILE_ID,
    val sportType: String = SportType.RUNNING.name,
    val durationSeconds: Int = 0,
    val caloriesBurned: Int = 0,
    val steps: Int = 0,
    val distanceMeters: Int = 0,
    val trackingMode: String = TrackingMode.MANUAL.name,
    val motionActiveSeconds: Int = 0,
    val activityDate: String = "",
    val startedAtMillis: Long = 0L,
    val endedAtMillis: Long = 0L
) {
    val sport: SportType
        get() = SportType.fromNameSafe(sportType)

    val mode: TrackingMode
        get() = TrackingMode.fromNameSafe(trackingMode)
}

data class ActiveWorkoutDraft(
    val sessionId: String,
    val sportType: SportType,
    val startedAtMillis: Long,
    val elapsedSeconds: Int,
    val motionActiveSeconds: Int,
    val steps: Int,
    val distanceMeters: Int,
    val caloriesExact: Double,
    val isRunning: Boolean,
    val lastSavedEpochMillis: Long
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
