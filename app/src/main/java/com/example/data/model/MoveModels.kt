package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class TrackingMode(val thaiLabel: String, val shortLabel: String) {
    CAMERA_AI("ตรวจจับด้วยกล้อง AI", "กล้อง AI"),
    SENSOR_GPS("เซนเซอร์ก้าว & GPS", "เซนเซอร์/GPS"),
    MANUAL("บันทึกด้วยตนเอง", "กำหนดเอง");

    companion object {
        fun fromNameSafe(raw: String?): TrackingMode =
            entries.firstOrNull { it.name == raw } ?: SENSOR_GPS
    }
}

@Serializable
data class RoutePoint(
    val lat: Double,
    val lng: Double,
    val speedKmh: Float = 0f,
    val elapsedSec: Int = 0
)

@Serializable
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: String = LOCAL_PROFILE_ID,
    val userId: String = LOCAL_PROFILE_ID,
    val displayName: String = "นักกีฬา MOVE",
    val dailyGoalMinutes: Int = 0, // 0 = เวลาอิสระ (Free Mode / ไม่จำกัดเวลา)
    val weightKg: Float = 65f,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastActiveDate: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val LOCAL_PROFILE_ID = "local_user"
    }
}

@Serializable
@Entity(tableName = "workouts")
data class WorkoutSession(
    @PrimaryKey val id: String = "",
    val userId: String = UserProfile.LOCAL_PROFILE_ID,
    val sportType: String = SportType.RUNNING.name,
    val durationSeconds: Int = 0,
    val caloriesBurned: Int = 0,
    val steps: Int = 0,
    val distanceMeters: Int = 0,
    val repsCount: Int = 0,
    val trackingMode: String = TrackingMode.MANUAL.name,
    val motionActiveSeconds: Int = 0,
    val activityDate: String = "",
    val startedAtMillis: Long = 0L,
    val endedAtMillis: Long = 0L,
    val avgFormScore: Int = 0,
    val avgSymmetryPercent: Int = 100,
    val routeJson: String = "",
    val workoutSubType: String = "FREE"
) {
    val sport: SportType
        get() = SportType.fromNameSafe(sportType)

    val mode: TrackingMode
        get() = TrackingMode.fromNameSafe(trackingMode)
}

data class ActiveWorkoutDraft(
    val sessionId: String,
    val sportType: SportType,
    val trackingMode: TrackingMode = sportType.defaultTrackingMode,
    val startedAtMillis: Long,
    val elapsedSeconds: Int,
    val motionActiveSeconds: Int,
    val steps: Int,
    val distanceMeters: Int,
    val repsCount: Int = 0,
    val caloriesExact: Double,
    val isRunning: Boolean,
    val lastSavedEpochMillis: Long,
    val routeJson: String = ""
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
