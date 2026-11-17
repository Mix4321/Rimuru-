package com.example.domain

import com.example.data.model.SportType
import com.example.data.model.WorkoutSession
import java.util.Locale
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

data class AdaptiveDailyPlan(
    val titleThai: String,
    val subtitleThai: String,
    val suggestedSport: SportType,
    val suggestedWorkSec: Int,
    val suggestedRestSec: Int,
    val suggestedRounds: Int,
    val isHiitRecommended: Boolean,
    val badgeTag: String
)

data class AchievementBadge(
    val id: String,
    val titleThai: String,
    val descriptionThai: String,
    val iconKey: String,
    val unlocked: Boolean,
    val progressFraction: Float,
    val progressLabel: String
)

data class AthleteProgression(
    val level: Int,
    val rankTitleThai: String,
    val totalXp: Int,
    val xpIntoLevel: Int,
    val xpNeededForNextLevel: Int,
    val levelProgressFraction: Float,
    val unlockedCount: Int,
    val badges: List<AchievementBadge>
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
            val durationLoad = (session.durationSeconds / 1800.0).coerceIn(0.15, 2.0) // Flexible session duration load
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

    fun buildAdaptiveDailyPlan(
        report: MuscleRecoveryReport,
        workouts: List<WorkoutSession>
    ): AdaptiveDailyPlan {
        val sport = report.recommendedSport
        return when {
            report.overallReadinessPercent >= 85 -> AdaptiveDailyPlan(
                titleThai = "HIIT & Circuit เผาผลาญเต็มพิกัด (${sport.thaiName})",
                subtitleThai = "ความพร้อมกล้ามเนื้อ ${report.overallReadinessPercent}% — ลุยรอบเร็ว 30/15 วิ หรือจับเวลาอิสระได้ทันที",
                suggestedSport = if (workouts.isEmpty()) SportType.GENERAL else sport,
                suggestedWorkSec = 30,
                suggestedRestSec = 15,
                suggestedRounds = 4,
                isHiitRecommended = true,
                badgeTag = "ฟิตเต็มร้อย"
            )
            report.overallReadinessPercent >= 62 -> AdaptiveDailyPlan(
                titleThai = "โฟกัสกล้ามเนื้อฟื้นตัว: ${sport.thaiName}",
                subtitleThai = report.recommendationReason,
                suggestedSport = sport,
                suggestedWorkSec = 40,
                suggestedRestSec = 20,
                suggestedRounds = 4,
                isHiitRecommended = sport == SportType.GENERAL || sport == SportType.JUMP_ROPE,
                badgeTag = "สมดุลกล้ามเนื้อ"
            )
            else -> AdaptiveDailyPlan(
                titleThai = "Active Recovery: ${SportType.WALKING.thaiName}เบาๆ",
                subtitleThai = "กล้ามเนื้อเพิ่งผ่านงานหนัก แนะนำเคลื่อนไหวเบาๆ แบบไม่เร่งเพื่อเร่งการฟื้นตัว",
                suggestedSport = SportType.WALKING,
                suggestedWorkSec = 0,
                suggestedRestSec = 0,
                suggestedRounds = 0,
                isHiitRecommended = false,
                badgeTag = "โหมดฟื้นฟู"
            )
        }
    }

    fun buildAthleteProgression(
        workouts: List<WorkoutSession>,
        streak: StreakStatus
    ): AthleteProgression {
        val totalMinutes = workouts.sumOf { it.durationSeconds } / 60
        val totalCalories = workouts.sumOf { it.caloriesBurned }
        val totalReps = workouts.sumOf { it.repsCount }
        val totalDistanceMeters = workouts.sumOf { it.distanceMeters }
        val bestFormScore = workouts.maxOfOrNull { it.avgFormScore } ?: 0
        val hiitCount = workouts.count { it.workoutSubType.contains("HIIT", ignoreCase = true) || it.workoutSubType.contains("CIRCUIT", ignoreCase = true) }
        val cameraWorkoutCount = workouts.count { it.mode.name == "CAMERA_AI" }

        val totalXp = (workouts.size * 35) +
            (totalCalories / 2) +
            (totalMinutes * 4) +
            (streak.longestStreak * 40) +
            (totalReps / 5)

        val xpPerLevel = 250
        val level = (totalXp / xpPerLevel) + 1
        val xpIntoLevel = totalXp % xpPerLevel
        val progress = (xpIntoLevel.toFloat() / xpPerLevel.toFloat()).coerceIn(0f, 1f)

        val rankTitle = when {
            level >= 15 -> "ตำนานสายฟิต MOVE"
            level >= 10 -> "นักกีฬาระดับโปร"
            level >= 6 -> "นักสู้สายอึด"
            level >= 3 -> "สายแอคทีฟตัวจริง"
            else -> "ผู้เริ่มต้น MOVE"
        }

        val badges = listOf(
            AchievementBadge(
                id = "first_step",
                titleThai = "ก้าวแรกสู่เส้นชัย",
                descriptionThai = "บันทึกการออกกำลังกายครั้งแรกสำเร็จ",
                iconKey = "FIRST",
                unlocked = workouts.isNotEmpty(),
                progressFraction = if (workouts.isNotEmpty()) 1f else 0f,
                progressLabel = "${workouts.size.coerceAtMost(1)}/1"
            ),
            AchievementBadge(
                id = "streak_3",
                titleThai = "สายฟิต 3 วันติด",
                descriptionThai = "ออกกำลังกายต่อเนื่อง 3 วัน",
                iconKey = "STREAK",
                unlocked = streak.longestStreak >= 3,
                progressFraction = (streak.longestStreak / 3f).coerceIn(0f, 1f),
                progressLabel = "${streak.longestStreak.coerceAtMost(3)}/3 วัน"
            ),
            AchievementBadge(
                id = "streak_7",
                titleThai = "สัปดาห์เหล็ก 7 วัน",
                descriptionThai = "รักษาความต่อเนื่องครบ 7 วัน",
                iconKey = "STREAK7",
                unlocked = streak.longestStreak >= 7,
                progressFraction = (streak.longestStreak / 7f).coerceIn(0f, 1f),
                progressLabel = "${streak.longestStreak.coerceAtMost(7)}/7 วัน"
            ),
            AchievementBadge(
                id = "kcal_500",
                titleThai = "นักเผาผลาญ 500 kcal",
                descriptionThai = "เผาผลาญสะสมครบ 500 กิโลแคลอรี",
                iconKey = "BURN",
                unlocked = totalCalories >= 500,
                progressFraction = (totalCalories / 500f).coerceIn(0f, 1f),
                progressLabel = "${totalCalories.coerceAtMost(500)}/500"
            ),
            AchievementBadge(
                id = "ai_form_90",
                titleThai = "ฟอร์มเทพ AI 90%+",
                descriptionThai = "ได้คะแนนฟอร์มเฉลี่ยจากกล้อง AI อย่างน้อย 90%",
                iconKey = "AI_FORM",
                unlocked = bestFormScore >= 90,
                progressFraction = (bestFormScore / 90f).coerceIn(0f, 1f),
                progressLabel = "$bestFormScore/90%"
            ),
            AchievementBadge(
                id = "distance_3km",
                titleThai = "นักวิ่งระยะ 3 กม.",
                descriptionThai = "สะสมระยะทางเดิน/วิ่ง/ปั่น ครบ 3 กิโลเมตร",
                iconKey = "DISTANCE",
                unlocked = totalDistanceMeters >= 3000,
                progressFraction = (totalDistanceMeters / 3000f).coerceIn(0f, 1f),
                progressLabel = "${String.format(Locale.US, "%.1f", (totalDistanceMeters / 1000f).coerceAtMost(3f))}/3.0 กม."
            ),
            AchievementBadge(
                id = "rep_master_50",
                titleThai = "จอมพลัง 50 ครั้ง",
                descriptionThai = "สะสมจำนวนครั้ง/กระโดดครบ 50 ครั้ง",
                iconKey = "REPS",
                unlocked = totalReps >= 50,
                progressFraction = (totalReps / 50f).coerceIn(0f, 1f),
                progressLabel = "${totalReps.coerceAtMost(50)}/50"
            ),
            AchievementBadge(
                id = "hiit_challenger",
                titleThai = "สาย HIIT & AI",
                descriptionThai = "ฝึกโหมด HIIT หรือกล้อง AI ครบ 2 ครั้ง",
                iconKey = "HIIT",
                unlocked = (hiitCount + cameraWorkoutCount) >= 2,
                progressFraction = ((hiitCount + cameraWorkoutCount) / 2f).coerceIn(0f, 1f),
                progressLabel = "${(hiitCount + cameraWorkoutCount).coerceAtMost(2)}/2"
            )
        )

        return AthleteProgression(
            level = level,
            rankTitleThai = rankTitle,
            totalXp = totalXp,
            xpIntoLevel = xpIntoLevel,
            xpNeededForNextLevel = xpPerLevel,
            levelProgressFraction = progress,
            unlockedCount = badges.count { it.unlocked },
            badges = badges
        )
    }
}
