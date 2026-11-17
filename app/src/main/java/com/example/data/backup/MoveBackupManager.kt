package com.example.data.backup

import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MoveBackupPayload(
    val formatVersion: Int = 1,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val profile: UserProfile? = null,
    val workouts: List<WorkoutSession> = emptyList()
)

object MoveBackupManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportToJson(profile: UserProfile, workouts: List<WorkoutSession>): String {
        val payload = MoveBackupPayload(
            formatVersion = 1,
            exportedAtMillis = System.currentTimeMillis(),
            profile = profile,
            workouts = workouts
        )
        return json.encodeToString(payload)
    }

    fun parseBackupJson(raw: String): Result<MoveBackupPayload> = runCatching {
        json.decodeFromString<MoveBackupPayload>(raw)
    }

    fun exportToCsv(workouts: List<WorkoutSession>): String {
        val sb = StringBuilder()
        // UTF-8 BOM so Microsoft Excel / Google Sheets opens Thai text cleanly
        sb.append('\uFEFF')
        sb.appendLine("id,activityDate,sportType,sportThai,trackingMode,workoutSubType,durationSeconds,motionActiveSeconds,caloriesBurned,steps,distanceMeters,repsCount,avgFormScore,avgSymmetryPercent,startedAtMillis,endedAtMillis")
        for (w in workouts) {
            sb.append(escapeCsv(w.id)).append(',')
            sb.append(escapeCsv(w.activityDate)).append(',')
            sb.append(escapeCsv(w.sportType)).append(',')
            sb.append(escapeCsv(w.sport.thaiName)).append(',')
            sb.append(escapeCsv(w.trackingMode)).append(',')
            sb.append(escapeCsv(w.workoutSubType)).append(',')
            sb.append(w.durationSeconds).append(',')
            sb.append(w.motionActiveSeconds).append(',')
            sb.append(w.caloriesBurned).append(',')
            sb.append(w.steps).append(',')
            sb.append(w.distanceMeters).append(',')
            sb.append(w.repsCount).append(',')
            sb.append(w.avgFormScore).append(',')
            sb.append(w.avgSymmetryPercent).append(',')
            sb.append(w.startedAtMillis).append(',')
            sb.append(w.endedAtMillis).appendLine()
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return "\"$clean\""
    }
}
