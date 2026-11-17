package com.example.domain

import com.example.data.model.SportType
import com.example.data.model.WorkoutSession
import kotlin.math.roundToInt

enum class MuscleGroup(val thaiName: String) {
    NECK_SHOULDERS("คอ บ่า และไหล่"),
    ARMS_WRISTS("แขนและข้อมือ"),
    CORE_HIPS("แกนกลางและสะโพก"),
    QUADS("ต้นขาด้านหน้า"),
    HAMSTRINGS("หลังขาและสะโพกหลัง"),
    CALVES_ANKLES("น่องและข้อเท้า")
}

enum class RecoveryState(val thaiLabel: String) {
    FRESH("พร้อมเต็มที่"),
    MODERATE("ใช้งานปานกลาง"),
    FATIGUED("ควรพักฟื้น")
}

data class MuscleLoadStatus(
    val muscle: MuscleGroup,
    val fatiguePercent: Int,
    val recoveryState: RecoveryState
)

data class MuscleRecoveryReport(
    val muscles: Map<MuscleGroup, MuscleLoadStatus>,
    val recommendedSport: SportType,
    val recommendationReason: String,
    val overallReadinessPercent: Int
)

data class GhostRivalSnapshot(
    val hasRival: Boolean = false,
    val rivalDateLabel: String = "",
    val rivalDurationSeconds: Int = 0,
    val targetTotalSteps: Int = 0,
    val targetTotalMeters: Int = 0,
    val targetTotalReps: Int = 0,
    val targetTotalCalories: Int = 0
) {
    fun expectedProgressAt(elapsedSeconds: Int): GhostProgress {
        if (!hasRival || rivalDurationSeconds <= 0) return GhostProgress()
        val ratio = (elapsedSeconds.toDouble() / rivalDurationSeconds.toDouble()).coerceAtLeast(0.0)
        return GhostProgress(
            steps = (targetTotalSteps * ratio).roundToInt(),
            meters = (targetTotalMeters * ratio).roundToInt(),
            reps = (targetTotalReps * ratio).roundToInt(),
            calories = (targetTotalCalories * ratio).roundToInt()
        )
    }
}

data class GhostProgress(
    val steps: Int = 0,
    val meters: Int = 0,
    val reps: Int = 0,
    val calories: Int = 0
)

object MoveAnalyticsEngine {

    private val sportMuscleWeights: Map<SportType, Map<MuscleGroup, Double>> = mapOf(
        SportType.RUNNING to mapOf(
            MuscleGroup.CALVES_ANKLES to 1.0,
            MuscleGroup.QUADS to 0.85,
            MuscleGroup.HAMSTRINGS to 0.80,
            MuscleGroup.CORE_HIPS to 0.45
        ),
        SportType.WALKING to mapOf(
            MuscleGroup.CALVES_ANKLES to 0.45,
            MuscleGroup.HAMSTRINGS to 0.35,
            MuscleGroup.QUADS to 0.30,
            MuscleGroup.CORE_HIPS to 0.25
        ),
        SportType.JUMP_ROPE to mapOf(
            MuscleGroup.CALVES_ANKLES to 1.0,
            MuscleGroup.ARMS_WRISTS to 0.65,
            MuscleGroup.NECK_SHOULDERS to 0.50,
            MuscleGroup.QUADS to 0.55
        ),
        SportType.FOOTBALL to mapOf(
            MuscleGroup.QUADS to 0.90,
            MuscleGroup.HAMSTRINGS to 0.85,
            MuscleGroup.CALVES_ANKLES to 0.85,
            MuscleGroup.CORE_HIPS to 0.65
        ),
        SportType.BASKETBALL to mapOf(
            MuscleGroup.QUADS to 0.85,
            MuscleGroup.CALVES_ANKLES to 0.85,
            MuscleGroup.ARMS_WRISTS to 0.65,
            MuscleGroup.NECK_SHOULDERS to 0.55,
            MuscleGroup.CORE_HIPS to 0.50
        ),
        SportType.BADMINTON to mapOf(
            MuscleGroup.NECK_SHOULDERS to 0.85,
            MuscleGroup.ARMS_WRISTS to 0.90,
            MuscleGroup.QUADS to 0.65,
            MuscleGroup.CALVES_ANKLES to 0.70,
            MuscleGroup.CORE_HIPS to 0.55
        ),
        SportType.CYCLING to mapOf(
            MuscleGroup.QUADS to 1.0,
            MuscleGroup.HAMSTRINGS to 0.65,
            MuscleGroup.CORE_HIPS to 0.45,
            MuscleGroup.NECK_SHOULDERS to 0.30
        ),
        SportType.GENERAL to mapOf(
            MuscleGroup.CORE_HIPS to 0.75,
            MuscleGroup.NECK_SHOULDERS to 0.60,
            MuscleGroup.ARMS_WRISTS to 0.65,
            MuscleGroup.QUADS to 0.60
        )
    )

    fun calculateMuscleRecovery(
        workouts: List<WorkoutSession>,
        nowMillis: Long = System.currentTimeMillis()
    ): MuscleRecoveryReport {
        val scores = MuscleGroup.entries.associateWith { 0.0 }.toMutableMap()
        val window72hMs = 72L * 3600L * 1000L

        for (session in workouts) {
            val ageMs = (nowMillis - session.startedAtMillis).coerceAtLeast(0L)
            if (ageMs > window72hMs) continue

            val recencyDecay = when {
                ageMs <= 20L * 3600L * 1000L -> 1.0
                ageMs <= 44L * 3600L * 1000L -> 0.58
                else -> 0.25
            }
            val durationLoad = (session.durationSeconds / 1800.0).coerceIn(0.4, 2.0) // 30m = 1.0 unit
            val weights = sportMuscleWeights[session.sport].orEmpty()

            for ((muscle, w) in weights) {
                scores[muscle] = (scores[muscle] ?: 0.0) + (w * recencyDecay * durationLoad * 58.0)
            }
        }

        val statuses = scores.mapValues { (muscle, rawScore) ->
            val pct = rawScore.roundToInt().coerceIn(0, 100)
            val state = when {
                pct >= 70 -> RecoveryState.FATIGUED
                pct >= 35 -> RecoveryState.MODERATE
                else -> RecoveryState.FRESH
            }
            MuscleLoadStatus(muscle, pct, state)
        }

        val avgFatigue = statuses.values.map { it.fatiguePercent }.average()
        val readiness = (100.0 - avgFatigue).roundToInt().coerceIn(15, 100)

        // Recommend sport whose primary muscles currently have lowest fatigue
        val rankedSports = SportType.entries.sortedBy { sport ->
            val wMap = sportMuscleWeights[sport].orEmpty()
            if (wMap.isEmpty()) 50.0
            else {
                val totalW = wMap.values.sum()
                wMap.entries.sumOf { (m, w) -> (statuses[m]?.fatiguePercent ?: 0) * w } / totalW
            }
        }

        val bestSport = rankedSports.firstOrNull() ?: SportType.WALKING
        val tiredMuscle = statuses.values.maxByOrNull { it.fatiguePercent }
        val reason = when {
            workouts.isEmpty() -> "กล้ามเนื้อทุกส่วนสดชื่น 100% พร้อมเริ่มได้ทุกกีฬา"
            tiredMuscle != null && tiredMuscle.fatiguePercent >= 65 ->
                "${tiredMuscle.muscle.thaiName}ใช้งานหนักล่าสุด แนะนำสลับมาเล่น${bestSport.thaiName}เพื่อฟื้นตัว"
            else ->
                "กล้ามเนื้อสมดุลดี เหมาะสำหรับการ${bestSport.thaiName}วันนี้"
        }

        return MuscleRecoveryReport(
            muscles = statuses,
            recommendedSport = bestSport,
            recommendationReason = reason,
            overallReadinessPercent = readiness
        )
    }

    fun findBestGhostRival(
        workouts: List<WorkoutSession>,
        sport: SportType
    ): GhostRivalSnapshot {
        val pastForSport = workouts.filter { it.sport == sport && it.durationSeconds >= DateStreakUtils.MIN_WORKOUT_SECONDS }
        if (pastForSport.isEmpty()) return GhostRivalSnapshot(hasRival = false)

        val best = pastForSport.maxByOrNull { session ->
            when {
                sport.supportsDistance && session.distanceMeters > 0 -> session.distanceMeters.toDouble()
                sport.supportsReps && session.repsCount > 0 -> session.repsCount.toDouble()
                sport.supportsSteps && session.steps > 0 -> session.steps.toDouble()
                else -> session.caloriesBurned.toDouble()
            }
        } ?: return GhostRivalSnapshot(hasRival = false)

        return GhostRivalSnapshot(
            hasRival = true,
            rivalDateLabel = best.activityDate,
            rivalDurationSeconds = best.durationSeconds,
            targetTotalSteps = best.steps,
            targetTotalMeters = best.distanceMeters,
            targetTotalReps = best.repsCount,
            targetTotalCalories = best.caloriesBurned
        )
    }
}
