package com.example.domain

import com.example.data.model.WorkoutSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class StreakStatus(
    val currentStreak: Int,
    val longestStreak: Int,
    val hasCompletedToday: Boolean,
    val lastActiveDate: String,
    val statusMessageThai: String
)

data class DailyGoalSummary(
    val goalMinutes: Int,
    val completedSecondsToday: Int,
    val completedMinutesToday: Int,
    val remainingMinutesToday: Int,
    val progressFraction: Float,
    val progressPercent: Int,
    val isGoalReached: Boolean,
    val caloriesToday: Int,
    val stepsToday: Int,
    val distanceMetersToday: Int
)

data class ChartBarPoint(
    val label: String,
    val minutes: Int,
    val isCurrentPeriod: Boolean
)

object DateStreakUtils {
    const val MIN_WORKOUT_SECONDS = 900 // 15 minutes required to save a workout
    const val MIN_STREAK_DAY_SECONDS = 60 // >= 1 minute/day qualifies to maintain/increment Streak

    private val isoFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    private val thaiDateTimeFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("d MMM yyyy • HH:mm น.", Locale("th", "TH"))
    }

    private val thaiDayShortFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("EEE", Locale("th", "TH"))
    }

    fun todayDateString(nowMillis: Long = System.currentTimeMillis()): String {
        return isoFormat.get()!!.format(Date(nowMillis))
    }

    fun dateStringFromMillis(millis: Long): String {
        return isoFormat.get()!!.format(Date(millis))
    }

    fun offsetDateString(baseDate: String, deltaDays: Int): String {
        val parser = isoFormat.get()!!
        val parsed = runCatching { parser.parse(baseDate) }.getOrNull() ?: Date()
        val cal = Calendar.getInstance().apply {
            time = parsed
            add(Calendar.DAY_OF_YEAR, deltaDays)
        }
        return parser.format(cal.time)
    }

    fun formatDurationClock(totalSeconds: Int): String {
        val safe = max(0, totalSeconds)
        val hrs = safe / 3600
        val mins = (safe % 3600) / 60
        val secs = safe % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun formatMinutesThai(totalSeconds: Int): String {
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return when {
            mins >= 60 -> {
                val h = mins / 60
                val m = mins % 60
                if (m == 0) "$h ชม." else "$h ชม. $m นาที"
            }
            mins > 0 && secs > 0 -> "$mins นาที $secs วิ"
            mins > 0 -> "$mins นาที"
            else -> "$secs วินาที"
        }
    }

    fun formatDateTimeThai(millis: Long): String {
        return thaiDateTimeFormat.get()!!.format(Date(millis))
    }

    fun formatDistanceKm(distanceMeters: Int): String {
        return String.format(Locale.US, "%.2f กม.", distanceMeters / 1000.0)
    }

    /**
     * Real date-based Streak calculator:
     * - A day qualifies if total workout duration on that calendar date >= 60 seconds (1 minute).
     * - If today already qualifies: count consecutive qualifying days backward starting from today.
     * - If today does NOT yet qualify, but yesterday qualified: keep streak from yesterday without incrementing today,
     *   prompting the user to exercise >= 1 minute today to extend by +1.
     * - Otherwise streak is 0 (next qualifying workout starts a fresh 1-day streak).
     */
    fun calculateStreak(
        workouts: List<WorkoutSession>,
        storedLongestStreak: Int = 0,
        today: String = todayDateString()
    ): StreakStatus {
        val secondsByDate = mutableMapOf<String, Int>()
        for (w in workouts) {
            val day = w.activityDate.ifBlank {
                if (w.startedAtMillis > 0L) dateStringFromMillis(w.startedAtMillis) else ""
            }
            if (day.length == 10) {
                secondsByDate[day] = (secondsByDate[day] ?: 0) + max(0, w.durationSeconds)
            }
        }

        val qualifyingDates = secondsByDate.filterValues { it >= MIN_STREAK_DAY_SECONDS }.keys
        val hasCompletedToday = qualifyingDates.contains(today)
        val yesterday = offsetDateString(today, -1)

        var streak = 0
        var cursor = when {
            hasCompletedToday -> today
            qualifyingDates.contains(yesterday) -> yesterday
            else -> null
        }

        while (cursor != null && qualifyingDates.contains(cursor)) {
            streak++
            cursor = offsetDateString(cursor, -1)
        }

        // Also scan all historical runs to compute accurate all-time longest streak
        val sortedDates = qualifyingDates.sorted()
        var historicalMax = 0
        var running = 0
        var prevDate: String? = null
        for (d in sortedDates) {
            running = if (prevDate != null && offsetDateString(prevDate, 1) == d) {
                running + 1
            } else {
                1
            }
            historicalMax = max(historicalMax, running)
            prevDate = d
        }

        val longest = max(storedLongestStreak, max(streak, historicalMax))
        val lastActive = sortedDates.lastOrNull() ?: ""

        val message = when {
            hasCompletedToday && streak == 1 -> "เริ่มต้น Streak วันแรกสำเร็จแล้ว!"
            hasCompletedToday && streak > 1 -> "ยอดเยี่ยม! รักษาความต่อเนื่องครบวันนี้แล้ว"
            streak > 0 -> "ออกกำลังกายอย่างน้อย 1 นาทีวันนี้เพื่อเพิ่มเป็น ${streak + 1} วัน"
            else -> "เริ่มออกกำลังกายวันนี้เพื่อสร้าง Streak วันแรก"
        }

        return StreakStatus(
            currentStreak = streak,
            longestStreak = longest,
            hasCompletedToday = hasCompletedToday,
            lastActiveDate = lastActive,
            statusMessageThai = message
        )
    }

    fun calculateDailyGoalSummary(
        workouts: List<WorkoutSession>,
        goalMinutes: Int,
        today: String = todayDateString()
    ): DailyGoalSummary {
        val safeGoal = goalMinutes.coerceIn(5, 300)
        val todayWorkouts = workouts.filter {
            val day = it.activityDate.ifBlank {
                if (it.startedAtMillis > 0L) dateStringFromMillis(it.startedAtMillis) else ""
            }
            day == today
        }
        val totalSeconds = todayWorkouts.sumOf { max(0, it.durationSeconds) }
        val completedMinutes = totalSeconds / 60
        val remainingMinutes = max(0, safeGoal - completedMinutes)
        val goalSeconds = safeGoal * 60
        val fraction = if (goalSeconds > 0) min(1.5f, totalSeconds.toFloat() / goalSeconds.toFloat()) else 0f
        val percent = if (safeGoal > 0) ((totalSeconds * 100.0) / goalSeconds).toInt().coerceAtLeast(0) else 0

        return DailyGoalSummary(
            goalMinutes = safeGoal,
            completedSecondsToday = totalSeconds,
            completedMinutesToday = completedMinutes,
            remainingMinutesToday = remainingMinutes,
            progressFraction = fraction.coerceIn(0f, 1f),
            progressPercent = percent,
            isGoalReached = totalSeconds >= goalSeconds,
            caloriesToday = todayWorkouts.sumOf { max(0, it.caloriesBurned) },
            stepsToday = todayWorkouts.sumOf { max(0, it.steps) },
            distanceMetersToday = todayWorkouts.sumOf { max(0, it.distanceMeters) }
        )
    }

    fun buildLast7DaysBars(
        workouts: List<WorkoutSession>,
        today: String = todayDateString()
    ): List<ChartBarPoint> {
        val parser = isoFormat.get()!!
        val labelFmt = thaiDayShortFormat.get()!!
        val secondsByDate = workouts.groupBy {
            it.activityDate.ifBlank {
                if (it.startedAtMillis > 0L) dateStringFromMillis(it.startedAtMillis) else ""
            }
        }.mapValues { (_, list) -> list.sumOf { max(0, it.durationSeconds) } }

        return (6 downTo 0).map { daysAgo ->
            val dateStr = offsetDateString(today, -daysAgo)
            val dateObj = runCatching { parser.parse(dateStr) }.getOrNull() ?: Date()
            val label = if (daysAgo == 0) "วันนี้" else labelFmt.format(dateObj)
            val mins = (secondsByDate[dateStr] ?: 0) / 60
            ChartBarPoint(label = label, minutes = mins, isCurrentPeriod = daysAgo == 0)
        }
    }

    fun buildLast4WeeksBars(
        workouts: List<WorkoutSession>,
        today: String = todayDateString()
    ): List<ChartBarPoint> {
        val parser = isoFormat.get()!!
        val todayDate = runCatching { parser.parse(today) }.getOrNull() ?: Date()
        val calToday = Calendar.getInstance().apply {
            time = todayDate
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val labels = listOf("3 สัปดาห์ก่อน", "2 สัปดาห์ก่อน", "สัปดาห์ก่อน", "สัปดาห์นี้")
        val weekSeconds = IntArray(4)

        for (w in workouts) {
            val dayStr = w.activityDate.ifBlank {
                if (w.startedAtMillis > 0L) dateStringFromMillis(w.startedAtMillis) else ""
            }
            val parsed = runCatching { parser.parse(dayStr) }.getOrNull() ?: continue
            val diffMillis = calToday.timeInMillis - parsed.time
            if (diffMillis < 0) continue
            val diffDays = (diffMillis / (24L * 60L * 60L * 1000L)).toInt()
            val weekBucketFromEnd = diffDays / 7
            if (weekBucketFromEnd in 0..3) {
                val idx = 3 - weekBucketFromEnd
                weekSeconds[idx] += max(0, w.durationSeconds)
            }
        }

        return (0..3).map { idx ->
            ChartBarPoint(
                label = labels[idx],
                minutes = weekSeconds[idx] / 60,
                isCurrentPeriod = idx == 3
            )
        }
    }
}
